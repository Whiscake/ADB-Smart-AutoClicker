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
import android.net.Uri
import android.util.Log

import com.buzbuz.smartautoclicker.baseui.ScreenMetrics
import com.buzbuz.smartautoclicker.domain.Backup
import com.buzbuz.smartautoclicker.domain.Repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import java.io.File

class BackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val folder = context.getExternalFilesDir(null) ?: return
        val file = intent.getStringExtra(EXTRA_FILE)?.let { File(folder, File(it).name) } ?: return
        val repository = Repository.getRepository(context)
        val screen = ScreenMetrics(context).screenSize
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                when (intent.action) {
                    ACTION_EXPORT -> {
                        file.delete()
                        val ids = repository.scenarios.first().map { it.id }
                        repository.createScenarioBackup(Uri.fromFile(file), ids, screen).finalState()
                    }
                    ACTION_IMPORT -> repository.restoreScenarioBackup(Uri.fromFile(file), screen).finalState()
                    else -> null
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Backup of ${file.name} failed", ex)
                null
            }
            pending.resultCode = if (result is Backup.Completed) RESULT_OK else RESULT_FAILED
            pending.resultData = (result as? Backup.Completed)?.successCount?.toString()
            pending.finish()
        }
    }
}

private suspend fun Flow<Backup>.finalState(): Backup = first { it is Backup.Completed || it is Backup.Error }

private const val ACTION_EXPORT = "com.buzbuz.smartautoclicker.EXPORT_SCENARIOS"
private const val ACTION_IMPORT = "com.buzbuz.smartautoclicker.IMPORT_SCENARIOS"
private const val EXTRA_FILE = "file"
private const val RESULT_OK = 1
private const val RESULT_FAILED = 2
private const val TAG = "BackupReceiver"
