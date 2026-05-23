package com.xyoye.local_component.ui.fragment.mine

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseFragment
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.config.TmdbApiConfig
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.FragmentMineBinding
import com.xyoye.local_component.ui.activities.scrape.MediaWallAdapter
import com.xyoye.local_component.ui.activities.scrape.MediaWallViewModel

@Route(path = RouteTable.Local.MineFragment)
class MineFragment : BaseFragment<MineFragmentViewModel, FragmentMineBinding>() {

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        MineFragmentViewModel::class.java
    )

    override fun getLayoutId() = R.layout.fragment_mine

    private val mediaWallViewModel: MediaWallViewModel by lazy {
        ViewModelProvider(this)[MediaWallViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun initView() {
        initMediaWall()
    }

    override fun onResume() {
        super.onResume()
        mediaWallViewModel.loadMedia()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_mine, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                val tmdbKey = TmdbApiConfig.apiKey
                if (tmdbKey.isEmpty()) {
                    ToastCenter.showWarning("请先在「我」→ API管理 中填写TMDB API密钥")
                } else {
                    mediaWallViewModel.refreshScrapeData()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initMediaWall() {
        mediaWallViewModel.sections.observe(this) { sections ->
            val isEmpty = sections.isEmpty()
            dataBinding.emptyHint.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
            dataBinding.wallRv.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
            if (!isEmpty) {
                val adapter = MediaWallAdapter(
                    sections = sections,
                    onItemClick = { media -> navigateToDetail(media) },
                    onItemLongClick = { media -> showMediaOptionsDialog(media) }
                )
                dataBinding.wallRv.layoutManager = LinearLayoutManager(requireContext())
                dataBinding.wallRv.adapter = adapter
            }
        }
        mediaWallViewModel.loadMedia()
    }

    private fun showMediaOptionsDialog(data: ScrapeMediaEntity) {
        val actions = mutableListOf<SheetActionBean>()
        actions.add(
            SheetActionBean(
                MediaAction.SearchMatch,
                "手动匹配",
                R.drawable.ic_danmu_search
            )
        )
        actions.add(
            SheetActionBean(
                MediaAction.Delete,
                "删除",
                R.drawable.ic_delete_storage
            )
        )

        BottomActionDialog(requireActivity(), actions) {
            when (it.actionId) {
                MediaAction.SearchMatch -> {
                    ARouter.getInstance()
                        .build(RouteTable.Scrape.SearchMatch)
                        .withInt("mediaId", data.id)
                        .withString("mediaType", data.mediaType)
                        .navigation()
                }

                MediaAction.Delete -> {
                    mediaWallViewModel.deleteMedia(data)
                }
            }
            return@BottomActionDialog true
        }.show()
    }

    private fun navigateToDetail(media: ScrapeMediaEntity) {
        ARouter.getInstance()
            .build(RouteTable.Scrape.ScrapeDetail)
            .withInt("mediaId", media.id)
            .navigation()
    }

    private enum class MediaAction {
        SearchMatch,
        Delete
    }
}