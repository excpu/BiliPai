package com.android.purebilibili.feature.video.ui.components

import androidx.media3.ui.AspectRatioFrameLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayingPortraitViewportAspectTest {
    @Test
    fun collapsingPortraitUsesFitWithoutChangingTheStoredPreference() {
        for (preferred in VideoAspectRatio.entries) {
            val duringCollapse = resolvePlayerViewportAspectRatio(preferred, true, false, false)
            assertEquals(VideoAspectRatio.FIT, duringCollapse)
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, duringCollapse.playerResizeMode)
            assertEquals(preferred, resolvePlayerViewportAspectRatio(preferred, false, false, false))
        }
    }

    @Test
    fun glassAndFullscreenKeepExistingAspectBehavior() {
        assertEquals(VideoAspectRatio.FIT, resolvePlayerViewportAspectRatio(VideoAspectRatio.FILL, false, true, false))
        assertEquals(VideoAspectRatio.FILL, resolvePlayerViewportAspectRatio(VideoAspectRatio.FILL, false, true, true))
    }
}
