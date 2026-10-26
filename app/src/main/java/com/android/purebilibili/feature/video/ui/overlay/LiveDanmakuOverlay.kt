package com.android.purebilibili.feature.video.ui.overlay

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.danmaku.engine.DanmakuEngine
import com.android.purebilibili.danmaku.engine.DanmakuItem
import com.android.purebilibili.danmaku.engine.DanmakuRenderConfig
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import com.android.purebilibili.feature.live.LiveDanmakuItem
import com.android.purebilibili.feature.video.danmaku.DanmakuTypeFilterSettings
import com.android.purebilibili.feature.video.danmaku.createBitmapDanmaku
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuStrokeWidthPx
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuTextSizePx
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuRenderLayerType
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuPinnedDurationMillis
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuScrollDurationMillis
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuTypeface
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuVisibleLineCount
import com.android.purebilibili.feature.video.danmaku.shouldBlockDanmakuByRules
import com.android.purebilibili.feature.video.danmaku.shouldDisplayStandardDanmaku
import java.util.ArrayDeque
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive

private const val LIVE_BATCH_INTERVAL_MS = 100L

/** 弹幕文本中的 [表情名] 标记，键形态与房间表情映射表对齐。 */
private val EMOTE_TAG_PATTERN = Regex("\\[(.*?)\\]")
private const val LIVE_HISTORY_MS = 20_000L
private const val MAX_ACTIVE_LIVE_DANMAKU = 160
private const val MAX_PENDING_LIVE_DANMAKU = 80
private const val MAX_PENDING_ITEMS_BEFORE_START = 48

private class PendingLiveDanmakuItem(
    val item: LiveDanmakuItem,
    val queuedAtTime: Long
)

