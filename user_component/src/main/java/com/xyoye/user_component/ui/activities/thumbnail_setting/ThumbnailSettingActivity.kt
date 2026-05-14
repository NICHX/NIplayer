package com.xyoye.user_component.ui.activities.thumbnail_setting

import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityThumbnailSettingBinding

@Route(path = RouteTable.User.ThumbnailSetting)
class ThumbnailSettingActivity : BaseActivity<ThumbnailSettingViewModel, ActivityThumbnailSettingBinding>() {

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            ThumbnailSettingViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_thumbnail_setting

    override fun initView() {
        title = "缩略图管理"
    }
}
