package com.buzbuz.smartautoclicker.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge

import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.buzbuz.smartautoclicker.activity.list.ScenarioListFragment
import com.buzbuz.smartautoclicker.activity.list.ScenarioListUiState
import com.buzbuz.smartautoclicker.core.base.extensions.delayDrawUntil
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.feature.revenue.UserConsentState

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gpt40.smartautoclicker.R
import com.netease.lava.nertc.sdk.NERtc
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay


@AndroidEntryPoint
class ScenarioActivity : AppCompatActivity(), ScenarioListFragment.Listener {

    private val TAG = "Hz:ScenarioActivity:"

    /** 提供点击场景数据给UI的ViewModel。 */

    private val scenarioViewModel: ScenarioViewModel by viewModels()

    /** 投屏权限对话框的结果启动器。 */

    private lateinit var projectionActivityResult: ActivityResultLauncher<Intent>

    /** 用户点击的场景。 */
    private var requestedItem: ScenarioListUiState.Item? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scenario)
        scenarioViewModel.stopScenario()
        scenarioViewModel.requestUserConsent(this)
        scenarioViewModel.createDumAndSmart();

        // 获取权限

        projectionActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

                if (result.resultCode != RESULT_OK) {
                    Toast.makeText(this, R.string.toast_denied_screen_sharing_permission, Toast.LENGTH_SHORT).show()
                } else {
                    (requestedItem?.scenario as? Scenario)?.let { scenario ->
                        startSmartScenario(result, scenario)
                    }
                }
            }

        // 延迟显示闪屏，直到我们有用户同意状态
        findViewById<View>(android.R.id.content).delayDrawUntil {
            scenarioViewModel.userConsentState.value != UserConsentState.UNKNOWN
        }
        requestPermissionsIfNeeded(this);
        scenarioViewModel.setRecordAudioParameters()
        scenarioViewModel.setPlaybackAudioParameters()
        scenarioViewModel.setLocalAudioEnable(true)
        scenarioViewModel.setupNERtc(applicationContext)
        scenarioViewModel.joinChannel()


    }
    private fun requestPermissionsIfNeeded(context: Activity?) {
        val missedPermissions = NERtc.checkPermission(context)
        if (missedPermissions.size > 0) {
            ActivityCompat.requestPermissions(
                context!!, missedPermissions.toTypedArray<String>(), 100
            )
        }
    }
    override fun onResume() {
        super.onResume()
        scenarioViewModel.refreshPurchaseState()
    }

    override fun startScenario(item: ScenarioListUiState.Item) {
        Log.d(TAG, "startScenario***********:$item")
        requestedItem = item
        /// 检测权限
        scenarioViewModel.startPermissionFlowIfNeeded(
            activity = this,
            onAllGranted = ::onMandatoryPermissionsGranted,
        )
    }

    override fun openOver() {

    }

    ///处理所有必要权限被授予的情况。它在需要时启动故障排除流程，然后启动适当的场景。
    private fun onMandatoryPermissionsGranted() {
        Log.d(TAG, "onMandatoryPermissionsGranted:****")
        scenarioViewModel.startTroubleshootingFlowIfNeeded(this) {
            when (val scenario = requestedItem?.scenario) {
                is DumbScenario -> startDumbScenario(scenario)
                is Scenario -> showMediaProjectionWarning()
            }
        }
    }

    private fun showMediaProjectionWarning() {
        Log.d(TAG, "showMediaProjectionWarning:")
        ContextCompat.getSystemService(this, MediaProjectionManager::class.java)?.let { projectionManager ->
            // 某些设备中，定义在com.android.internal.R.string.config_mediaProjectionPermissionDialogComponent的组件名称指定的请求权限对话框无效（例如，华为Honor6X Android 10）。
            // 在这些情况下，没有办法使用这个应用程序。
            try {
                projectionActivityResult.launch(projectionManager.createScreenCaptureIntent())
            } catch (npe: NullPointerException) {
                showUnsupportedDeviceDialog()
            } catch (ex: ActivityNotFoundException) {
                showUnsupportedDeviceDialog()
            }
        }
    }

    /// 请求屏幕捕获权限，并处理不支持设备的异常。
    private fun showUnsupportedDeviceDialog() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_overlay_title_warning)
            .setMessage(R.string.message_error_screen_capture_permission_dialog_not_found)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                finish()
            }.setNegativeButton(android.R.string.cancel, null).create().show()
    }
    /**
     * 打开悬浮
     */
    private fun startDumbScenario(scenario: DumbScenario) {
        Log.d(TAG, "startDumbScenario:")
        handleScenarioStartResult(
            scenarioViewModel.loadDumbScenario(
                context = this,
                scenario = scenario,
            )
        )
    }


    private fun startSmartScenario(result: ActivityResult, scenario: Scenario) {
        Log.d(TAG, "startSmartScenario:")

        handleScenarioStartResult(
            scenarioViewModel.loadSmartScenario(
                context = this,
                resultCode = result.resultCode,
                data = result.data!!,
                scenario = scenario,
            )
        )
    }

    private fun handleScenarioStartResult(result: Boolean) {
        Log.d(TAG, "handleScenarioStartResult:$result")

        if (result) finish()
        else Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
    }

    suspend fun executeVoiceActions() {

        delay(10000) // 延迟1秒


    }
}
