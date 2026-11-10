package com.android.purebilibili.core.cache

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayUrlCachePolicyTest {
    private val nowMs = 1_700_000_000_000L
    private val maxAgeMs = 600_000L

    @Test
    fun unsignedUrlsUseTheExistingMaximumAge() {
        assertEquals(nowMs + maxAgeMs, resolvePlayUrlCacheExpiresAt(nowMs, maxAgeMs, listOf("https://example.com/video")))
    }

    @Test
    fun earliestTrackDeadlineExpiresBeforeTheUrlDoes() {
        val deadlineSec = nowMs / 1000L + 90L
        val urls = listOf("https://example.com/video?deadline=$deadlineSec", "https://example.com/audio?deadline=${deadlineSec + 30L}")
        assertEquals(nowMs + 60_000L, resolvePlayUrlCacheExpiresAt(nowMs, maxAgeMs, urls))
    }

    @Test
    fun pastDeadlinesCannotBeCachedAsFresh() {
        val urls = listOf("https://example.com/video?deadline=${nowMs / 1000L - 1L}")
        assertTrue(resolvePlayUrlCacheExpiresAt(nowMs, maxAgeMs, urls) < nowMs)
    }

    @Test
    fun malformedAndOverflowingDeadlinesDoNotCrashLoading() {
        val urls = listOf("bad url", "https://example.com/video?deadline=invalid", "https://example.com/video?deadline=9223372036854775807")
        assertEquals(nowMs + maxAgeMs, resolvePlayUrlCacheExpiresAt(nowMs, maxAgeMs, urls))
    }

    @Test
    fun serverDeadlineDoesNotExtendTheLocalMaximumAge() {
        val urls = listOf("https://example.com/video?deadline=${nowMs / 1000L + 3600L}")
        assertEquals(nowMs + maxAgeMs, resolvePlayUrlCacheExpiresAt(nowMs, maxAgeMs, urls))
    }

    /**
     * 竖屏上滑切换后取流 HTTP 567 的缓存侧根因：详情内联播放器与 portrait pager / story
     * 预取按各自目标档位（80/125/127…）分键，但首播发现请求实际都以 qn=129 拿到同一批
     * 签名地址。多份互不认识的签名会让 B 站 CDN 判第二份非法。读写与失效必须归一到
     * 规范发现键；低清直取档（≤32）保持独立，避免命中高档签名却播低清流。
     */
    @Test
    fun discoveryQualityKeysNormalizeToOneCanonicalVariant() {
        assertEquals(129, PlayUrlCache.normalizeDiscoveryQualityKey(129))
        assertEquals(129, PlayUrlCache.normalizeDiscoveryQualityKey(127))
        assertEquals(129, PlayUrlCache.normalizeDiscoveryQualityKey(125))
        assertEquals(129, PlayUrlCache.normalizeDiscoveryQualityKey(80))
        // 低清单次直取不归一。
        assertEquals(32, PlayUrlCache.normalizeDiscoveryQualityKey(32))
        assertEquals(16, PlayUrlCache.normalizeDiscoveryQualityKey(16))
        // auto（null）维持原语义。
        assertEquals(null, PlayUrlCache.normalizeDiscoveryQualityKey(null))
    }

    @Test
    fun repositoryReadsWritesAndInvalidationsShareTheNormalizedKey() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/core/cache/PlayUrlCache.kt"),
            File("src/main/java/com/android/purebilibili/core/cache/PlayUrlCache.kt"),
        ).first { it.exists() }.readText()

        // get / put / invalidate 都必须先归一再构键，防止读写分叉重新制造多签名。
        val getBlock = source.substringAfter("fun get(").substringBefore("internal fun isFallbackReuseFreshEnough")
        val putBlock = source.substringAfter("fun put(").substringBefore("fun invalidate(")
        val invalidateBlock = source.substringAfter("fun invalidate(").substringBefore("fun clear()")
        assertTrue(getBlock.contains("normalizeDiscoveryQualityKey(requestedQuality)"))
        assertTrue(putBlock.contains("normalizeDiscoveryQualityKey(quality)"))
        assertTrue(invalidateBlock.contains("normalizeDiscoveryQualityKey(requestedQuality)"))
    }

    /**
     * 兜底复用（requestedQuality=null 时跨档位取最新条目）在旧实现里只要求「未过期」。
     * deadline-30s 的余量对起播+缓冲来说太紧：接近过期的签名被拿去播放同样吃 567。
     * 兜底命中必须额外保留足够的新鲜度；精确键命中沿用原有 expiresAt 判定。
     */
    @Test
    fun fallbackReuseRequiresMoreRemainingLifetimeThanExactMatch() {
        assertTrue(PlayUrlCache.isFallbackReuseFreshEnough(remainingMs = 1_000L, exactMatch = true))
        assertFalse(PlayUrlCache.isFallbackReuseFreshEnough(remainingMs = 1_000L, exactMatch = false))
        assertTrue(PlayUrlCache.isFallbackReuseFreshEnough(remainingMs = 60_000L, exactMatch = false))
        assertFalse(PlayUrlCache.isFallbackReuseFreshEnough(remainingMs = -1L, exactMatch = false))
    }

    /**
     * 双账号会让 `accountScope` 键空间翻倍（同一 bvid 的不同账号缓存必须隔离，不能互用）。
     *
     * 因此：① 容量必须为账号变体留足余量，否则热条目被 LRU 挤掉后重进同一视频必然
     * 重新解析 playurl；② 回退查找与整视频失效不得再整表 `cache.snapshot()` 拷贝，
     * 应走二级索引只遍历该视频的变体。
     *
     * 依赖 `android.util.LruCache` 运行时，无法纯单测；此结构用例锁定上述两条不变量。
     */
    @Test
    fun cacheCapacityAndVideoIndexedLookupSurviveMultiAccountKeySpace() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/core/cache/PlayUrlCache.kt"),
            File("src/main/java/com/android/purebilibili/core/cache/PlayUrlCache.kt"),
        ).first { it.exists() }.readText()

        // 容量为双账号变体留余量（原为 80）。
        val maxSize = source.substringAfter("private const val MAX_CACHE_SIZE =")
            .substringBefore("\n").trim().toInt()
        assertTrue(maxSize >= 160, "cache capacity must absorb the extra account-scope variants, was $maxSize")

        // 二级索引存在，且被回退查找与整视频失效复用。
        assertTrue(source.contains("private val variantsByVideo"), "video-keyed index must exist")
        assertTrue(source.contains("private fun variantsFor("))
        assertTrue(source.contains("private fun indexKey("))
        assertTrue(source.contains("private fun unindexKey("))

        // 回退查找 / 整视频失效里不得再出现整表拷贝与全表前缀扫描。
        val invalidateBlock = source.substringAfter("fun invalidate(").substringBefore("fun clear()")
        val finderBlock = source.substringAfter("private fun findNewestValidCacheForVideo(")
        assertFalse(
            invalidateBlock.contains("cache.snapshot()"),
            "invalidate must use the index instead of snapshotting the whole cache",
        )
        assertFalse(
            finderBlock.contains("cache.snapshot()"),
            "fallback lookup must use the index instead of snapshotting the whole cache",
        )

        // 索引必须在 clear / put 淘汰 / 失效路径同步维护，避免无界增长。
        assertTrue(source.substringAfter("fun clear()").contains("variantsByVideo.clear()"))
        val putBlock = source.substringAfter("fun put(").substringBefore("fun invalidate(")
        assertTrue(putBlock.contains("indexKey("))
        assertTrue(putBlock.contains("unindexKey("))
    }
}
