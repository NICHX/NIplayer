package com.xyoye.user_component.ui.activities.backup_manager

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityBackupManagerBinding

@Route(path = RouteTable.User.BackupManager)
class BackupManagerActivity :
    BaseActivity<BackupManagerViewModel, ActivityBackupManagerBinding>() {

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportConfig(this, uri)
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
            exportLauncher.launch("NIplayer_config.json")
        }

        dataBinding.importLl.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }
    }

    override fun getLayoutId() = R.layout.activity_backup_manager

    override fun initViewModel() =
        ViewModelInit(BR.viewModel, BackupManagerViewModel::class.java)
}
