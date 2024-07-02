package com.buzbuz.smartautoclicker.play


import android.content.Context
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NERtcManager : NERtcCallback {

    private val TAG = "NERtcManager"
    private val APP_KEY = "3c4f31f7f277ac27ec689b97b304da6d" // Replace with your actual app key
    private val roomId = "hz123"
    private val userId = 6668881234567890
    private val userId2 = 98988
    private var managerCallback: NERtcManagerCallback? = null
    fun setupNERtc(context: Context, callback: NERtcManagerCallback?) {
        managerCallback = callback
        Log.i(TAG, "setupNERtc.....")

        val parameters = NERtcParameters()
        NERtcEx.getInstance().setParameters(parameters) // Set parameters before initialization

        val options = NERtcOption()
        options.logLevel = if (BuildConfig.DEBUG) NERtcConstants.LogLevel.INFO else NERtcConstants.LogLevel.WARNING

        try {
            NERtcEx.getInstance().init(context, APP_KEY, null, options)
        } catch (e: Exception) {
            // Initialization might fail if not released properly, try releasing and initializing again
            NERtcEx.getInstance().release()
            try {
                NERtcEx.getInstance().init(context, APP_KEY, null, options)
            } catch (ex: Exception) {
                Toast.makeText(context, "SDK initialization failed", Toast.LENGTH_LONG).show()
                return
            }
        }
        setRecordAudioParameters();
        setPlaybackAudioParameters();
        setLocalAudioEnable(true)
        joinChannel();
        setSpeakerphoneOn(false)
    }

    private fun setRecordAudioParameters() {
        val formatMix = NERtcAudioFrameRequestFormat()
        formatMix.channels = 1 // Mono
        formatMix.sampleRate = 44100 // Sample rate
        formatMix.opMode = NERtcAudioFrameOpMode.kNERtcAudioFrameOpModeReadOnly // Read-only mode
        NERtcEx.getInstance().setRecordingAudioFrameParameters(formatMix)
    }

    private fun setPlaybackAudioParameters() {
        val formatMix = NERtcAudioFrameRequestFormat()
        formatMix.channels = 1 // Mono
        formatMix.sampleRate = 44100 // Sample rate
        formatMix.opMode = NERtcAudioFrameOpMode.kNERtcAudioFrameOpModeReadOnly // Read-only mode
        NERtcEx.getInstance().setPlaybackAudioFrameParameters(formatMix)
    }

    /**
     *改变音频可用状态
     * true: 麦克风开启
     * false: 麦克风关闭
     */
    private fun setLocalAudioEnable(enable: Boolean) {
        NERtcEx.getInstance().enableLocalAudio(enable)
        NERtc.getInstance().setAudioProfile(
            NERtcConstants.AudioScenario.SPEECH, NERtcConstants.AudioProfile.MIDDLE_QUALITY
        )
        // NERtcEx.getInstance().setPlayoutDeviceMute(true)  // Mute the playback device
        // NERtcEx.getInstance().setPlayoutDeviceMute(false) // Unmute the playback device
        // NERtcEx.getInstance().adjustChannelPlaybackSignalVolume(12)
        // NERtcEx.getInstance().adjustLoopBackRecordingSignalVolume(12)
        // NERtcEx.getInstance().adjustPlaybackSignalVolume(12)
        // NERtcEx.getInstance().setRemoteHighPriorityAudioStream(true, 12)
    }

    /**
     * 切换听筒和扬声器
     * true:麦克风
     * false: 听筒
     */
    private fun setSpeakerphoneOn(enable: Boolean) {
        NERtcEx.getInstance().setSpeakerphoneOn(enable)
    }

    fun joinChannel() {
        Log.i(TAG, "joinChannel userId: $userId")

        NERtcEx.getInstance().setChannelProfile(NERtcConstants.RTCChannelProfile.COMMUNICATION)
        NERtcEx.getInstance().joinChannel("", roomId, userId)

    }

    fun leaveChannel(): Boolean {
        setLocalAudioEnable(false)
        val ret = NERtcEx.getInstance().leaveChannel()
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
            managerCallback?.onUserJoined(userId)
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
