package com.xyoye.local_component.ui.activities.scrape

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.factory.StorageVideoSourceFactory
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.EpisodeEntity
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityScrapeDetailBinding
import com.xyoye.local_component.databinding.ItemEpisodeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route(path = RouteTable.Scrape.ScrapeDetail)
class ScrapeDetailActivity : BaseActivity<ScrapeDetailViewModel, ActivityScrapeDetailBinding>() {

    companion object {
        private const val TAG = "ScrapeDetailActivity"
    }

    @Autowired
    @JvmField
    var mediaId: Int = 0

    private val tmdbRepository = TmdbRepository()
    private var mediaData: com.xyoye.data_component.entity.ScrapeMediaEntity? = null

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        ScrapeDetailViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_scrape_detail

    override fun initView() {
        ARouter.getInstance().inject(this)

        loadMediaDetail()
        viewModel.episodes.observe(this) { episodeList ->
            val showSection = episodeList.isNotEmpty()
            dataBinding.detailEpisodeHeaderTv.visibility =
                if (showSection) android.view.View.VISIBLE else android.view.View.GONE
            dataBinding.detailEpisodeRv.visibility =
                if (showSection) android.view.View.VISIBLE else android.view.View.GONE
            if (showSection) {
                dataBinding.detailEpisodeRv.layoutManager = LinearLayoutManager(this)
                dataBinding.detailEpisodeRv.adapter = EpisodeAdapter(episodeList) { episode ->
                    playEpisode(episode)
                }
            }
        }

        viewModel.loadEpisodes(mediaId)
    }

    private fun loadMediaDetail() {
        lifecycleScope.launch(Dispatchers.IO) {
            val media = DatabaseManager.instance.getScrapeMediaDao().getById(mediaId)
            withContext(Dispatchers.Main) {
                mediaData = media
                if (media == null) {
                    finish()
                    return@withContext
                }

                title = media.name

                val posterUrl = tmdbRepository.buildImageUrl(media.poster, "w780")
                Glide.with(this@ScrapeDetailActivity)
                    .load(posterUrl)
                    .placeholder(R.drawable.ic_video_cover)
                    .into(dataBinding.detailCoverIv)

                dataBinding.detailNameTv.text = media.name
                dataBinding.detailOverviewTv.text = media.overview ?: "暂无简介"

                if (media.voteAverage > 0) {
                    dataBinding.detailRatingTv.text = String.format("%.1f", media.voteAverage)
                    dataBinding.detailRatingTv.visibility = android.view.View.VISIBLE
                } else {
                    dataBinding.detailRatingTv.visibility = android.view.View.GONE
                }

                if (media.releaseTime != null) {
                    dataBinding.detailReleaseTv.text = media.releaseTime
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
                        .withString("mediaType", media.mediaType)
                        .navigation()
                }
            }
        }
    }

    private fun playVideo() {
        mediaData?.let { data ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val library = findLibraryByMediaPath(data.path)
                    if (library == null) {
                        withContext(Dispatchers.Main) {
                            ToastCenter.showError("无法找到媒体库信息")
                        }
                        return@launch
                    }

                    val storage = StorageFactory.createStorage(library) ?: return@launch
                    var file = try { storage.pathFile(data.path, false) } catch (_: Exception) { null }
                    if (file == null) {
                        for (ext in listOf("mkv", "mp4", "avi", "wmv", "mov", "ts", "flv", "webm")) {
                            file = try { storage.pathFile("${data.path}.$ext", false) } catch (_: Exception) { null }
                            if (file != null) break
                        }
                    }
                    if (file == null) {
                        withContext(Dispatchers.Main) {
                            ToastCenter.showError("找不到播放文件")
                        }
                        return@launch
                    }

                    val playHistory = DatabaseManager.instance.getPlayHistoryDao()
                        .getPlayHistory(file.uniqueKey(), library.id)
                    file.playHistory = playHistory

                    val source = StorageVideoSourceFactory.create(file)
                    if (source != null) {
                        VideoSourceManager.getInstance().setSource(source)
                        withContext(Dispatchers.Main) {
                            ARouter.getInstance()
                                .build(RouteTable.Player.Player)
                                .navigation()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "playVideo error", e)
                    withContext(Dispatchers.Main) {
                        ToastCenter.showError("播放失败: ${e.message}")
                    }
                }
            }
        }
    }

    private fun playEpisode(episode: EpisodeEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val library = findLibraryByMediaPath(episode.filePath)
                if (library == null) {
                    withContext(Dispatchers.Main) {
                        ToastCenter.showError("无法找到媒体库信息")
                    }
                    return@launch
                }

                val storage = StorageFactory.createStorage(library) ?: return@launch
                var file = try { storage.pathFile(episode.filePath, false) } catch (_: Exception) { null }
                if (file == null) {
                    for (ext in listOf("mkv", "mp4", "avi", "wmv", "mov", "ts", "flv", "webm")) {
                        file = try { storage.pathFile("${episode.filePath}.$ext", false) } catch (_: Exception) { null }
                        if (file != null) break
                    }
                }
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        ToastCenter.showError("找不到播放文件")
                    }
                    return@launch
                }

                val playHistory = DatabaseManager.instance.getPlayHistoryDao()
                    .getPlayHistory(file.uniqueKey(), library.id)
                file.playHistory = playHistory

                val source = StorageVideoSourceFactory.create(file)
                if (source != null) {
                    VideoSourceManager.getInstance().setSource(source)
                    withContext(Dispatchers.Main) {
                        ARouter.getInstance()
                            .build(RouteTable.Player.Player)
                            .navigation()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "playEpisode error", e)
                withContext(Dispatchers.Main) {
                    ToastCenter.showError("播放失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun findLibraryByMediaPath(mediaPath: String): MediaLibraryEntity? {
        val allConfigs = DatabaseManager.instance.getMuluConfigDao().getAllSuspend()
        val trimmedPath = mediaPath.trimEnd('/')
        for (config in allConfigs) {
            val configPath = config.path.trimEnd('/')
            if (trimmedPath.startsWith(configPath)) {
                return DatabaseManager.instance.getMediaLibraryDao().getById(config.mediaLibraryId)
            }
        }
        return null
    }
}

private class EpisodeAdapter(
    private val episodes: List<EpisodeEntity>,
    private val onPlay: (EpisodeEntity) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEpisodeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(episodes[position], onPlay)
    }

    override fun getItemCount() = episodes.size

    class ViewHolder(private val binding: ItemEpisodeBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: EpisodeEntity, onPlay: (EpisodeEntity) -> Unit) {
            binding.episodeNumberTv.text = episode.episodeNumber.toString()
            binding.episodeTitleTv.text = episode.title ?: episode.fileName
            binding.episodeOverviewTv.text = episode.overview ?: ""
            binding.root.setOnClickListener { onPlay(episode) }
        }
    }
}