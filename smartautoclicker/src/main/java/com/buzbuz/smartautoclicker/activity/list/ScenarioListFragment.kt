package com.buzbuz.smartautoclicker.activity.list

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.WindowManager

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.buzbuz.smartautoclicker.activity.creation.CreationState

import com.buzbuz.smartautoclicker.activity.creation.ScenarioCreationDialog
import com.buzbuz.smartautoclicker.activity.creation.ScenarioTypeSelection
import com.buzbuz.smartautoclicker.feature.backup.ui.BackupDialogFragment.Companion.FRAGMENT_TAG_BACKUP_DIALOG
import com.buzbuz.smartautoclicker.feature.backup.ui.BackupDialogFragment

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.gpt40.smartautoclicker.R
import com.gpt40.smartautoclicker.databinding.FragmentScenariosBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

/**
 * 场景列表和创建对话框
 */
@AndroidEntryPoint
class ScenarioListFragment : Fragment() {

    interface Listener {
        fun startScenario(item: ScenarioListUiState.Item)
    }

    private val scenarioListViewModel: ScenarioListViewModel by viewModels()

    private lateinit var viewBinding: FragmentScenariosBinding

    private lateinit var scenariosAdapter: ScenarioAdapter


    private var dialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = FragmentScenariosBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    ///初始化适配器，并设置各类点击事件的处理方法。
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scenariosAdapter = ScenarioAdapter(
            bitmapProvider = scenarioListViewModel::getConditionBitmap,
            startScenarioListener = ::onStartClicked,
            deleteScenarioListener = ::onDeleteClicked,
            exportClickListener = ::onExportClicked,
        )
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * 设置视图绑定和点击事件监听器。
         */
        viewBinding.apply {
            list.adapter = scenariosAdapter
            emptyCreateButton.setOnClickListener { onCreateClicked() }
            add.setOnClickListener { onCreateClicked() }
            topAppBar.setOnMenuItemClickListener { onMenuItemSelected(it) }
            scenarioListViewModel.createDumAndSmart()
        }

