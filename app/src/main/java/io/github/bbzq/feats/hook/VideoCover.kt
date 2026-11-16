package io.github.bbzq.feats.hook

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal object VideoCover {
    class Info(val url: String, val title: String)

    private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view?bvid="
    private const val SAVE_DIR = "BBZQ"
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val TIMEOUT_MS = 10_000

    private val MIME_BY_EXTENSION = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
    )

    /** https, without the `@...` suffix the CDN uses to resize. */
    fun originalUrl(pic: String): String {
        val bare = pic.substringBefore('@')
        return when {
            bare.startsWith("//") -> "https:$bare"
            bare.startsWith("http://") -> "https://" + bare.removePrefix("http://")
            else -> bare
        }
    }

    fun parseInfo(response: String): Info? {
        val data = runCatching { JSONObject(response) }.getOrNull()
            ?.takeIf { it.optInt("code", -1) == 0 }
            ?.optJSONObject("data") ?: return null
        val pic = data.optString("pic").takeIf { it.isNotBlank() } ?: return null
        return Info(originalUrl(pic), data.optString("title"))
    }

    fun extensionOf(url: String): String =
        url.substringAfterLast('.', "").lowercase().takeIf { it in MIME_BY_EXTENSION } ?: "jpg"

    fun mimeOf(extension: String): String = MIME_BY_EXTENSION[extension] ?: "image/jpeg"

    /** [cookie] is the host account's session; the API refuses anonymous requests with 412. Blocks. */
    fun fetchInfo(bvid: String, cookie: String, userAgent: String): Info? =
        get(VIEW_URL + bvid, MAX_BYTES, cookie, userAgent)?.let { parseInfo(String(it, Charsets.UTF_8)) }

    fun download(url: String, userAgent: String): ByteArray? = get(url, MAX_BYTES, cookie = "", userAgent)

    /** Saves under Pictures/BBZQ; returns the path shown to the user, or null. */
    fun saveToGallery(context: Context, bytes: ByteArray, name: String, extension: String): String? {
        val fileName = "$name.$extension"
        val mime = mimeOf(extension)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, bytes, fileName, mime)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_DIR)
                check(dir.isDirectory || dir.mkdirs()) { "cannot create $dir" }
                val file = File(dir, fileName).apply { writeBytes(bytes) }
                MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
            }
            "${Environment.DIRECTORY_PICTURES}/$SAVE_DIR/$fileName"
        }.getOrNull()
    }

    private fun saveWithMediaStore(context: Context, bytes: ByteArray, fileName: String, mime: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SAVE_DIR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused $fileName")
        try {
            (resolver.openOutputStream(uri) ?: error("cannot open $uri")).use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private fun get(address: String, limit: Int, cookie: String, userAgent: String): ByteArray? = runCatching {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Referer", "https://www.bilibili.com/")
            if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie)
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            if (conn.contentLength > limit) return@runCatching null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > limit) return@runCatching null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
