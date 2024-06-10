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
package com.buzbuz.smartautoclicker.activity

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.SmartAutoClickerService
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.base.identifier.IdentifierCreator
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.feature.permissions.PermissionsController
import com.buzbuz.smartautoclicker.feature.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.feature.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.feature.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.buzbuz.smartautoclicker.feature.revenue.UserConsentState
import com.gpt40.smartautoclicker.R

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AndroidViewModel for create/delete/list click scenarios from an LifecycleOwner. */
@HiltViewModel
class ScenarioViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val revenueRepository: IRevenueRepository,
    private val qualityRepository: QualityRepository,
    private val permissionController: PermissionsController,
    ) : ViewModel() {

    private val TAG = "Hz:ScenarioViewModel:"


    /** Callback upon the availability of the [SmartAutoClickerService]. */
    private val serviceConnection: (SmartAutoClickerService.ILocalService?) -> Unit = { localService ->
        clickerService = localService
    }

    /**
     * Reference on the [SmartAutoClickerService].
     * Will be not null only if the Accessibility Service is enabled.
     */
    private var clickerService: SmartAutoClickerService.ILocalService? = null

    /** The Android notification manager. Initialized only if needed.*/
    private val notificationManager: NotificationManager?

    val userConsentState: StateFlow<UserConsentState> =
        revenueRepository.userConsentState.stateIn(viewModelScope, SharingStarted.Eagerly, UserConsentState.UNKNOWN)

    init {
        SmartAutoClickerService.getLocalService(serviceConnection)

        notificationManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.getSystemService(NotificationManager::class.java)
            else null
    }

    override fun onCleared() {
        SmartAutoClickerService.getLocalService(null)
        super.onCleared()
    }

    fun requestUserConsent(activity: Activity) {
        Log.d(TAG, "requestUserConsent")
        revenueRepository.startUserConsentRequestUiFlowIfNeeded(activity)
    }

    fun refreshPurchaseState() {
        Log.d(TAG, "refreshPurchaseState")
        revenueRepository.refreshPurchases()
    }

    /**
     * 获取悬浮权限
     */
    fun startPermissionFlowIfNeeded(activity: AppCompatActivity, onAllGranted: () -> Unit) {
        Log.d(TAG, "startPermissionFlowIfNeeded")

        permissionController.startPermissionsUiFlow(
            activity = activity,
            permissions = listOf(
                PermissionOverlay,
                PermissionAccessibilityService(
                    componentName = ComponentName(activity, SmartAutoClickerService::class.java),
                    isServiceRunning = { SmartAutoClickerService.isServiceStarted() },
                ),
                PermissionPostNotification,
            ),
            onAllGranted = onAllGranted,
        )
    }

    fun startTroubleshootingFlowIfNeeded(activity: FragmentActivity, onCompleted: () -> Unit) {
        Log.d(TAG, "startTroubleshootingFlowIfNeeded")

        qualityRepository.startTroubleshootingUiFlowIfNeeded(activity, onCompleted)
    }

    /**
     * Start the overlay UI and instantiates the detection objects for a given scenario.
     *
     * This requires the media projection permission code and its data intent, they both can be retrieved using the
     * results of the activity intent provided by
     * [android.media.projection.MediaProjectionManager.createScreenCaptureIntent] (this Intent shows the dialog
     * warning about screen recording privacy). Any attempt to call this method without the correct screen capture
     * intent result will leads to a crash.
     *
     * @param resultCode the result code provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param data the data intent provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param scenario the identifier of the scenario of clicks to be used for detection.
     */
    fun loadSmartScenario(context: Context, resultCode: Int, data: Intent, scenario: Scenario): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission =
                PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }

        clickerService?.startSmartScenario(resultCode, data, scenario)
        return true
    }

    fun loadDumbScenario(context: Context, scenario: DumbScenario): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission =
                PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }

        clickerService?.startDumbScenario(scenario)
        return true
    }

    /**停止覆盖UI并释放所有关联的资源*/

    fun stopScenario() {

        clickerService?.stop()
    }

    /**
     *
     * 一键添加数据 ：
     * TODO
     */
    fun createDumAndSmart() {
        viewModelScope.launch(Dispatchers.IO) {
            createDumbScenario();
            createSmartScenario();
        }

    }

    /**
     *
     * [DumbClick(id=Identifier(databaseId=4, tempId=null), scenarioId=Identifier(databaseId=11, tempId=null), name=Click, priority=0, repeatCount=18, isRepeatInfinite=false, repeatDelayMs=5, position=Point(460, 1494), pressDurationMs=10)]
     *
     */
    private suspend fun createDumbScenario() {
        val dumbActionsIdCreator = IdentifierCreator()
        val dumbActions: MutableList<DumbAction> = mutableListOf()
        val dumbScenarioId = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = 0L)
        //在 1ms 期间重复 点击2次
        val bean = DumbAction.DumbClick(
            id = dumbActionsIdCreator.generateNewIdentifier(),
            scenarioId = dumbScenarioId,
            name = "Click",
            priority = 0,//优先级
            position = Point(486, 1518),// 坐标位置
            pressDurationMs = 1,//单击持续时间（ms）
            repeatCount = 2,//重复计数
            isRepeatInfinite = true,
            repeatDelayMs = 6,//重复延迟（ms)
        )
        // 直接添加元素
        dumbActions.add(bean)
        //DumbClick(id=Identifier(databaseId=0, tempId=1), scenarioId=Identifier(databaseId=1, tempId=null), name=Click, priority=0, repeatCount=1, isRepeatInfinite=false, repeatDelayMs=0, position=Point(310, 1487), pressDurationMs=1)
        dumbRepository.addDumbScenario(
            DumbScenario(
                id = dumbScenarioId,
                name = "坐标$DATABASE_ID_INSERTION",
                dumbActions = dumbActions,    // 活动
                repeatCount = 1, // 重复次数
                isRepeatInfinite = false, //是否无限重复
                maxDurationMin = 1,  // 最长延长时间 分钟
                isDurationInfinite = true,
                randomize = false, // 是否反作弊
            )
        )
    }

    private suspend fun createSmartScenario() {
        smartRepository.addScenario(
            Scenario(
                id = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = 0L),
                name = "图形$DATABASE_ID_INSERTION",
                detectionQuality = 1200,
                randomize = false,
            )
        )
    }



}

