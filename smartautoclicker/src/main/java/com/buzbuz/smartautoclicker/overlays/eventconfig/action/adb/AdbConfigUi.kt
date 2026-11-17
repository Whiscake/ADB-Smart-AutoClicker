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

import android.text.Editable
import android.view.View
import android.widget.EditText

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

import com.buzbuz.smartautoclicker.databinding.IncludeAdbConfigBinding
import com.buzbuz.smartautoclicker.overlays.utils.OnAfterTextChangedListener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Binds the [IncludeAdbConfigBinding] to the [AdbConfigModel] using the dialog lifecycle. */
fun IncludeAdbConfigBinding.setupAdbUi(
    adbModel: AdbConfigModel,
    lifecycleOwner: LifecycleOwner,
    lifecycleScope: CoroutineScope,
) {
    actionConfigLayoutAdb.visibility = View.VISIBLE

    editAdbHost.onTextChanged { adbModel.setHost(it) }
    editAdbPort.onTextChanged { adbModel.setPort(it?.toIntOrNull()) }
    editAdbCommand.onTextChanged { adbModel.setCommand(it) }
    editAdbToken.onTextChanged { adbModel.setToken(it) }

    lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                adbModel.initialAction.collect { adb ->
                    editAdbHost.setText(adb.host)
                    editAdbPort.setText(adb.port?.toString())
                    editAdbCommand.setText(adb.command)
                    editAdbToken.setText(adb.token)
                }
            }
        }
    }
}

private fun EditText.onTextChanged(onChanged: (String?) -> Unit) {
    addTextChangedListener(object : OnAfterTextChangedListener() {
        override fun afterTextChanged(s: Editable?) {
            onChanged(s?.toString()?.takeIf { it.isNotEmpty() })
        }
    })
}
