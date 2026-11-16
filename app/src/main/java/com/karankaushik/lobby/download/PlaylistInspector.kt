package com.karankaushik.lobby.download

import com.karankaushik.lobby.model.DownloadFailureKind
import java.net.URI

sealed interface PlaylistScan {
    data class Media(val url: String) : PlaylistScan
    data class Master(val variantUrl: String) : PlaylistScan
    data class Reject(val kind: DownloadFailureKind, val message: String) : PlaylistScan
}

sealed interface EncryptionStatus {
    data object Clear : EncryptionStatus
    data class Aes128(val keyUrls: List<String>) : EncryptionStatus
    data class Rejected(val message: String) : EncryptionStatus
}

object PlaylistInspector {
    fun scan(body: String, playlistUrl: String, maxHeight: Int?): PlaylistScan {
        if (!body.contains("#EXTM3U")) {
            return PlaylistScan.Reject(
                DownloadFailureKind.OTHER,
                "The candidate URL did not return a valid HLS (.m3u8) playlist."
            )
        }
        if (body.contains("#EXT-X-STREAM-INF")) {
            val variant = selectVariant(body, playlistUrl, maxHeight)
                ?: return PlaylistScan.Reject(
                    DownloadFailureKind.OTHER,
                    "Master playlist did not list any playable variant streams."
                )
            return PlaylistScan.Master(variant)
        }
        val ended = body.lineSequence().any { it.trim().startsWith("#EXT-X-ENDLIST") }
        if (!ended) {
            return PlaylistScan.Reject(
                DownloadFailureKind.LIVE_STREAM,
                "This is a live stream and can’t be downloaded. Only VOD streams with #EXT-X-ENDLIST are supported."
            )
        }
        return PlaylistScan.Media(playlistUrl)
    }

    fun encryptionStatus(body: String, playlistUrl: String): EncryptionStatus {
        val keys = mutableListOf<String>()
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("#EXT-X-KEY:") && !line.startsWith("#EXT-X-SESSION-KEY:")) continue
            val upper = line.uppercase()
            if (upper.contains("METHOD=NONE")) continue
            val widevine = upper.contains("WIDEVINE") || upper.contains("EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED")
            val fairPlay = upper.contains("COM.APPLE.STREAMINGKEYDELIVERY") ||
                upper.contains("SKD://") ||
                upper.contains("METHOD=SAMPLE-AES")
            if (widevine) {
                return EncryptionStatus.Rejected("This stream is Widevine DRM-protected and cannot be downloaded.")
            }
            if (fairPlay) {
                return EncryptionStatus.Rejected("This stream is FairPlay DRM-protected and cannot be downloaded.")
            }
            val uri = quotedAttribute(line, "URI") ?: continue
            if (uri.lowercase().startsWith("skd://")) {
                return EncryptionStatus.Rejected("This stream is FairPlay DRM-protected and cannot be downloaded.")
            }
            val resolved = resolveUrl(playlistUrl, uri)
            val lower = resolved.lowercase()
            if (lower.startsWith("skd://") || lower.contains("widevine") || lower.contains("edef8ba9")) {
                return EncryptionStatus.Rejected("This stream is DRM-protected and cannot be downloaded.")
            }
            keys += resolved
        }
        return if (keys.isEmpty()) EncryptionStatus.Clear else EncryptionStatus.Aes128(keys.distinct())
    }

    private fun selectVariant(body: String, playlistUrl: String, maxHeight: Int?): String? {
        data class Variant(val url: String, val bandwidth: Long, val height: Int?)

        val variants = mutableListOf<Variant>()
        var pending: Pair<Long, Int?>? = null
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.get(1)?.toLongOrNull()
                    ?: Regex("AVERAGE-BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.get(1)?.toLongOrNull()
                    ?: 0L
                val height = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.get(1)?.toIntOrNull()
                pending = bandwidth to height
                continue
            }
            val waiting = pending ?: continue
            if (line.isEmpty() || line.startsWith("#")) continue
            variants += Variant(resolveUrl(playlistUrl, line), waiting.first, waiting.second)
            pending = null
        }
        if (variants.isEmpty()) return null
        val eligible = if (maxHeight == null) {
            variants
        } else {
            val capped = variants.filter { it.height == null || it.height <= maxHeight }
            capped.ifEmpty {
                listOf(variants.minBy { it.height ?: Int.MAX_VALUE })
            }
        }
        return eligible.maxBy { it.bandwidth }.url
    }

    fun resolveUrl(base: String, reference: String): String =
        runCatching { URI(base).resolve(reference).toString() }.getOrDefault(reference)

    private fun quotedAttribute(line: String, name: String): String? {
        val match = Regex("$name=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(line) ?: return null
        return match.groupValues[1]
    }
}
