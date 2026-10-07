package com.nichx.niplayer.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nichx.niplayer.datastore.AudioPlayerStyle
import com.nichx.niplayer.datastore.PlayerSettings
import com.nichx.niplayer.designsystem.motion.LocalNiReduceMotion
import com.nichx.niplayer.designsystem.theme.MotionTokens
import com.nichx.niplayer.designsystem.theme.NiExtraColors
import com.nichx.niplayer.feature.player.theme.ChromeTint
import com.nichx.niplayer.feature.player.theme.PlayerSkeleton
import com.nichx.niplayer.feature.player.theme.themeOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/** 横屏沉浸模式：无操作自动隐藏控件的延时（ms）。 */
internal const val AUTO_HIDE_DELAY_MS = 3000L

/** Apple 歌词页：无操作自动收起底部控件的延时（ms）。 */
private const val APPLE_LYRICS_CONTROLS_IDLE_MS = 4000L

/** Apple 封面页：歌名行与封面底部之间的间距。 */
private val APPLE_CREDITS_TOP_GAP = 16.dp

/** Apple 封面页：封面在「屏宽」之外再多占的高度（状态栏 + 上下留白）。 */
private val APPLE_COVER_EXTRA = 48.dp

/** Apple 封面页：封面之下必须留给歌名行、进度与传输键的高度。 */
private val APPLE_COVER_RESERVED = 300.dp

/**
 * Apple 封面页：封面高度占屏高的上限。
 *
 * 封面主体是屏宽见方的全宽贴顶图，短屏上再高就会把下面的控件挤没。
 */
private const val APPLE_COVER_HEIGHT_FRACTION = 0.60f

/**
 * Apple 封面页：封面块与底部控件块之间，留给空白的权重。
 *
 * 参考里下方的控件块很高（歌词条 + 进度 + 传输 + 音量 + 操作行），多余空间被
 * 它自己吃掉；我们只有进度和传输键，把空余全丢给封面与控件之间就会留下一个大洞。
 * 所以按比例分给「中间」和「底部」两段，两边都留一点呼吸，谁也不像被掏空。
 */
private const val APPLE_TOP_SLACK_WEIGHT = 0.45f
private const val APPLE_BOTTOM_SLACK_WEIGHT = 0.55f

/** 封面 → 歌词：封面整组上移的距离（dp），与歌词一起构成上下错位的推入感。 */
private val APPLE_ARTWORK_EXIT_DISTANCE = 300.dp

/** 封面 → 歌词：歌词自下方进入的起始距离（dp）。 */
private val APPLE_LYRICS_ENTER_DISTANCE = 400.dp

/**
 * 有封面时，动态流光背景作为**叠加层**的不透明度。
 *
 * 无封面时流光整屏兜底（1f）；有封面时压在封面色彩网格之上、封面横幅之下，
 * 取一个偏高的值让「流动」看得见，又不至于把封面自身的底色冲淡。
 */
private const val APPLE_FLOW_OVERLAY_ALPHA = 0.85f

/** 歌词层在「切出歌词页」后保留的时长（ms）：覆盖退场动画后再卸载，避免退场途中断帧。 */
private const val APPLE_LYRICS_LAYER_EXIT_MS = 420L

/** 甩动切歌：松手瞬间的横向速度超过该值（px/s）即视为「甩动」，即使位移没到阈值也切歌。 */
private const val FLICK_VELOCITY_PX_PER_S = 900f

/** 拖动超出半个屏宽后的跟随比例：越小越「重」，避免在边界硬邦邦地卡住。 */
private const val DRAG_OVERSHOOT_DAMPING = 0.3f

/**
 * 简约封面主题：封面边长占可用宽度的比例。
 *
 * 参考图里封面约占屏宽 2/3 —— 比黑胶/Apple 的封面小一档，换来的留白给到下方
 * 的歌名、艺术家与当前歌词三行。
 */
private const val GLASS_COVER_FRACTION = 0.67f

/** 简约封面主题：封面边长的下限，防止极短屏上封面被压到看不见。 */
private val GlassCoverMinSide = 140.dp

/**
 * 简约封面：封面组在「顶栏 → 控件」之间的落位比例（0 = 贴顶，0.5 = 正中）。
 *
 * 不做「贴顶 + 余量全留下面」：参考图那里本来还有一行快捷操作把余量吃掉，
 * 本主题不加那一行，于是歌词行与进度条之间会空出一大块，读起来像少了一块内容。
 * 0.45 把余量分成「上 45% / 下 55%」—— 既保住封面在上方的观感，又不留空洞。
 */
private const val GLASS_COVER_TOP_SLACK_BIAS = 0.45f

/**
 * 简约封面：底部控件下方的留白。
 *
 * 本主题没有参考图那行底部快捷操作，余量全留在「歌词行 → 进度条」之间会空出一大块。
 * 把这段留白加大一档，整块控件随之往上托，空白被压掉，播放键也避开了手势条。
 */
private val GlassControlsBottomSpace = 64.dp

/**
 * 简约封面主题：封面「浮动」的两个周期（ms）。
 *
 * 上下浮动与轻微摆动刻意用**两个不同周期**：两个正弦不会周期性同相，
 * 观感上就不会「每几秒重复一次」，而是始终不太一样。
 */
private const val GLASS_FLOAT_PERIOD_MS = 5_600
private const val GLASS_FLOAT_SWAY_PERIOD_MS = 7_900

/**
 * 浮动幅度：上下 ±2.5dp、摆动 ±0.3°、缩放 ±0.35%。
 *
 * 都是「悬着呼吸」的量级，比第一版再收一半 —— 幅度一大就不再是"浮"，而是"晃"，
 * 还会和跟手换片的手势抢戏。
 */
private val GlassFloatAmplitude = 2.5.dp
private const val GLASS_FLOAT_SWAY_DEGREES = 0.3f
private const val GLASS_FLOAT_SCALE = 0.0035f

/** 2π：把 0..1 的相位换算成正弦的弧度。 */
private val TWO_PI = (2.0 * PI).toFloat()

/**
 * 简约封面：封面 ↔ 歌词切换时，「退场」一侧缩到的比例。
 *
 * 0.94 是「后退一步」而不是「缩小」——再小就变成了弹窗式缩放，与整页内容不符。
 */
private const val GLASS_PAGE_EXIT_SCALE = 0.94f

/**
 * 简约封面：封面 ↔ 歌词交叉过渡的时序（ms）。
 *
 * **出场先走一步、进场略等一拍**：交叠只剩几十毫秒，两侧都还接近透明，
 * 于是读起来是一次干净的溶解；若两边同时长同起点，中途会「两层文字各半透明叠在一起」。
 */
private const val GLASS_PAGE_EXIT_MS = 170
private const val GLASS_PAGE_ENTER_DELAY_MS = 70
private const val GLASS_PAGE_ENTER_MS = 280

/**
 * 简约封面：**切歌**时封面的替换动效（ms 与缩放）。
 *
 * 与「封面 ↔ 歌词」同一套语言（后退淡出 / 推近淡入），只是更快：切歌是高频操作，
 * 等太久会显得拖。缩放取 0.96 —— 让新封面「推近」一点，读起来是换了一张，
 * 而不是两张在交叉。
 */
private const val GLASS_TRACK_EXIT_MS = 150
private const val GLASS_TRACK_ENTER_DELAY_MS = 50
private const val GLASS_TRACK_ENTER_MS = 230
private const val GLASS_TRACK_SWAP_SCALE = 0.96f

