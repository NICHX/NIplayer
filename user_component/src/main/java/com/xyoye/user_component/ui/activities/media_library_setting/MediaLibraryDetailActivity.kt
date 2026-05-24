package com.xyoye.user_component.ui.activities.media_library_setting

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.CommonDialog
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.MuluConfigEntity
import com.xyoye.user_component.BR
import com.xyoye.user_component.R
import com.xyoye.user_component.databinding.ActivityMediaLibraryDetailBinding

@Route(path = RouteTable.User.MediaLibraryDetail)
class MediaLibraryDetailActivity : BaseActivity<MediaLibraryDetailViewModel, ActivityMediaLibraryDetailBinding>() {

    @Autowired
    @JvmField
    var content_type: String = "movie"

    private var pendingLibraryId: Int = 0

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MediaLibraryDetailViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_media_library_detail

    override fun initView() {
        ARouter.getInstance().inject(this)
        val typeLabel = viewModel.contentTypes.firstOrNull { it.value == content_type }?.label ?: "电影"
        title = "$typeLabel 媒体库"

        viewModel.initType(content_type)
        dataBinding.contentTypeText.text = typeLabel
        initContentTypeSelector()
        initPathList()
        initAddButton()

        viewModel.pathListLiveData.observe(this) { paths ->
            dataBinding.pathRv.setData(paths)
        }
    }

    private fun initContentTypeSelector() {
        dataBinding.contentTypeSelector.setOnClickListener {
            val items = viewModel.contentTypes.map { it.label }.toTypedArray()
            val currentIndex = viewModel.contentTypes.indexOfFirst { it.value == viewModel.getCurrentType() }
            AlertDialog.Builder(this)
                .setTitle("选择内容类型")
                .setSingleChoiceItems(items, currentIndex.coerceAtLeast(0)) { dialog, which ->
                    val selected = viewModel.contentTypes[which]
                    viewModel.setContentType(selected.value)
                    dataBinding.contentTypeText.text = selected.label
                    title = "${selected.label} 媒体库"
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun initPathList() {
        dataBinding.pathRv.adapter = buildAdapter {
            addItem<MuluConfigEntity, com.xyoye.user_component.databinding.ItemMediaLibraryPathBinding>(R.layout.item_media_library_path) {
                initView { data, _, _ ->
                    itemBinding.pathText.text = data.path
                    itemBinding.deleteBtn.setOnClickListener {
                        CommonDialog.Builder(this@MediaLibraryDetailActivity)
                            .apply {
                                content = "确认移除此目录?\n\n${data.path}"
                                positiveText = "确认"
                                addPositive { dialog ->
                                    dialog.dismiss()
                                    viewModel.deletePath(data)
                                }
                                addNegative()
                            }.build().show()
                    }
                }
            }
        }
    }

    private fun initAddButton() {
        dataBinding.addFolderBt.setOnClickListener {
            viewModel.loadAvailableLibraries { libraries ->
                if (libraries.isEmpty()) {
                    ToastCenter.showWarning("请先在设置中添加存储")
                    return@loadAvailableLibraries
                }
                val actions = libraries.map {
                    SheetActionBean(it, it.displayName, it.mediaType.cover)
                }
                BottomActionDialog(this@MediaLibraryDetailActivity, actions, "选择存储位置") { action ->
                    val library = action.actionId as MediaLibraryEntity
                    pendingLibraryId = library.id
                    ARouter.getInstance()
                        .build(RouteTable.Stream.StorageFile)
                        .withParcelable("storageLibrary", library)
                        .withString("pickerMode", "mulu")
                        .navigation(this@MediaLibraryDetailActivity, REQUEST_CODE_PICK_FOLDER)
                    true
                }.show()
            }
        }
    }

    @Deprecated("Use onActivityResult with Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK && data != null) {
            val pickedPath = data.getStringExtra("picked_directory_path") ?: return
            if (pendingLibraryId < 0) return
            viewModel.addPath(pickedPath, pendingLibraryId)
            ToastCenter.showSuccess("已添加目录")
        }
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 2001
    }
}