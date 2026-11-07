package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.LocalAppThemeConfig

/**
 * 拖动快进/快退时使用的加粗底部进度条。
 *
 * 手势拖动期间控制栏会整体收起，细进度条又可能被"始终隐藏"设置关掉，
 * 用户无法分辨拖动进度，因此这里用更粗的轨道加进度点直观展示拖动位置。
 */
internal const val SeekGestureProgressBarHeightDp = 6f

private val SeekGestureTrackColor = Color.White.copy(alpha = 0.32f)

@Composable
internal fun SeekGestureBottomProgressBar(
    current: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val activeColor = AppContentColors.selectionAccent(
        LocalAppThemeConfig.current.liquidGlassEnabled,
    )
    val progress = resolvePersistentProgressFraction(current = current, duration = duration)
    val trackHeight = SeekGestureProgressBarHeightDp.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight),
    ) {
        val cornerRadiusPx = size.height / 2f
        // 半透明底槽，保证在任意画面上都能看清整条轨道
        drawRoundRect(
            color = SeekGestureTrackColor,
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        )
        // 已播放部分使用主题强调色
        val playedWidth = size.width * progress
        drawRoundRect(
            color = activeColor,
            size = Size(playedWidth, size.height),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        )
        // 进度点，方便对齐中间的拖动时间气泡
        drawCircle(
            color = Color.White,
            radius = cornerRadiusPx,
            center = Offset(playedWidth.coerceAtLeast(cornerRadiusPx), size.height / 2f),
        )
    }
}
