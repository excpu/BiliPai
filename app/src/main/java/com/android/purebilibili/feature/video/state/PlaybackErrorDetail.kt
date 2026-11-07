package com.android.purebilibili.feature.video.state

import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException

/**
 * 把 ExoPlayer 的错误链压成一行，供诊断日志使用。
 *
 * 应用内诊断日志只会保留异常的第一行，导出的内容里只剩下 `cause=<混淆后的类名>`，
 * 既看不到具体请求，也看不到 HTTP 状态码。这里把渲染器、以及错误链上每个 cause
 * （含 `InvalidResponseCodeException` 的状态码 / host / 路径 / 区间）显式拼进同一条日志，
 * 这样即使处于 release 混淆包、只能拿到一行，也足以定位取流失败的原因。
 */
internal fun describePlaybackErrorDetail(error: Throwable, maxDepth: Int = 6): String {
    val parts = mutableListOf<String>()
    (error as? ExoPlaybackException)?.let { exo ->
        parts += "renderer=${exo.rendererName ?: "?"}"
        parts += "mime=${exo.rendererFormat?.sampleMimeType ?: "?"}"
    }
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < maxDepth) {
        val node = current
        val message = node.message?.replace('\n', ' ')?.take(180)
        val text = StringBuilder()
        text.append('#').append(depth).append(' ').append(node.javaClass.simpleName)
        if (!message.isNullOrBlank()) text.append(": ").append(message)
        val http = node as? HttpDataSource.InvalidResponseCodeException
        if (http != null) {
            text.append(' ').append(
                formatHttpFailure(
                    responseCode = http.responseCode,
                    host = http.dataSpec.uri.host,
                    path = http.dataSpec.uri.path,
                    offset = http.dataSpec.position,
                    length = http.dataSpec.length,
                )
            )
        }
        parts += text.toString()
        current = node.cause
        depth++
    }
    return parts.joinToString(" | ")
}

/**
 * 取流失败的 Http 侧关键信息。查询串里带签名和时效参数，只保留路径尾部与区间，
 * 既能区分是视频轨还是音频轨（`-1-30077` / `-1-30280`），又不会把签名写进日志。
 */
internal fun formatHttpFailure(
    responseCode: Int,
    host: String?,
    path: String?,
    offset: Long,
    length: Long,
): String {
    val shortPath = path?.takeLast(48) ?: "?"
    return "[http=$responseCode, host=${host ?: "?"}, path=$shortPath, offset=$offset, length=$length]"
}
