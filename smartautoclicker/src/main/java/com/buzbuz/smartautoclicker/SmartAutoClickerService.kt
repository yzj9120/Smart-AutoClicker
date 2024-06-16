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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.*
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.AndroidRuntimeException
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.buzbuz.smartautoclicker.activity.ScenarioActivity
import com.buzbuz.smartautoclicker.core.base.AndroidExecutor
import com.buzbuz.smartautoclicker.core.base.Dumpable
import com.buzbuz.smartautoclicker.core.base.extensions.requestFilterKeyEvents
import com.buzbuz.smartautoclicker.core.base.extensions.startForegroundMediaProjectionServiceCompat
import com.buzbuz.smartautoclicker.core.bitmaps.IBitmapManager
import com.buzbuz.smartautoclicker.core.common.overlays.manager.OverlayManager
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityMetricsMonitor
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.display.DisplayMetrics
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine
import com.buzbuz.smartautoclicker.core.processing.domain.DetectionRepository
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.gpt40.smartautoclicker.R
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileDescriptor
import java.io.PrintWriter
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 一个 Android 无障碍服务（AccessibilityService）。该服务用于实现智能自动点击功能
 */
@AndroidEntryPoint //注解用于指示 Dagger Hilt 进行依赖注入。
class SmartAutoClickerService : AccessibilityService(), AndroidExecutor {
    private val TAG = "Hz:AutoService:"

    companion object {
        /**此服务的前台通知的标识符*/
        private const val NOTIFICATION_ID = 42

        /**此服务的前台通知的通道标识符*/
        private const val NOTIFICATION_CHANNEL_ID = "SmartAutoClickerService"

        /**通知中的操作*/
        private const val INTENT_ACTION_TOGGLE_OVERLAY = "com.buzbuz.smartautoclicker.ACTION_TOGGLE_OVERLAY_VISIBILITY"
        private const val INTENT_ACTION_STOP_SCENARIO = "com.buzbuz.smartautoclicker.ACTION_STOP_SCENARIO"

        /**[ILocalService]的实例，为该服务提供对“活动”的访问权限*/

        private var LOCAL_SERVICE_INSTANCE: ILocalService? = null
            set(value) {
                field = value
                LOCAL_SERVICE_CALLBACK?.invoke(field)
            }
        private var LOCAL_SERVICE_CALLBACK: ((ILocalService?) -> Unit)? = null
            set(value) {
                field = value
                value?.invoke(LOCAL_SERVICE_INSTANCE)
            }


        /**
         *静态方法，允许活动注册回调以监视的可用性
         *[ILocalService]。如果该服务在注册时已经可用，则会立即进行回调
         *调用。
         *
         *@param state在服务可用时回调要通知的对象。
         */
        fun getLocalService(stateCallback: ((ILocalService?) -> Unit)?) {
            LOCAL_SERVICE_CALLBACK = stateCallback
        }

        fun isServiceStarted(): Boolean = LOCAL_SERVICE_INSTANCE != null
    }

    interface ILocalService {

        fun startDumbScenario(dumbScenario: DumbScenario)
        fun startSmartScenario(resultCode: Int, data: Intent, scenario: Scenario)
        fun stop()
        fun release()
        fun openOverlayManager(dumbScenario: DumbScenario);
        fun gptLiner(dumbScenario: MutableList<NodeInfo>);
    }

    private val localService: LocalService?
        get() = LOCAL_SERVICE_INSTANCE as? LocalService

    ///覆盖层
    @Inject
    lateinit var overlayManager: OverlayManager

    ///屏幕相关
    @Inject
    lateinit var displayMetrics: DisplayMetrics

    ///
    @Inject
    lateinit var detectionRepository: DetectionRepository

    @Inject
    lateinit var dumbEngine: DumbEngine

    ////**管理单击条件的位图*/
    @Inject
    lateinit var bitmapManager: IBitmapManager

    @Inject
    lateinit var qualityRepository: QualityRepository

    @Inject
    lateinit var qualityMetricsMonitor: QualityMetricsMonitor

    @Inject
    lateinit var revenueRepository: IRevenueRepository

    private var currentScenarioName: String? = null

    /** Receives commands from the notification. */
    private var notificationActionsReceiver: BroadcastReceiver? = null

    /**
     * 方法在服务连接时调用，初始化和配置服务
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected...")

        qualityMetricsMonitor.onServiceConnected()
        LOCAL_SERVICE_INSTANCE = LocalService(
            context = this,
            overlayManager = overlayManager,
            displayMetrics = displayMetrics,
            detectionRepository = detectionRepository,
            dumbEngine = dumbEngine,
            bitmapManager = bitmapManager,
            androidExecutor = this,
            onStart = { isSmart, name ->
                Log.d(TAG, "isSmart=$isSmart.....name=$isSmart")
                qualityMetricsMonitor.onServiceForegroundStart()
                currentScenarioName = name
                if (isSmart) {
                    createNotificationChannel()
                    startForegroundMediaProjectionServiceCompat(NOTIFICATION_ID, createNotification())
                }
                requestFilterKeyEvents(true)
            },
            onStop = {},
        )

        notificationActionsReceiver = createNotificationActionReceiver()
        ContextCompat.registerReceiver(
            this,
            notificationActionsReceiver,
            IntentFilter().apply {
                addAction(INTENT_ACTION_TOGGLE_OVERLAY)
                addAction(INTENT_ACTION_STOP_SCENARIO)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /**
     * 断开
     */
    override fun onUnbind(intent: Intent?): Boolean {
        notificationActionsReceiver?.let { unregisterReceiver(it) }
        notificationActionsReceiver = null

        LOCAL_SERVICE_INSTANCE?.stop()
        LOCAL_SERVICE_INSTANCE?.release()
        LOCAL_SERVICE_INSTANCE = null

        qualityMetricsMonitor.onServiceUnbind()
        return super.onUnbind(intent)
    }

