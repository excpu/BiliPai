package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.combinedClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import kotlin.math.roundToInt

enum class PlayerControlAnchor { QUALITY, SPEED, EPISODES, RATIO, TRANSLATION, SUBTITLE, ENHANCEMENT, CHAPTERS }

internal const val PILI_PLAYER_BOTTOM_ROW_HEIGHT_DP = 30
internal const val PILI_PLAYER_BOTTOM_VERTICAL_PADDING_DP = 6
internal const val PILI_PLAYER_PROGRESS_GAP_DP = 4
/** 内嵌档轨道贴底后与按钮行零间距，保持图标上下视觉均衡；全屏沿用 4dp。 */
internal const val PILI_PLAYER_INLINE_PROGRESS_GAP_DP = 0
internal const val PILI_PLAYER_PROGRESS_HEIGHT_DP = 14
internal const val PILI_PLAYER_CHAPTER_PROGRESS_HEIGHT_DP = 23

internal fun piliPlayerProgressGapDp(isFullscreen: Boolean): Int =
    if (isFullscreen) PILI_PLAYER_PROGRESS_GAP_DP else PILI_PLAYER_INLINE_PROGRESS_GAP_DP

/**
 * 内嵌档轨道贴底预留高度：
 * - 非玻璃：图标在 30dp 行内居中、底边外留白为 0dp，收紧为 2dp 预留让轨道底边贴近行顶，
 *   缩小图标上方到进度条的距离，与图标下方到视频底边保持均衡。
 * - 液态玻璃：34dp 圆形/胶囊向上下各溢出 2dp，保留 4dp 让轨道贴在玻璃胶囊上方而不重叠。
 */
internal fun piliPlayerProgressBottomAnchorReserveDp(
    isFullscreen: Boolean,
    liquidGlassEnabled: Boolean,
): Int? = when {
    isFullscreen -> null
    liquidGlassEnabled -> 4
    else -> 2
}

internal fun piliPlayerBottomControlsHeightDp(
    hasChapters: Boolean,
    isFullscreen: Boolean = true,
): Int =
    PILI_PLAYER_BOTTOM_ROW_HEIGHT_DP +
        piliPlayerProgressGapDp(isFullscreen) + if (hasChapters) PILI_PLAYER_CHAPTER_PROGRESS_HEIGHT_DP
        else PILI_PLAYER_PROGRESS_HEIGHT_DP

/** 内嵌底栏贴紧视频下边缘，只保留顶部留白；横屏全屏仍有上下留白。 */
internal fun piliPlayerBottomVerticalPaddingDp(isFullscreen: Boolean): Int =
    if (isFullscreen) PILI_PLAYER_BOTTOM_VERTICAL_PADDING_DP * 2
    else PILI_PLAYER_BOTTOM_VERTICAL_PADDING_DP

/** 原生底栏整列高度：几何常量加垂直留白，供避让计算与首帧种子共用。 */
internal fun piliPlayerBottomColumnHeightDp(
    hasChapters: Boolean,
    isFullscreen: Boolean,
): Int =
    piliPlayerBottomControlsHeightDp(hasChapters, isFullscreen) +
        piliPlayerBottomVerticalPaddingDp(isFullscreen)

internal fun Modifier.playerActionBounds(onBounds: (IntRect) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (bounds.width > 0f && bounds.height > 0f) {
            onBounds(IntRect(
                bounds.left.roundToInt(), bounds.top.roundToInt(),
                bounds.right.roundToInt(), bounds.bottom.roundToInt(),
            ))
        }
    }

/**
 * PiliPlus PlayerBar: content-width groups at opposite edges; crowded groups scale together,
 * including their hit transforms. No material-specific slots, hidden actions or horizontal scroll.
 */
@Composable
internal fun PiliPlayerBar(
    modifier: Modifier = Modifier,
    groupGlassControls: Boolean = false,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    val glass = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    val spacing = if (glass) 4.dp else 0.dp
    Layout(content = {
        PlayerControlGlassGroup(enabled = groupGlassControls) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) { left() }
        }
        PlayerControlGlassGroup(enabled = groupGlassControls) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing)) { right() }
        }
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val leftGroup = measurables[0].measure(loose)
        val rightGroup = measurables[1].measure(loose)
        val totalWidth = leftGroup.width + rightGroup.width
        val width = constraints.maxWidth
        val height = constraints.constrainHeight(maxOf(leftGroup.height, rightGroup.height))
        val scale = if (totalWidth > width) width.toFloat() / totalWidth else 1f
        val y = ((height - maxOf(leftGroup.height, rightGroup.height) * scale) / 2f).roundToInt()
        val rightX = if (scale < 1f) (leftGroup.width * scale).roundToInt() else width - rightGroup.width
        layout(width, height) {
            leftGroup.placeWithLayer(0, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            rightGroup.placeWithLayer(rightX, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}


@Composable
internal fun PlayerControlIconAction(
    icon: ImageVector,
    description: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    iconSize: Dp = 22.dp,
    onLongClick: (() -> Unit)? = null,
    selectedTint: Color = Color.White,
) {
    val glass = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    // 玻璃单图标使用正圆；关闭玻璃时保留调用方原生槽位。
    PlayerControlGlassDock(
        enabled = true,
        modifier = modifier.then(if (glass) Modifier.size(PlayerGlassIconButtonSize) else Modifier.width(width).height(height)),
        shape = if (glass) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp),
    ) {
        AppExactTouchTargets {
            Box(
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = description
                    this.selected = selected
                }.combinedClickable(role = Role.Button, onClick = appActionWithFeedback(onClick), onLongClick = onLongClick),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = (if (selected) selectedTint else Color.White)
                        .copy(alpha = if (dimmed) 0.5f else 1f),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
internal fun PlayerControlTextAction(
    text: String,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    fontSp: Int = 13,
    height: Dp = 30.dp,
) {
    PlayerControlGlassDock(enabled = true, modifier = modifier, shape = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) RoundedCornerShape(50) else RoundedCornerShape(8.dp)) {
        AppExactTouchTargets {
            Box(
                modifier = Modifier.height(height).semantics {
                    contentDescription = description
                    this.selected = selected
                }.combinedClickable(role = Role.Button, onClick = appActionWithFeedback(onClick))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = text,
                    color = Color.White,
                    fontSize = fontSp.sp,
                    maxLines = 1,
                    softWrap = false,
                    tapToCopyEnabled = false,
                )
            }
        }
    }
}
