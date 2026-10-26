package com.android.purebilibili.feature.home

import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.core.store.HomeBarHideType
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScrollLayoutUiRegressionTest {
    @get:Rule
    val rule = createComposeRule()

    private var hostCompositions = 0

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun refreshInsetUpdatesIndicatorWithoutRecomposingFeedOrPageHost() {
        val inset = mutableStateOf(120.dp)
        var pageCompositions = 0
        var feedCompositions = 0
        rule.setContent {
            CompositionLocalProvider(LocalAppUiStyle provides AppUiStyle.MATERIAL3) {
                MaterialTheme {
                    SideEffect { pageCompositions++ }
                    HomeFeedPullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = {},
                        state = rememberPullToRefreshState(),
                        indicatorTopInset = inset,
                        indicator = { topInset -> Text("刷新起点：${topInset.value.toInt()}") },
                    ) {
                        SideEffect { feedCompositions++ }
                        Text("首页列表")
                    }
                }
            }
        }
        rule.onNodeWithText("刷新起点：120").assertIsDisplayed()
        var initialPageCompositions = 0
        var initialFeedCompositions = 0
        rule.runOnIdle {
            initialPageCompositions = pageCompositions
            initialFeedCompositions = feedCompositions
            inset.value = 64.dp
        }
        rule.onNodeWithText("刷新起点：64").assertIsDisplayed()
        rule.onNodeWithText("首页列表").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(initialPageCompositions, pageCompositions)
            assertEquals(initialFeedCompositions, feedCompositions)
        }
    }

    @Test
    fun embeddedPaddingUpdatesKeepPageHostStableAndRespectStatusBar() {
        val headerOffset = mutableFloatStateOf(0f)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme { EmbeddedPageHost(headerOffset) }
            }
        }
        rule.onNodeWithText("顶部留白：120").assertIsDisplayed()
        var initialHostCompositions = 0
        rule.runOnIdle { initialHostCompositions = hostCompositions }
        assertTrue(initialHostCompositions > 0)

        // Existing 4 px quantization maps -3 px to -4 px.
        rule.runOnIdle { headerOffset.floatValue = -3f }
        rule.onNodeWithText("顶部留白：116").assertIsDisplayed()
        rule.runOnIdle { assertEquals(initialHostCompositions, hostCompositions) }

        rule.runOnIdle { headerOffset.floatValue = -200f }
        rule.onNodeWithText("顶部留白：24").assertIsDisplayed()
        rule.runOnIdle { assertEquals(initialHostCompositions, hostCompositions) }
    }

    @Composable
    private fun EmbeddedPageHost(headerOffset: MutableFloatState) {
        SideEffect { hostCompositions += 1 }
        val offsetProvider = remember(headerOffset) { { headerOffset.floatValue } }
        val padding = rememberHomeEmbeddedPageTopPadding(
            expandedTopPadding = 120.dp,
            statusBarHeight = 24.dp,
            tabRowHeight = 40.dp,
            tabsCollapsed = false,
            headerOffsetProvider = offsetProvider,
        )
        HomeEmbeddedPageContent(padding) { topPadding ->
            Text("顶部留白：${topPadding.value.toInt()}")
        }
    }

    @Test
    fun headerScrollKeepsFeedScrollUnconsumedAndHonorsRevealLock() {
        val headerOffset = mutableFloatStateOf(0f)
        val globalOffset = mutableFloatStateOf(0f)
        val revealLocked = mutableStateOf(false)
        var tabsCollapsed = false
        val bottomBarVisibility = mutableListOf<Boolean>()
        lateinit var connection: NestedScrollConnection
        rule.setContent {
            val pager = rememberPagerState(pageCount = { 1 })
            val grid = rememberLazyStaggeredGridState()
            connection = rememberHomeHeaderScrollConnection(
                configuration = HomeHeaderScrollConfiguration(
                    collapseEnabled = true,
                    collapseDistancePx = 100f,
                    collapseTabs = true,
                    bottomBarAutoHideEnabled = true,
                    useSideNavigation = false,
                    liquidGlassEnabled = false,
                    hideType = HomeBarHideType.SYNC,
                ),
                pagerState = pager,
                topTabEntries = listOf(HomeTopTabEntry.Category(HomeCategory.RECOMMEND)),
                activeGridState = grid,
                subscriptionListState = grid,
                revealLocked = revealLocked.value,
                headerOffsetProvider = { headerOffset.floatValue },
                globalScrollOffset = globalOffset,
                onHeaderOffsetChanged = { value, _ -> headerOffset.floatValue = value },
                onTabsCollapsedChanged = { tabsCollapsed = it },
                onBottomBarVisibleChanged = { bottomBarVisibility.add(it) },
            )
        }
        rule.runOnIdle {
            assertEquals(
                Offset.Zero,
                connection.onPreScroll(Offset(0f, -15f), NestedScrollSource.UserInput),
            )
            assertEquals(-15f, headerOffset.floatValue, 0f)
            assertTrue(tabsCollapsed)
            assertEquals(listOf(false), bottomBarVisibility)

            connection.onPreScroll(Offset(20f, -5f), NestedScrollSource.UserInput)
            assertEquals(-15f, headerOffset.floatValue, 0f)
            assertEquals(1, bottomBarVisibility.size)
            // 列表已贴顶后，尾帧剩余位移也能展开，覆盖手势与惯性两条路径。
            assertEquals(
                Offset(0f, 6f),
                connection.onPostScroll(Offset.Zero, Offset(0f, 6f), NestedScrollSource.SideEffect),
            )
            assertEquals(-9f, headerOffset.floatValue, 0f)
            assertEquals(
                Offset(0f, 9f),
                connection.onPostScroll(Offset.Zero, Offset(0f, 30f), NestedScrollSource.UserInput),
            )
            assertEquals(0f, headerOffset.floatValue, 0f)
            assertEquals(false, tabsCollapsed)
            headerOffset.floatValue = -15f
            revealLocked.value = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            connection.onPreScroll(Offset(0f, 30f), NestedScrollSource.UserInput)
            assertEquals(-15f, headerOffset.floatValue, 0f)
            assertEquals(1, bottomBarVisibility.size)
        }
    }
}
