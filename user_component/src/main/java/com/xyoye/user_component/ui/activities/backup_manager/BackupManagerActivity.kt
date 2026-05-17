package com.xyoye.user_component.ui.activities.backup_manager

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.config.WebDavBackupConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.enums.MediaType
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityBackupManagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Route(path = RouteTable.User.BackupManager)
class BackupManagerActivity :
    BaseActivity<BackupManagerViewModel, ActivityBackupManagerBinding>() {

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportConfig(this, uri)
            if (WebDavBackupConfig.enabled) {
                viewModel.uploadBackupToWebDav(this)
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importConfig(this, uri)
        }
    }

    override fun initView() {
        title = "备份管理"

        dataBinding.exportLl.setOnClickListener {
            val fileName = viewModel.getBackupFileName()
            exportLauncher.launch(fileName)
        }

        dataBinding.importLl.setOnClickListener {
            viewModel.restoreFromWebDav(this)
        }

        dataBinding.importLl.setOnLongClickListener {
            importLauncher.launch(arrayOf("application/json"))
            true
        }

        setupWebDavViews()
        loadWebDavConfig()
    }

    private fun setupWebDavViews() {
        val binding = dataBinding

        binding.webdavHeaderLl.setOnClickListener {
            val isVisible = binding.webdavSettingsLl.visibility == View.VISIBLE
            binding.webdavSettingsLl.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.webdavEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            WebDavBackupConfig.enabled = isChecked
        }

        binding.serverModeRg.setOnCheckedChangeListener { _, checkedId ->
            val isCustom = checkedId == R.id.custom_mode_rb
            binding.customFieldsLl.visibility = if (isCustom) View.VISIBLE else View.GONE
            binding.existingServerLl.visibility = if (isCustom) View.GONE else View.VISIBLE
            WebDavBackupConfig.serverMode = if (isCustom) "custom" else "existing"
        }

        binding.passwordToggleIv.setOnClickListener {
            val passwordEt = binding.webdavPasswordEt
            val isPassword = passwordEt.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            passwordEt.inputType = if (isPassword) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            passwordEt.setSelection(passwordEt.length())
        }

        binding.selectServerTv.setOnClickListener {
            showServerSelectionDialog()
        }

        binding.saveWebdavConfigTv.setOnClickListener {
            saveWebDavConfig()
        }

        binding.uploadNowTv.setOnClickListener {
            saveWebDavConfig()
            viewModel.uploadBackupToWebDav(this)
        }
    }

    private fun loadWebDavConfig() {
        val config = WebDavBackupConfig
        dataBinding.webdavEnableSwitch.isChecked = config.enabled

        if (config.serverMode == "existing") {
            dataBinding.existingModeRb.isChecked = true
            dataBinding.customFieldsLl.visibility = View.GONE
            dataBinding.existingServerLl.visibility = View.VISIBLE
            updateSelectedServerDisplay()
        } else {
            dataBinding.customModeRb.isChecked = true
            dataBinding.customFieldsLl.visibility = View.VISIBLE
            dataBinding.existingServerLl.visibility = View.GONE
        }

        dataBinding.webdavUrlEt.setText(config.customUrl)
        dataBinding.webdavAccountEt.setText(config.customAccount)
        dataBinding.webdavPasswordEt.setText(config.customPassword)
        dataBinding.webdavDirEt.setText(config.directory)
        dataBinding.keepCountEt.setText(config.keepCount.toString())
    }

    private fun saveWebDavConfig() {
        val config = WebDavBackupConfig
        config.enabled = dataBinding.webdavEnableSwitch.isChecked
        config.serverMode = if (dataBinding.customModeRb.isChecked) "custom" else "existing"
        config.customUrl = dataBinding.webdavUrlEt.text?.toString()?.trim() ?: ""
        config.customAccount = dataBinding.webdavAccountEt.text?.toString()?.trim() ?: ""
        config.customPassword = dataBinding.webdavPasswordEt.text?.toString()?.trim() ?: ""

        val dirText = dataBinding.webdavDirEt.text?.toString()?.trim() ?: ""
        config.directory = dirText.ifBlank { "/NIplayer_backup" }

        val keepText = dataBinding.keepCountEt.text?.toString()?.trim() ?: ""
        config.keepCount = keepText.toIntOrNull()?.coerceIn(1, 99) ?: 5
        dataBinding.keepCountEt.setText(config.keepCount.toString())

        com.xyoye.common_component.weight.ToastCenter.showSuccess("WebDAV备份设置已保存")
    }

    private fun showServerSelectionDialog() {
        val servers = runBlocking(Dispatchers.IO) {
            DatabaseManager.instance.getMediaLibraryDao()
                .getByMediaTypeSuspend(MediaType.WEBDAV_SERVER)
        }

        if (servers.isEmpty()) {
            com.xyoye.common_component.weight.ToastCenter.showError("没有已添加的WebDAV服务器，请先在存储管理中添加上传服务器")
            return
        }

        val serverNames = servers.map { it.displayName }.toTypedArray()
        val currentId = WebDavBackupConfig.existingServerId
        var selectedIndex = servers.indexOfFirst { it.id == currentId }
        if (selectedIndex < 0) selectedIndex = 0

        AlertDialog.Builder(this)
            .setTitle("选择WebDAV服务器")
            .setSingleChoiceItems(serverNames, selectedIndex) { dialog, which ->
                selectedIndex = which
            }
            .setPositiveButton("确定") { _, _ ->
                val server = servers[selectedIndex]
                WebDavBackupConfig.existingServerId = server.id
                updateSelectedServerDisplay()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateSelectedServerDisplay() {
        val serverName = viewModel.getWebDavServerDisplayName()
        val nameTv = dataBinding.existingServerLl.getChildAt(0) as? android.widget.TextView
        nameTv?.text = serverName
        nameTv?.setTextColor(
            if (serverName != "请选择WebDAV服务器")
                resources.getColor(com.xyoye.common_component.R.color.text_black, null)
            else
                resources.getColor(com.xyoye.common_component.R.color.text_gray, null)
        )
    }

    override fun getLayoutId() = R.layout.activity_backup_manager

    override fun initViewModel() =
        ViewModelInit(BR.viewModel, BackupManagerViewModel::class.java)
}