package com.xyoye.local_component.ui.activities.scrape

import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.data_component.entity.TmdbSearchItem
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivitySearchMatchBinding
import com.xyoye.local_component.databinding.ItemSearchMatchBinding

@Route(path = RouteTable.Scrape.SearchMatch)
class SearchMatchActivity : BaseActivity<SearchMatchViewModel, ActivitySearchMatchBinding>() {

    @Autowired
    @JvmField
    var mediaId: Int = 0

    @Autowired
    @JvmField
    var mediaType: String = "tv"

    private val tmdbRepository = TmdbRepository()

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        SearchMatchViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_search_match

    override fun initView() {
        ARouter.getInstance().inject(this)
        title = "手动匹配"

        initRv()

        dataBinding.searchBtn.setOnClickListener { performSearch() }

        dataBinding.searchEt.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }

        viewModel.searchResultLiveData.observe(this) { results ->
            dataBinding.searchResultRv.setData(results)
        }
    }

    private fun initRv() {
        dataBinding.searchResultRv.apply {
            layoutManager = vertical()
            adapter = createAdapter()
        }
    }

    private fun createAdapter() = buildAdapter {
        addItem<TmdbSearchItem, ItemSearchMatchBinding>(R.layout.item_search_match) {
            initView { data, _, _ ->
                itemBinding.apply {
                    matchNameTv.text = data.name ?: data.title ?: ""
                    matchOverviewTv.text = data.overview ?: ""

                    val posterUrl = tmdbRepository.buildImageUrl(data.poster_path)
                    Glide.with(this@SearchMatchActivity)
                        .load(posterUrl)
                        .placeholder(R.drawable.ic_video_cover)
                        .into(matchCoverIv)

                    itemLayout.setOnClickListener {
                        viewModel.selectMatch(mediaId, data, mediaType)
                        finish()
                    }
                }
            }
        }
    }

    private fun performSearch() {
        val query = dataBinding.searchEt.text.toString()
        if (query.isNotBlank()) {
            viewModel.searchMulti(query)
        }
    }
}
