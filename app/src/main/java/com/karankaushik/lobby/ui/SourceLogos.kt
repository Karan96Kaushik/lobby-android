package com.karankaushik.lobby.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

internal object SourceLogos {
    private val memory = HashMap<String, Bitmap?>()
    private val lock = Any()

    fun peek(home: String): Bitmap? {
        val key = origin(home) ?: return null
        synchronized(lock) { return memory[key] }
    }

    suspend fun load(context: Context, home: String): Bitmap? = withContext(Dispatchers.IO) {
        val key = origin(home) ?: return@withContext null
        synchronized(lock) {
            if (memory.containsKey(key)) return@withContext memory[key]
        }
        val file = File(File(context.cacheDir, "source-logos"), fileName(key))
        decodeFile(file)?.let { cached ->
            synchronized(lock) { memory[key] = cached }
            return@withContext cached
        }
        val logo = fetch(key)
        if (logo != null) {
            runCatching {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { out -> logo.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
        }
        synchronized(lock) { memory[key] = logo }
        logo
    }

    private fun fetch(origin: String): Bitmap? {
        val base = URI("$origin/")
        val html = read(base, maxBytes = 96 * 1024, html = true)?.let { String(it, Charsets.UTF_8) }
        val icons = if (html != null) icons(html, base) else emptyList()
        val fallbacks = listOf("/apple-touch-icon.png", "/favicon.png", "/favicon.ico")
            .mapNotNull { path -> runCatching { URI(origin + path) }.getOrNull() }
        val urls = (icons.map { it.uri } + fallbacks).distinctBy { it.toString() }
        for (url in urls) {
            decodeBytes(read(url, maxBytes = 1_500_000, html = false))?.let { return it }
        }
        return null
    }

    private fun icons(html: String, base: URI): List<Icon> {
        return linkTag.findAll(html).mapNotNull { match ->
            val tag = match.value
            val rel = attr(tag, "rel") ?: return@mapNotNull null
            if (!isLogoRel(rel)) return@mapNotNull null
            val type = attr(tag, "type")?.lowercase().orEmpty()
            if ("svg" in type) return@mapNotNull null
            val href = attr(tag, "href") ?: return@mapNotNull null
            if (href.substringBefore('?').lowercase().endsWith(".svg")) return@mapNotNull null
            val resolved = runCatching { base.resolve(href.trim()) }.getOrNull() ?: return@mapNotNull null
            if (resolved.scheme?.lowercase() != "https") return@mapNotNull null
            val sizes = attr(tag, "sizes")
            val size = when {
                rel.contains("apple-touch-icon") && sizes == null -> 180
                sizes == null -> 32
                sizes.equals("any", ignoreCase = true) -> 48
                else -> sizes.substringBefore('x').toIntOrNull() ?: 32
            }
            Icon(resolved, size)
        }.sortedByDescending { it.size }.toList()
    }

    private fun isLogoRel(rel: String): Boolean {
        val tokens = rel.lowercase().split(Regex("\\s+")).toSet()
        if ("icon" in tokens) return true
        return tokens.any { it == "apple-touch-icon" || it == "apple-touch-icon-precomposed" }
    }

    private fun attr(tag: String, name: String): String? {
        val match = Regex(
            """\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
            RegexOption.IGNORE_CASE
        ).find(tag) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
    }

    private fun read(uri: URI, maxBytes: Int, html: Boolean): ByteArray? {
        if (uri.scheme?.lowercase() != "https") return null
        return runCatching {
            val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty(
                    "Accept",
                    if (html) "text/html,application/xhtml+xml" else "image/png,image/jpeg,image/webp,image/x-icon,*/*;q=0.5"
                )
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val type = connection.contentType?.lowercase().orEmpty()
                if (!html && ("text/html" in type || "svg" in type)) return@runCatching null
                connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (total < maxBytes) {
                        val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.toByteArray().takeIf { it.isNotEmpty() }
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun decodeFile(file: File): Bitmap? {
        if (!file.isFile) return null
        return decodeBytes(runCatching { file.readBytes() }.getOrNull()) ?: run {
            file.delete()
            null
        }
    }

    private fun decodeBytes(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.size < 16) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < 8 || bounds.outHeight < 8) return null
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun origin(home: String): String? {
        val uri = runCatching { URI(home) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""
        return "https://$host$port"
    }

    private fun fileName(origin: String): String {
        val host = origin.removePrefix("https://").replace(Regex("[^a-z0-9.-]"), "_")
        return "$host.png"
    }

    private data class Icon(val uri: URI, val size: Int)

    private val linkTag = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
}
