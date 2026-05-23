package com.xyoye.local_component.ui.activities.scrape

import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityScrapeDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Route(path = RouteTable.Scrape.ScrapeDetail)
class ScrapeDetailActivity : BaseActivity<ScrapeDetailViewModel, ActivityScrapeDetailBinding>() {

    @Autowired
    @JvmField
    var mediaId: Int = 0

    private val tmdbRepository = TmdbRepository()
    private var mediaData: ScrapeMediaEntity? = null

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        ScrapeDetailViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_scrape_detail

    override fun initView() {
        ARouter.getInstance().inject(this)

        loadMediaDetail()
    }

    private fun loadMediaDetail() {
        var media: ScrapeMediaEntity? = null
        runBlocking(Dispatchers.IO) {
            media = DatabaseManager.instance.getScrapeMediaDao().getById(mediaId)
        }

        mediaData = media
        if (media == null) {
            finish()
            return
        }

        title = media!!.name

        val posterUrl = tmdbRepository.buildImageUrl(media!!.poster, "w780")
        Glide.with(this)
            .load(posterUrl)
            .placeholder(R.drawable.ic_video_cover)
            .into(dataBinding.detailCoverIv)

        dataBinding.detailNameTv.text = media!!.name
        dataBinding.detailOverviewTv.text = media!!.overview ?: "暂无简介"

        if (media!!.voteAverage > 0) {
            dataBinding.detailRatingTv.text = String.format("%.1f", media!!.voteAverage)
            dataBinding.detailRatingTv.visibility = android.view.View.VISIBLE
        } else {
            dataBinding.detailRatingTv.visibility = android.view.View.GONE
        }

        if (media!!.releaseTime != null) {
            dataBinding.detailReleaseTv.text = media!!.releaseTime
            dataBinding.detailReleaseTv.visibility = android.view.View.VISIBLE
        } else {
            dataBinding.detailReleaseTv.visibility = android.view.View.GONE
        }

        dataBinding.detailPlayBt.setOnClickListener {
            playVideo()
        }

        dataBinding.detailMatchBt.setOnClickListener {
            ARouter.getInstance()
                .build(RouteTable.Scrape.SearchMatch)
                .withInt("mediaId", mediaId)
                .withString("mediaType", media!!.mediaType)
                .navigation()
        }
    }

    private fun playVideo() {
        mediaData?.let { data ->
            ARouter.getInstance()
                .build(RouteTable.Stream.StorageFile)
                .withString("initialStoragePath", data.path)
                .navigation()
        }
    }
}
