package com.xyoye.local_component.ui.activities.quick_access

import android.view.Menu
import android.view.MenuItem
import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.gridEmpty
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.common_component.weight.dialog.CommonDialog
import com.xyoye.data_component.enums.FileFilterType
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityQuickAccessBinding

@Route(path = RouteTable.Local.QuickAccess)
class QuickAccessActivity : BaseActivity<QuickAccessViewModel, ActivityQuickAccessBinding>() {

    private var mMenu: Menu? = null
    private var mAdapter: QuickAccessAdapter? = null

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            QuickAccessViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_quick_access

    override fun initView() {
        title = "快速访问"

        initRv()

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
        syncMenuVisibility()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val m = mMenu ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.action_toggle_view,
            R.id.action_toggle_view_sub -> {
                viewModel.toggleViewMode()
                refreshViewMode()
            }
            R.id.action_filter_folder,
            R.id.action_filter_video,
            R.id.action_filter_image,
            R.id.action_filter_audio -> {
                val selectedTypes = mutableSetOf<FileFilterType>()
                if (m.findItem(R.id.action_filter_folder).isChecked) selectedTypes.add(FileFilterType.FOLDER)
                if (m.findItem(R.id.action_filter_video).isChecked) selectedTypes.add(FileFilterType.VIDEO)
                if (m.findItem(R.id.action_filter_image).isChecked) selectedTypes.add(FileFilterType.IMAGE)
                if (m.findItem(R.id.action_filter_audio).isChecked) selectedTypes.add(FileFilterType.AUDIO)
                viewModel.setFilterTypes(selectedTypes)
            }
            R.id.action_edit -> {
                viewModel.enterEditMode()
                syncMenuVisibility()
                updateAdapterSelection()
            }
            R.id.action_done -> {
                viewModel.exitEditMode()
                syncMenuVisibility()
                updateAdapterSelection()
            }
            R.id.action_delete -> {
                val count = viewModel.selectedItemKeys.size
                if (count == 0) return true
                CommonDialog.Builder(this)
                    .apply {
                        content = "确认取消收藏选中的 $count 个项目?"
                        positiveText = "确认"
                        addPositive { dialog ->
                            dialog.dismiss()
                            viewModel.batchRemove()
                            viewModel.exitEditMode()
                            syncMenuVisibility()
                        }
                        addNegative()
                    }.build().show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun syncMenuVisibility() {
        mMenu?.run {
            findItem(R.id.action_edit)?.isVisible = !viewModel.isEditing
            findItem(R.id.action_done)?.isVisible = viewModel.isEditing
            findItem(R.id.action_delete)?.isVisible = viewModel.isEditing
            findItem(R.id.item_filter)?.isVisible = !viewModel.isEditing
            findItem(R.id.action_toggle_view)?.isVisible = !viewModel.isEditing
            findItem(R.id.action_toggle_view_sub)?.isVisible = !viewModel.isEditing
            title = if (viewModel.isEditing) {
                "已选择 ${viewModel.selectedItemKeys.size} 项"
            } else {
                "快速访问"
            }
        }
    }

    private fun updateAdapterSelection() {
        dataBinding.quickAccessRv.adapter?.notifyDataSetChanged()
    }

    private fun initRv() {
        mAdapter = QuickAccessAdapter(this, viewModel, viewModel.isGridView)
        mAdapter?.setOnItemSelectedListener { position ->
            syncMenuVisibility()
            dataBinding.quickAccessRv.adapter?.notifyItemChanged(position)
        }
        dataBinding.quickAccessRv.apply {
            layoutManager = if (viewModel.isGridView) gridEmpty(2) else vertical()
            adapter = mAdapter?.create()
        }
    }

    private fun refreshViewMode() {
        mAdapter = QuickAccessAdapter(this, viewModel, viewModel.isGridView)
        mAdapter?.setOnItemSelectedListener { position ->
            syncMenuVisibility()
            dataBinding.quickAccessRv.adapter?.notifyItemChanged(position)
        }
        dataBinding.quickAccessRv.apply {
            layoutManager = if (viewModel.isGridView) gridEmpty(2) else vertical()
            adapter = mAdapter?.create()
            viewModel.quickAccessLiveData.value?.let { setData(it) }
        }
    }

    override fun onBackPressed() {
        if (viewModel.isEditing) {
            viewModel.exitEditMode()
            syncMenuVisibility()
            updateAdapterSelection()
            return
        }
        super.onBackPressed()
    }
}
