package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal fun shouldReserveHiddenPlayerStatusBar(isFullscreen: Boolean, isPortrait: Boolean): Boolean =
    isFullscreen && isPortrait

/** 控件避让独立于视频画布；竖屏全屏隐藏系统栏后仍保留顶部挖孔与状态栏安全区。 */
@Composable
internal fun Modifier.playerControlTopSafeArea(
    isFullscreen: Boolean,
    isPortrait: Boolean,
    statusBarVisible: Boolean,
): Modifier = when {
    shouldReserveHiddenPlayerStatusBar(isFullscreen, isPortrait) -> windowInsetsPadding(
        WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top),
    )
    statusBarVisible -> windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
    else -> this
}
