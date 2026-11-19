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
package com.buzbuz.smartautoclicker.activity

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.buzbuz.smartautoclicker.SmartAutoClickerService
import com.buzbuz.smartautoclicker.domain.Repository
import com.buzbuz.smartautoclicker.domain.Scenario
import com.buzbuz.smartautoclicker.engine.lastScenarioId

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ResumeActivity : AppCompatActivity() {

    private var scenario: Scenario? = null

    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val toStart = scenario
        if (result.resultCode == RESULT_OK && data != null && toStart != null) {
            SmartAutoClickerService.getLocalService { service ->
                service?.start(result.resultCode, data, toStart, autoPlay = true)
                    ?: Log.w(TAG, "Accessibility service is not running, can't resume ${toStart.name}")
            }
            SmartAutoClickerService.getLocalService(null)
        }
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_SCENARIO_ID, lastScenarioId())
        lifecycleScope.launch {
            scenario = runCatching { Repository.getRepository(this@ResumeActivity).getScenario(id).first() }.getOrNull()
            if (scenario == null) {
                Log.w(TAG, "No scenario $id to resume")
                finishAndRemoveTask()
                return@launch
            }
            projection.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }
    }
}

private const val EXTRA_SCENARIO_ID = "scenario_id"
private const val TAG = "ResumeActivity"
