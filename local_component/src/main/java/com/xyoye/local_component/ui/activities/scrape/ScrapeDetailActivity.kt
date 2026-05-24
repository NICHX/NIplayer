package com.xyoye.local_component.ui.activities.scrape

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.factory.StorageVideoSourceFactory
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.EpisodeEntity
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.local_component.BR
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ActivityScrapeDetailNewBinding
import com.xyoye.local_component.databinding.ItemEpisodeNewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route(path = RouteTable.Scrape.ScrapeDetail)
class ScrapeDetailActivity : BaseActivity<ScrapeDetailViewModel, ActivityScrapeDetailNewBinding>() {

    companion object {
        private const val TAG = "ScrapeDetailActivity"
    }

    @Autowired
    @JvmField
    var mediaId: Int = 0

    private val tmdbRepository = TmdbRepository()
    private var mediaData: com.xyoye.data_component.entity.ScrapeMediaEntity? = null
    private var overviewExpanded = false
    private lateinit var episodeAdapter: EpisodeAdapter

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        ScrapeDetailViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_scrape_detail_new

    override fun initView() {
        ARouter.getInstance().inject(this)
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        loadMediaDetail()
        viewModel.loadEpisodes(mediaId)
    }

    private fun setupRecyclerView() {
        episodeAdapter = EpisodeAdapter(emptyList()) { playEpisode(it) }
        dataBinding.detailEpisodeRv.apply {
            layoutManager = GridLayoutManager(this@ScrapeDetailActivity, 2, GridLayoutManager.VERTICAL, false)
            adapter = episodeAdapter
        }
    }

    private fun setupClickListeners() {
        dataBinding.detailBackContainer.setOnClickListener { finish() }
        dataBinding.detailPlayBt.setOnClickListener { playVideo() }
        dataBinding.detailOverviewExpandTv.setOnClickListener { toggleOverview() }
    }

    private fun setupObservers() {
        viewModel.episodes.observe(this) { episodeList ->
            episodeAdapter.updateData(episodeList)
            updatePlayButtonText(episodeList.firstOrNull())
        }

        viewModel.seasons.observe(this) { seasonList ->
            if (seasonList.size > 1) {
                dataBinding.detailSeasonScroll.visibility = android.view.View.VISIBLE
                setupSeasonSelector(seasonList)
            } else {
                dataBinding.detailSeasonScroll.visibility = android.view.View.GONE
            }
        }

        viewModel.currentSeason.observe(this) { seasonNum ->
            updateSeasonSelection(seasonNum)
        }
    }

