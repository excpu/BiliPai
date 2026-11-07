package com.android.purebilibili.feature.video.state

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlaybackErrorDetailTest {

    @Test
    fun describePlaybackErrorDetail_includesEveryCauseMessage() {
        val error = IllegalStateException(
            "Source error",
            IllegalArgumentException("Response code: 403"),
        )

        val detail = describePlaybackErrorDetail(error)

        assertContains(detail, "#0 IllegalStateException: Source error")
        assertContains(detail, "#1 IllegalArgumentException: Response code: 403")
    }

    @Test
    fun describePlaybackErrorDetail_stopsAtDepthLimit() {
        val error = RuntimeException("level0", RuntimeException("level1", RuntimeException("level2")))

        val detail = describePlaybackErrorDetail(error, maxDepth = 2)

        assertContains(detail, "level0")
        assertContains(detail, "level1")
        assertFalse(detail.contains("level2"))
    }

    @Test
    fun formatHttpFailure_keepsTrackIdentityButDropsQuerySignature() {
        val text = formatHttpFailure(
            responseCode = 403,
            host = "upos-sz-mirrorcosov.bilivideo.com",
            path = "/upgcxcode/54/06/43024910654/43024910654-1-30077.m4s",
            offset = 0L,
            length = -1L,
        )

        assertContains(text, "http=403")
        assertContains(text, "host=upos-sz-mirrorcosov.bilivideo.com")
        assertContains(text, "43024910654-1-30077.m4s")
        assertFalse(text.contains("deadline"))
    }

    @Test
    fun formatHttpFailure_fallsBackToPlaceholdersWhenUriIsUnknown() {
        val text = formatHttpFailure(200, null, null, 0L, 0L)

        assertEquals("[http=200, host=?, path=?, offset=0, length=0]", text)
    }
}
