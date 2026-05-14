package com.xyoye.local_component.ui.fragment.mine

import android.transition.Fade
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import androidx.core.view.isVisible
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
import com.xyoye.common_component.extension.vertical
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.CommonDialog
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.FragmentMineBinding
import com.xyoye.local_component.databinding.ItemMediaLibraryBinding
import com.xyoye.local_component.databinding.ItemMediaLibraryGridBinding
import com.tencent.mmkv.MMKV

@Route(path = RouteTable.Local.MineFragment)
class MineFragment : BaseFragment<MineFragmentViewModel, FragmentMineBinding>() {

    private val gridViewKey = "mine_fragment_grid_view"
    private var isMenuExpanded = false

    private var isGridView: Boolean
        get() = MMKV.defaultMMKV().decodeBool(gridViewKey, true)
        set(value) {
            MMKV.defaultMMKV().encode(gridViewKey, value)
        }

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MineFragmentViewModel::class.java
    )

    override fun getLayoutId() = R.layout.fragment_mine

    override fun initView() {
        viewModel.initLocalStorage()
        initRv()
        dataBinding.moreMenuBt.setOnClickListener {
            toggleMenu()
        }
        dataBinding.viewToggleBt.setOnClickListener {
            collapseMenu()
            dataBinding.mediaLibRv.post { toggleViewMode() }
        }
        dataBinding.addMediaStorageBt.setOnClickListener {
            showAddStorageDialog()
            collapseMenu()
        }
        viewModel.mediaLibWithStatusLiveData.observe(this) {
            dataBinding.mediaLibRv.setData(it)
        }
    }

    private fun initRv() {
        dataBinding.mediaLibRv.apply {
            layoutManager = if (isGridView) grid(3) else vertical()
            adapter = if (isGridView) createGridAdapter() else createListAdapter()
        }
        updateToggleButtonIcon()
    }

    private fun updateToggleButtonIcon() {
        dataBinding.viewToggleBt.setImageResource(
            if (isGridView) R.drawable.ic_view_list else R.drawable.ic_view_grid
        )
    }

    private fun createListAdapter() = buildAdapter {
        addItem<MediaLibraryEntity, ItemMediaLibraryBinding>(R.layout.item_media_library) {
            initView { data, _, _ ->
                itemBinding.apply {
                    libraryNameTv.text = data.displayName
                    libraryUrlTv.text = data.disPlayDescribe
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

    private fun toggleMenu() {
        if (isMenuExpanded) {
            collapseMenu()
        } else {
            expandMenu()
        }
    }

    private fun expandMenu() {
        isMenuExpanded = true
        dataBinding.moreMenuBt.setImageResource(R.drawable.ic_close_white)
        val transition = TransitionSet()
            .addTransition(Slide(Gravity.BOTTOM).setDuration(300))
            .addTransition(Fade().setDuration(300))
        TransitionManager.beginDelayedTransition(dataBinding.fabContainer, transition)
        dataBinding.viewToggleBt.visibility = View.VISIBLE
        dataBinding.addMediaStorageBt.visibility = View.VISIBLE
    }

    private fun collapseMenu() {
        if (!isMenuExpanded) return
        isMenuExpanded = false
        dataBinding.moreMenuBt.setImageResource(R.drawable.ic_more_vert_white)
        val transition = TransitionSet()
            .addTransition(Slide(Gravity.BOTTOM).setDuration(200))
            .addTransition(Fade().setDuration(200))
        TransitionManager.beginDelayedTransition(dataBinding.fabContainer, transition)
        dataBinding.viewToggleBt.visibility = View.GONE
        dataBinding.addMediaStorageBt.visibility = View.GONE
    }

    private fun toggleViewMode() {
        isGridView = !isGridView
        dataBinding.mediaLibRv.apply {
            if (isGridView) {
                layoutManager = grid(3)
                adapter = createGridAdapter()
            } else {
                layoutManager = vertical()
                adapter = createListAdapter()
            }
        }
        updateToggleButtonIcon()
        viewModel.mediaLibWithStatusLiveData.value?.let {
            dataBinding.mediaLibRv.setData(it)
        }
    }

    private fun launchMediaStorage(data: MediaLibraryEntity) {
        when (data.mediaType) {
            MediaType.STREAM_LINK, MediaType.MAGNET_LINK, MediaType.OTHER_STORAGE -> {
                ARouter.getInstance()
                    .build(RouteTable.Local.PlayHistory)
                    .withSerializable("typeValue", data.mediaType.value)
                    .navigation()
            }
            MediaType.LOCAL_STORAGE,
            MediaType.FTP_SERVER,
            MediaType.SMB_SERVER,
            MediaType.WEBDAV_SERVER,
            MediaType.REMOTE_STORAGE,
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
        val actionList = listOf(MediaType.EXTERNAL_STORAGE)
            .map { it.toAction() }

        BottomActionDialog(
            requireActivity(),
            actionList,
            "新增设备存储库"
        ) {
            val mediaType = it.actionId as MediaType
            ARouter.getInstance()
                .build(RouteTable.Stream.StoragePlus)
                .withSerializable("mediaType", mediaType)
                .navigation()
            return@BottomActionDialog true
        }.show()
    }

    private fun showManageStorageDialog(data: MediaLibraryEntity) {
        val actions = mutableListOf<SheetActionBean>()
        actions.add(ManageStorage.Edit.toAction())
        actions.add(ManageStorage.Delete.toAction())
        BottomActionDialog(requireActivity(), actions) {
            if (it.actionId == ManageStorage.Edit) {
                ARouter.getInstance()
                    .build(RouteTable.Stream.StoragePlus)
                    .withSerializable("mediaType", data.mediaType)
                    .withParcelable("editData", data)
                    .navigation()
            } else if (it.actionId == ManageStorage.Delete) {
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
        Edit("编辑媒体库", R.drawable.ic_edit_storage),
        Delete("删除媒体库", R.drawable.ic_delete_storage);

        fun toAction() = SheetActionBean(this, title, icon)
    }
}
