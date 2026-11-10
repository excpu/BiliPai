package com.android.purebilibili.feature.video.ui.overlay

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 原生底栏几何策略：内嵌档（竖屏详情播放器）进度条贴满屏幕宽度、轨道贴底、
 * 与按钮行只留 2dp；横屏全屏保持 20dp 留白、轨道居中与 4dp 间距。
 */
class BottomControlBarGeometryPolicyTest {

    @Test
    fun progressGap_isTighterForInlineThanFullscreen() {
        assertEquals(4, piliPlayerProgressGapDp(isFullscreen = true))
        assertEquals(2, piliPlayerProgressGapDp(isFullscreen = false))
    }

    @Test
    fun columnHeight_tracksInlineGapForAvoidanceSeeds() {
        // 内嵌无章节：行 30 + 间距 2 + 轨道槽 14 + 顶部留白 6。
        assertEquals(52, piliPlayerBottomColumnHeightDp(hasChapters = false, isFullscreen = false))
        // 内嵌有章节：轨道槽换成 23dp 章节高度。
        assertEquals(61, piliPlayerBottomColumnHeightDp(hasChapters = true, isFullscreen = false))
        // 全屏：间距 4 + 上下留白各 6。
        assertEquals(60, piliPlayerBottomColumnHeightDp(hasChapters = false, isFullscreen = true))
        assertEquals(69, piliPlayerBottomColumnHeightDp(hasChapters = true, isFullscreen = true))
    }

    private fun bottomBarSource(): String = listOf(
        File("src/main/java/com/android/purebilibili/feature/video/ui/overlay/BottomControlBar.kt"),
        File("app/src/main/java/com/android/purebilibili/feature/video/ui/overlay/BottomControlBar.kt"),
    ).first { it.exists() }.readText()

    @Test
    fun inlineProgressBar_isFullBleedAndBottomAnchored() {
        val source = bottomBarSource()

        // 内嵌档不再吃系统返回手势区的横向 inset：进度条左右贴屏幕边。
        val seekInsetsBlock = source.substringAfter("val seekInsets = if (isFullscreen) {")
            .substringBefore("val hasSegments")
        assertTrue(
            seekInsetsBlock.contains("WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)"),
            "内嵌档进度条必须零横向 inset，才能贴满播放窗口宽度。",
        )

        // 轨道贴底锚定 + 收紧后的槽位间距（共用 piliPlayerProgressGapDp）。
        assertTrue(
            source.contains("padding(bottom = piliPlayerProgressGapDp(isFullscreen).dp)"),
            "槽位与按钮行的间距必须走同一策略函数，避免避让高度与实际布局分叉。",
        )
        assertTrue(source.contains("trackBottomAnchored = !isFullscreen"))
        assertTrue(source.contains("bottomAnchorReserveDp = if (isFullscreen) null else 4.dp"))
    }

    @Test
    fun bottomAnchoredTrack_offsetsTrackAndThumbTogether() {
        val source = listOf(
            File("src/main/java/com/android/purebilibili/feature/video/ui/overlay/VideoProgressBar.kt"),
            File("app/src/main/java/com/android/purebilibili/feature/video/ui/overlay/VideoProgressBar.kt"),
        ).first { it.exists() }.readText()

        // 轨道绘制顶部按预留量下移，并把同一 trackTop 传给共享轨道绘制，
        // 滑块用 BottomStart + 垂直偏移对齐轨道中心，两者不会脱节。
        assertTrue(source.contains("trackTopPx = if (trackBottomAnchored) trackTop else null"))
        assertTrue(source.contains(".align(thumbAlignment)"))
        assertTrue(source.contains("IntOffset(thumbOffsetProvider(), thumbVerticalOffsetPx)"))
        assertTrue(
            source.contains("bottomAnchorReserveDp?.toPx() ?: layoutPolicy.thumbDraggingSizeDp.dp.toPx() / 2f"),
            "默认预留拖动态滑块半径，调用方收紧后滑块允许轻微越界到间距区。",
        )
    }
}
