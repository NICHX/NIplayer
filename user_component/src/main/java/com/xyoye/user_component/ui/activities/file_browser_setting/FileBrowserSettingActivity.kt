package com.xyoye.user_component.ui.activities.file_browser_setting

import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityFileBrowserSettingBinding

@Route(path = RouteTable.User.FileBrowserSetting)
class FileBrowserSettingActivity :
    BaseActivity<FileBrowserSettingViewModel, ActivityFileBrowserSettingBinding>() {

    override fun initView() {
        title = "文件浏览器设置"

        dataBinding.thumbnailSettingLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.ThumbnailSetting)
                .navigation()
        }

        dataBinding.scanManagerLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.ScanManager)
                .navigation()
        }

        dataBinding.commonlyManagerLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.CommonManager)
                .navigation()
        }
    }

    override fun getLayoutId() = R.layout.activity_file_browser_setting

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            FileBrowserSettingViewModel::class.java
        )
}