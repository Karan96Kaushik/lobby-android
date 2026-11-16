package com.karankaushik.lobby.remote

import com.karankaushik.lobby.policy.SitePolicy
import java.net.URI

object RemoteUrl {
    fun normalize(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty() || text.length > 2048) return null
        if (!text.contains("://")) text = "https://$text"
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host.isBlank() || SitePolicy.isBlockedHost(host)) return null
        if (SitePolicy.hasDangerousExtension(text)) return null
        return text
    }
}
