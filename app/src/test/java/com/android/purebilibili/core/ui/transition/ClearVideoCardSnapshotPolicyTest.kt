package com.android.purebilibili.core.ui.transition

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClearVideoCardSnapshotPolicyTest {
    @Test
    fun clearSnapshotRunsOnlyDuringActiveTransitionWithSupportedMotion() {
        for (exposure in listOf(
            VideoCardTransitionExposure.Opening,
            VideoCardTransitionExposure.BackPreview,
            VideoCardTransitionExposure.Returning,
            VideoCardTransitionExposure.Restoring,
        )) {
            assertTrue(shouldUseClearVideoCardTransitionSnapshot(exposure, MotionTier.Normal, true, 35))
            assertFalse(shouldUseClearVideoCardTransitionSnapshot(exposure, MotionTier.Normal, false, 35))
            assertFalse(shouldUseClearVideoCardTransitionSnapshot(exposure, MotionTier.Reduced, true, 35))
            assertFalse(shouldUseClearVideoCardTransitionSnapshot(exposure, MotionTier.Normal, true, 30))
        }
        assertFalse(shouldUseClearVideoCardTransitionSnapshot(VideoCardTransitionExposure.Idle, MotionTier.Normal, true, 35))
        assertFalse(shouldUseClearVideoCardTransitionSnapshot(VideoCardTransitionExposure.SettledHidden, MotionTier.Normal, true, 35))
    }

    @Test
    fun clearSnapshotKeepsScrimButRemovesDepthEffects() {
        val clear = VideoCardTransitionBackgroundFrame(
            blurRadiusPx = 12f, scrimAlpha = 0.3f, contentScale = 0.9f,
            useLightScrimTint = true, cornerRadiusPx = 20f, shadowElevationPx = 5f,
        ).withoutDepthEffects()
        assertEquals(0.3f, clear.scrimAlpha)
        assertTrue(clear.useLightScrimTint)
        assertEquals(0f, clear.blurRadiusPx)
        assertEquals(1f, clear.contentScale)
        assertEquals(0f, clear.cornerRadiusPx)
        assertEquals(0f, clear.shadowElevationPx)
    }

    @Test
    fun hostOnlyBridgesWhenSnapshotIsAvailableAndSourceNotReady() {
        fun paint(recorded: Boolean, stale: Boolean, ready: Boolean) = shouldPaintHostOwnedDepthLayer(
            exposure = VideoCardTransitionExposure.BackPreview,
            hasRecordedContent = recorded,
            displayListStale = stale,
            motionTier = MotionTier.Normal,
            realtimeBlurEnabled = false,
            clearSnapshotEnabled = true,
            sdkInt = 35,
            sourceRendererReady = ready,
        )
        assertTrue(paint(recorded = true, stale = false, ready = false))
        assertFalse(paint(recorded = false, stale = false, ready = false))
        assertFalse(paint(recorded = true, stale = true, ready = false))
        assertFalse(paint(recorded = true, stale = false, ready = true))
    }
}
