package com.xyoye.local_component.ui.activities.scrape

import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityScrapeMediaBinding

@Route(path = RouteTable.Scrape.ScrapeMedia)
class ScrapeMediaActivity : BaseActivity<ScrapeMediaViewModel, ActivityScrapeMediaBinding>() {

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        ScrapeMediaViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_scrape_media

    override fun initView() {
        ARouter.getInstance().inject(this)
        title = "海报墙"

        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    com.xyoye.local_component.ui.fragment.mine.MineFragment()
                )
                .commit()
        }
    }
}
