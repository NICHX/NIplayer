package com.xyoye.local_component.ui.fragment.mine

import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.base.BaseFragment
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.grid
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.weight.ExpandableFabMenu
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.FragmentMineBinding
import com.xyoye.local_component.databinding.ItemScrapeMediaBinding
import com.google.android.material.tabs.TabLayout

@Route(path = RouteTable.Local.MineFragment)
class MineFragment : BaseFragment<MineFragmentViewModel, FragmentMineBinding>() {

    private val tmdbRepository = TmdbRepository()
    private var needRefresh = false

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MineFragmentViewModel::class.java
    )

    override fun getLayoutId() = R.layout.fragment_mine

    override fun initView() {
        initRv()
        initTab()
        setupExpandableFab()

        viewModel.scrapeMediaLiveData.observe(this) {
            dataBinding.scrapeMediaRv.setData(it)
        }
    }

    override fun onResume() {
        super.onResume()
        if (needRefresh) {
            needRefresh = false
            viewModel.refreshScrapeData()
        }
    }

    private fun initTab() {
        dataBinding.tabLayout.addTab(dataBinding.tabLayout.newTab().setText("电影"))
        dataBinding.tabLayout.addTab(dataBinding.tabLayout.newTab().setText("电视剧"))

        dataBinding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.switchType(if (tab.position == 0) "movie" else "tv")
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun initRv() {
        dataBinding.scrapeMediaRv.apply {
            layoutManager = grid(3)
            adapter = createAdapter()
        }
    }

    private fun createAdapter() = buildAdapter {
        addItem<ScrapeMediaEntity, ItemScrapeMediaBinding>(R.layout.item_scrape_media) {
            initView { data, _, _ ->
                itemBinding.apply {
                    nameTv.text = data.name
                    if (data.voteAverage > 0) {
                        ratingTv.text = String.format("%.1f", data.voteAverage)
                        ratingTv.visibility = android.view.View.VISIBLE
                    } else {
                        ratingTv.visibility = android.view.View.GONE
                    }

                    val posterUrl = tmdbRepository.buildImageUrl(data.poster)
                    Glide.with(this@MineFragment)
                        .load(posterUrl)
                        .placeholder(R.drawable.ic_video_cover)
                        .into(coverIv)

                    itemLayout.setOnClickListener {
                        ARouter.getInstance()
                            .build(RouteTable.Scrape.ScrapeDetail)
                            .withInt("mediaId", data.id)
                            .navigation()
                    }

                    itemLayout.setOnLongClickListener {
                        showMediaOptionsDialog(data)
                        true
                    }
                }
            }
        }
    }

    private fun setupExpandableFab() {
        dataBinding.expandableFab.addAction(
            ExpandableFabMenu.FabAction(
                id = 1,
                icon = R.drawable.ic_add_white,
                label = "目录设置",
                onClick = {
                    val currentType = viewModel.currentType.value ?: "movie"
                    needRefresh = true
                    ARouter.getInstance()
                        .build(RouteTable.Scrape.MuluSetting)
                        .withString("muluType", currentType)
                        .navigation()
                }
            )
        )
        dataBinding.expandableFab.addAction(
            ExpandableFabMenu.FabAction(
                id = 2,
                icon = R.drawable.ic_sort,
                label = "刷新刮削",
                onClick = {
                    viewModel.refreshScrapeData()
                }
            )
        )
    }

    private fun showMediaOptionsDialog(data: ScrapeMediaEntity) {
        val actions = mutableListOf<com.xyoye.data_component.bean.SheetActionBean>()
        actions.add(
            com.xyoye.data_component.bean.SheetActionBean(
                MediaAction.SearchMatch,
                "手动匹配",
                R.drawable.ic_danmu_search
            )
        )
        actions.add(
            com.xyoye.data_component.bean.SheetActionBean(
                MediaAction.Delete,
                "删除",
                R.drawable.ic_delete_storage
            )
        )

        com.xyoye.common_component.weight.BottomActionDialog(requireActivity(), actions) {
            when (it.actionId) {
                MediaAction.SearchMatch -> {
                    ARouter.getInstance()
                        .build(RouteTable.Scrape.SearchMatch)
                        .withInt("mediaId", data.id)
                        .withString("mediaType", data.mediaType)
                        .navigation()
                }

                MediaAction.Delete -> {
                    viewModel.deleteScrapeMedia(data)
                }
            }
            return@BottomActionDialog true
        }.show()
    }

    private enum class MediaAction {
        SearchMatch,
        Delete
    }
}
