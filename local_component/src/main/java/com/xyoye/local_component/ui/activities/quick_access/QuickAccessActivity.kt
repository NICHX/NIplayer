package com.xyoye.local_component.ui.activities.quick_access

import android.view.Menu
import android.view.MenuItem
import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.gridEmpty
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.common_component.weight.ExpandableFabMenu
import com.xyoye.data_component.enums.FileFilterType
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityQuickAccessBinding

@Route(path = RouteTable.Local.QuickAccess)
class QuickAccessActivity : BaseActivity<QuickAccessViewModel, ActivityQuickAccessBinding>() {

    private var mMenu: Menu? = null

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            QuickAccessViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_quick_access

    override fun initView() {
        title = "快速访问"

        initRv()
        initExpandableFab()

        viewModel.quickAccessLiveData.observe(this) {
            dataBinding.quickAccessRv.setData(it)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadQuickAccessItems()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_quick_access, menu)
        mMenu = menu
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val m = mMenu ?: return super.onOptionsItemSelected(item)
        val filterFolderItem = m.findItem(R.id.action_filter_folder)
        val filterVideoItem = m.findItem(R.id.action_filter_video)
        val filterImageItem = m.findItem(R.id.action_filter_image)
        val filterAudioItem = m.findItem(R.id.action_filter_audio)

        when (item.itemId) {
            R.id.action_filter_folder,
            R.id.action_filter_video,
            R.id.action_filter_image,
            R.id.action_filter_audio -> {
                val selectedTypes = mutableSetOf<FileFilterType>()
                if (filterFolderItem.isChecked) selectedTypes.add(FileFilterType.FOLDER)
                if (filterVideoItem.isChecked) selectedTypes.add(FileFilterType.VIDEO)
                if (filterImageItem.isChecked) selectedTypes.add(FileFilterType.IMAGE)
                if (filterAudioItem.isChecked) selectedTypes.add(FileFilterType.AUDIO)
                viewModel.setFilterTypes(selectedTypes)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initRv() {
        dataBinding.quickAccessRv.apply {
            layoutManager = vertical()
            adapter = QuickAccessAdapter(this@QuickAccessActivity, viewModel, viewModel.isGridView).create()
        }
    }

    private fun initExpandableFab() {
        dataBinding.expandableFab.addAction(
            ExpandableFabMenu.FabAction(
                id = 1,
                icon = if (viewModel.isGridView) R.drawable.ic_view_list else R.drawable.ic_view_grid,
                label = if (viewModel.isGridView) "列表视图" else "网格视图",
                onClick = {
                    viewModel.toggleViewMode()
                    dataBinding.expandableFab.updateAction(
                        1,
                        if (viewModel.isGridView) R.drawable.ic_view_list else R.drawable.ic_view_grid,
                        if (viewModel.isGridView) "列表视图" else "网格视图"
                    )
                    refreshViewMode()
                }
            )
        )
    }

    private fun refreshViewMode() {
        dataBinding.quickAccessRv.apply {
            layoutManager = if (viewModel.isGridView) gridEmpty(2) else vertical()
            adapter = QuickAccessAdapter(this@QuickAccessActivity, viewModel, viewModel.isGridView).create()
            viewModel.quickAccessLiveData.value?.let { setData(it) }
        }
    }
}
