/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.KeyEvent

import com.buzbuz.smartautoclicker.core.base.AndroidExecutor
import com.buzbuz.smartautoclicker.core.bitmaps.IBitmapManager
import com.buzbuz.smartautoclicker.core.display.DisplayMetrics
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.core.processing.domain.DetectionRepository
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.feature.smart.config.ui.MainMenu
import com.buzbuz.smartautoclicker.feature.dumb.config.ui.DumbMainMenu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocalService(
    private val context: Context,
    private val overlayManager: OverlayManager,  // 悬浮层
    private val displayMetrics: DisplayMetrics,
    private val detectionRepository: DetectionRepository,
    private val bitmapManager: IBitmapManager,
    private val dumbEngine: DumbEngine,
    private val androidExecutor: AndroidExecutor,
    private val onStart: (isSmart: Boolean, name: String) -> Unit,
    private val onStop: () -> Unit,
) : SmartAutoClickerService.ILocalService {


    private val TAG = "Hz:LocalService:"


    /** Scope for this LocalService. */
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Coroutine job for the delayed start of engine & ui. */
    private var startJob: Job? = null

    /** True if the overlay is started, false if not. */
    internal var isStarted: Boolean = false

    override fun startDumbScenario(dumbScenario: DumbScenario) {
        Log.d(TAG, "startDumbScenario：${dumbScenario.toString()}")
        startJob = serviceScope.launch {
            delay(500)
            dumbEngine.init(androidExecutor, dumbScenario)
            dumbEngine.startDumbScenario();
        }
    }


    @SuppressLint("ServiceCast")
    override fun startSmartScenario(resultCode: Int, data: Intent, scenario: Scenario) {

        Log.d(TAG, "startSmartScenario：${scenario.toString()}")


        if (isStarted) return
        isStarted = true
        onStart(true, scenario.name)

        displayMetrics.startMonitoring(context)
        startJob = serviceScope.launch {
            delay(500)
            detectionRepository.setScenarioId(scenario.id)
            overlayManager.navigateTo(
                context = context,
                newOverlay = MainMenu { stop() },
            )
            // If we start too quickly, there is a chance of crash because the service isn't in foreground state yet
            // That's not really an issue as the user just clicked the permission button and the activity is closing
            delay(1000)

            Log.d(TAG, "意图：$resultCode.......daata=$data")
            detectionRepository.startScreenRecord(
                context = context,
                resultCode = resultCode,
                data = data,
                androidExecutor = androidExecutor,
            )
        }
    }

    override fun stop() {

        Log.d(TAG, "stop：")

        if (!isStarted) return
        isStarted = false

        serviceScope.launch {
            startJob?.join()
            startJob = null

            dumbEngine.release()
            overlayManager.closeAll(context)
            detectionRepository.stopScreenRecord()
            displayMetrics.stopMonitoring(context)
            bitmapManager.releaseCache()

            onStop()
        }
    }

    override fun release() {
        serviceScope.cancel()
    }

    override fun openOverlayManager(dumbScenario: DumbScenario) {
        Log.d(TAG, "openOverlayManager：${dumbScenario.toString()}")
        if (isStarted) return
        isStarted = true
        onStart(false, dumbScenario.name)
        startJob = serviceScope.launch {
            delay(500)
            displayMetrics.startMonitoring(context)
            dumbEngine.init(androidExecutor, dumbScenario)
            overlayManager.navigateTo(
                context = context,
                newOverlay = DumbMainMenu(dumbScenario.id) { stop() },
            )
        }
    }

    override fun gptLiner(nodeInfoList: MutableList<NodeInfo>) {
        if (nodeInfoList.contains(NodeInfo("", "听写,开始语音对话,开始新聊天,编辑菜单"))) {
            println("列表中包含文本为 '聊天的节点信息")
        } else if (nodeInfoList.contains(NodeInfo("正在关联", "结束语音对话，停止"))) {
            println("列表中包含文本为 '语言的节点信息")
        }else{
            println("列表中包含文本为 '未知")
        }

    }

    fun onKyEvent(event: KeyEvent?): Boolean {

        Log.d(TAG, "onKeyEvent：")
        event ?: return false
        return overlayManager.propagateKeyEvent(event)
    }

    fun toggleOverlaysVisibility() {

        Log.d(TAG, "toggleOverlaysVisibility：")
        overlayManager.apply {
            if (isStackHidden()) {
                restoreVisibility()
            } else {
                hideAll()
            }
        }
    }
}