/** Live danmaku renderer backed by an append-only, bounded session timeline. */
@Composable
fun LiveDanmakuOverlay(
    danmakuFlow: SharedFlow<LiveDanmakuItem>,
    isFullscreen: Boolean,
    displayArea: Float = 1f,
    danmakuSettings: DanmakuSettings = DanmakuSettings(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val safeDisplayArea = displayArea.takeIf(Float::isFinite)?.coerceIn(0.25f, 1f) ?: 1f
    val latestDanmakuSettings by rememberUpdatedState(danmakuSettings)
    var renderView by remember { mutableStateOf<DanmakuRenderView?>(null) }
    var engine by remember { mutableStateOf<DanmakuEngine?>(null) }
    var startTime by remember { mutableLongStateOf(0L) }
    var isStarted by remember { mutableStateOf(false) }
    // 表情位图按当前渲染字号光栅化；字号由 update 块随设置刷新。
    var latestTextSizePx by remember { mutableFloatStateOf(48f) }
    val activeItems = remember { ArrayDeque<DanmakuItem>() }
    // 排队消息在入队时绑定时间，画面文字样式由渲染器统一配置。
    val pendingItems = remember { ArrayDeque<PendingLiveDanmakuItem>() }
    val pendingItemsBeforeStart = remember { ArrayDeque<LiveDanmakuItem>() }

    AndroidView(
        factory = { viewContext ->
            DanmakuRenderView(viewContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                renderView = this
                engine = this.engine
                startTime = SystemClock.elapsedRealtime()
                this.engine.start(0L)
                isStarted = true
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(safeDisplayArea),
        update = { view ->
            val textSize = resolveDanmakuTextSizePx(
                density = density,
                fontScale = danmakuSettings.fontScale,
                isFullscreen = isFullscreen
            )
            latestTextSizePx = textSize
            val strokeWidth = resolveDanmakuStrokeWidthPx(density, danmakuSettings.strokeWidth)
            view.engine.updateConfig(
                DanmakuRenderConfig(
                    alpha = (danmakuSettings.opacity.coerceIn(0f, 1f) * 255).toInt(),
                    textSizePx = textSize,
                    typeface = resolveDanmakuTypeface(danmakuSettings.fontWeight),
                    strokeWidthPx = strokeWidth,
                    scrollDurationMs = resolveDanmakuScrollDurationMillis(
                        scrollDurationSeconds = danmakuSettings.scrollDurationSeconds,
                        speedFactor = danmakuSettings.speed,
                        scrollFixedVelocity = danmakuSettings.scrollFixedVelocity,
                        viewportWidthPx = view.width
                    ),
                    lineHeightPx = textSize * danmakuSettings.lineHeight.coerceIn(1f, 3f),
                    lineCount = resolveDanmakuVisibleLineCount(
                        visibleHeightPx = view.height.toFloat(),
                        areaRatioHint = safeDisplayArea,
                        fontSize = textSize,
                        strokeWidth = strokeWidth,
                        strokeEnabled = strokeWidth > 0f,
                        lineHeight = danmakuSettings.lineHeight,
                        massiveMode = danmakuSettings.massiveMode
                    ),
                    pinnedDurationMs = resolveDanmakuPinnedDurationMillis(
                        danmakuSettings.staticDurationSeconds
                    )
                )
            )
        }
    )

    LaunchedEffect(engine, isStarted) {
        while (isActive) {
            val currentEngine = engine
            if (currentEngine != null && isStarted) {
                val currentTime = SystemClock.elapsedRealtime() - startTime
                val settings = latestDanmakuSettings

                while (pendingItemsBeforeStart.isNotEmpty()) {
                    pendingItems.addLast(
                        PendingLiveDanmakuItem(
                            item = pendingItemsBeforeStart.removeFirst(),
                            queuedAtTime = currentTime
                        )
                    )
                }

                if (pendingItems.isNotEmpty()) {
                    // 文字消息直接组装为有界批次，不发起图片加载。
                    val pendingBatch = ArrayList<PendingLiveDanmakuItem>(pendingItems.size)
                    while (pendingItems.isNotEmpty()) pendingBatch.add(pendingItems.removeFirst())
                    val currentTextSize = latestTextSizePx
                    val batch = pendingBatch.map { pending ->
                        createLiveDanmakuItem(
                            item = pending.item,
                            currentTime = pending.queuedAtTime,
                            staticDanmakuToScroll = settings.staticDanmakuToScroll,
                            textSizePx = currentTextSize,
                            onUpdate = { currentEngine.invalidate() },
                            context = context,
                        )
                    }
                    // 排队时间较久的消息以当前时间入场，避免使用已过期的时间戳。
                    val readyTime = SystemClock.elapsedRealtime() - startTime + 50L
                    batch.forEach {
                        it.showAtTime = maxOf(it.showAtTime, readyTime)
                        activeItems.addLast(it)
                    }
                    appendLiveDanmakuBatch(batch, currentEngine::append)
                }

                var trimBefore = currentTime - LIVE_HISTORY_MS
                while (activeItems.isNotEmpty() &&
                    (activeItems.first.showAtTime < trimBefore || activeItems.size > MAX_ACTIVE_LIVE_DANMAKU)
                ) {
                    val removed = activeItems.removeFirst()
                    trimBefore = maxOf(trimBefore, removed.showAtTime + 1L)
                }
                currentEngine.trimBefore(trimBefore)
            }
            delay(LIVE_BATCH_INTERVAL_MS)
        }
    }

    LaunchedEffect(danmakuFlow) {
        danmakuFlow.collect { item ->
            val settings = latestDanmakuSettings
            val typeFilter = DanmakuTypeFilterSettings(
                allowScroll = settings.allowScroll,
                allowTop = settings.allowTop,
                allowBottom = settings.allowBottom,
                allowColorful = settings.allowColorful,
                allowSpecial = settings.allowSpecial
            )
            if (item.isSuperChat && !settings.allowSpecial) {
                return@collect
            }
            if (!item.isSuperChat && !shouldDisplayStandardDanmaku(item.mode, item.color, typeFilter)) {
                return@collect
            }
            val userHash = item.uid.takeIf { it > 0L }?.toString().orEmpty()
            if (shouldBlockDanmakuByRules(item.text, settings.blockRules, userHash)) {
                return@collect
            }
            val currentEngine = engine
            if (!isStarted || currentEngine == null || startTime == 0L) {
                if (pendingItemsBeforeStart.size >= MAX_PENDING_ITEMS_BEFORE_START) {
                    pendingItemsBeforeStart.removeFirst()
                }
                pendingItemsBeforeStart.addLast(item)
                return@collect
            }

            val currentTime = SystemClock.elapsedRealtime() - startTime
            if (pendingItems.size >= MAX_PENDING_LIVE_DANMAKU) {
                pendingItems.removeFirst()
            }
            pendingItems.addLast(PendingLiveDanmakuItem(item, currentTime))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine?.close()
            renderView?.releaseRenderer()
            // 队列中的表情位图由弹幕引擎的渲染数据持有，这里只清理引用不主动回收。
            activeItems.clear()
            pendingItems.clear()
            pendingItemsBeforeStart.clear()
            isStarted = false
            engine = null
            renderView = null
        }
    }
}

private fun createLiveDanmakuItem(
    item: LiveDanmakuItem,
    currentTime: Long,
    staticDanmakuToScroll: Boolean,
    textSizePx: Float,
    onUpdate: () -> Unit,
    context: android.content.Context,
): DanmakuItem {
    val layerType = resolveDanmakuRenderLayerType(item.mode, staticDanmakuToScroll)
    val textColor = if (item.color == 0) AndroidColor.WHITE else (0xFF000000 or item.color.toLong()).toInt()
    val showAtTime = currentTime + 50L

    // 专属表情弹幕与内嵌表情在画面上光栅化为图文位图；纯文字消息仍走文本渲染，
    // 不发起图片加载。消息级 emots 优先于房间表情全局表。
    val directEmoteUrl = item.emoticon?.url ?: item.emoticonUrl
    val extraEmotes = buildMap {
        item.inlineEmotes.forEach { (key, emote) ->
            if (emote.url.isNotBlank()) put(key, emote.url)
        }
        if (directEmoteUrl != null && directEmoteUrl.isNotBlank()) {
            put(item.text, directEmoteUrl)
        }
    }
    // 房间表情包表情（如"[等着]"）通常不带消息级 emots，只存在于全局映射表；
    // 文本含中括号标记且任一标记能命中映射表时同样走位图路径。
    val hasMappedRoomEmote = extraEmotes.isEmpty() && item.text.contains('[') && run {
        val emoteMap = com.android.purebilibili.feature.live.components.DanmakuEmoticonMapper.emoticonMap.value
        emoteMap.isNotEmpty() && EMOTE_TAG_PATTERN.findAll(item.text).any { match ->
            val key = match.groupValues[1]
            emoteMap.containsKey(key) || emoteMap.containsKey("[$key]")
        }
    }
    if (extraEmotes.isNotEmpty() || hasMappedRoomEmote) {
        val bitmapItem = createBitmapDanmaku(
            context = context,
            text = item.text,
            textColor = textColor,
            textSize = textSizePx,
            layerType = layerType,
            showAtTime = showAtTime,
            enableEmoticon = true,
            extraEmotes = extraEmotes,
            emoteAspectRatios = buildMap {
                item.inlineEmotes.forEach { (key, emote) ->
                    put(key, emote.width.toFloat() / emote.height.coerceAtLeast(1))
                }
                item.emoticon?.let { emote ->
                    put(item.text, emote.width.toFloat() / emote.height.coerceAtLeast(1))
                }
            },
            onUpdate = onUpdate,
        )
        bitmapItem.isSelf = item.isSelf
        return bitmapItem
    }

    return DanmakuItem().apply {
        text = item.text
        this.textColor = textColor
        this.layerType = layerType
        this.showAtTime = showAtTime
        isSelf = item.isSelf
    }
}
