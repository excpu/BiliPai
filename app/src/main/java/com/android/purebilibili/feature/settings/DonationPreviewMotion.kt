package com.android.purebilibili.feature.settings

import androidx.compose.animation.core.animate
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntSize
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class DonationPreviewMotion(
    val progress: State<Float>,
    val expanded: Boolean,
    val predictive: Boolean,
    val open: () -> Unit,
    val close: () -> Unit,
    val onBackStarted: () -> Unit,
    val onBackProgress: (Float) -> Unit,
    val onBackCancelled: () -> Unit,
)

/** 一条有限进度覆盖点击、返回、跟手和取消；收回结束前保留原图窗口的返回所有权。 */
@Composable
internal fun rememberDonationPreviewMotion(): DonationPreviewMotion {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val progress = remember { mutableFloatStateOf(if (expanded) 1f else 0f) }
    val motionEnabled = LocalComponentMotionEnabled.current && !rememberSystemReduceMotion()
    val spec = AppMotionTokens.spatialSpec<Float>()
    val scope = rememberCoroutineScope()
    val animation = remember { arrayOfNulls<Job>(1) }
    val gesture = remember { floatArrayOf(0f, 0f) } // 起始展开进度、当前速度。
    val lastSample = remember { longArrayOf(0L) }
    var trackingBack by remember { mutableStateOf(false) }
    fun settle(target: Float) {
        animation[0]?.cancel()
        trackingBack = false
        animation[0] = scope.launch {
            if (motionEnabled) {
                animate(progress.floatValue, target, initialVelocity = gesture[1], animationSpec = spec) { value, velocity ->
                    progress.floatValue = value.coerceIn(0f, 1f)
                    gesture[1] = velocity
                }
            }
            progress.floatValue = target
            gesture[1] = 0f
            if (target == 0f) { expanded = false; closing = false }
        }
    }
    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) {
            animation[0]?.cancel()
            trackingBack = false
            gesture[1] = 0f
            progress.floatValue = if (expanded && !closing) 1f else 0f
            if (closing) { expanded = false; closing = false }
        }
    }
    DisposableEffect(Unit) { onDispose { animation[0]?.cancel() } }
    return DonationPreviewMotion(
        progress = progress,
        expanded = expanded,
        predictive = motionEnabled,
        open = { expanded = true; closing = false; settle(1f) },
        close = { if (expanded && !closing) { closing = true; settle(0f) } },
        onBackStarted = {
            if (expanded) {
                animation[0]?.cancel()
                closing = false
                trackingBack = true
                gesture[0] = progress.floatValue
                gesture[1] = 0f
                lastSample[0] = System.nanoTime()
            }
        },
        onBackProgress = { backProgress ->
            if (trackingBack && motionEnabled) {
                val now = System.nanoTime()
                val next = gesture[0] * (1f - backProgress.coerceIn(0f, 1f))
                val seconds = (now - lastSample[0]) / 1_000_000_000f
                if (seconds > 0f) gesture[1] = ((next - progress.floatValue) / seconds).coerceIn(-10f, 10f)
                progress.floatValue = next
                lastSample[0] = now
            }
        },
        onBackCancelled = { if (trackingBack) { closing = false; settle(1f) } },
    )
}

internal fun donationPreviewContainerSize(compact: IntSize, preview: IntSize, progress: Float): IntSize {
    val fraction = progress.coerceIn(0f, 1f)
    return IntSize(
        (compact.width + (preview.width - compact.width) * fraction).roundToInt(),
        (compact.height + (preview.height - compact.height) * fraction).roundToInt(),
    )
}
