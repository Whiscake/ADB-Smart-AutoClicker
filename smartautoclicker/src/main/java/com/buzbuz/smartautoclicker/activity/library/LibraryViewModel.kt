/*
 * Copyright (C) 2026 Whiscake
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
package com.buzbuz.smartautoclicker.activity.library

import android.app.Application
import android.content.Context
import android.graphics.Point
import android.net.Uri

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.baseui.ScreenMetrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A config listed in the library index. */
data class LibraryConfig(
    val name: String,
    val description: String,
    val author: String,
    val screen: Point?,
    val events: Int,
    val url: String,
)

sealed class LibraryState {
    object Loading : LibraryState()
    data class Loaded(val configs: List<LibraryConfig>, val downloading: LibraryConfig? = null) : LibraryState()
    data class Error(val reason: String) : LibraryState()
    data class Downloaded(val file: Uri) : LibraryState()
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state

    val screen: Point = ScreenMetrics(application.applicationContext).screenSize.let {
        Point(minOf(it.x, it.y), maxOf(it.x, it.y))
    }

    fun load() {
        _state.value = LibraryState.Loading
        viewModelScope.launch {
            _state.value = try {
                val index = application().libraryUrl()
                val configs = withContext(Dispatchers.IO) { parseIndex(index, URL(index).readText()) }
                LibraryState.Loaded(configs.sortedWith(compareBy({ it.screen != screen }, { it.name.lowercase() })))
            } catch (ex: Exception) {
                LibraryState.Error(ex.message ?: ex.javaClass.simpleName)
            }
        }
    }

    fun download(config: LibraryConfig) {
        val loaded = _state.value as? LibraryState.Loaded ?: return
        _state.value = loaded.copy(downloading = config)
        viewModelScope.launch {
            _state.value = try {
                val file = withContext(Dispatchers.IO) {
                    File(application().cacheDir, LIBRARY_FILE).also { URL(config.url).download(it) }
                }
                LibraryState.Downloaded(Uri.fromFile(file))
            } catch (ex: Exception) {
                LibraryState.Error(ex.message ?: ex.javaClass.simpleName)
            }
        }
    }

    private fun application(): Context = getApplication()

    private fun parseIndex(indexUrl: String, json: String): List<LibraryConfig> {
        val configs = JSONObject(json).getJSONArray("configs")
        return (0 until configs.length()).mapNotNull { i ->
            runCatching {
                configs.getJSONObject(i).run {
                    LibraryConfig(
                        name = getString("name"),
                        description = optString("description"),
                        author = optString("author"),
                        screen = optString("screen").split("x").mapNotNull { it.trim().toIntOrNull() }
                            .takeIf { it.size == 2 }?.let { (w, h) -> Point(minOf(w, h), maxOf(w, h)) },
                        events = optInt("events"),
                        url = URL(URL(indexUrl), getString("file")).toString(),
                    )
                }
            }.getOrNull()
        }
    }
}

private fun URL.connect(): HttpURLConnection = (openConnection() as HttpURLConnection).apply {
    connectTimeout = TIMEOUT_MS
    readTimeout = TIMEOUT_MS
    if (responseCode != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP $responseCode")
}

private fun URL.readText(): String = connect().inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

private fun URL.download(target: File) = connect().inputStream.use { input ->
    target.outputStream().use { input.copyTo(it) }
}

fun Context.libraryUrl(): String =
    getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getString(KEY_URL, null) ?: DEFAULT_LIBRARY_URL

fun Context.setLibraryUrl(url: String) = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().apply {
    val custom = url.trim().takeIf { it.isNotEmpty() && it != DEFAULT_LIBRARY_URL }
    if (custom == null) remove(KEY_URL) else putString(KEY_URL, custom)
}.apply()

const val DEFAULT_LIBRARY_URL =
    "https://raw.githubusercontent.com/barone138/ykwp-clicker-library/main/index.json"

private const val PREFERENCES_NAME = "Library"
private const val KEY_URL = "Index_Url"
private const val LIBRARY_FILE = "library_config.zip"
private const val TIMEOUT_MS = 15_000
