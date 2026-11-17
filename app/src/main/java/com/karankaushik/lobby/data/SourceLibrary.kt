package com.karankaushik.lobby.data

import android.content.SharedPreferences
import com.karankaushik.lobby.model.MediaSource
import com.karankaushik.lobby.policy.SitePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

class SourceLibrary(private val prefs: SharedPreferences) {
    fun load(): Pair<List<MediaSource>, MediaSource> {
        val overrides = loadOverrides()
        val builtIn = builtIns.map { source ->
            val override = overrides[source.id]
            if (override == null) source else source.copy(allowsDownload = override)
        }
        val sources = builtIn + loadCustom()
        val selectedId = prefs.getString(SELECTED_KEY, null)
        val selected = sources.firstOrNull { it.id == selectedId } ?: sources.first()
        return sources to selected
    }

    fun saveDownloadOverride(sourceId: String, allowsDownload: Boolean) {
        val overrides = loadOverrides().toMutableMap()
        overrides[sourceId] = allowsDownload
        val json = JSONObject()
        overrides.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(DOWNLOAD_OVERRIDES_KEY, json.toString()).apply()
    }

    fun saveCustom(sources: List<MediaSource>) {
        val array = JSONArray()
        sources.filter { !it.isBuiltIn }.forEach { source ->
            array.put(
                JSONObject()
                    .put("id", source.id)
                    .put("name", source.name)
                    .put("home", source.home)
                    .put("allowedHosts", JSONArray(source.allowedHosts))
                    .put("isBuiltIn", false)
                    .put("allowsDownload", source.allowsDownload)
            )
        }
        prefs.edit().putString(CUSTOM_KEY, array.toString()).apply()
    }

    fun saveSelectedId(id: String) {
        prefs.edit().putString(SELECTED_KEY, id).apply()
    }

    fun parseCustom(
        name: String,
        address: String,
        allowsDownload: Boolean = false,
        existing: List<MediaSource>
    ): Pair<MediaSource?, String?> {
        val trimmedAddress = address.trim()
        if (trimmedAddress.isEmpty()) return null to "Enter a web address."
        var raw = trimmedAddress
        if (!raw.contains("://")) raw = "https://$raw"
        val uri = runCatching { URI(raw) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.lowercase()
        if (uri == null || (scheme != "https" && scheme != "http") || host == null || !isAcceptableHost(host)) {
            return null to "Enter a full web address, like https://example.com."
        }
        if (SitePolicy.isBlockedHost(host)) return null to "That address is blocked."
        val canonical = canonicalHost(host)
        if (existing.any { covers(canonical, it) }) return null to "That source is already in the list."
        val path = if (uri.path.isNullOrEmpty()) "/" else uri.path
        val query = if (uri.query.isNullOrEmpty()) "" else "?${uri.query}"
        val home = "https://$host$path$query"
        if (runCatching { URI(home) }.isFailure) {
            return null to "Enter a full web address, like https://example.com."
        }
        val displayName = name.trim().ifEmpty { host }
        val source = MediaSource(
            id = UUID.randomUUID().toString(),
            name = displayName,
            home = home,
            allowedHosts = listOf(canonical),
            isBuiltIn = false,
            allowsDownload = allowsDownload
        )
        return source to null
    }

    private fun loadOverrides(): Map<String, Boolean> {
        val raw = prefs.getString(DOWNLOAD_OVERRIDES_KEY, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getBoolean(it) }
        }.getOrDefault(emptyMap())
    }

    private fun loadCustom(): List<MediaSource> {
        val raw = prefs.getString(CUSTOM_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    val hosts = json.getJSONArray("allowedHosts")
                    val allowed = buildList {
                        for (hostIndex in 0 until hosts.length()) add(hosts.getString(hostIndex))
                    }
                    val source = MediaSource(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        home = json.getString("home"),
                        allowedHosts = allowed,
                        isBuiltIn = false,
                        allowsDownload = json.optBoolean("allowsDownload", false)
                    )
                    if (source.home.isNotBlank() &&
                        source.allowedHosts.isNotEmpty() &&
                        source.allowedHosts.all { isAcceptableHost(it) }
                    ) {
                        add(source)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val CUSTOM_KEY = "sources.custom.v1"
        private const val SELECTED_KEY = "sources.selected.v1"
        private const val DOWNLOAD_OVERRIDES_KEY = "sources.allowsDownload.v1"

        val builtIns = listOf(
            MediaSource(
                id = "cinejoy",
                name = "Cinejoy",
                home = "https://cinejoy.pk/",
                allowedHosts = listOf("cinejoy.pk", "cinejoy.to"),
                isBuiltIn = true,
                allowsDownload = false
            ),
            MediaSource(
                id = "cinefork",
                name = "Cinefork",
                home = "https://cinefork.net/",
                allowedHosts = listOf("cinefork.net"),
                isBuiltIn = true,
                allowsDownload = false
            ),
            MediaSource(
                id = "cinecat",
                name = "Cinecat",
                home = "https://cinecat.eu/",
                allowedHosts = listOf("cinecat.eu"),
                isBuiltIn = true,
                allowsDownload = false
            ),
            MediaSource(
                id = "ghostflix",
                name = "Ghostflix",
                home = "https://ghostflix.st/",
                allowedHosts = listOf("ghostflix.st"),
                isBuiltIn = true,
                allowsDownload = false
            )
        )

        fun canonicalHost(host: String): String {
            val lower = host.lowercase()
            return if (lower.startsWith("www.")) lower.removePrefix("www.") else lower
        }

        private fun covers(host: String, source: MediaSource): Boolean {
            val canonical = canonicalHost(host)
            if (canonicalHost(source.hostLabel) == canonical) return true
            return source.allowedHosts.any { base ->
                val root = canonicalHost(base)
                canonical == root || canonical.endsWith(".$root")
            }
        }

        private fun isAcceptableHost(host: String): Boolean {
            val lower = host.lowercase()
            if (lower.isEmpty() || lower.length > 253 || lower.contains(' ')) return false
            if (!lower.contains('.')) return false
            return lower.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }
}
