package com.android.purebilibili.feature.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaceScrollPolicyTest {
    @Test
    fun `progressive blur retains flowing tabs while disabled mode pins them`() {
        assertFalse(shouldPinSpaceContentTabs(progressiveTopBlurEnabled = true))
        assertTrue(shouldPinSpaceContentTabs(progressiveTopBlurEnabled = false))
    }

    @Test
    fun `tabs approach toolbar naturally and stop below its actual inset`() {
        assertEquals(0, resolveSpaceTabsPinOffsetPx(240, 96, 96))
        assertEquals(0, resolveSpaceTabsPinOffsetPx(0, 96, 96))
        assertEquals(40, resolveSpaceTabsPinOffsetPx(-40, 96, 96))
        // stickyHeader pins at the viewport edge, before the grid's leading padding.
        assertEquals(96, resolveSpaceTabsPinOffsetPx(-96, 96, 96))
        assertEquals(144, resolveSpaceTabsPinOffsetPx(-144, 144, 144))
    }

    @Test
    fun `long and short profiles fade only during final toolbar height`() {
        assertEquals(0f, resolveSpaceHeaderClosingFraction(640f, 56f))
        assertEquals(0f, resolveSpaceHeaderClosingFraction(56f, 56f))
        assertEquals(0.5f, resolveSpaceHeaderClosingFraction(28f, 56f))
        assertEquals(1f, resolveSpaceHeaderClosingFraction(0f, 56f))
        assertEquals(1f, resolveSpaceHeaderClosingFraction(-10f, 56f))
        assertEquals(0.5f, resolveSpaceHeaderClosingFraction(84f, 168f))
    }

    @Test
    fun `floating summary follows distance and reverses continuously without snapping`() {
        val state = SpaceFloatingSummaryState().apply { heightPx = 48f }
        state.scrollBy(-300f)
        assertEquals(0f, state.exposedHeightPx)
        state.scrollBy(18f)
        assertEquals(18f, state.exposedHeightPx)
        state.scrollBy(-7f)
        assertEquals(11f, state.exposedHeightPx)
        state.scrollBy(300f)
        assertEquals(48f, state.exposedHeightPx)
        state.scrollBy(-12f)
        assertEquals(36f, state.exposedHeightPx)
        state.reset()
        assertEquals(0f, state.exposedHeightPx)
    }

    @Test
    fun `floating summary remains hidden until measured`() {
        val state = SpaceFloatingSummaryState()
        state.scrollBy(100f)
        assertEquals(0f, state.exposedHeightPx)
    }
}