@Composable
internal fun PortraitLayout(
    state: AudioPlayerUiState,
    actions: AudioPlayerActions,
    style: AudioPlayerStyle = AudioPlayerStyle.APPLE_MUSIC,
) {
    // 把两个 holder 摊平回局部名字：函数体是既有实现，逐处改名会产出几百行难以复核的 diff，
    // 而这段代码没有单测、只能靠真机验证。摊平后函数体**一字未动**，回归风险为零。
    // 将来若要改名，是一次独立的机械改动。
    val (
        hasActiveContent, playbackError, showLyrics, lrcLines, positionMs, durationMs,
        title, artist, isPlaying, hasPrev, hasNext, coverPath, playlist, currentIndex,
        neighborPrevCover, neighborNextCover, neighborPrevIndex, neighborNextIndex,
        playMode, modeIcon, modeLabel, speedOptions, currentSpeedIndex, sleepTimerText,
        showDownload, showExternalActions,
    ) = state
    val (
        onRetry, onSeek, onTogglePlay, onPrevious, onNext, onToggleLyrics, onCyclePlayMode,
        onShowPlaylist, onBack, onDownload, onEqualizer, onSpeedSelect, onSleepTimer,
        onOpenWith, onShare, onStyleSelect,
    ) = actions

    val onSurface = MaterialTheme.colorScheme.onSurface
    val theme = themeOf(style)
    // 「这块 UI 属于哪个主题」一律问骨架，不问 style —— 骨架是 3 值枚举、在 themeOf 处穷尽映射，
    // 新增主题时编译器会在那里点名；原先的 `style == APPLE_MUSIC` 会让新主题静默走错分支。
    val isAppleSkeleton = theme.skeleton == PlayerSkeleton.APPLE_BANNER
    val isCardSkeleton = theme.skeleton == PlayerSkeleton.CARD
    val titleColor = if (isAppleSkeleton) Color.White else onSurface
    val secondaryColor =
        if (isAppleSkeleton) Color.White.copy(alpha = 0.6f) else onSurface.copy(alpha = 0.6f)

    // Apple 歌词页：底部控件（进度 + 传输键）一段时间无操作后自动收起，整页交给歌词。
    // controlsActivityGeneration 让「滚动等交互」也能把计时器重新计时（否则控件已可见时不会再续期）。
    var lyricsControlsVisible by remember(showLyrics) { mutableStateOf(true) }
    var controlsActivityGeneration by remember(showLyrics) { mutableIntStateOf(0) }
    LaunchedEffect(isAppleSkeleton, showLyrics, lyricsControlsVisible, controlsActivityGeneration) {
        if (isAppleSkeleton && showLyrics && lyricsControlsVisible) {
            delay(APPLE_LYRICS_CONTROLS_IDLE_MS)
            lyricsControlsVisible = false
        }
    }

    // 歌词层较重（整条 LyricsView + 逐行模糊图层 + 文本测量）：
    // 封面页（例如「最近播放」直接进播放器）**不为一个不可见的歌词层付出组合/layout 代价**，
    // 只在歌词页、以及刚切出歌词页的退场动画期间保留。
    var keepLyricsLayer by remember { mutableStateOf(showLyrics) }
    LaunchedEffect(showLyrics) {
        if (showLyrics) {
            keepLyricsLayer = true
        } else {
            delay(APPLE_LYRICS_LAYER_EXIT_MS)
            keepLyricsLayer = false
        }
    }

    // Apple：封面文件路径（Apple 风格的封面是本地抽帧文件，形如 String 路径）
    val coverFile = coverPath as? String
    // Apple 封面页：封面自身的颜色网格 + 全宽贴顶的封面（参照 BitChord）。
    // 网格由封面从小图解码后算一次并缓存，不涉及整屏 RenderEffect。
    val appleMesh = if (isAppleSkeleton) rememberAppleArtworkMesh(coverFile) else null
    // 动态流光背景用的 9 色取色（无封面时是兜底配色）
    val applePalette = if (isAppleSkeleton) {
        rememberAppleArtworkPalette(coverFile)
    } else {
        AppleArtworkPalette.Fallback
    }

    // 简约封面：强调色取自当前封面（每首歌一套配色）。取色只算一次并缓存，切歌换封面才重算；
    // 未就绪 / 无封面 / 灰阶封面时回落到主题色，因此进度条与播放键不会出现「没有颜色」的空档。
    val coverAccent = if (isCardSkeleton) {
        rememberCoverAccent(coverFile, NiExtraColors.current.isDark)
    } else {
        null
    }
    val accent = coverAccent?.color ?: MaterialTheme.colorScheme.primary
    val onAccent = coverAccent?.onColor ?: MaterialTheme.colorScheme.onPrimary

    // 「逐字歌词」开关（设置 → 音频播放器设置 → 外观）。进入播放器时读一次即可 ——
    // 设置页是另一个页面，改完回来会重新组合。
    val perCharLyrics = remember { PlayerSettings.lyricPerCharEnabled }

    // 简约封面：让封面「浮」起来。两个不同周期的正弦叠加，避免周期性重复感。
    // **暂停即停**：相位循环退出、不再请求帧；同时幅度系数平滑落到 0，
    // 于是封面从当前弧位缓缓回到正中，而不是「啪」一下跳回去或僵在半空。
    // 「减少动态效果」开启时不挂动画；其它两套主题也不挂。
    val glassFloatEnabled = isCardSkeleton && !LocalNiReduceMotion.current
    val glassFloatBob = rememberLoopPhase(
        enabled = glassFloatEnabled && isPlaying,
        periodMs = GLASS_FLOAT_PERIOD_MS,
        label = "glassFloatBob",
    )
    val glassFloatSway = rememberLoopPhase(
        enabled = glassFloatEnabled && isPlaying,
        periodMs = GLASS_FLOAT_SWAY_PERIOD_MS,
        label = "glassFloatSway",
    )
    val glassFloatActivity by animateFloatAsState(
        targetValue = if (glassFloatEnabled && isPlaying) 1f else 0f,
        animationSpec = tween(MotionTokens.SURFACE, easing = MotionTokens.easeStandard),
        label = "glassFloatActivity",
    )

    // 换片时序：旧盘抬针 → 平移换片 → 新盘落针开播。
    // 平移时长即 AnimatedContent 的 PAGE_ENTER；抬针覆盖整段平移，平移走完才落针。
    var needleLifted by remember { mutableStateOf(false) }
    var lastTrackIndex by remember { mutableIntStateOf(currentIndex) }
    LaunchedEffect(currentIndex) {
        // LaunchedEffect 首次组合也会执行一次：下标没变就直接返回，
        // 否则「进入播放页」时会白抬一次唱针（看起来像多了一次切歌）。
        if (currentIndex == lastTrackIndex) return@LaunchedEffect
        val previous = lastTrackIndex
        lastTrackIndex = currentIndex
        // 载入首个曲目（此前无下标）不抬针，避免唱片刚出现就抬一下
        if (currentIndex < 0 || previous < 0) return@LaunchedEffect
        needleLifted = true
        delay(MotionTokens.PAGE_ENTER.toLong())
        needleLifted = false
    }

    // 能否左右滑动切歌：由播放器预定的上/下一首下标决定（随机模式、首尾回绕同样正确）
    val canSwipePrevious = neighborPrevIndex >= 0
    val canSwipeNext = neighborNextIndex >= 0

    // 「下标 → 封面」的本地记录：给换页动画里**滑出中的旧唱片**取它自己的封面。
    // 必须本地记：coverPath 与 currentIndex 分属两条流，换片的头一两帧里
    // 旧下标若退回到实时 coverPath，拿到的就是新封面——旧唱片会闪一下。
    val pageCoverCache = remember { mutableMapOf<Int, Any?>() }
    SideEffect {
        if (coverPath != null) pageCoverCache[currentIndex] = coverPath
    }

    /**
     * 某一页（AnimatedContent 冻结在那一页的下标）该显示的封面。
     *
     * 取用顺序：当前页用实时封面 → 邻居页用播放器预取的封面 →
     * 其余（主要是滑出中的旧页）用 [pageCoverCache] 里该下标最近一次见到的封面。
     */
    fun coverForPage(pageIndex: Int): Any? = when {
        pageIndex == currentIndex -> coverPath
        pageIndex >= 0 && pageIndex == neighborPrevIndex && neighborPrevCover != null -> neighborPrevCover
        pageIndex >= 0 && pageIndex == neighborNextIndex && neighborNextCover != null -> neighborNextCover
        else -> pageCoverCache[pageIndex] ?: coverPath
    }

    // 「下标 → 文件名」的本地记录：与 [pageCoverCache] 同理——无封面时贴纸要写上**这一页自己**
    // 的文件名，而 title 与 currentIndex 分属两条流，换片头一两帧会拿到新曲目的标题。
    val pageTitleCache = remember { mutableMapOf<Int, String>() }
    SideEffect {
        if (title.isNotEmpty()) pageTitleCache[currentIndex] = title
    }

    /** 某一页（无封面贴纸）该写的文件名；邻居若从未播放过则留白，绝不写错别人的名字。 */
    fun titleForPage(pageIndex: Int): String =
        if (pageIndex == currentIndex) title else pageTitleCache[pageIndex] ?: ""

    // 换片跟手：唱片随手指横向平移的实时位移（px）；[discSettleJob] 持松手后的归位动画。
    var discDragOffset by remember { mutableFloatStateOf(0f) }
    var discSettleJob by remember { mutableStateOf<Job?>(null) }
    val dragScope = rememberCoroutineScope()

    // 手指是否按在唱片上。
    var discDragging by remember { mutableStateOf(false) }
    // 已提交换片、但播放器还没真正切下标：此期间位移**冻结**在松手处，不弹回。
    var commitPending by remember { mutableStateOf(false) }
    // 下标已变化、位置交接给换片动画：此时邻居须立刻退场，否则会出现两张新唱片。
    var neighborHandoff by remember { mutableStateOf(false) }

    // 本次切歌的「意图方向」：手势或上/下一首按钮发起时记 ±1，其余为 0。
    var pendingSwipeDirection by remember { mutableIntStateOf(0) }
    // 冻结到本次下标变化：换页动画用它决定方向，而不是用下标差——
    // 随机模式/列表回绕时下标增量与视觉方向不一致，只看下标会让动画反向（就是「乱」）。
    val slideDirection = remember(currentIndex) { pendingSwipeDirection }
    LaunchedEffect(currentIndex) {
        // 意图方向只服务「本次」下标变化，变化后立刻清零，不影响后续（自动续播、点歌单等）
        pendingSwipeDirection = 0
    }

    // 邻居唱片的显示条件：正在拖动；或已松手但位移尚未收回、且还没交接给换片动画。
    // 用 derivedStateOf 只在布尔翻转时通知重组，拖动过程不逐帧重建整页。
    val showNeighbors by remember {
        derivedStateOf { discDragging || (discDragOffset != 0f && !neighborHandoff) }
    }

    // 唱片一旦被拖走（或正在归位），唱针就抬起来——否则唱针会「指着空处」，看着像错位。
    // 用 derivedStateOf：只在「抬起 / 落下」翻转时才通知重组，拖动过程中不逐帧重建整页。
    val needleUp by remember {
        derivedStateOf { needleLifted || discDragOffset != 0f }
    }

    // 换片提交后：等播放器真正切了下标，再把位移与换片平移**同步**收回。
    // 二者同时长、同曲线，读起来是一段连续运动；若在松手时就归位，会变成
    // 「先弹回中心 → 再滑出换片」的两段运动——那就是之前的松手动画 bug。
    LaunchedEffect(currentIndex) {
        if (!commitPending) return@LaunchedEffect
        commitPending = false
        neighborHandoff = true
        discSettleJob?.cancel()
        discSettleJob = dragScope.launch {
            animate(
                discDragOffset,
                0f,
                animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
            ) { v, _ -> discDragOffset = v }
            neighborHandoff = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // 封面从屏幕 y=0 起画（含状态栏背后）。参考里的封面块是「方形封面 + 状态栏 +
    // 上下留白」，所以这里也取屏宽再加回状态栏与留白，屏高不够时再收敛。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val coverHeight = minOf(
        maxWidth + statusBarTop + APPLE_COVER_EXTRA,
        maxHeight * APPLE_COVER_HEIGHT_FRACTION,
        // 给歌名行和底部控件留出底线，短屏上封面不能把下面的内容顶出去
        (maxHeight - APPLE_COVER_RESERVED).coerceAtLeast(0.dp),
    )
    // Apple：有可显示的封面/歌词内容（无内容或播放失败时另走提示分支）
    val appleActive = isAppleSkeleton && hasActiveContent && playbackError == null

    // 布局可用宽度：@LayoutScopeMarker 会挡住深层 DSL（Column/Row 等）里的 BoxWithConstraintsScope，
    // 所以在作用域内先存成普通 val，供下方歌名行「给序号留位」的宽度上限复用。
    val layoutWidth = maxWidth

    // 封面页 ↔ 歌词页 切换动效优化：
    // - 封面整组上移 −300dp 并淡出；
    // - 歌词自下方 +400dp 上移并淡入；
    // - 同一时间线，走 bounce≈0.3（ζ=0.7）的弹性，切换带一点回弹。
    val pageTransition = updateTransition(targetState = showLyrics, label = "applePage")
    val artworkAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleArtworkAlpha",
    ) { if (it) 0f else 1f }
    val artworkOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleArtworkOffset",
    ) { if (it) -APPLE_ARTWORK_EXIT_DISTANCE else 0.dp }
    val lyricsAlpha by pageTransition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleLyricsAlpha",
    ) { if (it) 1f else 0f }
    val lyricsOffset by pageTransition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 300f) },
        label = "appleLyricsOffset",
    ) { if (it) 0.dp else APPLE_LYRICS_ENTER_DISTANCE }

    // 封面组是否还「可交互」：用 derivedStateOf 只在跨过阈值时变一次（避免逐帧重组整个布局）。
    // 封面淡出后其按钮必须立刻失效——否则那层透明按钮会盖在歌词上、把一大片点击吃掉。
    val artworkInteractive by remember {
        derivedStateOf { artworkAlpha > 0.01f }
    }

    if (isAppleSkeleton) {
        if (coverFile != null) {
            AppleArtworkMeshBackdrop(
                mesh = appleMesh,
                // 网格接缝随封面页让位：进歌词页时收回到 0，整屏变成连续渐变
                seam = if (showLyrics) 0.dp else coverHeight,
            )
        }
        // 动态流光背景：无封面时作为整屏兜底；有封面时半透明叠加在色彩网格之上
        // （封面横幅随后盖在其上，所以封面本身仍保持清晰）。
        AppleFlowingLightBackdrop(
            palette = applePalette,
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (coverFile == null) 1f else APPLE_FLOW_OVERLAY_ALPHA },
        )
        if (appleActive) {
            AppleMusicCoverBanner(
                coverData = coverPath,
                height = coverHeight,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        alpha = artworkAlpha
                        translationY = artworkOffset.toPx()
                    },
            )
        }
    }

    // 歌词页：铺满整屏的独立层（自下方上滑 + 淡入）。
    // 放在 Column **之前**，这样 Column 里的底部控件叠在它之上、可正常点击；
    // 未进入歌词页时它已被推到屏外且 alpha=0，不会拦截触摸。
    if (appleActive && keepLyricsLayer) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = lyricsAlpha
                    translationY = lyricsOffset.toPx()
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { lyricsControlsVisible = !lyricsControlsVisible },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 顶部信息条：缩略图 + 歌名/艺术家 + 「···」
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppleMusicThumbnail(
                            coverData = coverPath,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = onToggleLyrics),
                        )
                        AppleTrackTitle(
                            title = title,
                            artist = artist,
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                        TopBarActions(
                            onDownload = onDownload,
                            onEqualizer = onEqualizer,
                            speedOptions = speedOptions,
                            currentSpeedIndex = currentSpeedIndex,
                            onSpeedSelect = onSpeedSelect,
                            showDownload = showDownload,
                            sleepTimerText = sleepTimerText,
                            onSleepTimer = onSleepTimer,
                            showExternalActions = showExternalActions,
                            onOpenWith = onOpenWith,
                            onShare = onShare,
                            chrome = theme.chrome,
                            audioStyle = style,
                            onStyleSelect = onStyleSelect,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        LyricsView(
                            lrcLines = lrcLines,
                            currentPositionMs = positionMs,
                            onSeek = onSeek,
                            maxVisibleLines = Int.MAX_VALUE,
                            modifier = Modifier.fillMaxSize(),
                            theme = theme.lyrics,
                            isPlaying = isPlaying,
                            perChar = perCharLyrics,
                            // 任何滚动交互都把控件呼出并把自动收起计时器重新计时
                            onUserScroll = {
                                lyricsControlsVisible = true
                                controlsActivityGeneration += 1
                            },
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        if (!isAppleSkeleton) {
        if (isCardSkeleton) {
            // 简约封面：顶栏居中显示「正在播放 + 当前歌名」，「更多」用三横线
            GlassTopBar(
                title = title,
                onBack = onBack,
                onDownload = onDownload,
                onEqualizer = onEqualizer,
                speedOptions = speedOptions,
                currentSpeedIndex = currentSpeedIndex,
                onSpeedSelect = onSpeedSelect,
                showDownload = showDownload,
                sleepTimerText = sleepTimerText,
                onSleepTimer = onSleepTimer,
                showExternalActions = showExternalActions,
                onOpenWith = onOpenWith,
                onShare = onShare,
                audioStyle = style,
                onStyleSelect = onStyleSelect,
            )
        } else {
        TopBar(
            // 这个共享顶栏只服务黑胶 —— Apple 与简约封面各有自己的顶栏（见上面的分支）
            title = title,
            onBack = onBack,
            onDownload = onDownload,
            onEqualizer = onEqualizer,
            speedOptions = speedOptions,
            currentSpeedIndex = currentSpeedIndex,
            onSpeedSelect = onSpeedSelect,
            showDownload = showDownload,
            sleepTimerText = sleepTimerText,
            onSleepTimer = onSleepTimer,
            showExternalActions = showExternalActions,
            onOpenWith = onOpenWith,
            onShare = onShare,
            chrome = theme.chrome,
            audioStyle = style,
            onStyleSelect = onStyleSelect,
        )
        }
        }

        if (isAppleSkeleton && appleActive) {
            // ---- Apple 封面页：封面点击区 + 歌名行（整组随切换上移 + 淡出）----
            // 封面本身画在背景层上（全宽贴顶），这里只放一个与封面等高的透明点击区，
            // 点它进歌词页；歌名行紧贴在封面下方。
            //
            // 顺序很重要：这一块是**固定高度**、不带权重，权重只给下面两段 Spacer，
            // Column 会先把固定高度的孩子量完再分剩下的——否则底部的进度与传输键
            // 会被挤成 0 高度（就是「控件没了」）。
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = artworkAlpha
                    translationY = artworkOffset.toPx()
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((coverHeight - statusBarTop).coerceAtLeast(0.dp))
                        // 封面淡出后撤掉点击：避免这层透明区域挡住歌词交互
                        .then(
                            if (artworkInteractive) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onToggleLyrics() }
                            } else {
                                Modifier
                            },
                        ),
                )
                Spacer(Modifier.height(APPLE_CREDITS_TOP_GAP))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = theme.infoArea.horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppleTrackTitle(
                        title = title,
                        artist = artist,
                        modifier = Modifier.weight(1f),
                    )
                    // 同理：淡出后不再渲染动作按钮（它们是不可见的点击目标，会吃掉歌词页的点击）
                    if (artworkInteractive) {
                        TopBarActions(
                            onDownload = onDownload,
                            onEqualizer = onEqualizer,
                            speedOptions = speedOptions,
                            currentSpeedIndex = currentSpeedIndex,
                            onSpeedSelect = onSpeedSelect,
                            showDownload = showDownload,
                            sleepTimerText = sleepTimerText,
                            onSleepTimer = onSleepTimer,
                            showExternalActions = showExternalActions,
                            onOpenWith = onOpenWith,
                            onShare = onShare,
                            chrome = theme.chrome,
                            audioStyle = style,
                            onStyleSelect = onStyleSelect,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(APPLE_TOP_SLACK_WEIGHT))
        } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = if (isAppleSkeleton) Alignment.TopCenter else Alignment.Center,
        ) {
            if (!hasActiveContent) {
                Text(
                    text = stringResource(R.string.player_no_source),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryColor,
                )
            } else if (playbackError != null) {
                PlaybackErrorState(
                    errorMessage = playbackError,
                    onRetry = onRetry,
                )
            } else {
                // 唱片 ↔ 歌词 的切换：淡入淡出 + 轻微纵向位移，避免硬切；
                // 出向略快于入向（MotionTokens 的退场≈出场×0.65 口径），读起来更跟手。
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        if (isCardSkeleton) {
                            // 简约封面：**交叉溶解**，而不是黑胶那种「整幅竖向推挤」。
                            //
                            // 黑胶的语言是「翻页」：封面整块滑走、歌词整块滑上来，首尾相接
                            // 像一条胶片（那张封面本身就是一块会转的唱片，推挤是对的）。
                            // 简约封面只有一张静态封面，用「同一页内容换了一面」更贴：
                            // 封面稍稍后退并淡出、歌词自下方轻轻浮起并淡入。
                            // 位移只取 1/18 屏高 —— 位移一大就又会读成「翻页」。
                            // 位移方向与内容在页面上的位置一致：进歌词时歌词自**下方**浮起，
                            // 回封面时封面自**上方**落回。
                            val rising = targetState
                            val enter = fadeIn(
                                tween(
                                    durationMillis = GLASS_PAGE_ENTER_MS,
                                    delayMillis = GLASS_PAGE_ENTER_DELAY_MS,
                                    easing = MotionTokens.easeEnter,
                                ),
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = GLASS_PAGE_ENTER_MS,
                                    delayMillis = GLASS_PAGE_ENTER_DELAY_MS,
                                    easing = MotionTokens.easeEnter,
                                ),
                                initialOffsetY = { if (rising) it / 18 else -it / 18 },
                            ) + scaleIn(
                                animationSpec = tween(
                                    durationMillis = GLASS_PAGE_ENTER_MS,
                                    delayMillis = GLASS_PAGE_ENTER_DELAY_MS,
                                    easing = MotionTokens.easeEnter,
                                ),
                                initialScale = GLASS_PAGE_EXIT_SCALE,
                            )
                            val exit = fadeOut(
                                tween(GLASS_PAGE_EXIT_MS, easing = MotionTokens.easeExit),
                            ) + scaleOut(
                                animationSpec = tween(
                                    GLASS_PAGE_EXIT_MS,
                                    easing = MotionTokens.easeExit,
                                ),
                                targetScale = GLASS_PAGE_EXIT_SCALE,
                            )
                            enter togetherWith exit
                        } else {
                        // 与底部「回到唱片」的 ▼ 语义对齐：进入歌词整组上移、回到唱片整组下移。
                        //
                        // 进/出用**同一时长同一曲线**、位移取**整幅**，两块内容因此始终首尾相接：
                        // 像一张竖版胶片被推上/推下，全程既不重叠、也不留缝。
                        // 这里刻意**不**套窗口转场的「退场 ≈ 出场 × 0.65」，也不做淡入淡出——
                        // 一旦进出不同步、或半透明叠加，读起来就是「重叠」。
                        val up = targetState
                        slideInVertically(
                            animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
                            initialOffsetY = { if (up) it else -it },
                        ) togetherWith slideOutVertically(
                            animationSpec = tween(MotionTokens.PAGE_ENTER, easing = MotionTokens.easeStandard),
                            targetOffsetY = { if (up) -it else it },
                        )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                    label = "discLyrics",
                ) { lyricsVisible ->
                if (lyricsVisible) {
                // 歌词视图：点击**空白处**切换底部控件显隐，点击歌词文本才是跳转进度。
                //
                // 这一层只负责空白处，所以用普通（冒泡阶段）的 clickable 就够了——歌词
                // 行的点击区域收在文字上，文字把事件消费掉，空白处自然落回这里。
                // 其它风格保持原样：点歌词区域回到封面。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (isAppleSkeleton) {
                                lyricsControlsVisible = !lyricsControlsVisible
                            } else {
                                onToggleLyrics()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Apple Music：歌词页顶部一条信息条（缩略图 + 歌名/艺术家 + 「···」）
                        if (isAppleSkeleton) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppleMusicThumbnail(
                                    coverData = coverPath,
                                    modifier = Modifier
                                        .size(48.dp)
                                        // 点击小封面回到封面页
                                        .clickable(onClick = onToggleLyrics),
                                )
                                AppleTrackTitle(
                                    title = title,
                                    artist = artist,
                                    modifier = Modifier.weight(1f),
                                    compact = true,
                                )
                                TopBarActions(
                                    onDownload = onDownload,
                                    onEqualizer = onEqualizer,
                                    speedOptions = speedOptions,
                                    currentSpeedIndex = currentSpeedIndex,
                                    onSpeedSelect = onSpeedSelect,
                                    showDownload = showDownload,
                                    sleepTimerText = sleepTimerText,
                                    onSleepTimer = onSleepTimer,
                                    showExternalActions = showExternalActions,
                                    onOpenWith = onOpenWith,
                                    onShare = onShare,
                                    chrome = theme.chrome,
                                    audioStyle = style,
                                    onStyleSelect = onStyleSelect,
                                )
                            }
                        }
                        // 歌词：占满剩余高度，不设行数上限（能显示多少由高度与行距决定）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            LyricsView(
                                lrcLines = lrcLines,
                                currentPositionMs = positionMs,
                                onSeek = onSeek,
                                maxVisibleLines = Int.MAX_VALUE,
                                modifier = Modifier.fillMaxSize(),
                                // 简约封面用自己的歌词口径：居中、字号几乎齐平、层次只由颜色给出
                                theme = theme.lyrics,
                                // 简约封面：歌词高亮跟着封面强调色走，与封面页同一套配色
                                accentColor = accent,
                                isPlaying = isPlaying,
                                perChar = perCharLyrics,
                            )
                        }
                        // 非 Apple 主题：底部常驻「回到唱片」触发条。
                        // 行数放开后歌词几乎铺满、可点空白被挤没，这里给一个全宽 48dp、
                        // 带图标与文案的明确入口（Apple 主题另有小封面入口，故不重复）。
                        if (!isAppleSkeleton) {
                            BackToRecordBar(
                                label = stringResource(
                                    if (isCardSkeleton) {
                                        R.string.lyrics_collapse
                                    } else {
                                        R.string.lyrics_back_to_record
                                    },
                                ),
                                onClick = onToggleLyrics,
                            )
                            // 与下方歌名行拉开 8dp：原来两行紧贴，加上序号就糊成一块
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                } else {
                // 封面/唱片视图（Apple 的封面页不走这里，见上面的 Apple 分支）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 横向跟手换片：拖动时唱片随手指平移，松手过阈值即切上一首/下一首。
                        // 唱针不参与平移（见下方 VinylNeedle）——唱机上的针是固定的。
                        //
                        // **简约封面不做这个手势**：那里只有一张静态封面，没有「把唱片推走」
                        // 的语义；横向拖动还会和「点封面进歌词页」抢手势。切歌交给传输键。
                        .then(
                            if (isCardSkeleton) {
                                Modifier
                            } else {
                                Modifier.pointerInput(currentIndex, canSwipeNext, canSwipePrevious) {
                            val threshold = 72.dp.toPx()
                            val flickMinDistance = 24.dp.toPx()
                            // 拖动超过半个屏宽后转为阻尼跟随（越拖越「重」），避免硬邦邦地卡在边界
                            val softLimit = size.width * 0.5f
                            val velocityTracker = VelocityTracker()
                            detectHorizontalDragGestures(
                                // 按下先掐掉上一次的归位动画，避免两个动画抢方向盘
                                onDragStart = {
                                    discSettleJob?.cancel()
                                    discDragging = true
                                    commitPending = false
                                    neighborHandoff = false
                                    velocityTracker.resetTracking()
                                },
                                onDragCancel = {
                                    discDragging = false
                                    commitPending = false
                                    neighborHandoff = false
                                    discSettleJob = dragScope.launch {
                                        animate(
                                            discDragOffset,
                                            0f,
                                            animationSpec = MotionTokens.springPanel,
                                        ) { v, _ -> discDragOffset = v }
                                    }
                                },
                                onDragEnd = {
                                    discDragging = false
                                    // 松手速度：快速甩动即使位移没到阈值也该切歌——这是手感的关键一环
                                    val velocityX = velocityTracker.calculateVelocity().x
                                    val flicking = abs(velocityX) > FLICK_VELOCITY_PX_PER_S
                                    val commit = when {
                                        discDragOffset <= -threshold && canSwipeNext -> 1
                                        discDragOffset >= threshold && canSwipePrevious -> -1
                                        flicking && discDragOffset <= -flickMinDistance && canSwipeNext -> 1
                                        flicking && discDragOffset >= flickMinDistance && canSwipePrevious -> -1
                                        else -> 0
                                    }
                                    if (commit == 0) {
                                        // 未过阈值：弹簧回中（邻居随之滑回屏外）
                                        discSettleJob = dragScope.launch {
                                            animate(
                                                discDragOffset,
                                                0f,
                                                animationSpec = MotionTokens.springPanel,
                                            ) { v, _ -> discDragOffset = v }
                                        }
                                    } else {
                                        // 已换片：记下意图方向；位移**冻结**在松手处，
                                        // 等下标真正变化后由 LaunchedEffect(currentIndex) 与换片平移同步收回
                                        commitPending = true
                                        pendingSwipeDirection = commit
                                        if (commit > 0) onNext() else onPrevious()
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val next = discDragOffset + dragAmount
                                    discDragOffset = if (abs(next) <= softLimit) {
                                        next
                                    } else {
                                        // 超出软上限后只跟三成：给出「拖不动了」的阻尼手感，又不硬断
                                        val sign = if (next > 0f) 1f else -1f
                                        sign * (softLimit + (abs(next) - softLimit) * DRAG_OVERSHOOT_DAMPING)
                                    }
                                },
                            )
                                }
                            },
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onToggleLyrics() },
                    contentAlignment = Alignment.Center,
                ) {
                    // 三层共享同一个几何盒子：**先定盒子尺寸，三层一律 fillMaxSize**。
                    //
                    // 为什么必须这样：AnimatedContent 会用「紧密约束」测量其内容，
                    // 内容里的 fillMaxWidth(0.85f) 会被直接顶到容器宽 —— 唱片会按整幅宽算布局
                    // （碟径 0.80W 而非 0.68W），而唱针层是松约束的兄弟、仍按 0.85W 算，
                    // 于是出现「唱片变大 + 唱针错位」。把尺寸挪到共享盒子上，三层拿到完全
                    // 相同的约束，尺寸与对齐都被钉死。
                    val discBoxFraction = if (style == AudioPlayerStyle.GLASS) {
                        // 简约封面：封面下方还要塞下歌名/艺术家/歌词三行，封面收窄一档
                        GLASS_COVER_FRACTION
                    } else {
                        0.85f
                    }
                    // 碟面区域：BoxWithConstraints 拿到可用宽度，据此**显式**算出三层共用的
                    // 盒子边长 side —— 不能依赖父级约束：父级一旦给紧密约束，fillMaxWidth 就失效，
                    // 这正是之前「唱片被放大 + 唱针错位」的成因。
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 简约封面：封面边长要先扣掉下方三行信息栈的高度 —— 三行是固定高度、
                        // 不参与权重分配，不先扣的话短屏上歌词会被挤出屏幕。
                        val side = if (isCardSkeleton) {
                            val reserved = GlassCoverTopGap + GlassCreditsGap + glassCreditsHeight
                            minOf(maxWidth * discBoxFraction, maxHeight - reserved)
                                .coerceAtLeast(GlassCoverMinSide)
                        } else {
                            maxWidth * discBoxFraction
                        }
                        // 简约封面：封面组在剩余高度里居中偏上（见 GLASS_COVER_TOP_SLACK_BIAS）。
                        // 恒按「有歌词」算组高：歌词是异步到的，按当前有没有算会让封面在
                        // 歌词到达那一帧突然挪位。
                        val coverTopGap = if (isCardSkeleton) {
                            val groupHeight =
                                GlassCoverTopGap + side + GlassCreditsGap + glassCreditsHeight
                            ((maxHeight - groupHeight) * GLASS_COVER_TOP_SLACK_BIAS)
                                .coerceAtLeast(GlassCoverTopGap)
                        } else {
                            GlassCoverTopGap
                        }
                        // 邻居间距 = 1.06 × 页宽 ≈ 1.32 × 碟径，与网易云的观感一致
                        // （原先 0.84× 两张几乎相切，太挤）。
                        val pageShift = side * 1.06f
                        // 最底层：唱盘阴影。语义上属于「唱机」——唱片滑走时它**原地不动**。
                        if (style == AudioPlayerStyle.VINYL) {
                            VinylPlatter(modifier = Modifier.size(side))
                        }
                        // 跟手平移条：三页（上一张 / 当前 / 下一张）一起跟着手指走。
                        //
                        // 宽度必须用 requiredWidth 撑到 **3 个页宽**：普通 fillMaxWidth 会被父级宽度
                        // 卡住，邻居页正好落在容器之外被裁掉，拖动时只剩边缘一条缝——就是「只看到一个角」。
                        Box(
                            modifier = Modifier
                                .requiredWidth(side * 3f)
                                .height(side)
                                // 简约封面：整组按 GLASS_COVER_TOP_SLACK_BIAS 落位；
                                // 其它风格维持整块居中。
                                //
                                // 位移必须用 offset 而**不是 padding**：padding 会从高度里吃掉这一段
                                // （内容只拿到 side − gap），封面会被压成 side × (side − gap) 的长方形 ——
                                // 之前 gap 只有 6dp 看不出来，改成几十 dp 后就非常明显。
                                .then(
                                    if (isCardSkeleton) {
                                        Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = coverTopGap)
                                    } else {
                                        Modifier
                                    },
                                )
                                .graphicsLayer {
                                    translationX = discDragOffset
                                    // 简约封面：封面缓慢上下浮 + 极轻微摆动/缩放。
                                    // 值都在绘制阶段读，因此每帧的变化只让这一层重画，不带着整页重组。
                                    // 幅度再乘 glassFloatActivity：暂停时它平滑落到 0，封面回到正中。
                                    val activity = glassFloatActivity
                                    val bob = sin(glassFloatBob.value * TWO_PI) * activity
                                    translationY = bob * GlassFloatAmplitude.toPx()
                                    rotationZ = sin(glassFloatSway.value * TWO_PI) *
                                        activity * GLASS_FLOAT_SWAY_DEGREES
                                    // 进度以「一个页宽」为基准（size.width = 3 个页宽）
                                    val pageW = (size.width / 3f).coerceAtLeast(1f)
                                    val p = (abs(discDragOffset) / pageW).coerceIn(0f, 1f)
                                    // 跟手换片的收缩与浮动缩放相乘：拖动时仍然只有「缩小 + 淡出」
                                    val s = (1f - 0.06f * p) * (1f + GLASS_FLOAT_SCALE * bob)
                                    scaleX = s
                                    scaleY = s
                                    alpha = 1f - 0.25f * p
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            // 邻居唱片：各占一个「页宽」，与当前唱片首尾相接，拖动时从两侧滑入。
                            // isPlaying=false：邻居不转，也不带自身投影（投影在固定的唱盘层上）。
                            // 只要存在上一首/下一首就画出来（封面未就绪则显示无封面空盘）；
                            // 显示条件见 showNeighbors——拖动中，或松手后尚未交接给换片动画时。
                            if (showNeighbors && canSwipePrevious) {
                                VinylRecordPlayer(
                                    coverData = coverForPage(neighborPrevIndex),
                                    labelTitle = titleForPage(neighborPrevIndex),
                                    isPlaying = false,
                                    modifier = Modifier
                                        .size(side)
                                        .offset(x = -pageShift),
                                    drawShadow = false,
                                )
                            }
                            if (showNeighbors && canSwipeNext) {
                                VinylRecordPlayer(
                                    coverData = coverForPage(neighborNextIndex),
                                    labelTitle = titleForPage(neighborNextIndex),
                                    isPlaying = false,
                                    modifier = Modifier
                                        .size(side)
                                        .offset(x = pageShift),
                                    drawShadow = false,
                                )
                            }
                            // 切歌动画：旧唱片向一侧平移出场、新唱片自另一侧进场，形成「换唱片」的观感。
                            // 方向由下标变化推断——`targetState > initialState` 即「下一首」
                            // （自右入、向左出），所以各种切歌方式都能得到正确取向。
                            //
                            // 简约封面不做「唱片横移」：封面是被**换掉**的，不是被推走的 ——
                            // 旧封面后退淡出、新封面推近淡入，与「封面 ↔ 歌词」用同一种语言。
                            AnimatedContent(
                                targetState = currentIndex,
                                transitionSpec = {
                                    val base = if (initialState < 0 || targetState < 0) {
                                        // 载入跳变（下标尚未有效，如 -1 → 首曲）不做动效
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else if (isCardSkeleton) {
                                        val swapIn = tween<Float>(
                                            durationMillis = GLASS_TRACK_ENTER_MS,
                                            delayMillis = GLASS_TRACK_ENTER_DELAY_MS,
                                            easing = MotionTokens.easeEnter,
                                        )
                                        val swapOut = tween<Float>(
                                            GLASS_TRACK_EXIT_MS,
                                            easing = MotionTokens.easeExit,
                                        )
                                        (
                                            fadeIn(swapIn) + scaleIn(
                                                animationSpec = swapIn,
                                                initialScale = GLASS_TRACK_SWAP_SCALE,
                                            )
                                            ) togetherWith (
                                            fadeOut(swapOut) + scaleOut(
                                                animationSpec = swapOut,
                                                targetScale = GLASS_TRACK_SWAP_SCALE,
                                            )
                                            )
                                    } else {
                                        val dir = if (slideDirection != 0) {
                                            // 手势/按钮的意图方向（随机模式、列表回绕同样正确）
                                            slideDirection
                                        } else if (targetState > initialState) {
                                            // 其余来源（自动续播、点歌单跳转）退回按下标差推断
                                            1
                                        } else {
                                            -1
                                        }
                                        // 进出并行（同时长同曲线）、位移取整幅宽，两张唱片首尾相接，
                                        // 不重叠、不留缝——读起来是「一条胶片横移换片」。
                                        slideInHorizontally(
                                            animationSpec = tween(
                                                MotionTokens.PAGE_ENTER,
                                                easing = MotionTokens.easeStandard,
                                            ),
                                            initialOffsetX = { dir * it },
                                        ) togetherWith slideOutHorizontally(
                                            animationSpec = tween(
                                                MotionTokens.PAGE_ENTER,
                                                easing = MotionTokens.easeStandard,
                                            ),
                                            targetOffsetX = { -dir * it },
                                        )
                                    }
                                    // 容器只有唱片盒子那么大，必须关掉裁剪，
                                    // 否则唱片滑到容器边缘会被切出一条硬边。
                                    base.using(SizeTransform(clip = false))
                                },
                                modifier = Modifier.size(side),
                                contentAlignment = Alignment.Center,
                                label = "trackChange",
                            ) { pageIndex ->
                                // 按页下标取封面：滑出中的旧唱片仍显示自己的封面（见 coverForPage）
                                val pageCover = coverForPage(pageIndex)
                                when (style) {
                                    AudioPlayerStyle.GLASS -> CoverCard(
                                        coverData = pageCover,
                                        labelTitle = titleForPage(pageIndex),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    AudioPlayerStyle.APPLE_MUSIC,
                                    AudioPlayerStyle.VINYL,
                                    -> VinylRecordPlayer(
                                        coverData = pageCover,
                                        labelTitle = titleForPage(pageIndex),
                                        isPlaying = isPlaying,
                                        modifier = Modifier.fillMaxSize(),
                                        // 投影由固定的 VinylPlatter 画（见上）；唱片自带影子会跟着滑走
                                        drawShadow = false,
                                    )
                                }
                            }
                        }
                        // 唱针：**不随唱片平移**——唱机上的针是固定的，跟手的只是唱片。
                        if (style == AudioPlayerStyle.VINYL) {
                            VinylNeedle(
                                isPlaying = isPlaying,
                                // 拖动中/归位中也算抬起（见 needleUp）
                                needleLifted = needleUp,
                                modifier = Modifier.size(side),
                            )
                        }
                        // 简约封面：封面下方的三行信息栈（歌名 / 艺术家 / 当前歌词）。
                        // 用 align + padding 叠在封面上方那一块之下，而不是塞进 Column ——
                        // 唱针必须与唱片重叠，一旦把封面挪进 Column，唱针就会变成排在下面。
                        if (isCardSkeleton) {
                            GlassTrackCredits(
                                title = title,
                                artist = artist,
                                lrcLines = lrcLines,
                                positionMs = positionMs,
                                isPlaying = isPlaying,
                                perChar = perCharLyrics,
                                accent = accent,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = coverTopGap + side + GlassCreditsGap),
                            )
                        }
                    }
                }
            }
                }
            }
        }
        }

        if (hasActiveContent) {
            // 切歌按钮：记下意图方向，让换页动画与按钮语义一致（随机模式下下标差不可靠）
            val handlePrevious: () -> Unit = {
                pendingSwipeDirection = -1
                onPrevious()
            }
            val handleNext: () -> Unit = {
                pendingSwipeDirection = 1
                onNext()
            }
            // Apple 歌词页：底部控件自动收起，整页交给歌词
            AnimatedVisibility(visible = !(isAppleSkeleton && showLyrics) || lyricsControlsVisible) {
            if (isCardSkeleton) {
                // 简约封面：歌名/艺术家/歌词已经搬到封面下方，这里只剩细进度条 + 居中的传输键行
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GlassControlColumn(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = onSeek,
                        isPlaying = isPlaying,
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        onTogglePlay = onTogglePlay,
                        onPrevious = handlePrevious,
                        onNext = handleNext,
                        modeIcon = modeIcon,
                        modeLabel = modeLabel,
                        onCyclePlayMode = onCyclePlayMode,
                        onShowPlaylist = onShowPlaylist,
                        accent = accent,
                        onAccent = onAccent,
                    )

                    // 收尾留白：比黑胶 / Apple 的 20dp 大一档 —— 本主题没有底部那行快捷操作，
                    // 这段留白把整块控件往上托，压缩歌词行与进度条之间的空白，
                    // 顺带让播放键避开手势条。
                    Spacer(modifier = Modifier.height(GlassControlsBottomSpace))
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.infoArea.horizontalPadding),
                horizontalAlignment = theme.infoArea.horizontalAlignment,
            ) {
                // Apple：歌名行在封面下方（封面页）或顶部信息条里（歌词页），
                // 底部这一块不再重复，直接留白给进度与传输键。
                if (theme.infoArea.showsTitleRow) {
                    // 歌名居中 + 序号**贴行尾**（叠层，互不参与对方布局）：
                    // - 序号位置固定，不再随歌名长短左右漂（原来并进居中行就会漂）；
                    // - 序号迟到/就绪都不挪动歌名，也就不会再有跳动。
                    // 行尾正好与下方进度条右端、时长文字对齐。
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 上限 60%：再长也只自己省略号，绝不与右侧序号重叠
                            modifier = Modifier.widthIn(max = layoutWidth * 0.6f),
                        )
                        if (playlist.isNotEmpty() && currentIndex >= 0) {
                            Text(
                                text = "${currentIndex + 1} / ${playlist.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryColor,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ProgressArea(
                    variant = theme.control.progress,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )

                Spacer(modifier = Modifier.height(8.dp))

                PlaybackControls(
                    isPlaying = isPlaying,
                    buffering = false,
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onTogglePlay = onTogglePlay,
                    onPrevious = handlePrevious,
                    onNext = handleNext,
                    playMode = playMode,
                    modeIcon = modeIcon,
                    modeLabel = modeLabel,
                    onCyclePlayMode = onCyclePlayMode,
                    onShowPlaylist = onShowPlaylist,
                    spec = theme.control,
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
            }
            }
        }

        // Apple：底部再留一段空白，与控件上方那段一起把空余分掉
        // （见 APPLE_BOTTOM_SLACK_WEIGHT）。条件只取决于「有无可播放内容」，与是否歌词页无关，
        // 这样封面/歌词切换时控件位置稳定、不会跳。
        if (isAppleSkeleton && appleActive) {
            Spacer(Modifier.weight(APPLE_BOTTOM_SLACK_WEIGHT))
        }
    }
    // Apple 封面页：悬浮返回；歌词页不显示返回按钮（点小封面回到封面页 / 系统返回手势）
    if (isAppleSkeleton && !showLyrics) {
        AppleMusicBackButton(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
    }
}