    private fun setupSeasonSelector(seasonList: List<Int>) {
        dataBinding.detailSeasonContainer.removeAllViews()
        val density = resources.displayMetrics.density

        seasonList.forEach { seasonNum ->
            val tab = TextView(this).apply {
                text = "第${seasonNum}季"
                gravity = android.view.Gravity.CENTER
                textSize = 16f
                setPadding(
                    (20 * density).toInt(),
                    (8 * density).toInt(),
                    (20 * density).toInt(),
                    (8 * density).toInt()
                )
                tag = seasonNum
                setOnClickListener {
                    viewModel.switchSeason(mediaId, seasonNum)
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = (12 * density).toInt()
            tab.layoutParams = lp
            dataBinding.detailSeasonContainer.addView(tab)
        }

        updateSeasonSelection(viewModel.currentSeason.value ?: seasonList.first())
    }

    private fun updateSeasonSelection(seasonNum: Int) {
        val container = dataBinding.detailSeasonContainer
        val density = resources.displayMetrics.density
        val themeColor = ContextCompat.getColor(this, R.color.theme)
        val transparent = android.graphics.Color.TRANSPARENT

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            val isSelected = child.tag == seasonNum

            if (isSelected) {
                child.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = (24 * density).toFloat()
                    setColor(themeColor)
                }
                child.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                child.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = (24 * density).toFloat()
                    setColor(transparent)
                }
                child.setTextColor(ContextCompat.getColor(this, R.color.gray_40))
            }
        }
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
                    .asBitmap()
                    .load(posterUrl)
                    .placeholder(R.drawable.ic_video_cover)
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun onResourceReady(
                            resource: Bitmap?,
                            model: Any?,
                            target: Target<Bitmap>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            resource?.let {
                                Palette.from(it).generate { palette ->
                                    palette?.let { applyColorsFromPalette(palette) }
                                }
                            }
                            return false
                        }
                    })
                    .into(dataBinding.detailBackdropIv)

                dataBinding.detailNameTv.text = media.name
                dataBinding.detailOverviewTv.text = media.overview ?: "暂无简介"

                if (media.voteAverage > 0) {
                    dataBinding.detailRatingTv.text = String.format("%.1f", media.voteAverage)
                    dataBinding.detailRatingTv.visibility = android.view.View.VISIBLE
                    dataBinding.detailRatingIconIv.visibility = android.view.View.VISIBLE
                } else {
                    dataBinding.detailRatingTv.visibility = android.view.View.GONE
                    dataBinding.detailRatingIconIv.visibility = android.view.View.GONE
                }

                if (media.releaseTime != null) {
                    dataBinding.detailReleaseTv.text = media.releaseTime
                    dataBinding.detailReleaseTv.visibility = android.view.View.VISIBLE
                } else {
                    dataBinding.detailReleaseTv.visibility = android.view.View.GONE
                }

                dataBinding.detailFilePathTv.text = media.path
            }
        }
    }

    private fun applyColorsFromPalette(palette: Palette) {
        // 首先尝试获取 Vibrant 色（鲜艳色），其次是 DarkVibrant，再次是 Muted 等
        val primaryColor = palette.getVibrantColor(
            palette.getDarkVibrantColor(
                palette.getMutedColor(
                    ContextCompat.getColor(this, R.color.black)
                )
            )
        )
        val darkColor = palette.getDarkMutedColor(
            palette.getDarkVibrantColor(
                ContextCompat.getColor(this, R.color.black)
            )
        )

        // 创建渐变背景，从深色到黑色
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                darkColor,
                Color.BLACK
            )
        )
        gradientDrawable.cornerRadius = 0f
        
        // 应用背景颜色
        dataBinding.root.background = gradientDrawable

        // 应用颜色到 CollapsingToolbar（AppBarLayout）
        dataBinding.detailAppbar.setBackgroundColor(darkColor)
        
        // 高亮播放按钮
        dataBinding.detailPlayBt.setBackgroundColor(primaryColor)
        dataBinding.detailPlayBt.setTextColor(Color.WHITE)
    }

    private fun updatePlayButtonText(episode: EpisodeEntity?) {
        val buttonText = if (episode != null) {
            "播放 第${episode.episodeNumber}集"
        } else {
            "播放"
        }
        dataBinding.detailPlayBt.text = buttonText
    }

    private fun toggleOverview() {
        overviewExpanded = !overviewExpanded
        dataBinding.detailOverviewTv.maxLines = if (overviewExpanded) Int.MAX_VALUE else 4
        dataBinding.detailOverviewExpandTv.text = if (overviewExpanded) "收起" else "全部 >"
    }

    private fun playVideo() {
        mediaData?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val episodes = DatabaseManager.instance.getEpisodeDao().getEpisodesByMediaId(mediaId)
                    val firstEpisode = episodes.firstOrNull()

                    if (firstEpisode != null) {
                        playEpisode(firstEpisode)
                    } else {
                        withContext(Dispatchers.Main) {
                            ToastCenter.showError("未找到可播放的视频")
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
                val file = resolveFile(storage, episode.filePath, episode)
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        ToastCenter.showError("找不到播放文件")
                    }
                    return@launch
                }

                resolveAndPlay(file, library)
            } catch (e: Exception) {
                Log.e(TAG, "playEpisode error", e)
                withContext(Dispatchers.Main) {
                    ToastCenter.showError("播放失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun resolveFile(
        storage: Storage,
        path: String,
        episode: EpisodeEntity? = null
    ): StorageFile? {
        var file = try { storage.pathFile(path, false) } catch (_: Exception) { null }
        if (file != null) return file

        for (ext in listOf("mkv", "mp4", "avi", "wmv", "mov", "ts", "flv", "webm")) {
            file = try { storage.pathFile("$path.$ext", false) } catch (_: Exception) { null }
            if (file != null) return file
        }

        if (episode != null) {
            val dirFile = try { storage.pathFile(path, true) } catch (_: Exception) { null }
            if (dirFile != null) {
                val children = try { storage.openDirectory(dirFile, false) } catch (_: Exception) { emptyList() }
                val seasonDirs = children.filter { it.isDirectory() }
                val directVideos = children.filter { it.isVideoFile() }
                val seasonStr = "s${episode.seasonNumber.toString().padStart(2, '0')}e${episode.episodeNumber.toString().padStart(2, '0')}"

                val matchInRoot = directVideos.find { it.fileName().lowercase().contains(seasonStr) }
                if (matchInRoot != null) return matchInRoot
                if (directVideos.isNotEmpty()) return directVideos.first()

                for (seasonDir in seasonDirs) {
                    val seasonFiles = try { storage.openDirectory(seasonDir, false).filter { it.isVideoFile() } } catch (_: Exception) { emptyList() }
                    val matchInSeason = seasonFiles.find { it.fileName().lowercase().contains(seasonStr) }
                    if (matchInSeason != null) return matchInSeason
                    if (seasonFiles.isNotEmpty()) return seasonFiles.first()
                }
            }
        }

        return null
    }

    private suspend fun resolveAndPlay(file: StorageFile, library: MediaLibraryEntity) {
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
    private var episodes: List<EpisodeEntity>,
    private val onPlay: (EpisodeEntity) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

    fun updateData(newEpisodes: List<EpisodeEntity>) {
        episodes = newEpisodes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEpisodeNewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(episodes[position], onPlay)
    }

    override fun getItemCount() = episodes.size

    class ViewHolder(private val binding: ItemEpisodeNewBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(episode: EpisodeEntity, onPlay: (EpisodeEntity) -> Unit) {
            binding.episodeNumberTv.text = "${episode.episodeNumber}. ${episode.title ?: episode.fileName}"
            binding.root.setOnClickListener { onPlay(episode) }
        }
    }
}
