/*
 * Copyright (C) 2022 Nain57
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import android.util.Log

import com.buzbuz.smartautoclicker.domain.Repository
import com.buzbuz.smartautoclicker.domain.Event
import com.buzbuz.smartautoclicker.detection.NativeDetector
import com.buzbuz.smartautoclicker.baseui.ScreenMetrics
import com.buzbuz.smartautoclicker.domain.EndCondition
import com.buzbuz.smartautoclicker.domain.Scenario

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Detects [Event] conditions on a display and execute its actions.
 *
 * In order to detect, you must start recording the screen to get images to detect on, this can be done by calling
 * [startScreenRecord]. Then, to take a screenshot of the screen, you can use [captureArea]. Or, you can start the
 * detection of a list of [Event] by using [startDetection].
 * The states of the recording and the detection are available in [isScreenRecording] and [isDetecting] respectively.
 * Once you no longer needs to capture or detect, call [stopDetection] or [stopScreenRecord] to release all processing resources.
 *
 * @param context the Android context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetectorEngine(context: Context) {

    companion object {

        /** Tag for logs */
        private const val TAG = "DetectorEngine"
        /** Waiting delay when the screen has no new image, instead of polling it endlessly. */
        private const val NO_IMAGE_DELAY_MS = 10L
        /** Waiting delay after an image failed to be processed. */
        private const val ERROR_DELAY_MS = 500L
        /** With no new image for this long, the screen record is started again, as some emulators stop feeding it. */
        private const val STALE_SCREEN_MS = 10_000L
        /** Singleton preventing multiple instances of the repository at the same time. */
        @Volatile
        private var INSTANCE: DetectorEngine? = null

        /**
         * Get the engine singleton, or instantiates it if it wasn't yet.
         *
         * @param context the Android context.
         *
         * @return the engine singleton.
         */
        fun getDetectorEngine(context: Context): DetectorEngine {
            return INSTANCE ?: synchronized(this) {
                Log.i(TAG, "Instantiates new detector engine")
                val instance = DetectorEngine(context)
                INSTANCE = instance
                instance
            }
        }

        /** Clear this singleton instance, forcing to instantiates it again. */
        private fun cleanInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }

    /** Repository providing data for the scenario. */
    private val scenarioRepository = Repository.getRepository(context)
    /** Monitors the state of the screen. */
    private val screenMetrics = ScreenMetrics(context)
    private val appContext = context.applicationContext

    /** Record the screen and provide images via [ScreenRecorder.acquireLatestImage]. */
    private val screenRecorder = ScreenRecorder()
    /** The executor for the actions requiring an interaction with Android. */
    private var androidExecutor: AndroidExecutor? = null

    /** The scope for the flows declared in the detector engine. */
    private val detectorEngineScope = CoroutineScope(Job() + Dispatchers.IO)
    /** Coroutine scope for the image processing. */
    private var processingScope: CoroutineScope? = null
    /** Coroutine job for the image currently processed. */
    private var processingJob: Job? = null

    /** Tells if we are screen recording.  */
    private val isScreenRecording = MutableStateFlow(false)
    /** Backing property for [isDetecting]. */
    private val _isDetecting = MutableStateFlow(false)
    /** Backing property for [isDetecting]. */
    private val _isDebugging = MutableStateFlow(false)

    /** True if we are currently detecting clicks, false if not. */
    val isDetecting: StateFlow<Boolean> = _isDetecting
    /** True if we are collecting debug data, false if not. */
    val isDebugging: StateFlow<Boolean> = _isDebugging

    /** Engine for debugging purposes. */
    val debugEngine = DebugEngine()

    /** The current scenario. */
    private val _scenario = MutableStateFlow<Scenario?>(null)
    /** The list of events for the [_scenario]. */
    val scenarioEvents: StateFlow<List<Event>> = _scenario
        .flatMapLatest {
            it?.let { event ->
                scenarioRepository.getCompleteEventList(event.id)
            } ?: flow { emit(emptyList<Event>()) }
        }
        .stateIn(
            detectorEngineScope,
            SharingStarted.Eagerly,
            emptyList()
        )
    /** The scenario with its end conditions. */
    val scenarioEndConditions: StateFlow<Pair<Scenario, List<EndCondition>>?> = _scenario
        .flatMapLatest {
            it?.let { scenario ->
                scenarioRepository.getScenarioWithEndConditionsFlow(scenario.id)
            } ?: emptyFlow()
        }
        .stateIn(
            detectorEngineScope,
            SharingStarted.Eagerly,
            null
        )

    /**
     * Start the screen detection.
     *
     * This requires the media projection permission code and its data intent, they both can be retrieved using the
     * results of the activity intent provided by [MediaProjectionManager.createScreenCaptureIntent] (this Intent shows
     * the dialog warning about screen recording privacy). Any attempt to call this method without the correct screen
     * capture intent result will leads to a crash.
     *
     * Once started, you can use [captureArea] or [startDetection]. Once your are done, call [stopScreenRecord].
     *
     * @param context the Android context.
     * @param resultCode the result code provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param data the data intent provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param scenario the current scenario used with the detection.
     * @param androidExecutor the executor for the actions requiring an interaction with Android.
     */
    fun startScreenRecord(
        context: Context,
        resultCode: Int,
        data: Intent,
        scenario: Scenario,
        androidExecutor: AndroidExecutor
    ) {
        if (isScreenRecording.value) {
            Log.w(TAG, "startScreenRecord: Screen record is already started")
            return
        }

        Log.i(TAG, "startScreenRecord")

        this.androidExecutor = androidExecutor
        screenMetrics.registerOrientationListener(::onOrientationChanged)

        processingScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

        screenRecorder.apply {
            startProjection(context, resultCode, data) {
                this@DetectorEngine.stopScreenRecord()
            }

            processingScope?.launch {
                startScreenRecord(context, screenMetrics.screenSize)
                isScreenRecording.emit(true)
                _scenario.emit(scenario)
            }
        }
    }

    /**
     * Capture the provided area on the next [Image] of the screen.
     *
     * After calling this method, the next [Image] processed by the [processLatestImage] will be cropped to the provided area
     * and a bitmap will be generated from it, then notified through the provided callback.
     * [isScreenRecording] should be true to capture. Calling [stopScreenRecord] will drop any capture info provided here.
     *
     * @param area the area of the screen to be captured.
     * @param callback the object to notify upon capture completion.
     */
    fun captureArea(area: Rect, callback: (Bitmap) -> Unit) {
        if (!isScreenRecording.value) {
            Log.w(TAG, "captureArea: Screen record is not started.")
            return
        }

        processingScope?.launch {
            var image: Image?
            do {
                image = screenRecorder.acquireLatestImage()
                image?.use {
                    val bitmap = Bitmap.createBitmap(
                        it.toBitmap(),
                        area.left,
                        area.top,
                        area.width(),
                        area.height()
                    )

                    withContext(Dispatchers.Main) {
                        callback(bitmap)
                    }
                }
            } while (image == null)
        }
    }

    /**
     * Start the screen detection.
     *
     * After calling this method, all [Image] displayed on the screen will be checked for the provided clicks conditions
     * fulfillment. For each image, the first event in the list that is detected will be notified through the provided
     * callback.
     * [isScreenRecording] should be true to capture. Detection can be stopped with [stopDetection] or [stopScreenRecord].
     *
     * @param debugMode true to get the debug info via the [debugEngine], false if not.
     */
    fun startDetection(debugMode: Boolean = false) {
        if (!isScreenRecording.value) {
            Log.w(TAG, "captureArea: Screen record is not started.")
            return
        } else if (_isDetecting.value) {
            Log.w(TAG, "captureArea: detection is already started.")
            return
        } else if (scenarioEndConditions.value == null) {
            Log.w(TAG, "startDetection: the scenario is not loaded yet.")
            return
        }

        Log.i(TAG, "startDetection")

        _isDetecting.value = true
        _isDebugging.value = debugMode

        processingJob = launchProcessing(debugMode)
    }

    private fun launchProcessing(debugMode: Boolean): Job? = processingScope?.launch {
        NativeDetector().use { imageDetector ->
            val scenarioProcessor = ScenarioProcessor(
                imageDetector = imageDetector,
                detectionQuality = scenarioEndConditions.value!!.first.detectionQuality,
                events = scenarioEvents.value,
                bitmapSupplier = { path, width, height ->
                    scenarioRepository.getBitmapNow(path, width, height)
                },
                androidExecutor = androidExecutor!!,
                endConditionOperator = scenarioEndConditions.value!!.first.endConditionOperator,
                endConditions =  scenarioEndConditions.value!!.second,
                onEndConditionReached = {
                    stopDetection()
                },
                debugEngine = if (debugMode) debugEngine else null,
                isAdbEnabled = appContext::isAdbEnabled,
                bridge = appContext::bridge,
            )

            processLatestImage(scenarioProcessor)
        }
    }

    /**
     * Stop the screen detection started with [startDetection].
     *
     * After a call to this method, the events provided in the start method will no longer be checked on the current
     * image. Note that this will not stop the screen recording, you should still call [stopScreenRecord] to completely
     * release the [DetectorEngine] resources.
     */
    fun startDetectionWhenReady() {
        detectorEngineScope.launch {
            isScreenRecording.first { it }
            scenarioEvents.first { it.isNotEmpty() }
            scenarioEndConditions.first { it != null }
            withContext(Dispatchers.Main) { startDetection() }
        }
    }

    fun stopDetection() {
        Log.i(TAG, "stopDetection")

        _isDetecting.value = false
        _isDebugging.value = false

        val job = processingJob
        processingJob = null
        job?.cancel()
        processingScope?.launch {
            job?.join()
            debugEngine.clear()
        }
    }

    /**
     * Stop the screen recording and the detection, if any.
     *
     * First, calls [stopDetection] if the detection was active. Then, stop the screen recording and release any related
     * resources.
     */
    fun stopScreenRecord() {
        if (!isScreenRecording.value) {
            Log.w(TAG, "stop: Screen record is already stopped.")
            return
        } else if (_isDetecting.value) {
            stopDetection()
        }

        Log.i(TAG, "stopScreenRecord")

        screenMetrics.unregisterOrientationListener()
        processingScope?.launch {
            screenRecorder.stopProjection()
            isScreenRecording.emit(false)

            processingScope?.cancel()
            processingScope = null
        }
    }

    /**
     * Called when the orientation of the screen changes.
     * As we now have different screen metrics, we need to stop and start the virtual display with the correct one.
     *
     * @param context the Android context.
     */
    private fun onOrientationChanged(context: Context) {
        val job = processingJob
        processingJob = null
        job?.cancel()
        processingScope?.launch {
            job?.join()

            screenRecorder.stopScreenRecord()
            screenRecorder.startScreenRecord(context, screenMetrics.screenSize)

            if (job != null && _isDetecting.value) processingJob = launchProcessing(_isDebugging.value)
        }
    }

    /**
     * Process the latest images provided by the [ScreenRecorder] until the processing is cancelled.
     * An image that can't be processed is skipped, so one faulty event never stops the scenario.
     */
    private suspend fun processLatestImage(scenarioProcessor: ScenarioProcessor) {
        var lastImageAt = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive) {
            try {
                val image = screenRecorder.acquireLatestImage()
                if (image != null) {
                    lastImageAt = SystemClock.elapsedRealtime()
                    image.use { scenarioProcessor.process(it) }
                } else if (SystemClock.elapsedRealtime() - lastImageAt > STALE_SCREEN_MS) {
                    Log.w(TAG, "No new screen image for ${STALE_SCREEN_MS / 1000}s, starting the screen record again")
                    screenRecorder.stopScreenRecord()
                    screenRecorder.startScreenRecord(appContext, screenMetrics.screenSize)
                    lastImageAt = SystemClock.elapsedRealtime()
                } else {
                    delay(NO_IMAGE_DELAY_MS)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Log.e(TAG, "Can't process the screen image, skipping it", ex)
                delay(ERROR_DELAY_MS)
            }
            yield()
        }
    }

    /** Clear this engine. It can't be used after this call. */
    fun clear() {
        if (isScreenRecording.value) {
            Log.w(TAG, "Clearing the detector but it was still started.")
            stopScreenRecord()
        }

        Log.i(TAG, "clear")
        detectorEngineScope.cancel()
        androidExecutor = null
        cleanInstance()
    }
}