    /**
     * 处理按键事件
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean = false

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    /**
     *创建此服务的通知，允许将其设置为前台服务。
     *
     *@返回新建的通知。
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, ScenarioActivity::class.java)
        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.drawable.ic_notification_vector
        else R.drawable.ic_notification

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title, currentScenarioName ?: ""))
            .setContentText(getString(R.string.notification_message))
            .setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(icon).setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setLocalOnly(true)

        localService?.let {
            builder.addAction(
                R.drawable.ic_visible_on,
                getString(R.string.notification_button_toggle_menu),
                PendingIntent.getBroadcast(this, 0, Intent(INTENT_ACTION_TOGGLE_OVERLAY), PendingIntent.FLAG_IMMUTABLE),
            )
            builder.addAction(
                R.drawable.ic_stop,
                getString(R.string.notification_button_stop),
                PendingIntent.getBroadcast(this, 0, Intent(INTENT_ACTION_STOP_SCENARIO), PendingIntent.FLAG_IMMUTABLE),
            )
        }

        return builder.build()
    }

    private fun createNotificationActionReceiver(): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val service = localService ?: return

            when (intent.action) {
                INTENT_ACTION_TOGGLE_OVERLAY -> service.toggleOverlaysVisibility()
                INTENT_ACTION_STOP_SCENARIO -> service.stop()
            }
        }
    }

    /**
     * 执行手势操作
     */
    override suspend fun executeGesture(gestureDescription: GestureDescription) {
        suspendCoroutine<Unit?> { continuation ->
            dispatchGesture(
                gestureDescription,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = continuation.resume(null)
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Gesture cancelled: $gestureDescription")
                        continuation.resume(null)
                    }
                },
                null,
            )
        }
    }

    /**
     * 启动活动
     */
    override fun executeStartActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
            Log.w(TAG, "Can't start activity, it is not found.")
        } catch (arex: AndroidRuntimeException) {
            Log.w(TAG, "Can't start activity, Intent is invalid: $intent", arex)
        }
    }

    /**
     * 发送广播
     */
    override fun executeSendBroadcast(intent: Intent) {
        try {
            sendBroadcast(intent)
        } catch (iaex: IllegalArgumentException) {
            Log.w(TAG, "Can't send broadcast, Intent is invalid: $intent", iaex)
        }
    }

    /**
     *通过adb转储服务的状态。
     *adb shell“dumpsys活动服务com.buzbuz.smartautoclicker”
     */
    override fun dump(fd: FileDescriptor?, writer: PrintWriter?, args: Array<out String>?) {
        if (writer == null) return

        writer.append("* SmartAutoClickerService:").println()
        writer.append(Dumpable.DUMP_DISPLAY_TAB).append("- isStarted=")
            .append("${(LOCAL_SERVICE_INSTANCE as? LocalService)?.isStarted ?: false}; ").append("scenarioName=")
            .append("$currentScenarioName; ").println()
        Log.d(TAG, "dump: $writer")
        displayMetrics.dump(writer)
        bitmapManager.dump(writer)
        overlayManager.dump(writer)
        detectionRepository.dump(writer)
        dumbEngine.dump(writer)
        qualityRepository.dump(writer)
        revenueRepository.dump(writer)
    }

    override fun onInterrupt() { /* Unused */
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        /**
         * PackageName: com.openai.chatgpt; MovementGranularity
         *  聊天首页： Text       contentDescription: 听写 ，开始语音对话 ，开始新聊天，编辑菜单
         *  语音页面：Text 正在关联   (状态1: 加载汇中.....New Voice Mode coming soon  结束语音对话 停止)
         *
         */
        Log.d(TAG,"===============")

        nodeInfoList.clear()
        val rootNode = rootInActiveWindow
        rootNode?.let { traverseNode(it) }
        Log.d(TAG,"nodeInfoList="+ nodeInfoList.toString())
        localService?.gptLiner(nodeInfoList);

    }
    val nodeInfoList = mutableListOf<NodeInfo>()
    private fun traverseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return

        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""

        if(text.isNotEmpty() || contentDescription.isNotEmpty()){
            // 将节点信息添加到列表中
            val info = NodeInfo(text, contentDescription)
            nodeInfoList.add(info)
        }
        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i))
        }
    }
}

data class NodeInfo(val text: String, val contentDescription: String)

/** Tag for the logs. */
private const val TAG = "HUANGZHEN：SmartAutoClickerService"