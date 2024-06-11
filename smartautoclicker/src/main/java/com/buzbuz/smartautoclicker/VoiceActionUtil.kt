package com.buzbuz.smartautoclicker

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction

import android.graphics.Point
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.IdentifierCreator
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import kotlinx.coroutines.delay
import javax.inject.Inject

class VoiceActionUtil (
    private val dumbRepository: IDumbRepository,
    private val dumbEngine: DumbEngine,
) {
    private val dumbActionsIdCreator = IdentifierCreator()

    private suspend fun openVoice() {
        val dumbActions: MutableList<DumbAction> = mutableListOf()
        val dumbScenarioId = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = 0L)

        //在 1ms 期间重复 点击2次
        dumbActions.add(
            DumbAction.DumbClick(
                id = dumbActionsIdCreator.generateNewIdentifier(),
                scenarioId = dumbScenarioId,
                name = "Click",
                priority = 0,
                position = Point(1002, 1942),
                pressDurationMs = 1, //单击持续时间（ms）
                repeatCount = 2,//重复计数
                isRepeatInfinite = false,
                repeatDelayMs = 6//重复延迟（ms)
            )
        )
        dumbRepository.addDumbScenario(
            DumbScenario(
                id = dumbScenarioId,
                name = "打开语音",
                dumbActions = dumbActions,
                repeatCount = 1, // 重复次数
                isRepeatInfinite = false, //是否无限重复
                maxDurationMin = 1,// 最长延长时间 分钟
                isDurationInfinite = true,
                randomize = true // 是否反作弊
            )
        )
    }

    private suspend fun pauseVoice() {
        val dumbActions: MutableList<DumbAction> = mutableListOf()
        val dumbScenarioId = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = 0L)
        dumbActions.add(
            DumbAction.DumbClick(
                id = dumbActionsIdCreator.generateNewIdentifier(),
                scenarioId = dumbScenarioId,
                name = "Click",
                priority = 0,
                position = Point(167, 1904),
                pressDurationMs = 1,
                repeatCount = 2,
                isRepeatInfinite = true,
                repeatDelayMs = 6
            )
        )
        dumbRepository.addDumbScenario(
            DumbScenario(
                id = dumbScenarioId,
                name = "暂停语音",
                dumbActions = dumbActions,
                repeatCount = 1,
                isRepeatInfinite = false,
                maxDurationMin = 1,
                isDurationInfinite = true,
                randomize = false
            )
        )
    }

    private suspend fun closeVoice() {
        val dumbActions: MutableList<DumbAction> = mutableListOf()
        val dumbScenarioId = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = 0L)
        dumbActions.add(
            DumbAction.DumbClick(
                id = dumbActionsIdCreator.generateNewIdentifier(),
                scenarioId = dumbScenarioId,
                name = "Click",
                priority = 0,
                position = Point(901, 1895),
                pressDurationMs = 1,
                repeatCount = 2,
                isRepeatInfinite = true,
                repeatDelayMs = 6
            )
        )
        dumbRepository.addDumbScenario(
            DumbScenario(
                id = dumbScenarioId,
                name = "关闭语音",
                dumbActions = dumbActions,
                repeatCount = 1,
                isRepeatInfinite = false,
                maxDurationMin = 1,
                isDurationInfinite = true,
                randomize = false
            )
        )
    }

    suspend fun executeVoiceActions() {
        openVoice()
        delay(1000) // 延迟1秒
        pauseVoice()
        delay(1000) // 再延迟1秒
        closeVoice()
    }

    fun  onStartDumbScenario(){
        dumbEngine.stopDumbScenario();
        dumbEngine.startDumbScenario();
    }

}
