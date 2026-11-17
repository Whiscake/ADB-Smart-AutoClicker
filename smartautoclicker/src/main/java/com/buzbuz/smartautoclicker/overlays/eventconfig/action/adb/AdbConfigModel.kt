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
package com.buzbuz.smartautoclicker.overlays.eventconfig.action.adb

import android.content.SharedPreferences

import com.buzbuz.smartautoclicker.domain.Action
import com.buzbuz.smartautoclicker.overlays.eventconfig.action.ActionModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * Allow to observe/edit the values of an adb command action.
 *
 * @param viewModelScope the scope for the view model holding this model.
 * @param configuredAction the flow on the edited action.
 */
class AdbConfigModel(
    private val viewModelScope: CoroutineScope,
    private val configuredAction: MutableStateFlow<Action?>,
) : ActionModel() {

    override val isValidAction: Flow<Boolean> = configuredAction
        .map { action ->
            action is Action.Adb && !action.name.isNullOrEmpty() && !action.host.isNullOrBlank()
                    && action.port != null && !action.command.isNullOrBlank()
        }

    override fun saveLastConfig(eventConfigPrefs: SharedPreferences) = Unit

    /** The initial values of the action, emitted once to fill the inputs. */
    val initialAction: Flow<Action.Adb> = configuredAction
        .filterIsInstance<Action.Adb>()
        .take(1)

    /** Set the address of the computer running the bridge. */
    fun setHost(host: String?) = update { it.copy(host = host) }
    /** Set the port of the bridge. */
    fun setPort(port: Int?) = update { it.copy(port = port) }
    /** Set the adb command to execute. */
    fun setCommand(command: String?) = update { it.copy(command = command) }
    /** Set the shared secret of the bridge. */
    fun setToken(token: String?) = update { it.copy(token = token) }

    private fun update(transform: (Action.Adb) -> Action.Adb) {
        (configuredAction.value as Action.Adb).let { adb ->
            viewModelScope.launch { configuredAction.value = transform(adb) }
        }
    }
}
