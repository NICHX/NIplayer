package com.xyoye.user_component.ui.activities.music_metadata

import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.MusicMetadataConfig
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.weight.dialog.MusicMetadataEditDialog
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityMusicMetadataApiBinding

@Route(path = RouteTable.User.MusicMetadataApi)
class MusicMetadataApiActivity :
    BaseActivity<MusicMetadataApiViewModel, ActivityMusicMetadataApiBinding>() {

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            MusicMetadataApiViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_music_metadata_api

    override fun initView() {
        title = "音乐元数据API"

        updateStatus()

        dataBinding.musicMetadataSettingLl.setOnClickListener {
            showEditDialog()
        }
    }

    private fun updateStatus() {
        dataBinding.apiStatusTv.text = viewModel.apiStatus
    }

    private fun showEditDialog() {
        MusicMetadataEditDialog(
            this,
            MusicMetadataConfig.getApiUrl(),
            MusicMetadataConfig.getApiAuth()
        ) { apiUrl, apiAuth ->
            viewModel.saveAll(apiUrl, apiAuth)
            updateStatus()
        }.show()
    }
}