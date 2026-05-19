package com.xyoye.local_component.ui.fragment.mine

import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.application.DanDanPlay
import com.xyoye.common_component.base.BaseFragment
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.deletable
import com.xyoye.common_component.extension.grid
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ExpandableFabMenu
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.CommonDialog
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.FragmentMineBinding
import com.xyoye.local_component.databinding.ItemMediaLibraryGridBinding

@Route(path = RouteTable.Local.MineFragment)
class MineFragment : BaseFragment<MineFragmentViewModel, FragmentMineBinding>() {

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MineFragmentViewModel::class.java
    )

    override fun getLayoutId() = R.layout.fragment_mine

    override fun initView() {
        viewModel.initLocalStorage()

        initRv()

        setupExpandableFab()

        viewModel.mediaLibWithStatusLiveData.observe(this) {
            dataBinding.mediaLibRv.setData(it)
        }
    }

    private fun setupExpandableFab() {
        dataBinding.expandableFab.addAction(
            ExpandableFabMenu.FabAction(
                id = 2,
                icon = R.drawable.ic_add_white,
                label = "新增设备存储库",
                onClick = { showAddStorageDialog() }
            )
        )
    }

    private fun initRv() {
        dataBinding.mediaLibRv.apply {
            layoutManager = grid(3)
            adapter = createGridAdapter()
        }
    }

    private fun createGridAdapter() = buildAdapter {
        addItem<MediaLibraryEntity, ItemMediaLibraryGridBinding>(R.layout.item_media_library_grid) {
            initView { data, _, _ ->
                itemBinding.apply {
                    libraryNameTv.text = data.displayName
                    libraryCoverIv.setImageResource(data.mediaType.cover)
                    itemLayout.setOnClickListener {
                        DanDanPlay.permission.storage.request(this@MineFragment) {
                            onGranted {
                                launchMediaStorage(data)
                            }
                            onDenied {
                                ToastCenter.showError("获取文件读取权限失败，无法打开媒体库")
                            }
                        }
                    }
                    itemLayout.setOnLongClickListener {
                        if (data.mediaType.deletable) {
                            showManageStorageDialog(data)
                        }
                        true
                    }
                }
            }
        }
    }

    private fun launchMediaStorage(data: MediaLibraryEntity) {
        when (data.mediaType) {
            MediaType.OTHER_STORAGE -> {
                ARouter.getInstance()
                    .build(RouteTable.Local.PlayHistory)
                    .withSerializable("typeValue", data.mediaType.value)
                    .navigation()
            }

            MediaType.QUICK_ACCESS -> {
                ARouter.getInstance()
                    .build(RouteTable.Local.QuickAccess)
                    .navigation()
            }

            MediaType.LOCAL_STORAGE,
            MediaType.FTP_SERVER,
            MediaType.SMB_SERVER,
            MediaType.WEBDAV_SERVER,
            MediaType.EXTERNAL_STORAGE,
            MediaType.ALSIT_STORAGE -> {
                ARouter.getInstance()
                    .build(RouteTable.Stream.StorageFile)
                    .withParcelable("storageLibrary", data)
                    .navigation()
            }
        }
    }

    private fun showAddStorageDialog() {
        BottomActionDialog(
            requireActivity(),
            listOf(MediaType.EXTERNAL_STORAGE.toAction()),
            "新增"
        ) {
            ARouter.getInstance()
                .build(RouteTable.Stream.StoragePlus)
                .withSerializable("mediaType", it.actionId as MediaType)
                .navigation()
            return@BottomActionDialog true
        }.show()
    }

    private fun showManageStorageDialog(data: MediaLibraryEntity) {
        val actions = mutableListOf<SheetActionBean>()
        actions.add(ManageStorage.Delete.toAction())

        BottomActionDialog(requireActivity(), actions) {
            if (it.actionId == ManageStorage.Delete) {
                showDeleteStorageDialog(data)
            }
            return@BottomActionDialog true
        }.show()
    }

    private fun showDeleteStorageDialog(data: MediaLibraryEntity) {
        CommonDialog.Builder(requireActivity())
            .apply {
                content = "确认删除以下媒体库?\n\n${data.displayName}"
                positiveText = "确认"
                addPositive { dialog ->
                    dialog.dismiss()
                    viewModel.deleteStorage(data)
                }
                addNegative()
            }.build().show()
    }

    private enum class ManageStorage(val title: String, val icon: Int) {
        Delete("删除媒体库", R.drawable.ic_delete_storage);

        fun toAction() = SheetActionBean(this, title, icon)
    }
}