        /**
         * 启动一个协程收集 uiState 的变化，并调用 updateUiState 方法进行 UI 更新。
         */

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    Log.d(TAG, "========监听uistate的变化")
                    scenarioListViewModel.uiState.collect(::updateUiState)
                }
            }
        }
    }

    /**
     * 处理菜单项的点击事件，根据不同的菜单项执行不同的操作。
     *
     */
    private fun onMenuItemSelected(item: MenuItem): Boolean {
        val uiState = scenarioListViewModel.uiState.value ?: return false

        when (item.itemId) {
            R.id.action_export -> when {
                uiState.type == ScenarioListUiState.Type.EXPORT -> showBackupDialog(
                    isImport = false,
                    smartScenariosToBackup = scenarioListViewModel.getSmartScenariosSelectedForBackup(),
                    dumbScenariosToBackup = scenarioListViewModel.getDumbScenariosSelectedForBackup(),
                )

                else -> scenarioListViewModel.setUiState(ScenarioListUiState.Type.EXPORT)
            }

            R.id.action_import -> showBackupDialog(true)
            R.id.action_cancel -> scenarioListViewModel.setUiState(ScenarioListUiState.Type.SELECTION)
            R.id.action_search -> scenarioListViewModel.setUiState(ScenarioListUiState.Type.SEARCH)
            R.id.action_select_all -> scenarioListViewModel.toggleAllScenarioSelectionForBackup()
            R.id.action_privacy_settings -> activity?.let(scenarioListViewModel::showPrivacySettings)
            R.id.action_purchase -> context?.let(scenarioListViewModel::showPurchaseActivity)
            R.id.action_troubleshooting -> activity?.let(scenarioListViewModel::showTroubleshootingDialog)
            else -> return false
        }

        return true
    }

    /**
     * 更新 UI 状态，包括菜单和场景列表。
     *
     */
    private fun updateUiState(uiState: ScenarioListUiState?) {
        uiState ?: return
        updateMenu(uiState.menuUiState)
        updateScenarioList(uiState)
    }

    /**
     * 更新菜单项的状态和行为。
     *
     */
    private fun updateMenu(menuState: ScenarioListUiState.Menu) {
        viewBinding.topAppBar.menu.apply {
            findItem(R.id.action_select_all)?.bind(menuState.selectAllItemState)
            findItem(R.id.action_cancel)?.bind(menuState.cancelItemState)
            findItem(R.id.action_import)?.bind(menuState.importItemState)
            findItem(R.id.action_export)?.bind(menuState.exportItemState)
            findItem(R.id.action_search)?.apply {
                bind(menuState.searchItemState)
                actionView?.let { actionView ->
                    (actionView as SearchView).apply {
                        setIconifiedByDefault(true)
                        setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String?) = false
                            override fun onQueryTextChange(newText: String?): Boolean {
                                scenarioListViewModel.updateSearchQuery(newText)
                                return true
                            }
                        })
                        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                            override fun onViewDetachedFromWindow(arg0: View) {
                                scenarioListViewModel.updateSearchQuery(null)
                                scenarioListViewModel.setUiState(ScenarioListUiState.Type.SELECTION)
                            }

                            override fun onViewAttachedToWindow(arg0: View) {}
                        })
                    }
                }
            }
            findItem(R.id.action_privacy_settings)?.bind(menuState.privacyItemState)
            findItem(R.id.action_purchase)?.bind(menuState.purchaseItemState)
            findItem(R.id.action_troubleshooting)?.bind(menuState.troubleshootingItemState)
        }
    }

    /**
     * 更新场景列表的显示状态，根据内容是否为空显示或隐藏相关视图。
     */
    private fun updateScenarioList(uiState: ScenarioListUiState) {
        viewBinding.apply {
            loading.visibility = View.GONE
            if (uiState.listContent.isEmpty() && uiState.type == ScenarioListUiState.Type.SELECTION) {
                list.visibility = View.GONE
                add.visibility = View.GONE
                layoutEmpty.visibility = View.VISIBLE
            } else {
                list.visibility = View.VISIBLE
                add.visibility = View.VISIBLE
                layoutEmpty.visibility = View.GONE
            }
        }


        Log.d(TAG, "model_list: ${uiState.listContent}");
        scenariosAdapter.submitList(uiState.listContent)
    }

    /**
     * 显示一个对话框，并确保同时只有一个对话框显示。
     *
     */
    private fun showDialog(newDialog: AlertDialog) {
        dialog.let {
            Log.w(TAG, "Requesting show dialog while another one is one screen.")
            it?.dismiss()
        }

        dialog = newDialog
        newDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        newDialog.setOnDismissListener { dialog = null }
        newDialog.show()
    }

    /**
     *  启动
     * @param scenario the scenario clicked.
     */
    private fun onStartClicked(scenario: ScenarioListUiState.Item) {
        (requireActivity() as? Listener)?.startScenario(scenario)
    }

    /**
     *  导入
     *
     * @param item the scenario clicked.
     */
    private fun onExportClicked(item: ScenarioListUiState.Item) {
        scenarioListViewModel.toggleScenarioSelectionForBackup(item)
    }

    /**
     * 创建场景
     * Create and show the [dialog]. Upon Ok press, creates the scenario.
     */
    private fun onCreateClicked() {
        ScenarioCreationDialog().show(requireActivity().supportFragmentManager, ScenarioCreationDialog.FRAGMENT_TAG)
    }



    /**
     * 删除
     *
     * @param item the scenario to delete.
     */
    private fun onDeleteClicked(item: ScenarioListUiState.Item) {
        showDialog(
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.dialog_title_delete_scenario)
                .setMessage(resources.getString(R.string.message_delete_scenario, item.displayName))
                .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int ->
                    scenarioListViewModel.deleteScenario(item)
                }.setNegativeButton(android.R.string.cancel, null).create()
        )
    }

    /**
     * 显示备份对话框，根据 isImport 参数决定是导入模式还是导出模式。
     *
     */
    private fun showBackupDialog(
        isImport: Boolean,
        smartScenariosToBackup: Collection<Long>? = null,
        dumbScenariosToBackup: Collection<Long>? = null,
    ) {
        activity?.let {
            BackupDialogFragment.newInstance(isImport, smartScenariosToBackup, dumbScenariosToBackup)
                .show(it.supportFragmentManager, FRAGMENT_TAG_BACKUP_DIALOG)
        }
        scenarioListViewModel.setUiState(ScenarioListUiState.Type.SELECTION)
    }
}

/**
 * 更新 MenuItem 的状态，包括可见性、启用状态和图
 */
private fun MenuItem.bind(state: ScenarioListUiState.Menu.Item) {
    isVisible = state.visible
    isEnabled = state.enabled
    icon = icon?.mutate()?.apply {
        alpha = state.iconAlpha
    }
}

/** Tag for logs. */
private const val TAG = "HUANGZHEN:ScenarioListFragment："