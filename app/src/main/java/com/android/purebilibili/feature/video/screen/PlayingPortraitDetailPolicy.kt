package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.store.PortraitPlayerCollapseMode

internal fun usePlayingPortraitDetailCollapse(
    isVerticalVideo: Boolean,
    isPlaybackPaused: Boolean,
    isLandscape: Boolean,
    isFullscreen: Boolean,
    useTabletLayout: Boolean,
    suppressDetailBody: Boolean,
): Boolean = isVerticalVideo && !isPlaybackPaused && !isLandscape &&
    !isFullscreen && !useTabletLayout && !suppressDetailBody

internal fun playingPortraitDetailCollapsedHeightDp(
    screenHeightDp: Float,
    expandedHeightDp: Float,
): Float = minOf(expandedHeightDp, screenHeightDp / 3f).coerceAtLeast(0f)

internal fun enablePhoneDetailPlayerScroll(
    playingPortraitDetailCollapse: Boolean,
    collapseMode: PortraitPlayerCollapseMode,
    selectedTabIndex: Int,
    isVerticalVideo: Boolean,
    isPlaybackPaused: Boolean,
    isCommentThreadVisible: Boolean,
): Boolean = playingPortraitDetailCollapse || shouldEnableInlinePortraitScrollTransform(
    collapseMode = collapseMode,
    selectedTabIndex = selectedTabIndex,
    isVerticalVideo = isVerticalVideo,
    isPlaybackPaused = isPlaybackPaused,
    isCommentThreadVisible = isCommentThreadVisible,
)
