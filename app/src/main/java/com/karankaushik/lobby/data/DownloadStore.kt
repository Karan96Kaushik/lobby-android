package com.karankaushik.lobby.data

import android.content.Context
import com.karankaushik.lobby.model.DownloadFailureKind
import com.karankaushik.lobby.model.DownloadItem
import com.karankaushik.lobby.model.DownloadState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadStore(context: Context) {
    private val file = File(context.filesDir, "downloads/downloads.json")

    @Synchronized
    fun load(): List<DownloadItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    add(fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(items: List<DownloadItem>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        val temp = File(file.parentFile, "downloads.json.tmp")
        temp.writeText(array.toString(2))
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    private fun toJson(item: DownloadItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("title", item.title)
        .put("sourceId", item.sourceId)
        .put("originalUrl", item.originalUrl)
        .put("pageUrl", item.pageUrl ?: JSONObject.NULL)
        .put("mediaUrl", item.mediaUrl ?: JSONObject.NULL)
        .put("pageOrigin", item.pageOrigin ?: JSONObject.NULL)
        .put("userAgent", item.userAgent)
        .put("state", item.state.name)
        .put("progress", item.progress)
        .put("bytes", item.bytes)
        .put("createdAt", item.createdAt)
        .put("errorMessage", item.errorMessage ?: JSONObject.NULL)
        .put("failureKind", item.failureKind?.name ?: JSONObject.NULL)

    private fun optional(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key).ifEmpty { null }
    }

    private fun fromJson(json: JSONObject): DownloadItem = DownloadItem(
        id = json.getString("id"),
        title = json.optString("title"),
        sourceId = json.optString("sourceId"),
        originalUrl = json.optString("originalUrl"),
        pageUrl = optional(json, "pageUrl"),
        mediaUrl = optional(json, "mediaUrl"),
        pageOrigin = optional(json, "pageOrigin"),
        userAgent = json.optString("userAgent"),
        state = runCatching { DownloadState.valueOf(json.getString("state")) }.getOrDefault(DownloadState.FAILED),
        progress = json.optDouble("progress", 0.0),
        bytes = json.optLong("bytes"),
        createdAt = json.optLong("createdAt"),
        errorMessage = optional(json, "errorMessage"),
        failureKind = optional(json, "failureKind")?.let {
            runCatching { DownloadFailureKind.valueOf(it) }.getOrNull()
        }
    )
}
