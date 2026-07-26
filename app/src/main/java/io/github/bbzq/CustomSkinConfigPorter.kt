package io.github.bbzq

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads a BiliRoaming skin configuration from either a JSON file or a ZIP archive. */
object CustomSkinConfigPorter {
    sealed interface Result {
        data class Success(val json: String) : Result
        data class Failure(val reason: String) : Result
    }

    fun read(bytes: ByteArray): Result {
        if (bytes.size < 2) return Result.Failure("文件为空")
        return if (bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) readZip(bytes)
        else validate(bytes.decodeToString())
    }

    private fun readZip(bytes: ByteArray): Result = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val candidates = mutableListOf<String>()
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val text = zip.readBytesLimited(MAX_JSON_BYTES).decodeToString()
                    candidates += text
                }
                entry = zip.nextEntry
            }
            candidates.asSequence()
                .mapNotNull { (validate(it) as? Result.Success)?.json }
                .firstOrNull()
                ?.let(Result::Success)
                ?: Result.Failure("ZIP 中找不到包含 package_url 的主题配置 JSON")
        }
    }.getOrElse { Result.Failure("无法读取主题 ZIP：${it.message ?: "未知错误"}") }

    private fun validate(text: String): Result {
        val json = text.trim()
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return Result.Failure("不是有效的 JSON 配置")
        val skin = root.optJSONObject("user_equip") ?: root
        return if (skin.optLong("id") > 0 && skin.optString("package_url").isNotBlank()) {
            Result.Success(json)
        } else {
            Result.Failure("配置缺少 user_equip.id 或 package_url")
        }
    }

    private fun ZipInputStream.readBytesLimited(maxBytes: Int): ByteArray {
        val output = ArrayList<Byte>()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size + count > maxBytes) throw IllegalArgumentException("主题 JSON 过大")
            repeat(count) { output += buffer[it] }
        }
        return output.toByteArray()
    }

    private const val MAX_JSON_BYTES = 512 * 1024
}
