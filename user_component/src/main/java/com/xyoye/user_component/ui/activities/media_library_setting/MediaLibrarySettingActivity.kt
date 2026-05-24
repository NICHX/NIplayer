package com.xyoye.user_component.ui.activities.media_library_setting

import android.app.AlertDialog
import android.app.ProgressDialog
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.setData
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityMediaLibrarySettingBinding

@Route(path = RouteTable.User.MediaLibrarySetting)
class MediaLibrarySettingActivity : BaseActivity<MediaLibrarySettingViewModel, ActivityMediaLibrarySettingBinding>() {

    private var progressDialog: ProgressDialog? = null

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MediaLibrarySettingViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_media_library_setting

    override fun initView() {
        title = "媒体库设置"

        dataBinding.addLibraryBt.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.User.MediaLibraryDetail)
                .withString("content_type", "movie")
                .navigation()
        }

        dataBinding.scanLibraryBt.setOnClickListener {
            showScanModeDialog()
        }

        initLibraryList()

        viewModel.libraryListLiveData.observe(this) { libraries ->
            dataBinding.libraryRv.setData(libraries)
        }

        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.Idle -> {
                    progressDialog?.dismiss()
                    progressDialog = null
                }
                is ScanState.InProgress -> {
                    if (progressDialog == null) {
                        progressDialog = ProgressDialog(this).apply {
                            setTitle("扫描中")
                            setMessage(state.message)
                            setCancelable(false)
                            show()
                        }
                    } else {
                        progressDialog?.setMessage(state.message)
                    }
                }
                is ScanState.Done -> {
                    progressDialog?.dismiss()
                    progressDialog = null
                    viewModel.resetScanState()
                    if (state.success) {
                        AlertDialog.Builder(this)
                            .setTitle("扫描完成")
                            .setMessage(state.message)
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("扫描失败")
                            .setMessage(state.message)
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLibraryList()
    }

    override fun onDestroy() {
        progressDialog?.dismiss()
        progressDialog = null
        super.onDestroy()
    }

    private fun showScanModeDialog() {
        val options = arrayOf("仅扫描文件", "扫描文件并刮削")
        AlertDialog.Builder(this)
            .setTitle("选择扫描模式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.startScan(ScanMode.SCAN_ONLY)
                    1 -> viewModel.startScan(ScanMode.SCAN_AND_SCRAPE)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun initLibraryList() {
        dataBinding.libraryRv.adapter = buildAdapter {
            addItem<MediaLibraryItem, com.xyoye.user_component.databinding.ItemMediaLibraryItemBinding>(R.layout.item_media_library_item) {
                initView { data, _, _ ->
                    itemBinding.typeLabel.text = data.typeLabel
                    itemBinding.pathCount.text = "${data.pathCount}个目录"
                    itemBinding.root.setOnClickListener {
                        ARouter.getInstance()
                            .build(RouteTable.User.MediaLibraryDetail)
                            .withString("content_type", data.type)
                            .navigation()
                    }
                }
            }
        }
    }
}