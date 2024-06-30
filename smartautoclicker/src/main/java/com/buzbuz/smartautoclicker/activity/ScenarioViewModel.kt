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
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.SmartAutoClickerService
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.feature.permissions.PermissionsController
import com.buzbuz.smartautoclicker.feature.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.feature.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.feature.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.buzbuz.smartautoclicker.feature.revenue.UserConsentState
import com.buzbuz.smartautoclicker.utils.AppUtil
import com.buzbuz.smartautoclicker.utils.SharedPreferencesUtil
import com.buzbuz.smartautoclicker.utils.VoiceActionUtil
import com.netease.lava.nertc.sdk.NERtc
import com.netease.lava.nertc.sdk.NERtcCallback
import com.netease.lava.nertc.sdk.NERtcConstants
import com.netease.lava.nertc.sdk.NERtcEx
import com.netease.lava.nertc.sdk.NERtcOption
import com.netease.lava.nertc.sdk.NERtcParameters
import com.netease.lava.nertc.sdk.NERtcUserJoinExtraInfo
import com.netease.lava.nertc.sdk.NERtcUserLeaveExtraInfo
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrameOpMode
import com.netease.lava.nertc.sdk.audio.NERtcAudioFrameRequestFormat
import com.netease.lite.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AndroidViewModel for create/delete/list click scenarios from an LifecycleOwner. */
@HiltViewModel
class ScenarioViewModel @Inject constructor(
    @ApplicationContext application: Context,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val revenueRepository: IRevenueRepository,
    private val qualityRepository: QualityRepository,
    private val permissionController: PermissionsController,
    private val dumbEngine: DumbEngine,

    ) : ViewModel(), NERtcCallback {

    private val TAG = "Hz:ScenarioViewModel:"
    private val APP_KEY = "3c4f31f7f277ac27ec689b97b304da6d"
    private val userId = 6668881234567890
    private val userId2 = 98988
    private val roomId = "12345678900"
    val sharedPreferencesUtil = SharedPreferencesUtil(application)

    @SuppressLint("StaticFieldLeak")
    var mContext = application;

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) mContext.getSystemService(NotificationManager::class.java)
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

    fun loadSmartScenario(context: Context, resultCode: Int, data: Intent, scenario: Scenario): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission =
                PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }

        clickerService?.startSmartScenario(resultCode, data, scenario)
        return true
    }

    var bean: DumbScenario? = null;
    fun loadDumbScenario(context: Context, scenario: DumbScenario): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission =
                PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }
        bean = scenario;

        clickerService?.openOverlayManager(scenario)
        return true
    }

    /**停止覆盖UI并释放所有关联的资源*/

    fun stopScenario() {
        clickerService?.stop()
    }

    fun startDumbScenario() {
        bean?.let { clickerService?.startDumbScenario(it) };
    }

    /**
     *
     * 一键添加数据 ：
     * TODO
     */
    fun createDumAndSmart() {
        val isCreateScenario = sharedPreferencesUtil.getBoolean("is_create_scenario")
        if (!isCreateScenario) {
            viewModelScope.launch(Dispatchers.IO) {
                val voiceActionUtil = VoiceActionUtil(dumbRepository, dumbEngine)
                voiceActionUtil.executeVoiceActions()
                // createSmartScenario();
                sharedPreferencesUtil.putBoolean("is_create_scenario", true)
            }
        }
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


    fun setRecordAudioParameters() {
        val formatMix = NERtcAudioFrameRequestFormat()
        //单声道、双声道
        //单声道、双声道
        formatMix.channels = 1
        //采样率
        //采样率
        formatMix.sampleRate = 32000
        //读写权限
        //读写权限
        formatMix.opMode = NERtcAudioFrameOpMode.kNERtcAudioFrameOpModeReadOnly
        NERtcEx.getInstance().setRecordingAudioFrameParameters(formatMix)


    }

    fun setPlaybackAudioParameters() {
        val formatMix = NERtcAudioFrameRequestFormat()
        //单声道、双声道
        //单声道、双声道
        formatMix.channels = 1
        //采样率
        //采样率
        formatMix.sampleRate = 32000
        //读写权限
        //读写权限
        formatMix.opMode = NERtcAudioFrameOpMode.kNERtcAudioFrameOpModeReadOnly
        NERtcEx.getInstance().setPlaybackAudioFrameParameters(formatMix)
    }

    fun setupNERtc(context: Context) {
        val parameters = NERtcParameters()
        NERtcEx.getInstance().setParameters(parameters) //先设置参数，后初始化


        val options = NERtcOption()

        if (BuildConfig.DEBUG) {
            options.logLevel = NERtcConstants.LogLevel.INFO
        } else {
            options.logLevel = NERtcConstants.LogLevel.WARNING
        }

        try {
            NERtcEx.getInstance().init(context, APP_KEY, this, options)
        } catch (e: Exception) {
            // 可能由于没有release导致初始化失败，release后再试一次
            NERtcEx.getInstance().release()
            try {
                NERtcEx.getInstance().init(context, APP_KEY, this, options)
            } catch (ex: Exception) {
                Toast.makeText(context, "SDK初始化失败", Toast.LENGTH_LONG).show()
                return
            }
        }
        //设置质量透明回调
//        NERtcEx.getInstance().setStatsObserver(object : NERtcStatsObserver {
//            override fun onRtcStats(neRtcStats: NERtcStats) {
//                //  Log.d(TAG, "onRtcStats:" + neRtcStats.toString())
//
//            }
//
//            override fun onLocalAudioStats(neRtcAudioSendStats: NERtcAudioSendStats) {
//
//                Log.d(TAG, "onLocalAudioStats:" + neRtcAudioSendStats.toString())
//            }
//
//            override fun onRemoteAudioStats(neRtcAudioRecvStats: Array<NERtcAudioRecvStats>) {
////                Log.d(TAG, "onRemoteAudioStats:" + neRtcAudioRecvStats.size)
////                val tmp = neRtcAudioRecvStats[0].layers[0]
////                Log.d(TAG, "音量:" + tmp.volume)
//
//            }
//
//            override fun onLocalVideoStats(neRtcVideoSendStats: NERtcVideoSendStats) {}
//            override fun onRemoteVideoStats(neRtcVideoRecvStats: Array<NERtcVideoRecvStats>) {}
//            override fun onNetworkQuality(neRtcNetworkQualityInfos: Array<NERtcNetworkQualityInfo>) {
////                val packageName = "com.openai.chatgpt" // 替换为你要检查的应用包名
////                val isForeground = isActivityForeground(context, packageName)
////
////                if (isForeground) {
////                    Log.d("ActivityStatus", "$packageName is in foreground")
////                } else {
////                    Log.d("ActivityStatus", "$packageName is not in foreground")
////                }
//
////                Log.d(TAG, "onNetworkQuality:" + neRtcNetworkQualityInfos.size)
////                val tmp = neRtcNetworkQualityInfos[0]
////                Log.d(TAG, "网络质量:" + NetQuality.getMsg(tmp.downStatus) + "---")
//            }
//        })

//        NERtcEx.getInstance().setAudioFrameObserver(object : NERtcAudioFrameObserver {
//            override fun onRecordFrame(neRtcAudioFrame: NERtcAudioFrame) {
//                Log.d(
//                    TAG, "onRecordFrame:" + neRtcAudioFrame.data
//                )
//            }
//
//            override fun onRecordSubStreamAudioFrame(neRtcAudioFrame: NERtcAudioFrame) {
//                Log.d(
//                    TAG, "onRecordSubStreamAudioFrame:" + neRtcAudioFrame.data
//                )
//            }
//
//            override fun onPlaybackFrame(neRtcAudioFrame: NERtcAudioFrame) {
//                Log.d(
//                    TAG, "onPlaybackFrame"
//                )
//            }
//
//            override fun onPlaybackAudioFrameBeforeMixingWithUserID(
//                l: Long, neRtcAudioFrame: NERtcAudioFrame
//            ) {
//                Log.d(
//                    TAG, "onPlaybackAudioFrameBeforeMixingWithUserID"
//                )
//            }
//
//            override fun onPlaybackAudioFrameBeforeMixingWithUserID(
//                l: Long, neRtcAudioFrame: NERtcAudioFrame, l1: Long
//            ) {
//                Log.d(
//                    TAG, "onPlaybackAudioFrameBeforeMixingWithUserID"
//                )
//            }
//
//            override fun onMixedAudioFrame(neRtcAudioFrame: NERtcAudioFrame) {
//                Log.d(
//                    TAG, "onMixedAudioFrame"
//                )
//            }
//
//            override fun onPlaybackSubStreamAudioFrameBeforeMixingWithUserID(
//                l: Long, neRtcAudioFrame: NERtcAudioFrame, l1: Long
//            ) {
//                Log.d(
//                    TAG, "onPlaybackSubStreamAudioFrameBeforeMixingWithUserID"
//                )
//            }
//        })
        setLocalAudioEnable(false)


    }

    /**
     * 设置本地音频可用性
     *
     * @param enable
     */
    fun setLocalAudioEnable(enable: Boolean) {
        //开启或关闭本地音频采集和发送。
        NERtcEx.getInstance().enableLocalAudio(true)
       //设置播放设备静音
        NERtcEx.getInstance().setPlayoutDeviceMute(true);
        //设置播放设备取消静音
       // NERtcEx.getInstance().setPlayoutDeviceMute(false);
        NERtc.getInstance().setAudioProfile(
            NERtcConstants.AudioScenario.SPEECH, NERtcConstants.AudioProfile.MIDDLE_QUALITY
        )
        //NERtcEx.getInstance().setSpeakerphoneOn(false)
        NERtcEx.getInstance().adjustChannelPlaybackSignalVolume(12)
        NERtcEx.getInstance().adjustLoopBackRecordingSignalVolume(12)
        NERtcEx.getInstance().adjustPlaybackSignalVolume(12)
        //NERtcEx.getInstance().setRemoteHighPriorityAudioStream(true,12)
    }

    /**
     * 加入房间
     *
     * @param userId 用户ID
     * @param roomId 房间ID
     */
    fun joinChannel() {
        Log.i(TAG, "joinChannel userId: $userId")
       // NERtcEx.getInstance().joinChannel(null, roomId, userId)
       //


        NERtcEx.getInstance().setChannelProfile(NERtcConstants.RTCChannelProfile.COMMUNICATION)
        NERtcEx.getInstance().joinChannel("", roomId, userId)
//        val ret = NERtcEx.getInstance().enableSuperResolution(false)
        setRecordAudioParameters()
        setPlaybackAudioParameters()
        AudioReceiverDecoder().setMediaComm()
    }

    private fun leaveChannel(): Boolean {
        setLocalAudioEnable(false)
        val ret: Int = NERtcEx.getInstance().leaveChannel()
        return ret == NERtcConstants.ErrorCode.OK
    }

    override fun onJoinChannel(result: Int, channelId: Long, elapsed: Long, l2: Long) {
        Log.i(TAG, "onJoinChannel result: $result channelId: $channelId elapsed: $elapsed")
        if (result == NERtcConstants.ErrorCode.OK) {
            MainScope().launch {
                delay(2000) // 延时2秒
            }
        }
    }

    override fun onLeaveChannel(result: Int) {
        Log.i(TAG, "onLeaveChannel result: $result")
    }

    @Deprecated("Deprecated in Java")
    override fun onUserJoined(userId: Long) {
        Log.i(TAG, "onUserJoined userId: $userId ")

        if (userId.toInt() == userId2) {
            val tag = AppUtil.openPackage(mContext, "com.openai.chatgpt");
            if (tag) {
                startDumbScenario()
            }
        }
    }

    override fun onUserJoined(uid: Long, joinExtraInfo: NERtcUserJoinExtraInfo?) {
        Log.i(TAG, "onUserJoined uid: $uid")

    }

    @Deprecated("Deprecated in Java")
    override fun onUserLeave(userId: Long, i: Int) {
        Log.i(TAG, "onUserLeave uid: $userId")

    }


    override fun onUserLeave(uid: Long, reason: Int, leaveExtraInfo: NERtcUserLeaveExtraInfo?) {}

    override fun onUserAudioStart(userId: Long) {
        Log.i(TAG, "onUserAudioStart uid: $userId")
        NERtcEx.getInstance().subscribeRemoteAudioStream(userId, true)
    }

    override fun onUserAudioStop(userId: Long) {
        Log.i(TAG, "onUserAudioStop uid: $userId")
        NERtcEx.getInstance().subscribeRemoteAudioStream(userId, false)
    }

    override fun onUserVideoStart(userId: Long, profile: Int) {
    }

    override fun onUserVideoStop(userId: Long) {
    }

    override fun onDisconnect(i: Int) {
        Log.i(TAG, "onDisconnect uid: $i")

    }

    override fun onClientRoleChange(old: Int, newRole: Int) {
        Log.i(TAG, "onUserAudioStart old: $old, newRole : $newRole")
    }


}

