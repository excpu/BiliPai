package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.ui.layout.ContentScale
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitPagerSwitchPolicyTest {

    private fun related(
        bvid: String,
        aid: Long = 0L,
        title: String = bvid,
        ownerMid: Long = 1L,
        duration: Int = 0,
        pic: String = ""
    ): RelatedVideo {
        return RelatedVideo(
            aid = aid,
            bvid = bvid,
            title = title,
            pic = pic,
            owner = Owner(mid = ownerMid, name = "up"),
            duration = duration
        )
    }

    @Test
    fun resolveCommittedPage_returnsNullWhileScrolling() {
        val target = resolveCommittedPage(
            isScrollInProgress = true,
            currentPage = 2,
            lastCommittedPage = 1
        )

        assertNull(target)
    }

    /**
     * 竖屏上滑切换后取流 HTTP 567（B 站对非当前签名的 m4s 的拒绝码）时，pager 必须
     * 判定为「需要作废缓存重取播放地址」；纯解码类失败重试同一地址没有意义，不触发。
     */
    @Test
    fun signedUrlRecovery_triggersOnBadHttpStatusButNotDecoderErrors() {
        assertTrue(
            shouldRefreshSignedPlayUrlAfterPlaybackError(
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            )
        )
        assertFalse(
            shouldRefreshSignedPlayUrlAfterPlaybackError(
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
            )
        )
        assertFalse(
            shouldRefreshSignedPlayUrlAfterPlaybackError(
                androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
            )
        )
    }

    /**
     * 取流被拒（567）后的自愈与详情页同源：先按候选顺序轮换 CDN 线路（复用已排好序的
     * 候选，不重新请求 playurl），候选耗尽才 invalidateVideoLoadCaches 作废缓存重取签名。
     */
    @Test
    fun signedUrlRecovery_rotatesCdnCandidatesBeforeInvalidatingPlayUrlCache() {
        val source = java.io.File(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt"
        ).takeIf { it.exists() }
            ?: java.io.File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt")
        val text = source.readText()

        val errorBlock = text.substringAfter("override fun onPlayerError(error: PlaybackException) {")
            .substringBefore("override fun onPlaybackStateChanged(playbackState: Int) {")
        assertTrue(
            errorBlock.contains("shouldRefreshSignedPlayUrlAfterPlaybackError(error.errorCode)"),
            "onPlayerError 需按错误码判定是否轮换线路。",
        )
        // 监听器只登记恢复请求，不直接作废缓存（轮换优先，交给消费端与重试入口决策）。
        assertTrue(
            errorBlock.contains("signedUrlRecoveryRequest = PortraitSignedUrlRecoveryRequest("),
            "onPlayerError 应登记带 generation 的恢复请求而不是直接调用后置局部函数。",
        )
        assertFalse(
            errorBlock.contains("invalidateVideoLoadCaches"),
            "线路轮换不应作废 playurl 缓存；只有候选耗尽才作废。",
        )

        // 共享自愈入口：优先取下一个候选索引，强制重建并沿用失败快照的进度与播放意图。
        val retryBlock = text.substringAfter("fun retryFailedPortraitPlayback(")
            .substringBefore("// 消费 onPlayerError 登记的签名重取请求")
        assertTrue(retryBlock.contains("preferNextCdnCandidate: Boolean = false"))
        assertTrue(retryBlock.contains("portraitCdnCandidateIndex + 1"))
        assertTrue(retryBlock.contains("portraitCdnCandidates.size"))
        // 候选耗尽（nextIndex == null）时作废缓存重取新签名。
        assertTrue(retryBlock.contains("if (nextIndex == null && preferNextCdnCandidate)"))
        assertTrue(retryBlock.contains("VideoRepository.invalidateVideoLoadCaches"))
        assertTrue(retryBlock.contains("forceReload = true"), "自愈重播必须强制重建媒体。")
        assertTrue(retryBlock.contains("cdnCandidateIndex = nextIndex ?: 0"))

        // 消费端只重试一次：立即清空请求并校验 generation / 失败仍是本次，且要求轮换。
        val consumerBlock = text.substringAfter("LaunchedEffect(signedUrlRecoveryRequest) {")
            .substringBefore("\n    }")
        assertTrue(consumerBlock.contains("signedUrlRecoveryRequest = null"))
        assertTrue(consumerBlock.contains("request.generation != activeLoadGeneration"))
        assertTrue(consumerBlock.contains("playbackFailure?.generation != request.generation"))
        assertTrue(
            consumerBlock.contains("retryFailedPortraitPlayback(request.snapshot, preferNextCdnCandidate = true)"),
        )

        // 「重试播放」按钮与自动恢复共用同一入口，同样先轮换线路。
        val retryButtonBlock = text.substringAfter("AppButton(onClick = {\n                    val failure = playbackFailure")
            .substringBefore("AppText(\"重试播放\")")
        assertTrue(retryButtonBlock.contains("retryFailedPortraitPlayback("))
        assertTrue(retryButtonBlock.contains("preferNextCdnCandidate = true"))
    }

    /**
     * 竖屏 pager 的起播线路必须与详情页共用同一套选线解析（resolvePlaybackCdnCandidates），
     * 用户在线路设置里选的内置/自定义线路要对竖屏生效，不能只认插件重排后的第一条。
     */
    @Test
    fun portraitPlayback_usesSharedCdnSelectionWithUserPreferences() {
        val pager = java.io.File(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt"
        ).takeIf { it.exists() }
            ?: java.io.File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt")
        val text = pager.readText()

        val loadBlock = text.substringAfter("val cdnCandidates = resolvePortraitPlaybackCdnCandidates(")
        assertTrue(
            loadBlock.substringBefore("\n                        }").contains("currentPlaybackCdnSelectionSettings()"),
            "竖屏起播选线必须读取与详情页相同的线路偏好设置。",
        )
        assertTrue(
            text.contains("settings = viewModel.currentPlaybackCdnSelectionSettings()"),
            "预取/换音轨等其它竖屏选线入口同样要传入用户偏好设置。",
        )

        val policy = java.io.File(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoLoadPolicy.kt"
        ).takeIf { it.exists() }
            ?: java.io.File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoLoadPolicy.kt")
        val policyText = policy.readText()
        val selectionBlock = policyText.substringAfter("internal fun resolvePortraitPlaybackCdnCandidates(")
            .substringBefore("internal fun resolvePortraitPlaybackCdnUrls(")
        assertTrue(
            selectionBlock.contains("resolvePlaybackCdnCandidates("),
            "竖屏候选解析必须复用共享的 resolvePlaybackCdnCandidates，而不是自己只调插件重排。",
        )
    }

    @Test
    fun resolveCommittedPage_returnsPageWhenSettledAndChanged() {
        val target = resolveCommittedPage(
            isScrollInProgress = false,
            currentPage = 2,
            lastCommittedPage = 1
        )

        assertEquals(2, target)
    }

    @Test
    fun resolveCommittedPage_returnsNullWhenSettledButUnchanged() {
        val target = resolveCommittedPage(
            isScrollInProgress = false,
            currentPage = 2,
            lastCommittedPage = 2
        )

        assertNull(target)
    }

    @Test
    fun shouldHandlePortraitSeekGesture_disablesSeekWhenZoomed() {
        assertFalse(
            shouldHandlePortraitSeekGesture(scale = 1.02f)
        )
        assertTrue(
            shouldHandlePortraitSeekGesture(scale = 1f)
        )
    }

    @Test
    fun shouldHandlePortraitTapGesture_disablesTapWhenZoomed() {
        assertFalse(
            shouldHandlePortraitTapGesture(scale = 1.1f)
        )
        assertTrue(
            shouldHandlePortraitTapGesture(scale = 1f)
        )
    }

    @Test
    fun shouldEnablePortraitPagerUserScroll_blocksWhenCommentOverlayActive() {
        assertFalse(
            shouldEnablePortraitPagerUserScroll(
                scale = 1f,
                commentOverlayActive = true,
                upPreviewActive = false
            )
        )
        assertTrue(
            shouldEnablePortraitPagerUserScroll(
                scale = 1f,
                commentOverlayActive = false,
                upPreviewActive = false
            )
        )
        assertFalse(
            shouldEnablePortraitPagerUserScroll(
                scale = 1.2f,
                commentOverlayActive = false,
                upPreviewActive = false
            )
        )
    }

    @Test
    fun shouldEnablePortraitPagerUserScroll_blocksWhenUpPreviewActive() {
        assertFalse(
            shouldEnablePortraitPagerUserScroll(
                scale = 1f,
                commentOverlayActive = false,
                upPreviewActive = true
            )
        )
    }

    @Test
    fun shouldBlockPortraitPagerScrollForCommentOverlay_tracksSheetAndThreadStates() {
        assertTrue(
            shouldBlockPortraitPagerScrollForCommentOverlay(
                commentSheetVisible = false,
                subReplyVisible = true,
                commentVisibilityProgress = 0f
            )
        )
        assertTrue(
            shouldBlockPortraitPagerScrollForCommentOverlay(
                commentSheetVisible = true,
                subReplyVisible = false,
                commentVisibilityProgress = 0f
            )
        )
        assertFalse(
            shouldBlockPortraitPagerScrollForCommentOverlay(
                commentSheetVisible = false,
                subReplyVisible = false,
                commentVisibilityProgress = 0f
            )
        )
    }

    @Test
    fun shouldHandlePortraitLongPressGesture_disablesLongPressWhenZoomed() {
        assertFalse(
            shouldHandlePortraitLongPressGesture(scale = 1.08f)
        )
        assertTrue(
            shouldHandlePortraitLongPressGesture(scale = 1f)
        )
    }

    @Test
    fun shouldRestorePortraitLongPressSpeed_restoresWhenPageStopsBeingCurrent() {
        assertTrue(
            shouldRestorePortraitLongPressSpeed(
                isLongPressing = true,
                isCurrentPage = false
            )
        )
        assertFalse(
            shouldRestorePortraitLongPressSpeed(
                isLongPressing = false,
                isCurrentPage = false
            )
        )
        assertFalse(
            shouldRestorePortraitLongPressSpeed(
                isLongPressing = true,
                isCurrentPage = true
            )
        )
    }

    @Test
    fun portraitTemporaryLongPressSpeed_doesNotRouteThroughDetailViewModel() {
        val source = java.io.File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt")
            .readText()
        val longPressBlock = source
            .substringAfter("fun applyPortraitTemporaryPlaybackParameters")
            .substringBefore("// 进度调整手势")

        assertFalse(longPressBlock.contains("viewModel.applyPlaybackSpeedFromUi"))
        assertTrue(longPressBlock.contains("exoPlayer.playbackParameters = parameters"))
    }

    @Test
    fun shouldApplyLoadResult_acceptsOnlyLatestGenerationForSameVideo() {
        assertTrue(
            shouldApplyLoadResult(
                requestGeneration = 5,
                activeGeneration = 5,
                expectedBvid = "BV1xx411c7mD",
                currentPlayingBvid = "BV1xx411c7mD"
            )
        )

        assertFalse(
            shouldApplyLoadResult(
                requestGeneration = 4,
                activeGeneration = 5,
                expectedBvid = "BV1xx411c7mD",
                currentPlayingBvid = "BV1xx411c7mD"
            )
        )

        assertFalse(
            shouldApplyLoadResult(
                requestGeneration = 5,
                activeGeneration = 5,
                expectedBvid = "BV17x411w7KC",
                currentPlayingBvid = "BV1xx411c7mD"
            )
        )
    }

    @Test
    fun shouldSkipPortraitReloadForCurrentMedia_onlyWhenBvidAndMediaIdAlreadyMatch() {
        assertTrue(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = "BV1xx411c7mD"
            )
        )
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV17x411w7KC",
                currentPlayerMediaId = "BV1xx411c7mD"
            )
        )
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = " "
            )
        )
        // Multi-P: same bvid, different cid must reload.
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = "BV1xx411c7mD#1",
                targetCid = 2L,
                currentPlayingCid = 1L
            )
        )
    }

    @Test
    fun shouldSkipPortraitReloadForCurrentMedia_fallsBackToCidWhenMediaIdMissing() {
        // 内联播放器写入的条目没有 mediaId，同一条视频进入竖屏时不应整条重建。
        assertTrue(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = "",
                targetCid = 43L,
                currentPlayingCid = 43L
            )
        )
        // 同一 bvid 的不同分P仍要重新加载。
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = null,
                targetCid = 44L,
                currentPlayingCid = 43L
            )
        )
        // 换了视频必须重建。
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV17x411w7KC",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = "",
                targetCid = 43L,
                currentPlayingCid = 43L
            )
        )
    }

    @Test
    fun isPortraitPlayerMediaForVideo_acceptsMissingMediaIdAndLegacyBvidOnlyId() {
        assertTrue(
            isPortraitPlayerMediaForVideo(
                currentPlayerMediaId = "BV1xx411c7mD#43",
                expectedBvid = "BV1xx411c7mD",
                expectedCid = 43L
            )
        )
        assertFalse(
            isPortraitPlayerMediaForVideo(
                currentPlayerMediaId = "BV1xx411c7mD#44",
                expectedBvid = "BV1xx411c7mD",
                expectedCid = 43L
            )
        )
        // 未写入 mediaId 时无法区分，交给调用方给出的当前播放身份。
        assertTrue(
            isPortraitPlayerMediaForVideo(
                currentPlayerMediaId = "",
                expectedBvid = "BV1xx411c7mD",
                expectedCid = 43L
            )
        )
        assertFalse(
            isPortraitPlayerMediaForVideo(
                currentPlayerMediaId = "",
                expectedBvid = null,
                expectedCid = 43L
            )
        )
        assertTrue(
            isPortraitPlayerMediaForVideo(
                currentPlayerMediaId = "BV1xx411c7mD",
                expectedBvid = "BV1xx411c7mD",
                expectedCid = 43L,
                acceptLegacyBvidOnlyMediaId = true
            )
        )
        assertFalse(
            isPortraitPlayerMediaForVideo(
                currentPlayerMediaId = "BV1xx411c7mD",
                expectedBvid = "BV1xx411c7mD",
                expectedCid = 43L,
                acceptLegacyBvidOnlyMediaId = false
            )
        )
    }

    @Test
    fun shouldShowPortraitCover_holdsUntilFirstFrameAndStaysHiddenDuringRebuffer() {
        assertTrue(
            shouldShowPortraitCover(
                isLoading = true,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )

        assertTrue(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = false,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )

        assertTrue(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = false,
                hasRenderedFirstFrame = false
            )
        )

        assertTrue(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )

        assertFalse(
            shouldShowPortraitCover(
                isLoading = true,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun portraitCover_usesVideoViewportSizingBeforeFirstFrame() {
        assertTrue(
            shouldUseViewportBoundPortraitCover(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )
        assertFalse(
            shouldUseViewportBoundPortraitCover(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun portraitCover_fillsVerticalViewportButKeepsLandscapeCoverComplete() {
        assertEquals(
            ContentScale.Crop,
            resolvePortraitCoverContentScale(isVerticalVideo = true)
        )
        assertEquals(
            ContentScale.Fit,
            resolvePortraitCoverContentScale(isVerticalVideo = false)
        )
    }

    @Test
    fun portraitCover_usesStableFallbackAspectBeforeFirstFrame() {
        assertEquals(
            9f / 16f,
            resolvePortraitCoverViewportAspect(
                currentVideoAspect = 16f / 9f,
                hasRenderedFirstFrame = false
            )
        )
    }

    @Test
    fun portraitCover_usesRuntimeAspectAfterFirstFrame() {
        assertEquals(
            16f / 9f,
            resolvePortraitCoverViewportAspect(
                currentVideoAspect = 16f / 9f,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun shouldShowPortraitPauseIcon_hidesWhileBufferingOrLoading() {
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = true,
                isLoading = false,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = false,
                isLoading = true,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = false,
                isLoading = false,
                isSeekGesture = true
            )
        )
    }

    @Test
    fun shouldShowPortraitPauseIcon_showsOnlyWhenActuallyPaused() {
        assertTrue(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = false,
                isLoading = false,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = false,
                isPlaying = false,
                playWhenReady = false,
                isLoading = false,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = true,
                playWhenReady = true,
                isLoading = false,
                isSeekGesture = false
            )
        )
    }

    @Test
    fun resolvePortraitInitialProgressPosition_usesEntryPositionOnlyForFirstPage() {
        assertEquals(
            12000L,
            resolvePortraitInitialProgressPosition(
                isFirstPage = true,
                initialStartPositionMs = 12000L
            )
        )
        assertEquals(
            0L,
            resolvePortraitInitialProgressPosition(
                isFirstPage = false,
                initialStartPositionMs = 12000L
            )
        )
    }

    @Test
    fun shouldLoadMorePortraitRecommendations_onlyWhenNearTailAndNotAlreadyLoading() {
        assertTrue(
            shouldLoadMorePortraitRecommendations(
                committedPage = 3,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = false
            )
        )
        assertFalse(
            shouldLoadMorePortraitRecommendations(
                committedPage = 1,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = false
            )
        )
        assertFalse(
            shouldLoadMorePortraitRecommendations(
                committedPage = 3,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = true
            )
        )
        assertFalse(
            shouldLoadMorePortraitRecommendations(
                committedPage = -1,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = false
            )
        )
    }

    @Test
    fun verticalRecommendations_startLoadingBeforeTheShortFilteredFeedRunsOut() {
        assertEquals(
            4,
            resolvePortraitRecommendationPrefetchThreshold(
                onlyVerticalRecommendations = true,
            )
        )
        assertEquals(
            3,
            resolvePortraitRecommendationFetchAttemptLimit(
                onlyVerticalRecommendations = true,
            )
        )
        assertEquals(
            1,
            resolvePortraitRecommendationPrefetchThreshold(
                onlyVerticalRecommendations = false,
            )
        )
    }

    @Test
    fun mergePortraitRecommendationAppendItems_filtersDuplicatesAndCurrentVideo() {
        val appendItems = mergePortraitRecommendationAppendItems(
            currentBvid = "BV_CURRENT",
            existingBvids = setOf("BV_CURRENT", "BV_A", "BV_B"),
            existingRecommendations = emptyList(),
            fetchedRecommendations = listOf(
                related("BV_CURRENT", aid = 1L),
                related("BV_B", aid = 2L),
                related("BV_C", aid = 3L),
                related("BV_D", aid = 4L),
                related("BV_C", aid = 5L)
            )
        )

        assertEquals(listOf("BV_C", "BV_D"), appendItems.map { it.bvid })
    }

    @Test
    fun externalRecommendationAppend_keepsOnlyNewBvidsInOrder() {
        val appendItems = resolvePortraitExternalRecommendationAppendItems(
            currentInitialBvid = "BV_SEED",
            existingBvids = setOf("BV_SEED", "BV_A"),
            externalRecommendations = listOf(
                related("BV_SEED"),
                related("BV_A"),
                related("BV_B"),
                related("BV_C"),
                related("BV_B")
            )
        )

        assertEquals(listOf("BV_B", "BV_C"), appendItems.map { it.bvid })
    }

    @Test
    fun mergePortraitRecommendationAppendItems_filtersNearDuplicateContentAgainstExistingRecommendations() {
        val appendItems = mergePortraitRecommendationAppendItems(
            currentBvid = "BV_CURRENT",
            existingBvids = setOf("BV_CURRENT"),
            existingRecommendations = listOf(
                related(
                    bvid = "BV_EXISTING",
                    aid = 11L,
                    title = "勇士vs湖人 全场集锦",
                    ownerMid = 7L,
                    duration = 120
                )
            ),
            fetchedRecommendations = listOf(
                related(
                    bvid = "BV_DUPLICATE",
                    aid = 12L,
                    title = "勇士 VS 湖人 全场集锦",
                    ownerMid = 9L,
                    duration = 118
                ),
                related(
                    bvid = "BV_FRESH",
                    aid = 13L,
                    title = "猫咪踩奶名场面",
                    ownerMid = 20L,
                    duration = 45
                )
            )
        )

        assertEquals(listOf("BV_FRESH"), appendItems.map { it.bvid })
    }

    @Test
    fun mergePortraitRecommendationAppendItems_limitsDominantRecentOwner() {
        val appendItems = mergePortraitRecommendationAppendItems(
            currentBvid = "BV_CURRENT",
            existingBvids = setOf("BV_CURRENT", "BV_EXISTING_1", "BV_EXISTING_2"),
            existingRecommendations = listOf(
                related("BV_EXISTING_1", ownerMid = 9L, title = "厨房收纳技巧"),
                related("BV_EXISTING_2", ownerMid = 9L, title = "阳台植物养护")
            ),
            fetchedRecommendations = listOf(
                related("BV_SAME_OWNER", ownerMid = 9L, title = "周末城市散步"),
                related("BV_OTHER_OWNER", ownerMid = 20L, title = "猫咪睡觉观察")
            )
        )

        assertEquals(listOf("BV_OTHER_OWNER"), appendItems.map { it.bvid })
    }

    @Test
    fun portraitPagerSurfaceRecovery_runsOnlyForCurrentForegroundVideo() {
        assertTrue(
            shouldRecoverPortraitPagerSurfaceOnResume(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasPlayerView = true
            )
        )
        assertFalse(
            shouldRecoverPortraitPagerSurfaceOnResume(
                isCurrentPage = false,
                isPlayerReadyForThisVideo = true,
                hasPlayerView = true
            )
        )
        assertFalse(
            shouldRecoverPortraitPagerSurfaceOnResume(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = false,
                hasPlayerView = true
            )
        )
        assertFalse(
            shouldRecoverPortraitPagerSurfaceOnResume(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasPlayerView = false
            )
        )
    }

    @Test
    fun shufflePortraitRecommendations_isStableForSameSeed() {
        val source = listOf(
            related("BV_A"),
            related("BV_B"),
            related("BV_C"),
            related("BV_D")
        )

        val first = shufflePortraitRecommendations(seed = 42, recommendations = source)
        val second = shufflePortraitRecommendations(seed = 42, recommendations = source)

        assertEquals(first.map { it.bvid }, second.map { it.bvid })
        assertEquals(source.map { it.bvid }.sorted(), first.map { it.bvid }.sorted())
    }

    @Test
    fun portraitRecommendationSimilarity_usesHoistedTitleRegexAndCachedSignatures() {
        val source = java.io.File(
            "src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitPagerSwitchPolicy.kt"
        ).readText()

        assertTrue(source.contains("private val PORTRAIT_TITLE_BRACKET_PATTERN"))
        assertTrue(source.contains("private val PORTRAIT_TITLE_ZH_TOKEN_PATTERN"))
        assertTrue(source.contains("signatureCache.getOrPut"))
        assertFalse(source.contains("val zhTokens = Regex("))
    }

    @Test
    fun shufflePortraitRecommendations_deduplicatesBlankAndRepeatedBvids() {
        val shuffled = shufflePortraitRecommendations(
            seed = 7,
            recommendations = listOf(
                related("BV_A"),
                related(""),
                related("BV_A"),
                related("BV_B")
            )
        )

        assertEquals(listOf("BV_A", "BV_B").sorted(), shuffled.map { it.bvid }.sorted())
    }

    @Test
    fun shufflePortraitRecommendations_filtersNearDuplicateTitles() {
        val shuffled = shufflePortraitRecommendations(
            seed = 17,
            recommendations = listOf(
                related(
                    bvid = "BV_A",
                    aid = 1L,
                    title = "勇士vs湖人 全场集锦",
                    ownerMid = 1L,
                    duration = 120
                ),
                related(
                    bvid = "BV_B",
                    aid = 2L,
                    title = "勇士 VS 湖人 全场集锦",
                    ownerMid = 2L,
                    duration = 122
                ),
                related(
                    bvid = "BV_C",
                    aid = 3L,
                    title = "旅行随手拍",
                    ownerMid = 3L,
                    duration = 80
                )
            )
        )

        assertEquals(2, shuffled.size)
        assertEquals(1, shuffled.count { "湖人" in it.title })
    }

    @Test
    fun shufflePortraitRecommendations_avoidsSameOwnerAtPreviousListBoundary() {
        val shuffled = shufflePortraitRecommendations(
            seed = 1,
            recommendations = listOf(
                related(bvid = "BV_SAME", ownerMid = 7L),
                related(bvid = "BV_OTHER", ownerMid = 8L)
            ),
            precedingOwnerMid = 7L
        )

        assertEquals(8L, shuffled.first().owner.mid)
    }

    @Test
    fun toRelatedVideoForPortraitRecommendation_mapsVideoItemFields() {
        val related = toRelatedVideoForPortraitRecommendation(
            VideoItem(
                id = 80L,
                bvid = "BV1xx411c7mD",
                aid = 81L,
                cid = 82L,
                title = "sample",
                pic = "https://example.com/cover.jpg",
                owner = Owner(mid = 6L, name = "up"),
                stat = Stat(view = 10, like = 5),
                duration = 99,
                isVertical = true,
            )
        )

        assertEquals("BV1xx411c7mD", related?.bvid)
        assertEquals(81L, related?.aid)
        assertEquals(82L, related?.cid)
        assertEquals("sample", related?.title)
        assertEquals(99, related?.duration)
        assertEquals(true, related?.isVertical)
    }

    @Test
    fun resolvePortraitRecommendationAppendSeed_changesWithCurrentBvid() {
        val seedA = resolvePortraitRecommendationAppendSeed(baseSeed = 10, currentBvid = "BV_A")
        val seedB = resolvePortraitRecommendationAppendSeed(baseSeed = 10, currentBvid = "BV_B")

        assertFalse(seedA == seedB)
    }

    @Test
    fun toViewInfoForPortraitDetail_mapsCoreFieldsFromRelatedVideo() {
        val related = RelatedVideo(
            aid = 1001L,
            bvid = "BV1xK4y1d7Q1",
            title = "sample",
            pic = "https://example.com/cover.jpg",
            owner = Owner(mid = 123L, name = "up"),
            stat = Stat(view = 999, like = 77),
            duration = 65
        )

        val info = toViewInfoForPortraitDetail(related)

        assertEquals(related.bvid, info.bvid)
        assertEquals(related.aid, info.aid)
        assertEquals(related.title, info.title)
        assertEquals(related.pic, info.pic)
        assertEquals(related.owner.mid, info.owner.mid)
        assertEquals(related.stat.view, info.stat.view)
        assertEquals(65L, info.pages.firstOrNull()?.duration)
    }

    @Test
    fun requestVideoChange_insertsMissingPortraitPageInsteadOfNoOp() {
        val source = java.io.File(
            "app/src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt"
        ).takeIf { it.exists() }
            ?: java.io.File("src/main/java/com/android/purebilibili/feature/video/ui/pager/PortraitVideoPager.kt")
        val text = source.readText()

        assertTrue(
            text.contains("val jumpToPortraitPageForVideo: (String, Long, UgcSeason?) -> Unit"),
            "竖屏点视频应复用 insert-or-scroll 跳转 helper。"
        )
        assertTrue(
            text.contains("onRequestVideoChange = { targetBvid ->"),
        )
        assertTrue(
            text.contains("jumpToPortraitPageForVideo(normalizedBvid, 0L, null)"),
            "Up 预览点视频必须走 jumpToPortraitPageForVideo，不能静默 no-op。"
        )
        assertTrue(
            text.contains("onRequestCollectionItem = jumpToPortraitPageForVideo"),
            "合集点击与 Up 预览点击共用同一 insert-or-scroll 路径。"
        )
    }
}
