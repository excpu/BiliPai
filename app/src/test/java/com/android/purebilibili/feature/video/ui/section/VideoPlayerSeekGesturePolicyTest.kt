package com.android.purebilibili.feature.video.ui.section

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoPlayerSeekGesturePolicyTest {

    @Test
    fun `player seek requires horizontal dominance over vertical drift`() {
        assertTrue(
            shouldEngageHorizontalPlayerSeek(
                totalDragDistanceX = 80f,
                totalDragDistanceY = 20f,
            )
        )
        assertFalse(
            shouldEngageHorizontalPlayerSeek(
                totalDragDistanceX = 80f,
                totalDragDistanceY = 70f,
            )
        )
    }

    @Test
    fun `fullscreen fixed setting uses precise proportional range`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 240f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 10,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(6_000L, delta)
    }

    @Test
    fun `fullscreen precise range caps long drags at selected maximum`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 1200f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 10,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(10_000L, delta)
    }

    @Test
    fun `fullscreen falls back to linear when setting disabled`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = false,
            totalDragDistanceX = 110f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 15,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(22_000L, delta)
    }

    @Test
    fun `portrait uses configurable precise seek range regardless of fullscreen setting`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = false,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 50f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1.2f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(4_500L, delta)
    }

    @Test
    fun `portrait precise seek range caps long drags`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = false,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 1200f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 15,
            gestureSensitivity = 2f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(15_000L, delta)
    }

    @Test
    fun `fullscreen precise range keeps very small drags below commit threshold`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 20f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 10,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(500L, delta)
    }

    @Test
    fun `configured seek range reaches selected maximum after half screen drag`() {
        val fullscreenDelta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 400f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 60,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )
        val inlineDelta = resolveHorizontalSeekDeltaMs(
            isFullscreen = false,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 400f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = 30,
            inlineSwipeSeekSeconds = 60,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertEquals(30_000L, fullscreenDelta)
        assertEquals(60_000L, inlineDelta)
    }

    @Test
    fun `fullscreen fixed-step seek waits for loaded step setting`() {
        val delta = resolveHorizontalSeekDeltaMs(
            isFullscreen = true,
            fullscreenSwipeSeekEnabled = true,
            totalDragDistanceX = 110f,
            containerWidthPx = 800f,
            fullscreenSwipeSeekSeconds = null,
            inlineSwipeSeekSeconds = 30,
            gestureSensitivity = 1f,
            durationMs = 120_000L,
            useRelativeDuration = false,
            relativeSeekPercent = 90
        )

        assertNull(delta)
    }

    @Test
    fun `fixed seek stays precise across video durations and viewport densities`() {
        for (durationMs in listOf(60_000L, 10_800_000L)) {
            for (widthPx in listOf(720f, 1440f)) {
                assertEquals(
                    6_000L,
                    resolveHorizontalSeekDeltaMs(
                        isFullscreen = false,
                        fullscreenSwipeSeekEnabled = true,
                        totalDragDistanceX = widthPx * 0.1f,
                        containerWidthPx = widthPx,
                        fullscreenSwipeSeekSeconds = 15,
                        inlineSwipeSeekSeconds = 30,
                        gestureSensitivity = 1f,
                        durationMs = durationMs,
                        useRelativeDuration = false,
                        relativeSeekPercent = 90
                    )
                )
            }
        }
    }

    @Test
    fun `relative seek uses full width and media percentage instead of fixed ranges`() {
        for (isFullscreen in listOf(false, true)) {
            for (durationMs in listOf(120_000L, 10_800_000L)) {
                assertEquals(
                    durationMs / 16,
                    resolveHorizontalSeekDeltaMs(
                        isFullscreen = isFullscreen,
                        fullscreenSwipeSeekEnabled = false,
                        totalDragDistanceX = 200f,
                        containerWidthPx = 800f,
                        fullscreenSwipeSeekSeconds = null,
                        inlineSwipeSeekSeconds = 5,
                        gestureSensitivity = 1f,
                        durationMs = durationMs,
                        useRelativeDuration = true,
                        relativeSeekPercent = 25
                    )
                )
            }
        }
    }

    @Test
    fun `relative seek respects sensitivity and caps both drag directions`() {
        for ((dragPx, expectedMs) in listOf(100f to 15_000L, 1200f to 30_000L, -1200f to -30_000L)) {
            assertEquals(
                expectedMs,
                resolveHorizontalSeekDeltaMs(
                    isFullscreen = true,
                    fullscreenSwipeSeekEnabled = true,
                    totalDragDistanceX = dragPx,
                    containerWidthPx = 400f,
                    fullscreenSwipeSeekSeconds = 15,
                    inlineSwipeSeekSeconds = 30,
                    gestureSensitivity = 2f,
                    durationMs = 120_000L,
                    useRelativeDuration = true,
                    relativeSeekPercent = 25
                )
            )
        }
    }

    @Test
    fun `relative seek waits for a valid duration and viewport`() {
        for ((durationMs, widthPx) in listOf(0L to 800f, -1L to 800f, 120_000L to 0f)) {
            assertNull(
                resolveHorizontalSeekDeltaMs(
                    isFullscreen = true,
                    fullscreenSwipeSeekEnabled = true,
                    totalDragDistanceX = 100f,
                    containerWidthPx = widthPx,
                    fullscreenSwipeSeekSeconds = 15,
                    inlineSwipeSeekSeconds = 30,
                    gestureSensitivity = 1f,
                    durationMs = durationMs,
                    useRelativeDuration = true,
                    relativeSeekPercent = 90
                )
            )
        }
    }

    @Test
    fun `gesture seek commit requires meaningful delta`() {
        assertEquals(
            false,
            shouldCommitGestureSeek(
                currentPositionMs = 100_000L,
                targetPositionMs = 100_500L
            )
        )
        assertEquals(
            true,
            shouldCommitGestureSeek(
                currentPositionMs = 100_000L,
                targetPositionMs = 102_000L
            )
        )
    }

    @Test
    fun `seek step haptic ticks once per second of target progress`() {
        assertFalse(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 10_200L,
                currentTargetMs = 10_800L
            )
        )
        assertTrue(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 10_200L,
                currentTargetMs = 11_100L
            )
        )
        assertTrue(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 11_100L,
                currentTargetMs = 9_900L
            )
        )
        assertFalse(
            shouldTriggerSeekStepHaptic(
                previousTargetMs = 5_000L,
                currentTargetMs = 5_000L
            )
        )
    }

    @Test
    fun `velocity gain stays neutral inside the dead zone and when disabled`() {
        for (velocity in listOf(0f, 100f, -100f, 200f, -200f)) {
            assertEquals(
                1f,
                resolveSeekVelocityGain(
                    velocityPxPerSec = velocity,
                    containerWidthPx = 800f,
                    enabled = true,
                    maxGain = 3f
                ),
                "velocity $velocity should be inside the slow-swipe dead zone"
            )
        }
        assertEquals(
            1f,
            resolveSeekVelocityGain(
                velocityPxPerSec = 10_000f,
                containerWidthPx = 800f,
                enabled = false,
                maxGain = 4f
            )
        )
    }

    @Test
    fun `velocity gain rises with swipe speed and is symmetric per direction`() {
        for (velocity in listOf(800f, -800f)) {
            assertEquals(
                2.2f,
                resolveSeekVelocityGain(
                    velocityPxPerSec = velocity,
                    containerWidthPx = 800f,
                    enabled = true,
                    maxGain = 3f
                ),
                0.001f,
                "one container width per second should earn a 2.2x gain"
            )
        }
        val gentle = resolveSeekVelocityGain(400f, 800f, true, 4f)
        val brisk = resolveSeekVelocityGain(1_600f, 800f, true, 4f)
        assertTrue(gentle > 1f && brisk > gentle, "gain must increase monotonically with speed")
    }

    @Test
    fun `velocity gain respects configured ceiling and hard cap`() {
        assertEquals(
            2f,
            resolveSeekVelocityGain(
                velocityPxPerSec = 8_000f,
                containerWidthPx = 800f,
                enabled = true,
                maxGain = 2f
            )
        )
        assertEquals(
            VIDEO_PLAYER_SEEK_VELOCITY_GAIN_HARD_CAP,
            resolveSeekVelocityGain(
                velocityPxPerSec = 80_000f,
                containerWidthPx = 800f,
                enabled = true,
                maxGain = 10f
            )
        )
    }

    @Test
    fun `velocity gain falls back to neutral for unusable geometry`() {
        for (width in listOf(0f, -800f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, resolveSeekVelocityGain(10_000f, width, true, 3f))
        }
        assertEquals(1f, resolveSeekVelocityGain(Float.NaN, 800f, true, 3f))
    }

    @Test
    fun `weighted seek drag keeps progress when the finger decelerates`() {
        val estimator = SeekVelocityEstimator()
        // 首个事件只能建立时间基准，还没有速度可言。
        val baseline = accumulateVelocityWeightedSeekDrag(
            weightedDragDistanceX = 0f,
            dragAmountX = 0f,
            uptimeMs = 0L,
            containerWidthPx = 800f,
            velocityEstimator = estimator,
            velocityEnabled = true,
            velocityMaxGain = 3f
        )
        assertEquals(1f, baseline.velocityGain)

        var accumulation = accumulateVelocityWeightedSeekDrag(
            weightedDragDistanceX = baseline.weightedDragDistanceX,
            dragAmountX = 600f,
            uptimeMs = 16L,
            containerWidthPx = 800f,
            velocityEstimator = estimator,
            velocityEnabled = true,
            velocityMaxGain = 3f
        )
        assertEquals(3f, accumulation.velocityGain, 0.001f, "a fast flick should hit the ceiling")

        val distanceAfterFastFlick = accumulation.weightedDragDistanceX
        accumulation = accumulateVelocityWeightedSeekDrag(
            weightedDragDistanceX = distanceAfterFastFlick,
            dragAmountX = 20f,
            uptimeMs = 516L,
            containerWidthPx = 800f,
            velocityEstimator = estimator,
            velocityEnabled = true,
            velocityMaxGain = 3f
        )

        // 减速后倍率回落，但加权距离只能继续向前，绝不能把已经快进的进度倒着跳回去。
        assertEquals(1f, accumulation.velocityGain, 0.001f)
        assertTrue(
            accumulation.weightedDragDistanceX > distanceAfterFastFlick,
            "decelerating must not rewind the accumulated seek distance"
        )
    }

    @Test
    fun `weighted seek drag matches raw distance while velocity is disabled`() {
        val estimator = SeekVelocityEstimator()
        val accumulation = accumulateVelocityWeightedSeekDrag(
            weightedDragDistanceX = 120f,
            dragAmountX = 600f,
            uptimeMs = 16L,
            containerWidthPx = 800f,
            velocityEstimator = estimator,
            velocityEnabled = false,
            velocityMaxGain = 4f
        )

        assertEquals(1f, accumulation.velocityGain)
        assertEquals(720f, accumulation.weightedDragDistanceX)
    }

    @Test
    fun `velocity estimator adopts instantaneous speed after a pause`() {
        val estimator = SeekVelocityEstimator()
        assertEquals(0f, estimator.addDrag(uptimeMs = 0L, dragAmountX = 100f))
        // 同一时间戳的重复事件无法计算速度，保持原估计。
        assertEquals(0f, estimator.addDrag(uptimeMs = 0L, dragAmountX = 900f))
        // 短间隔走 EMA 平滑：0.35 * 2000 + 0.65 * 0
        assertEquals(700f, estimator.addDrag(uptimeMs = 50L, dragAmountX = 100f), 0.001f)
        // 间隔超过过期阈值（手指停顿）后直接采用新的瞬时速度，避免粘滞在高位。
        assertEquals(100f, estimator.addDrag(uptimeMs = 1_050L, dragAmountX = 100f), 0.001f)

        estimator.reset()
        assertEquals(0f, estimator.velocity)
    }

    @Test
    fun `velocity hint only appears once the gain actually leaves the dead zone`() {
        assertFalse(shouldShowSeekVelocityHint(1f))
        assertFalse(shouldShowSeekVelocityHint(1.02f))
        assertTrue(shouldShowSeekVelocityHint(1.05f))
    }

    @Test
    fun `velocity gain formats to one decimal`() {
        assertEquals("1.0x", formatSeekVelocityGain(1f))
        assertEquals("2.2x", formatSeekVelocityGain(2.2f))
        assertEquals("4.0x", formatSeekVelocityGain(9f))
    }
}