/**
 * 歌词页底部「收起」触发条：全宽 48dp，图标 + 文案。
 *
 * 行数放开后歌词几乎铺满整屏、可点空白被压缩，因此需要一个明确且足够大的返回入口。
 * 用「向下箭头」表达收起语义，并配文案，避免纯图标按钮的可发现性问题。
 *
 * 文案由调用方给：黑胶是「回到唱片」，简约封面没有唱片，用「收起歌词」。
 */
@Composable
private fun BackToRecordBar(label: String, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onSurface.copy(alpha = 0.55f),
        )
    }
}

/**
 * 横屏布局：左右分栏。
 * 左侧展示黑胶 / 播放错误 / 无播放源（保持不变）；
 * 右侧同时显示歌词（上方，行数自适应、最少 3 行）与一行式播放控件（底部）；
 * 无歌词时右侧仅显示控件并居中。横屏高度紧凑，使用紧凑模式控件。
 */
@Composable
internal fun LandscapeLayout(
    state: AudioPlayerUiState,
    actions: AudioPlayerActions,
    style: AudioPlayerStyle = AudioPlayerStyle.APPLE_MUSIC,
) {
    // 把两个 holder 摊平回局部名字：函数体是既有实现，逐处改名会产出几百行难以复核的 diff，
    // 而这段代码没有单测、只能靠真机验证。摊平后函数体**一字未动**，回归风险为零。
    // 将来若要改名，是一次独立的机械改动。
    val (
        hasActiveContent, playbackError, _, lrcLines, positionMs, durationMs,
        title, artist, isPlaying, hasPrev, hasNext, coverPath, _, _,
        _, _, _, _, playMode, modeIcon, modeLabel, speedOptions, currentSpeedIndex,
        sleepTimerText, showDownload, showExternalActions,
    ) = state
    val (
        onRetry, onSeek, onTogglePlay, onPrevious, onNext, _, onCyclePlayMode,
        onShowPlaylist, onBack, onDownload, onEqualizer, onSpeedSelect, onSleepTimer,
        onOpenWith, onShare, onStyleSelect,
    ) = actions

    val onSurface = MaterialTheme.colorScheme.onSurface
    val theme = themeOf(style)
    // 同竖屏：一律问骨架，不问 style（见 PortraitLayout 的说明）
    val isAppleSkeleton = theme.skeleton == PlayerSkeleton.APPLE_BANNER
    val isCardSkeleton = theme.skeleton == PlayerSkeleton.CARD
    // 简约封面：横屏的歌词高亮与控件同样跟着封面强调色走（与竖屏同一套口径）
    val landscapeCoverAccent = if (style == AudioPlayerStyle.GLASS) {
        rememberCoverAccent(coverPath as? String, NiExtraColors.current.isDark)
    } else {
        null
    }
    val landscapeAccent = landscapeCoverAccent?.color ?: MaterialTheme.colorScheme.primary
    val landscapeOnAccent = landscapeCoverAccent?.onColor ?: MaterialTheme.colorScheme.onPrimary
    val landscapePerCharLyrics = remember { PlayerSettings.lyricPerCharEnabled }
    // 大屏（平板/大屏手机横屏）下歌词行数更多，配合 LyricsView 内部字号/行高自适应放大
    val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 800

    // 沉浸模式：横屏 3s 无操作自动隐藏顶部行与底部控件，仅留歌词（5 行）；
    // 点击空白处切换控件显隐（显示时歌词 3 行，隐藏时 5 行）。
    // 无歌词时不启用自动隐藏。
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val hasLyrics = lrcLines.isNotEmpty()
    // 更多/倍速下拉菜单展开中：暂停自动隐藏计时，避免菜单还开着控件就收起
    var menuOpen by remember { mutableStateOf(false) }

    fun onBackgroundTap() {
        // 点击空白处切换控件显隐；恢复显示时重启 3s 自动隐藏计时
        controlsVisible = !controlsVisible
        if (controlsVisible) interactionTick++
    }

    // 每次交互递增 interactionTick 重启 3s 计时；无歌词时强制显示控件；
    // 下拉菜单展开时不执行自动隐藏
    LaunchedEffect(interactionTick, hasLyrics, menuOpen) {
        if (!hasLyrics || menuOpen) {
            controlsVisible = true
            return@LaunchedEffect
        }
        delay(AUTO_HIDE_DELAY_MS)
        controlsVisible = false
    }

    // 横屏不使用独立顶栏：整屏为左右分栏 Row。
    // 左侧唱片占满全部高度；右侧顶部一行 = 返回 + 歌名 + 操作按钮（更多），
    // 下方为歌词（占满剩余空间）与一行式播放控件。最底部常驻细进度条。
    Box(modifier = Modifier.fillMaxSize()) {
    if (isAppleSkeleton) {
        // 横屏没有贴顶封面，整屏都是封面自身的颜色网格
        AppleArtworkMeshBackdrop(
            mesh = rememberAppleArtworkMesh(coverPath as? String),
            seam = 0.dp,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 点击任意空白处切换控件显隐
            .pointerInput(Unit) { detectTapGestures { onBackgroundTap() } },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // 横屏已沉浸全屏（隐藏系统栏），无需 statusBarsPadding，保证唱片居中
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // 左侧：唱片占满全部高度
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasActiveContent -> Text(
                    text = stringResource(R.string.player_no_source),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isAppleSkeleton) Color.White.copy(alpha = 0.6f)
                    else onSurface.copy(alpha = 0.6f),
                )
                playbackError != null -> PlaybackErrorState(
                    errorMessage = playbackError,
                    onRetry = onRetry,
                )
                style == AudioPlayerStyle.APPLE_MUSIC -> AppleMusicArtwork(
                    coverData = coverPath,
                    labelTitle = title,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxHeight(0.86f)
                        .aspectRatio(1f),
                )
                style == AudioPlayerStyle.GLASS -> CoverCard(
                    coverData = coverPath,
                    labelTitle = title,
                    modifier = Modifier
                        .fillMaxHeight(0.86f)
                        .aspectRatio(1f),
                )
                else -> VinylRecordPlayer(
                    coverData = coverPath,
                    isPlaying = isPlaying,
                    // 横屏不放唱针；高度留白较多，让出空间给歌词区
                    modifier = Modifier
                        .fillMaxHeight(0.78f)
                        .aspectRatio(1f),
                    needleSpace = false,
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // 右侧列：顶部行（返回 + 歌名 + 操作按钮）+ 歌词 + 控件
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
        ) {
            // 顶部行：返回 | 歌名 | 更多（沉浸模式下自动隐藏）
            AnimatedVisibility(visible = controlsVisible) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (theme.chrome.tint) {
                                            ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.08f)
                                            ChromeTint.LIGHT_ON_DARK -> Color.Transparent
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.player_back),
                                    tint = when (theme.chrome.tint) {
                                        ChromeTint.MATERIAL -> onSurface.copy(alpha = 0.8f)
                                        ChromeTint.LIGHT_ON_DARK -> Color.White.copy(alpha = 0.9f)
                                    },
                                )
                            }
                        }
                        if (isAppleSkeleton) {
                            AppleTrackTitle(
                                title = title.ifEmpty { stringResource(R.string.player_unknown_song) },
                                artist = artist,
                                modifier = Modifier.weight(1f),
                                compact = true,
                            )
                        } else {
                            Text(
                                text = if (title.isNotEmpty()) title else stringResource(R.string.player_unknown_song),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurface.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TopBarActions(
                            onDownload = onDownload,
                            onEqualizer = onEqualizer,
                            speedOptions = speedOptions,
                            currentSpeedIndex = currentSpeedIndex,
                            onSpeedSelect = onSpeedSelect,
                            onMenuOpenChange = { menuOpen = it },
                            showDownload = showDownload,
                            sleepTimerText = sleepTimerText,
                            onSleepTimer = onSleepTimer,
                            showExternalActions = showExternalActions,
                            onOpenWith = onOpenWith,
                            onShare = onShare,
                            chrome = theme.chrome,
                            audioStyle = style,
                            onStyleSelect = onStyleSelect,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (hasLyrics) {
                // 歌词：控件可见时 3 行（为顶栏/控件让位），隐藏时 5 行（沉浸展示）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 132.dp), // 44dp × 3 行下限
                    contentAlignment = Alignment.Center,
                ) {
                    LyricsView(
                        lrcLines = lrcLines,
                        currentPositionMs = positionMs,
                        onSeek = onSeek,
                        // 大屏行数更多：控件可见 3/5 行，沉浸 5/8 行
                        maxVisibleLines = if (controlsVisible) {
                            if (isLargeScreen) 5 else 3
                        } else {
                            if (isLargeScreen) 8 else 5
                        },
                        modifier = Modifier.fillMaxSize(),
                        theme = theme.lyrics,
                        accentColor = landscapeAccent,
                        isPlaying = isPlaying,
                        perChar = landscapePerCharLyrics,
                    )
                }

                // 底部一行式控件（沉浸模式下自动隐藏）
                AnimatedVisibility(visible = controlsVisible) {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isCardSkeleton) {
                            GlassControlColumn(
                                positionMs = positionMs,
                                durationMs = durationMs,
                                onSeek = onSeek,
                                isPlaying = isPlaying,
                                hasPrev = hasPrev,
                                hasNext = hasNext,
                                onTogglePlay = onTogglePlay,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                modeIcon = modeIcon,
                                modeLabel = modeLabel,
                                onCyclePlayMode = onCyclePlayMode,
                                onShowPlaylist = onShowPlaylist,
                                accent = landscapeAccent,
                                onAccent = landscapeOnAccent,
                                compact = true,
                            )
                        } else {
                            ControlColumn(
                                positionMs = positionMs,
                                durationMs = durationMs,
                                onSeek = onSeek,
                                isPlaying = isPlaying,
                                hasPrev = hasPrev,
                                hasNext = hasNext,
                                onTogglePlay = onTogglePlay,
                                onPrevious = onPrevious,
                                onNext = onNext,
                                playMode = playMode,
                                modeIcon = modeIcon,
                                modeLabel = modeLabel,
                                onCyclePlayMode = onCyclePlayMode,
                                onShowPlaylist = onShowPlaylist,
                                compact = true,
                                spec = theme.control,
                            )
                        }
                    }
                }
            } else if (hasActiveContent) {
                // 无歌词：控件在剩余空间垂直居中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCardSkeleton) {
                        GlassControlColumn(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeek = onSeek,
                            isPlaying = isPlaying,
                            hasPrev = hasPrev,
                            hasNext = hasNext,
                            onTogglePlay = onTogglePlay,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            modeIcon = modeIcon,
                            modeLabel = modeLabel,
                            onCyclePlayMode = onCyclePlayMode,
                            onShowPlaylist = onShowPlaylist,
                            accent = landscapeAccent,
                            onAccent = landscapeOnAccent,
                            compact = true,
                        )
                    } else {
                        ControlColumn(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeek = onSeek,
                            isPlaying = isPlaying,
                            hasPrev = hasPrev,
                            hasNext = hasNext,
                            onTogglePlay = onTogglePlay,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            playMode = playMode,
                            modeIcon = modeIcon,
                            modeLabel = modeLabel,
                            onCyclePlayMode = onCyclePlayMode,
                            onShowPlaylist = onShowPlaylist,
                            compact = true,
                            spec = theme.control,
                        )
                    }
                }
            }
        }
        }

        // 最底部细进度条：仅在沉浸模式（控件隐藏）下显示，作为进度的唯一指示；
        // 控件可见时底部已有完整进度条，避免重复
        AnimatedVisibility(visible = !controlsVisible) {
            ThinProgressBar(
                positionMs = positionMs,
                durationMs = durationMs,
                modifier = Modifier.fillMaxWidth(),
                // MATERIAL 下 landscapeAccent 已含「简约封面取封面强调色 / 黑胶回落主色」的口径
                accentColor = when (theme.chrome.tint) {
                    ChromeTint.MATERIAL -> landscapeAccent
                    ChromeTint.LIGHT_ON_DARK -> Color.White
                },
                trackColor = when (theme.chrome.tint) {
                    ChromeTint.MATERIAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ChromeTint.LIGHT_ON_DARK -> Color.White.copy(alpha = 0.18f)
                },
            )
        }
    }
    }
}
