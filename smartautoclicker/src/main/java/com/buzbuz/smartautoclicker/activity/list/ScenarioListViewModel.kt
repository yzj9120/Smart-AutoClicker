package com.buzbuz.smartautoclicker.activity.list

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.VoiceActionUtil
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.condition.ImageCondition
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.Repeatable
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.core.ui.utils.formatDuration
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.buzbuz.smartautoclicker.feature.revenue.UserBillingState
import com.buzbuz.smartautoclicker.feature.smart.config.utils.getImageConditionBitmap
import com.buzbuz.smartautoclicker.utils.SharedPreferencesUtil
import com.gpt40.smartautoclicker.R

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject

/**
 * 类负责管理场景列表 UI 的状态和逻辑
 */
@HiltViewModel
class ScenarioListViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val revenueRepository: IRevenueRepository,
    private val qualityRepository: QualityRepository,
) : ViewModel() {

    private val TAG = "HUANGZHEN:ScenarioListViewModel:"

    /**
     * 这是一个 MutableStateFlow，它是 Kotlin 协程中的一种 StateFlow。
     * StateFlow 是一个状态持有的可观察流，会向其收集器发出当前和新的状态更新。
     * uiStateType 保存当前的 UI 状态，并初始化为 ScenarioListUiState.Type.SELECTION。
     */
    private val uiStateType = MutableStateFlow(ScenarioListUiState.Type.SELECTION)

    /**
     * 这是另一个 MutableStateFlow，但它持有一个可为空的 String。
     * 它表示用户当前输入的搜索查询。如果没有搜索查询，它就是 null。
     */
    private val searchQuery = MutableStateFlow<String?>(null)

    /**
     *
     * 这是一个 Flow，会发出一个包含 ScenarioListUiState.Item 对象的列表。
     * 它组合了两个不同的场景列表：来自 dumbRepository 的 dumbScenarios 和来自 smartRepository 的 scenarios。
     * 使用 Kotlin 协程中的 combine 函数来合并这两个列表。
     * 列表中的每个项目都会映射到一个 ScenarioListUiState.Item，然后合并成一个可变列表，并按照每个项目的 displayName 排序。
     *
     */

    private val allScenarios: Flow<List<ScenarioListUiState.Item>> =
        combine(dumbRepository.dumbScenarios, smartRepository.scenarios) { dumbList, smartList ->
            mutableListOf<ScenarioListUiState.Item>().apply {
                addAll(dumbList.map { it.toItem(context) })
                addAll(smartList.map { it.toItem() })
            }.sortedBy { it.displayName }
        }

    /**
     *
     * 这是另一个 Flow，会发出一个包含 ScenarioListUiState.Item 对象的列表。
     * 它将 allScenarios 流与 searchQuery 流结合起来。
     * combine 函数中的 lambda 函数根据 query 过滤 scenarios。
     * 如果 query 为 null 或为空，它会返回原始场景。如果有查询，它会过滤场景，只包括那些 displayName 包含查询字符串的场景（忽略大小写差异）。
     *
     */
    private val filteredScenarios: Flow<List<ScenarioListUiState.Item>> =
        allScenarios.combine(searchQuery) { scenarios, query ->
            scenarios.mapNotNull { scenario ->
                if (query.isNullOrEmpty()) return@mapNotNull scenario
                if (scenario.displayName.contains(query.toString(), true)) scenario else null
            }
        }

    /**
     * 这是一个 MutableStateFlow，持有一个 ScenarioBackupSelection 对象。
     * 它表示选择用于备份的场景标识符集合。
     */
    private val selectedForBackup = MutableStateFlow(ScenarioBackupSelection())




    /**
     * 这是一个 StateFlow<ScenarioListUiState?> 类型的属性。
     * 它结合了多个流：uiStateType、filteredScenarios、selectedForBackup、revenueRepository.userBillingState 和 revenueRepository.isPrivacySettingRequired。
     * 使用 combine 函数来合并这些流，并生成一个新的 ScenarioListUiState 对象。
     * 合并函数的 lambda 表达式根据当前的状态类型、场景列表、备份选择、账单状态和隐私设置的要求来创建 ScenarioListUiState 对象。
     * ScenarioListUiState 的 type 属性被设置为 stateType。
     * menuUiState 通过调用 stateType.toMenuUiState 并传递 scenarios、backupSelection、billingState 和 privacyRequired 来生成。
     * listContent 属性：
     * 如果 stateType 不是 ScenarioListUiState.Type.EXPORT，则直接使用 scenarios。
     * 否则，使用 scenarios.filterForBackupSelection(backupSelection) 来过滤场景。
     * 使用 stateIn 函数将流转换为 StateFlow，并在 viewModelScope 范围内共享，启动策略为 SharingStarted.WhileSubscribed(5_000)，初始值为 null。
     */
    // 定义一个函数来创建 ScenarioListUiState 对象
    private fun createScenarioListUiState(
        stateType: ScenarioListUiState.Type,
        scenarios: List<ScenarioListUiState.Item>,
        backupSelection: ScenarioBackupSelection,
        billingState: UserBillingState,
        privacyRequired: Boolean
    ): ScenarioListUiState {
        Log.d(
            TAG,
            "createScenarioListUiState =$stateType.. $scenarios......$backupSelection....$billingState......$privacyRequired"
        )
        return ScenarioListUiState(
            type = stateType,
            menuUiState = stateType.toMenuUiState(scenarios, backupSelection, billingState, privacyRequired),
            listContent = if (stateType != ScenarioListUiState.Type.EXPORT) scenarios
            else scenarios.filterForBackupSelection(backupSelection)
        )
    }

    // 创建一个组合函数
    private fun combineScenarios(
        stateType: ScenarioListUiState.Type,
        scenarios: List<ScenarioListUiState.Item>,
        backupSelection: ScenarioBackupSelection,
        billingState: UserBillingState,
        privacyRequired: Boolean
    ): ScenarioListUiState {
        return createScenarioListUiState(stateType, scenarios, backupSelection, billingState, privacyRequired)
    }

    val uiState: StateFlow<ScenarioListUiState?> = combine(
        uiStateType,
        filteredScenarios,
        selectedForBackup,
        revenueRepository.userBillingState,
        revenueRepository.isPrivacySettingRequired,
        ::combineScenarios
    ).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000L), null
    )


    /**
     * Change the ui state type.
     * @param state the new state.
     */
    fun setUiState(state: ScenarioListUiState.Type) {

        Log.d(
            TAG, "setUiState =$state"
        )

        uiStateType.value = state
        selectedForBackup.value = selectedForBackup.value.copy(
            dumbSelection = emptySet(),
            smartSelection = emptySet(),
        )
    }

    /**
     * Update the action search query.
     * @param query the new query.
     */
    fun updateSearchQuery(query: String?) {
        searchQuery.value = query
    }

    /**
     * 坐标定位 列表
     */
    fun getDumbScenariosSelectedForBackup(): Collection<Long> = selectedForBackup.value.dumbSelection.toList()

    /**
     * 图像检测列表
     */
    fun getSmartScenariosSelectedForBackup(): Collection<Long> = selectedForBackup.value.smartSelection.toList()

    /**
     * 切换场景的选定备份状态。
     * @param scenario the scenario to be toggled.
     */
    fun toggleScenarioSelectionForBackup(scenario: ScenarioListUiState.Item) {

        Log.d(TAG, "toggleScenarioSelectionForBackup=$scenario")
        selectedForBackup.value.toggleScenarioSelectionForBackup(scenario)?.let {
            selectedForBackup.value = it
        }
    }

    /** 切换所有方案的所选备份状态值. */
    fun toggleAllScenarioSelectionForBackup() {
        Log.d(TAG, "toggleAllScenarioSelectionForBackup")

        selectedForBackup.value = selectedForBackup.value.toggleAllScenarioSelectionForBackup(
            uiState.value?.listContent ?: emptyList()
        )
    }

    /**
     * Delete a click scenario.
     *
     * This will also delete all child entities associated with the scenario.
     *
     * @param item the scenario to be deleted.
     */
    fun deleteScenario(item: ScenarioListUiState.Item) {
        Log.d(TAG, "deleteScenario=$item")


        viewModelScope.launch(Dispatchers.IO) {
            when (val scenario = item.scenario) {
                is DumbScenario -> dumbRepository.deleteDumbScenario(scenario)
                is Scenario -> smartRepository.deleteScenario(scenario.id)
            }
        }
    }

    /**
     *获取与条件对应的位图。
     *加载是异步的，结果通过onBitmapLoaded参数通知。
     *
     * @param condition the condition to load the bitmap of.
     * @param onBitmapLoaded the callback notified upon completion.
     */
    fun getConditionBitmap(condition: ImageCondition, onBitmapLoaded: (Bitmap?) -> Unit): Job {

        Log.d(TAG, "getConditionBitmap=$condition......$onBitmapLoaded")


        return getImageConditionBitmap(smartRepository, condition, onBitmapLoaded);
    }


    fun showPrivacySettings(activity: Activity) {
        Log.d(TAG, "showPrivacySettings=")

        revenueRepository.startPrivacySettingUiFlow(activity)
    }

    fun showPurchaseActivity(context: Context) {
        Log.d(TAG, "showPurchaseActivity=")

        revenueRepository.startPurchaseUiFlow(context)
    }

    /**
     * 故障排除 弹窗提示
     */
    fun showTroubleshootingDialog(activity: FragmentActivity) {

        Log.d(TAG, "showTroubleshootingDialog=")

        qualityRepository.startTroubleshootingUiFlow(activity)
    }

    private fun ScenarioListUiState.Type.toMenuUiState(
        scenarioItems: List<ScenarioListUiState.Item>,
        backupSelection: ScenarioBackupSelection,
        billingState: UserBillingState,
        isPrivacyRequired: Boolean,
    ): ScenarioListUiState.Menu {

        Log.d(TAG, "toMenuUiState=$this")


        return when (this) {
            ScenarioListUiState.Type.SEARCH -> ScenarioListUiState.Menu.Search
            ScenarioListUiState.Type.EXPORT -> ScenarioListUiState.Menu.Export(
                canExport = !backupSelection.isEmpty(),
            )

            ScenarioListUiState.Type.SELECTION -> ScenarioListUiState.Menu.Selection(
                searchEnabled = scenarioItems.isNotEmpty(),
                exportEnabled = scenarioItems.firstOrNull { it is ScenarioListUiState.Item.Valid } != null,
                privacyRequired = isPrivacyRequired,
                canPurchase = billingState != UserBillingState.PURCHASED,
            )
        }
    }

    private suspend fun Scenario.toItem(): ScenarioListUiState.Item {


        Log.d(TAG, "Scenario.toItem=$this")


        return if (eventCount == 0) ScenarioListUiState.Item.Empty.Smart(this)
        else ScenarioListUiState.Item.Valid.Smart(
            scenario = this,
            eventsItems = smartRepository.getImageEvents(id.databaseId).map { event ->
                ScenarioListUiState.Item.Valid.Smart.EventItem(
                    id = event.id.databaseId,
                    eventName = event.name,
                    actionsCount = event.actions.size,
                    conditionsCount = event.conditions.size,
                    firstCondition = if (event.conditions.isNotEmpty()) event.conditions.first() else null,
                )
            },
            triggerEventCount = smartRepository.getTriggerEvents(id.databaseId).size,
            detectionQuality = detectionQuality,
        )
    }


    private fun DumbScenario.toItem(context: Context): ScenarioListUiState.Item {


        Log.d(TAG, "DumbScenario.toItem=$this")


        return if (dumbActions.isEmpty()) ScenarioListUiState.Item.Empty.Dumb(this)
        else ScenarioListUiState.Item.Valid.Dumb(
            scenario = this,
            clickCount = dumbActions.count { it is DumbAction.DumbClick },
            swipeCount = dumbActions.count { it is DumbAction.DumbSwipe },
            pauseCount = dumbActions.count { it is DumbAction.DumbPause },
            repeatText = getRepeatDisplayText(context),
            maxDurationText = getMaxDurationDisplayText(context),
        )
    }


    private fun Repeatable.getRepeatDisplayText(context: Context): String =
        if (isRepeatInfinite) context.getString(R.string.item_desc_dumb_scenario_repeat_infinite)
        else context.getString(R.string.item_desc_dumb_scenario_repeat_count, repeatCount)

    private fun DumbScenario.getMaxDurationDisplayText(context: Context): String =
        if (isDurationInfinite) context.getString(R.string.item_desc_dumb_scenario_max_duration_infinite)
        else context.getString(
            R.string.item_desc_dumb_scenario_max_duration,
            formatDuration(maxDurationMin.minutes.inWholeMilliseconds),
        )

    private fun List<ScenarioListUiState.Item>.filterForBackupSelection(
        backupSelection: ScenarioBackupSelection,
    ): List<ScenarioListUiState.Item> = mapNotNull { item ->

        Log.d(TAG, "filterForBackupSelection.toItem=$this")

        when (item) {
            is ScenarioListUiState.Item.Valid.Dumb -> item.copy(
                showExportCheckbox = true,
                checkedForExport = backupSelection.dumbSelection.contains(item.scenario.id.databaseId)
            )

            is ScenarioListUiState.Item.Valid.Smart -> item.copy(
                showExportCheckbox = true,
                checkedForExport = backupSelection.smartSelection.contains(item.scenario.id.databaseId)
            )

            else -> null
        }
    }



}
