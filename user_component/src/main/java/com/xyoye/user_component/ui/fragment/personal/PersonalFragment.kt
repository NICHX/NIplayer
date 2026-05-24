package com.xyoye.user_component.ui.fragment.personal

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseFragment
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.config.TmdbApiConfig
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.CommonEditDialog
import com.xyoye.data_component.bean.EditBean
import com.xyoye.data_component.bean.SheetActionBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        dataBinding.fileBrowserSettingLl?.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.FileBrowserSetting)
                .navigation()
        }

        dataBinding.mediaLibrarySettingLl?.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.MediaLibrarySetting)
                .withString("content_type", "movie")
                .navigation()
        }

        dataBinding.apiManagerLl?.setOnClickListener {
            showApiManagerDialog()
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
        val tmdbApiAction = SheetActionBean(
            actionId = "tmdb_api",
            actionName = "TMDB API",
            actionIconRes = R.drawable.ic_key
        )
        val tmdbTestAction = SheetActionBean(
            actionId = "tmdb_test",
            actionName = "测试TMDB连接",
            actionIconRes = R.drawable.ic_refresh
        )

        BottomActionDialog(
            requireActivity(),
            listOf(subtitleApiAction, musicMetadataApiAction, tmdbApiAction, tmdbTestAction),
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
                "tmdb_api" -> {
                    showTmdbApiKeyDialog()
                }
                "tmdb_test" -> {
                    testTmdbConnection()
                }
            }
            return@BottomActionDialog true
        }.show()
    }

    private fun testTmdbConnection() {
        val apiKey = TmdbApiConfig.apiKey
        if (apiKey.isEmpty()) {
            android.app.AlertDialog.Builder(requireActivity())
                .setTitle("提示")
                .setMessage("请先在「TMDB API」中填写API密钥")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val dialog = android.app.ProgressDialog(requireActivity()).apply {
            setMessage("正在测试TMDB连接...")
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    TmdbRepository().testConnection(apiKey)
                }
                dialog.dismiss()
                android.app.AlertDialog.Builder(requireActivity())
                    .setTitle("连接测试结果")
                    .setMessage(result)
                    .setPositiveButton("确定", null)
                    .show()
            } catch (e: Exception) {
                dialog.dismiss()
                android.app.AlertDialog.Builder(requireActivity())
                    .setTitle("连接测试失败")
                    .setMessage("测试过程异常：${e.message}")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun showTmdbApiKeyDialog() {
        val currentKey = TmdbApiConfig.apiKey
        CommonEditDialog(
            requireActivity() as AppCompatActivity,
            EditBean(
                title = "TMDB API密钥",
                emptyWarningMsg = "请输入TMDB API密钥",
                hint = "请输入TMDB API v3密钥",
                defaultText = currentKey.ifEmpty { null },
                inputTips = "TMDB API密钥用于获取电影/电视剧元数据。\n请访问 https://www.themoviedb.org/settings/api 申请"
            )
        ) { result ->
            TmdbApiConfig.apiKey = result.trim()
            ToastCenter.showSuccess("TMDB API密钥已保存")
        }.show()
    }
}