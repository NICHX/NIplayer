package com.xyoye.local_component.ui.activities.scrape

import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityMediaWallBinding

@Route(path = RouteTable.Scrape.ScrapeMedia)
class MediaWallActivity : BaseActivity<MediaWallViewModel, ActivityMediaWallBinding>() {

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MediaWallViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_media_wall

    override fun initView() {
        title = "媒体墙"

        viewModel.sections.observe(this) { sections ->
            val isEmpty = sections.isEmpty()
            dataBinding.emptyHint.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
            dataBinding.wallRv.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
            val adapter = MediaWallAdapter(
                sections = sections,
                onItemClick = { media -> navigateToDetail(media) }
            )
            dataBinding.wallRv.layoutManager = LinearLayoutManager(this)
            dataBinding.wallRv.adapter = adapter
        }

        viewModel.loadMedia()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadMedia()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_media_wall, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.refreshScrapeData()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun navigateToDetail(media: ScrapeMediaEntity) {
        ARouter.getInstance()
            .build(RouteTable.Scrape.ScrapeDetail)
            .withInt("mediaId", media.id)
            .navigation()
    }
}