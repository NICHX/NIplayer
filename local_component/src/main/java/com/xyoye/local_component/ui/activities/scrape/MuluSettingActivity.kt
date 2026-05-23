package com.xyoye.local_component.ui.activities.scrape

import android.app.Activity
import android.content.Intent
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.grid
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.CommonDialog
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.MuluConfigEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityMuluSettingBinding
import com.xyoye.local_component.databinding.ItemMediaLibraryGridBinding

@Route(path = RouteTable.Scrape.MuluSetting)
class MuluSettingActivity : BaseActivity<MuluSettingViewModel, ActivityMuluSettingBinding>() {

    @Autowired
    @JvmField
    var muluType: String = "tv"

    private var pendingLibraryId: Int = -1

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MuluSettingViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_mulu_setting

    override fun initView() {
        ARouter.getInstance().inject(this)
        title = if (muluType == "tv") "电视剧目录设置" else "电影目录设置"

        initRv()

        viewModel.loadMuluList(muluType)

        viewModel.muluListLiveData.observe(this) {
            dataBinding.muluRv.setData(it)
        }

        dataBinding.addMuluBt.setOnClickListener {
            showAddMuluDialog()
        }
    }

    @Deprecated("Use onActivityResult with Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK && data != null) {
            val pickedPath = data.getStringExtra("picked_directory_path") ?: return
            val libraryId = data.getIntExtra("picked_library_id", -1)
            if (libraryId < 0) return

            val config = MuluConfigEntity(
                mediaLibraryId = libraryId,
                muluType = muluType,
                path = pickedPath
            )
            viewModel.addMulu(config)
            ToastCenter.showSuccess("已添加刮削目录")
        }
    }

    private fun initRv() {
        dataBinding.muluRv.apply {
            layoutManager = grid(3)
            adapter = createAdapter()
        }
    }

    private fun createAdapter() = buildAdapter {
        addItem<MuluConfigEntity, ItemMediaLibraryGridBinding>(R.layout.item_media_library_grid) {
            initView { data, _, _ ->
                itemBinding.apply {
                    libraryNameTv.text = data.path
                    libraryCoverIv.setImageResource(R.drawable.ic_local_storage)
                    itemLayout.setOnClickListener {
                        showManageMuluDialog(data)
                    }
                }
            }
        }
    }

    private fun showAddMuluDialog() {
        viewModel.loadAvailableLibraries { libraries ->
            if (libraries.isEmpty()) return@loadAvailableLibraries

            val actions = libraries.map {
                SheetActionBean(it, it.displayName, it.mediaType.cover)
            }

            BottomActionDialog(this, actions) { action ->
                val library = action.actionId as MediaLibraryEntity
                showFolderPicker(library)
                true
            }.show()
        }
    }

    private fun showFolderPicker(library: MediaLibraryEntity) {
        pendingLibraryId = library.id
        ARouter.getInstance()
            .build(RouteTable.Stream.StorageFile)
            .withParcelable("storageLibrary", library)
            .withString("pickerMode", "mulu")
            .navigation(this, REQUEST_CODE_PICK_FOLDER)
    }

    private fun showManageMuluDialog(data: MuluConfigEntity) {
        val actions = mutableListOf<SheetActionBean>()
        actions.add(SheetActionBean(ManageAction.Delete, "删除目录", R.drawable.ic_delete_storage))

        BottomActionDialog(this, actions) {
            if (it.actionId == ManageAction.Delete) {
                showDeleteMuluDialog(data)
            }
            return@BottomActionDialog true
        }.show()
    }

    private fun showDeleteMuluDialog(data: MuluConfigEntity) {
        CommonDialog.Builder(this)
            .apply {
                content = "确认删除此刮削目录?\n\n${data.path}"
                positiveText = "确认"
                addPositive { dialog ->
                    dialog.dismiss()
                    viewModel.deleteMulu(data)
                }
                addNegative()
            }.build().show()
    }

    private enum class ManageAction {
        Delete
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1001
    }
}
