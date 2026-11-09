package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.store.PortraitPlayerCollapseMode
import com.android.purebilibili.feature.video.policy.reduceVideoDetailPostScroll
import com.android.purebilibili.feature.video.policy.reduceVideoDetailPreScroll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayingPortraitDetailPolicyTest {
    private fun active(
        vertical: Boolean = true,
        paused: Boolean = false,
        landscape: Boolean = false,
        fullscreen: Boolean = false,
        tablet: Boolean = false,
        suppressed: Boolean = false,
    ) = usePlayingPortraitDetailCollapse(vertical, paused, landscape, fullscreen, tablet, suppressed)

    @Test
    fun playingPortraitOnlyOnPhoneInPortraitAndOutsideFullscreen() {
        assertTrue(active())
        assertFalse(active(vertical = false))
        assertFalse(active(paused = true))
        assertFalse(active(landscape = true))
        assertFalse(active(fullscreen = true))
        assertFalse(active(tablet = true))
        assertFalse(active(suppressed = true))
    }

    @Test
    fun bothTabsCollapseFollowingFingerRegardlessOfSettingAndRestoreOnDownwardScroll() {
        val expanded = 594.75f
        val screen = 915f
        val collapsed = playingPortraitDetailCollapsedHeightDp(screen, expanded)
        val range = expanded - collapsed
        assertEquals(305f, collapsed, 0.001f)
        assertEquals(screen * 4f / 6f, screen - collapsed, 0.001f)

        for (mode in PortraitPlayerCollapseMode.entries) {
            for (tab in 0..1) {
                val enabled = enablePhoneDetailPlayerScroll(
                    playingPortraitDetailCollapse = active(),
                    collapseMode = mode,
                    selectedTabIndex = tab,
                    isVerticalVideo = true,
                    isPlaybackPaused = false,
                    isCommentThreadVisible = false,
                )
                val partial = assertNotNull(reduceVideoDetailPreScroll(0f, -100f, -range, enabled, false))
                assertEquals(-100f, partial.nextOffsetPx)
                val end = assertNotNull(reduceVideoDetailPreScroll(partial.nextOffsetPx, -1000f, -range, enabled, false))
                assertEquals(-range, end.nextOffsetPx, 0.001f)
                val restore = assertNotNull(reduceVideoDetailPostScroll(end.nextOffsetPx, 50f, -range, enabled, false))
                assertEquals(-range + 50f, restore.nextOffsetPx, 0.001f)
            }
        }
    }

    @Test
    fun smallExpandedViewportIsNotEnlargedByCollapse() {
        assertEquals(120f, playingPortraitDetailCollapsedHeightDp(460f, 120f))
    }

    @Test
    fun otherCasesKeepExistingScrollSettingsAndPausedBehavior() {
        for (mode in PortraitPlayerCollapseMode.entries) {
            for (tab in 0..1) {
                for (vertical in listOf(false, true)) {
                    for (paused in listOf(false, true)) {
                        assertEquals(
                            shouldEnableInlinePortraitScrollTransform(mode, tab, vertical, paused),
                            enablePhoneDetailPlayerScroll(false, mode, tab, vertical, paused, false),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun activePortraitCollapseDoesNotAutoPauseAndCanRecoverPreviousAutoPause() {
        assertFalse(shouldAutoPauseOnPlayerCollapse(true, true, true, playingPortraitDetailCollapse = active()))
        assertTrue(shouldAutoPauseOnPlayerCollapse(true, true, true))
        assertTrue(shouldAutoResumeOnPlayerExpand(true, true, true, playingPortraitDetailCollapse = active()))
        assertFalse(shouldAutoResumeOnPlayerExpand(true, true, false, playingPortraitDetailCollapse = active()))
    }
}
