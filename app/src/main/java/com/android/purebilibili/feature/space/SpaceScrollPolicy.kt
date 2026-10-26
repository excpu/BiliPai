package com.android.purebilibili.feature.space

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** 渐进模糊需要内容连续穿过顶部；关闭后才启用资料折叠和标签吸顶。 */
internal fun shouldPinSpaceContentTabs(progressiveTopBlurEnabled: Boolean): Boolean =
    !progressiveTopBlurEnabled

internal fun resolveSpaceTabsPinOffsetPx(
    tabsOffsetPx: Int,
    beforeContentPaddingPx: Int,
    chromeBottomPx: Int,
): Int = (chromeBottomPx - beforeContentPaddingPx - tabsOffsetPx).coerceAtLeast(0)

/** 根据资料实际剩余高度收尾，长简介、大字号和宽屏不共用固定折叠距离。 */
internal fun resolveSpaceHeaderClosingFraction(
    remainingHeightPx: Float,
    toolbarHeightPx: Float,
): Float = (1f - remainingHeightPx / toolbarHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** 定位前先确保标签经过测量，避免首屏尚未出现的标签被当作零高度。 */
internal suspend fun LazyGridState.measureSpacePinnedTabsHeightPx(): Int {
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }?.let { return it.size.height }
    scrollToItem(1)
    return snapshotFlow {
        layoutInfo.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }?.size?.height
    }.filterNotNull().first()
}

/** 浮动排序条按实际滚动距离露出/收回，松手后保留当前露出量。 */
internal class SpaceFloatingSummaryState {
    var heightPx: Float = 0f
    var exposedHeightPx: Float by mutableFloatStateOf(0f)
        private set

    fun scrollBy(deltaPx: Float) {
        exposedHeightPx = (exposedHeightPx + deltaPx).coerceIn(0f, heightPx.coerceAtLeast(0f))
    }

    fun reset() {
        exposedHeightPx = 0f
    }
}
