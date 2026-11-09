package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayingPortraitDetailShadowTest {
    @Test
    fun playingPortraitHasNoShadowDuringCollapse() {
        for (progress in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(
                0f,
                resolveVideoDetailCollapseShadowAlpha(progress, playingPortraitDetailCollapse = true),
            )
        }
    }

    @Test
    fun otherLayoutsRetainExistingCollapseShadow() {
        assertEquals(0f, resolveVideoDetailCollapseShadowAlpha(0f, false), 0.0001f)
        assertEquals(0.08f, resolveVideoDetailCollapseShadowAlpha(0.5f, false), 0.0001f)
        assertEquals(0.16f, resolveVideoDetailCollapseShadowAlpha(1f, false), 0.0001f)
    }

    @Test
    fun hiddenPausedPlayerHasNoShadowButPartialCollapseKeepsIt() {
        assertEquals(0f, resolveVideoDetailCollapseShadowAlpha(1f, pausedPlayerHidden = true))
        assertEquals(0.08f, resolveVideoDetailCollapseShadowAlpha(0.5f, pausedPlayerHidden = false), 0.0001f)
        assertEquals(0.16f, resolveVideoDetailCollapseShadowAlpha(1f, pausedPlayerHidden = false), 0.0001f)
    }
}
