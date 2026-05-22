package com.xyoye.user_component.ui.fragment.personal

import androidx.core.view.isVisible
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseFragment
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.FragmentPersonalBinding

/**
 * Created by xyoye on 2020/7/28.
 */

@Route(path = RouteTable.User.PersonalFragment)
class PersonalFragment : BaseFragment<PersonalFragmentViewModel, FragmentPersonalBinding>() {

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        PersonalFragmentViewModel::class.java
    )

    override fun getLayoutId() = R.layout.fragment_personal

    override fun initView() {
        initClick()
    }

    private fun initClick() {
        dataBinding.playerSettingLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.SettingPlayer)
                .navigation()
        }

        dataBinding.scanManagerLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.ScanManager)
                .navigation()
        }

        dataBinding.cacheManagerLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.CacheManager)
                .navigation()
        }

        dataBinding.commonlyManagerLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.CommonManager)
                .navigation()
        }

        dataBinding.apiManagerLl?.setOnClickListener {
            showApiManagerDialog()
        }

        dataBinding.thumbnailSettingLl?.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.ThumbnailSetting)
                .navigation()
        }

        dataBinding.appSettingLl.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.SettingApp)
                .navigation()
        }
    }

    private fun showApiManagerDialog() {
        val subtitleApiAction = SheetActionBean(
            actionId = "subtitle_api",
            actionName = "字幕API",
            actionIconRes = R.drawable.ic_shooter_subtitle_download
        )
        val musicMetadataApiAction = SheetActionBean(
            actionId = "music_metadata_api",
            actionName = "音乐元数据API",
            actionIconRes = R.drawable.ic_domain_url
        )

        BottomActionDialog(
            requireActivity(),
            listOf(subtitleApiAction, musicMetadataApiAction),
            "API管理"
        ) {
            when (it.actionId) {
                "subtitle_api" -> {
                    ARouter.getInstance()
                        .build(RouteTable.Local.ShooterSubtitle)
                        .navigation()
                }
                "music_metadata_api" -> {
                    ARouter.getInstance()
                        .build(RouteTable.User.MusicMetadataApi)
                        .navigation()
                }
            }
            return@BottomActionDialog true
        }.show()
    }
}