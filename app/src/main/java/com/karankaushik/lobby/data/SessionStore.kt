package com.karankaushik.lobby.data

import android.content.Context
import com.karankaushik.lobby.policy.SitePolicy
import org.json.JSONObject
import java.io.File

class SessionStore(context: Context) {
    private val directory = File(context.filesDir, "sessions").apply { mkdirs() }

    fun save(sourceId: String, url: String, title: String, allowedHosts: List<String>) {
        if (!SitePolicy.isKeepable(url, allowedHosts)) return
        val file = fileFor(sourceId) ?: return
        val json = JSONObject().put("url", url).put("title", title)
        file.writeText(json.toString())
    }

    fun read(sourceId: String, allowedHosts: List<String>): Record? {
        val file = fileFor(sourceId) ?: return null
        if (!file.exists()) return null
        val record = runCatching {
            val json = JSONObject(file.readText())
            Record(json.getString("url"), json.optString("title"))
        }.getOrNull() ?: return null
        if (!SitePolicy.isKeepable(record.url, allowedHosts)) return null
        return record
    }

    fun savedTitle(sourceId: String): String? {
        val file = fileFor(sourceId) ?: return null
        if (!file.exists()) return null
        val title = runCatching { JSONObject(file.readText()).optString("title") }.getOrNull()
            ?.trim()
            .orEmpty()
        return title.ifEmpty { null }
    }

    fun remove(sourceId: String) {
        fileFor(sourceId)?.delete()
    }

    fun removeAll() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(sourceId: String): File? {
        if (sourceId.isEmpty() || sourceId.length >= 80) return null
        if (!sourceId.all { it.isLetterOrDigit() || it == '-' }) return null
        return File(directory, "$sourceId.json")
    }

    data class Record(val url: String, val title: String)
}
