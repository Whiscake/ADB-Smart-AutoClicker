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
package com.buzbuz.smartautoclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.buzbuz.smartautoclicker.engine.Bridge
import com.buzbuz.smartautoclicker.engine.DEFAULT_BRIDGE_HOST
import com.buzbuz.smartautoclicker.engine.DEFAULT_BRIDGE_PORT
import com.buzbuz.smartautoclicker.engine.setAdbEnabled
import com.buzbuz.smartautoclicker.engine.setBridge

class BridgeSettingsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_BRIDGE) return
        context.setBridge(
            Bridge(
                host = intent.getStringExtra(EXTRA_HOST) ?: DEFAULT_BRIDGE_HOST,
                port = intent.getIntExtra(EXTRA_PORT, DEFAULT_BRIDGE_PORT),
                token = intent.getStringExtra(EXTRA_TOKEN) ?: "",
            )
        )
        context.setAdbEnabled(true)
        resultCode = 1
    }
}

private const val ACTION_SET_BRIDGE = "com.buzbuz.smartautoclicker.SET_BRIDGE"
private const val EXTRA_HOST = "host"
private const val EXTRA_PORT = "port"
private const val EXTRA_TOKEN = "token"
