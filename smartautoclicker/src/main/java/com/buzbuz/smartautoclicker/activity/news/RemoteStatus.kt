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
package com.buzbuz.smartautoclicker.activity.news

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import com.buzbuz.smartautoclicker.BuildConfig
import com.buzbuz.smartautoclicker.activity.library.fetchText
import com.buzbuz.smartautoclicker.activity.library.libraryUrl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

import org.json.JSONObject

import java.net.URL
import java.security.MessageDigest

/** A version of the clicker published in the library repository, next to its index. */
data class AppRelease(val code: Int, val name: String, val url: String, val notes: String)

/** What the library repository says about the library and the app, checked each time the config list opens. */
object RemoteStatus {

    private val _libraryChanged = MutableStateFlow(false)
    /** True when the library index changed since the Library was last opened. */
    val libraryChanged: StateFlow<Boolean> = _libraryChanged

    private val _release = MutableStateFlow<AppRelease?>(null)
    /** The published version, when it is newer than this one. */
    val release: StateFlow<AppRelease?> = _release

    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        if (!context.areNotificationsEnabled()) return@withContext
        val index = context.libraryUrl()
        try {
            val text = URL(index).fetchText()
            val seen = context.prefs().getString(KEY_LIBRARY_SEEN, null)
            if (seen == null) context.librarySeen(text) else _libraryChanged.value = seen != text.fingerprint()
        } catch (ex: Exception) {
            Log.w(TAG, "Can't read the library index", ex)
        }
        try {
            val release = JSONObject(URL(URL(index), APP_FILE).fetchText()).run {
                AppRelease(getInt("versionCode"), optString("versionName"), optString("url"), optString("notes"))
            }
            _release.value = release.takeIf { it.code > BuildConfig.VERSION_CODE }
        } catch (ex: Exception) {
            Log.w(TAG, "Can't read the published version", ex)
        }
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        if (!enabled) {
            _libraryChanged.value = false
            _release.value = null
        }
    }

    fun onLibrarySeen(context: Context, index: String) {
        context.prefs().edit().putString(KEY_LIBRARY_SEEN, index.fingerprint()).apply()
        _libraryChanged.value = false
    }
}

fun Context.librarySeen(index: String) = RemoteStatus.onLibrarySeen(this, index)

/** False when the user turned off the red badges, the new version notice and the news after an update. */
fun Context.areNotificationsEnabled(): Boolean = prefs().getBoolean(KEY_NOTIFICATIONS, true)

/** True once for each new version of the app, the first time its config list opens. */
fun Context.consumeNewVersion(): Boolean {
    val prefs = prefs()
    val shown = prefs.getInt(KEY_NEWS_SHOWN, 0)
    if (shown >= BuildConfig.VERSION_CODE) return false
    prefs.edit().putInt(KEY_NEWS_SHOWN, BuildConfig.VERSION_CODE).apply()
    return true
}

/** True once for each published version, so its notice opens by itself only the first time. */
fun Context.consumeReleaseNotice(release: AppRelease): Boolean {
    val prefs = prefs()
    if (prefs.getInt(KEY_RELEASE_NOTIFIED, 0) >= release.code) return false
    prefs.edit().putInt(KEY_RELEASE_NOTIFIED, release.code).apply()
    return true
}

fun Context.isFreshInstall(): Boolean =
    packageManager.getPackageInfo(packageName, 0).let { it.firstInstallTime == it.lastUpdateTime }

private fun Context.prefs(): SharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun String.fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

private const val PREFERENCES_NAME = "News"
private const val KEY_LIBRARY_SEEN = "Library_Seen"
private const val KEY_NEWS_SHOWN = "News_Shown"
private const val KEY_RELEASE_NOTIFIED = "Release_Notified"
private const val KEY_NOTIFICATIONS = "Notifications"
private const val APP_FILE = "app.json"
private const val TAG = "RemoteStatus"
