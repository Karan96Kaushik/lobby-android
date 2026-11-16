package com.karankaushik.lobby.model

import java.net.URI

data class MediaSource(
    val id: String,
    val name: String,
    val home: String,
    val allowedHosts: List<String>,
    val isBuiltIn: Boolean,
    val allowsDownload: Boolean = false
) {
    val hostLabel: String
        get() = runCatching { URI(home).host?.lowercase() ?: home }.getOrDefault(home)
}

enum class PlaylistRole {
    MASTER,
    VARIANT,
    UNKNOWN;

    companion object {
        private val master = Regex(
            """(^|[\/_\-.])(master|manifest|multivariant)([\/_\-.]|$)""",
            RegexOption.IGNORE_CASE
        )
        private val variant = Regex(
            """(chunklist|rendition|variant|prog_index|index_[0-9]+|stream_[0-9]+|[\/_\-](240|360|480|540|720|1080|1440|2160)p?[\/_\-.]|audio|subtitles|captions)""",
            RegexOption.IGNORE_CASE
        )

        fun classify(url: String): PlaylistRole {
            val lower = runCatching {
                val uri = URI(url)
                buildString {
                    append(uri.path ?: "")
                    if (!uri.query.isNullOrEmpty()) {
                        append('?')
                        append(uri.query)
                    }
                }
            }.getOrDefault(url).lowercase()
            if (master.containsMatchIn(lower)) return MASTER
            if (variant.containsMatchIn(lower)) return VARIANT
            return UNKNOWN
        }
    }
}

data class HlsCandidate(
    val streamUrl: String,
    val pageUrl: String?,
    val pageTitle: String,
    val sourceId: String,
    val role: PlaylistRole
) {
    val pageOrigin: String?
        get() {
            val raw = pageUrl ?: streamUrl
            return runCatching {
                val uri = URI(raw)
                val scheme = uri.scheme?.lowercase() ?: return null
                val host = uri.host?.lowercase() ?: return null
                if (uri.port != -1) "$scheme://$host:${uri.port}/" else "$scheme://$host/"
            }.getOrNull()
        }
}

enum class DownloadQuality(
    val storageKey: String,
    val label: String,
    val shortLabel: String,
    val maxHeight: Int?
) {
    BEST("best", "Best available", "Best", null),
    P1080("p1080", "Up to 1080p", "1080p", 1080),
    P720("p720", "Up to 720p", "720p", 720),
    P480("p480", "Up to 480p", "480p", 480);

    companion object {
        fun fromStorage(raw: String?): DownloadQuality =
            entries.firstOrNull { it.storageKey == raw } ?: BEST
    }
}

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, NEEDS_REDOWNLOAD }

enum class DownloadFailureKind {
    NO_CANDIDATE,
    LIVE_STREAM,
    DRM_PROTECTED,
    EXPIRED_URL,
    FORBIDDEN_403,
    NO_DISK_SPACE,
    NETWORK_LOST,
    OTHER;

    val supportsRetryFromPage: Boolean
        get() = this == EXPIRED_URL || this == FORBIDDEN_403 || this == NO_CANDIDATE

    val supportsDirectRetry: Boolean
        get() = this == NETWORK_LOST || this == OTHER || this == NO_DISK_SPACE
}

data class DownloadItem(
    val id: String,
    val title: String,
    val sourceId: String,
    val originalUrl: String,
    val pageUrl: String?,
    val mediaUrl: String?,
    val pageOrigin: String?,
    val userAgent: String,
    val state: DownloadState,
    val progress: Double,
    val bytes: Long,
    val createdAt: Long,
    val errorMessage: String?,
    val failureKind: DownloadFailureKind?
)
