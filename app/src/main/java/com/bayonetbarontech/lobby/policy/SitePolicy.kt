package com.bayonetbarontech.lobby.policy

import com.bayonetbarontech.lobby.model.PlaylistRole
import java.net.URI

/**
 * Offline capture is allowed only when the source's allowsDownload flag is on.
 * That flag defaults to false and changes only through the explicit switch.
 */
object SitePolicy {
    val blockedHosts = listOf(
        "llvpn.com",
        "popads.net",
        "popads.com",
        "popcash.net",
        "popunder.net",
        "propellerads.com",
        "adsterra.com",
        "exoclick.com",
        "exosrv.com",
        "clickadu.com",
        "trafficjunky.com",
        "trafficjunky.net",
        "hilltopads.net",
        "hilltopads.com",
        "adcash.com",
        "onclickads.net",
        "admaven.com",
        "monetag.com",
        "juicyads.com",
        "trafficstars.com",
        "tsyndicate.com",
        "galaksion.com",
        "pushground.com",
        "richpush.co",
        "evadav.com",
        "adnxs.com",
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adskeeper.co.uk",
        "adskeeper.com",
        "mgid.com",
        "outbrain.com",
        "taboola.com",
        "revcontent.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "bidswitch.net",
        "criteo.com",
        "criteo.net",
        "smartadserver.com",
        "sharethrough.com",
        "moatads.com",
        "amazon-adsystem.com",
        "serving-sys.com",
        "adform.net",
        "media.net"
    )

    val dangerousExtensions = setOf(
        "apk", "ipa", "exe", "dmg", "pkg", "msi", "bat", "cmd", "scr",
        "zip", "rar", "7z", "iso", "torrent", "mobileconfig", "deb", "rpm",
        "jar", "dll", "bin", "vbs", "ps1", "sh", "crt"
    )

    val mediaExtensions = setOf(
        "m3u8", "mpd", "ts", "m4s", "mp4", "m4v", "webm", "mov", "aac", "m4a", "mp3", "vtt", "srt"
    )

    enum class Decision { ALLOW, BLOCK_POPUP }

    fun isFirstPartyHost(host: String, allowedHosts: List<String>): Boolean {
        val normalized = host.lowercase()
        return allowedHosts.any { base ->
            val root = base.lowercase()
            normalized == root || normalized.endsWith(".$root")
        }
    }

    fun isBlockedHost(host: String): Boolean {
        val normalized = host.lowercase()
        return blockedHosts.any { normalized == it || normalized.endsWith(".$it") }
    }

    fun isFirstPartyPage(url: String, allowedHosts: List<String>): Boolean {
        val uri = parse(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host ?: return false
        return isFirstPartyHost(host, allowedHosts)
    }

    fun hasDangerousExtension(url: String): Boolean =
        dangerousExtensions.contains(pathExtension(url))

    fun isKeepable(url: String, allowedHosts: List<String>): Boolean =
        isFirstPartyPage(url, allowedHosts) && !hasDangerousExtension(url)

    fun decision(url: String, mainFrame: Boolean, allowedHosts: List<String>): Decision {
        val uri = parse(url)
        val scheme = uri?.scheme?.lowercase().orEmpty()
        if (mainFrame) {
            if (scheme == "about") return Decision.ALLOW
            return if (isFirstPartyPage(url, allowedHosts)) Decision.ALLOW else Decision.BLOCK_POPUP
        }
        if (scheme !in setOf("http", "https", "about", "blob", "data")) return Decision.BLOCK_POPUP
        val host = uri?.host
        if (host != null && isBlockedHost(host)) return Decision.BLOCK_POPUP
        return Decision.ALLOW
    }

    fun pathExtension(url: String): String {
        val path = parse(url)?.path ?: return ""
        val name = path.substringAfterLast('/')
        if (!name.contains('.')) return ""
        return name.substringAfterLast('.').lowercase()
    }

    fun classifyPlaylist(url: String): PlaylistRole = PlaylistRole.classify(url)

    private fun parse(url: String): URI? = runCatching { URI(url) }.getOrNull()
}
