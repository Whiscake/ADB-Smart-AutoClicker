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

import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.util.Log

import com.buzbuz.smartautoclicker.domain.Action
import com.buzbuz.smartautoclicker.domain.Action.Click
import com.buzbuz.smartautoclicker.domain.Action.Pause
import com.buzbuz.smartautoclicker.domain.Action.Swipe
import com.buzbuz.smartautoclicker.domain.putExtra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Execute the actions of an event.
 *
 * @param androidExecutor the executor for the actions requiring an interaction with Android.
 */
internal class ActionExecutor(private val androidExecutor: AndroidExecutor) {

    /**
     * Execute the provided actions.
     * @param actions the actions to be executed.
     * @param conditionPosition the position of the detected condition.
     */
    suspend fun executeActions(actions: List<Action>, conditionPosition: Point?) {
        actions.forEach { action ->
            when (action) {
                is Click -> executeClick(action, conditionPosition)
                is Swipe -> executeSwipe(action)
                is Pause -> executePause(action)
                is Action.Intent -> executeIntent(action)
                is Action.Adb -> executeAdb(action)
            }
        }
    }

    /**
     * Execute the provided click.
     * @param click the click to be executed.
     */
    private suspend fun executeClick(click: Click, conditionPosition: Point?) {
        val clickPath = Path()
        val clickBuilder = GestureDescription.Builder()

        if (click.clickOnCondition) {
            conditionPosition?.let { conditionCenter ->
                clickPath.moveTo(conditionCenter.x.toFloat(), conditionCenter.y.toFloat())
            } ?: run {
                Log.w(TAG, "Can't click on position, there is no condition position")
                return
            }
        } else {
            clickPath.moveTo(click.x!!.toFloat(), click.y!!.toFloat())
        }
        clickBuilder.addStroke(GestureDescription.StrokeDescription(clickPath, 0, click.pressDuration!!))

        withContext(Dispatchers.Main) {
            androidExecutor.executeGesture(clickBuilder.build())
        }
        delay(click.pressDuration!!)
    }

    /**
     * Execute the provided swipe.
     * @param swipe the swipe to be executed.
     */
    private suspend fun executeSwipe(swipe: Swipe) {
        val swipePath = Path()
        val clickBuilder = GestureDescription.Builder()

        swipePath.moveTo(swipe.fromX!!.toFloat(), swipe.fromY!!.toFloat())
        swipePath.lineTo(swipe.toX!!.toFloat(), swipe.toY!!.toFloat())
        clickBuilder.addStroke(GestureDescription.StrokeDescription(swipePath, 0, swipe.swipeDuration!!))

        withContext(Dispatchers.Main) {
            androidExecutor.executeGesture(clickBuilder.build())
        }
        delay(swipe.swipeDuration!!)
    }

    /**
     * Execute the provided pause.
     * @param pause the pause to be executed.
     */
    private suspend fun executePause(pause: Pause) {
        delay(pause.pauseDuration!!)
    }

    /**
     * Execute the provided intent.
     * @param intent the intent to be executed.
     */
    private suspend fun executeIntent(intent: Action.Intent) {
        val androidIntent = Intent().apply {
            action = intent.intentAction!!
            flags = intent.flags!!

            intent.componentName?.let {
                component = intent.componentName
            }

            intent.extras?.forEach { putExtra(it) }
        }

        if (intent.isBroadcast!!) {
            withContext(Dispatchers.Main) {
                androidExecutor.executeSendBroadcast(androidIntent)
            }
            delay(INTENT_BROADCAST_DELAY)
        } else {
            withContext(Dispatchers.Main) {
                androidExecutor.executeStartActivity(androidIntent)
            }
            delay(INTENT_START_ACTIVITY_DELAY)
        }
    }

    /**
     * Send the provided adb command to the bridge running on the host computer.
     * A failure is logged and never interrupts the scenario.
     * @param adb the adb command to be sent.
     */
    private suspend fun executeAdb(adb: Action.Adb) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("http", adb.host!!, adb.port!!, "/adb").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = ADB_CONNECT_TIMEOUT
                    readTimeout = ADB_READ_TIMEOUT
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    adb.token?.let { setRequestProperty("X-Adb-Bridge-Token", it) }
                }
                val body = "command=" + URLEncoder.encode(adb.command!!, "UTF-8")
                connection.outputStream.use { it.write(body.toByteArray()) }

                val response = (if (connection.responseCode < 400) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }
                if (connection.responseCode >= 400) {
                    Log.w(TAG, "Adb bridge rejected the command (${connection.responseCode}): $response")
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Can't send adb command to ${adb.host}:${adb.port}", ex)
            } finally {
                connection?.disconnect()
            }
            Unit
        }
    }
}

/** Execute the actions related to Android. */
interface AndroidExecutor {

    /** Execute the provided gesture. */
    fun executeGesture(gestureDescription: GestureDescription)

    /** Start the activity defined by the provided intent. */
    fun executeStartActivity(intent: Intent)

    /** Send a broadcast defined by the provided intent. */
    fun executeSendBroadcast(intent: Intent)
}

/** Tag for logs. */
private const val TAG = "ActionExecutor"
/** Waiting delay after a start activity to avoid overflowing the system. */
private const val INTENT_START_ACTIVITY_DELAY = 1000L
/** Waiting delay after a broadcast to avoid overflowing the system. */
private const val INTENT_BROADCAST_DELAY = 100L
/** Connection timeout to the adb bridge, in milliseconds. */
private const val ADB_CONNECT_TIMEOUT = 3000
/** Maximum time to wait for the adb bridge to run the command, in milliseconds. */
private const val ADB_READ_TIMEOUT = 15000