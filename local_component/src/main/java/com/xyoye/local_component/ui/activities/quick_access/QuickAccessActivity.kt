package com.xyoye.local_component.ui.activities.quick_access

import com.alibaba.android.arouter.facade.annotation.Route
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityQuickAccessBinding

@Route(path = RouteTable.Local.QuickAccess)
class QuickAccessActivity : BaseActivity<QuickAccessViewModel, ActivityQuickAccessBinding>() {

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

    private fun initRv() {
        dataBinding.quickAccessRv.apply {
            layoutManager = vertical()
            adapter = QuickAccessAdapter(this@QuickAccessActivity, viewModel).create()
        }
    }
}