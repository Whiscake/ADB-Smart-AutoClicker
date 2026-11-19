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
package com.buzbuz.smartautoclicker.engine

import android.content.Context

data class Bridge(val host: String, val port: Int, val token: String)

fun Context.isAdbEnabled(): Boolean = engineSettings().getBoolean(KEY_ADB_ENABLED, true)

fun Context.setAdbEnabled(enabled: Boolean) = engineSettings().edit().putBoolean(KEY_ADB_ENABLED, enabled).apply()

fun Context.bridge(): Bridge = engineSettings().let { settings ->
    Bridge(
        host = settings.getString(KEY_BRIDGE_HOST, null) ?: DEFAULT_BRIDGE_HOST,
        port = settings.getInt(KEY_BRIDGE_PORT, DEFAULT_BRIDGE_PORT),
        token = settings.getString(KEY_BRIDGE_TOKEN, null) ?: "",
    )
}

fun Context.setBridge(bridge: Bridge) = engineSettings().edit()
    .putString(KEY_BRIDGE_HOST, bridge.host)
    .putInt(KEY_BRIDGE_PORT, bridge.port)
    .putString(KEY_BRIDGE_TOKEN, bridge.token)
    .apply()

fun Context.lastScenarioId(): Long = engineSettings().getLong(KEY_LAST_SCENARIO, -1)

fun Context.setLastScenarioId(id: Long) = engineSettings().edit().putLong(KEY_LAST_SCENARIO, id).apply()

private fun Context.engineSettings() = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

const val DEFAULT_BRIDGE_HOST = "127.0.0.1"
const val DEFAULT_BRIDGE_PORT = 8765

private const val PREFERENCES_NAME = "EngineSettings"
private const val KEY_ADB_ENABLED = "Adb_Enabled"
private const val KEY_BRIDGE_HOST = "Bridge_Host"
private const val KEY_BRIDGE_PORT = "Bridge_Port"
private const val KEY_BRIDGE_TOKEN = "Bridge_Token"
private const val KEY_LAST_SCENARIO = "Last_Scenario"
