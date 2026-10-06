package io.github.bbzq.feats.hook

import org.json.JSONObject

/**
 * Turns the subtitle list of the resolver's `x/player/v2` answer into the `subtitle` field
 * (`VideoSubtitle`) of a `DmViewReply`.
 */
internal object SeaBangumiSubtitleCodec {
    private const val REPLY_SUBTITLE = 3

    data class Entry(
        val id: Long,
        val idStr: String,
        val lan: String,
        val lanDoc: String,
        val url: String,
        val type: Int,
        val aiType: Int,
        val aiStatus: Int,
    )

    fun parse(body: String): List<Entry> {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return emptyList()
        val items = root.optJSONObject("data")?.optJSONObject("subtitle")?.optJSONArray("subtitles")
            ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val url = normalizeUrl(item.optString("subtitle_url"))
            if (url.isEmpty()) return@mapNotNull null
            Entry(
                id = item.optLong("id"),
                idStr = item.optString("id_str"),
                lan = item.optString("lan"),
                lanDoc = item.optString("lan_doc"),
                url = url,
                type = item.optInt("type"),
                aiType = item.optInt("ai_type"),
                aiStatus = item.optInt("ai_status"),
            )
        }
    }

    /** Protocol-relative addresses such as `//i0.hdslb.com/...` are served over https. */
    fun normalizeUrl(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    /** A Chinese track when there is one, otherwise the first; the player shows it by default. */
    fun defaultEntry(entries: List<Entry>): Entry? =
        entries.firstOrNull { it.lan.startsWith("zh") } ?: entries.firstOrNull()

    fun encodeSubtitle(entries: List<Entry>): ByteArray? {
        val default = defaultEntry(entries) ?: return null
        return ProtoWriter().apply {
            string(1, default.lan)
            string(2, default.lanDoc)
            entries.forEach { entry ->
                message(3, ProtoWriter().apply {
                    int(1, entry.id)
                    string(2, entry.idStr)
                    string(3, entry.lan)
                    string(4, entry.lanDoc)
                    string(5, entry.url)
                    int(7, entry.type.toLong())
                    int(9, entry.aiType.toLong())
                    int(10, entry.aiStatus.toLong())
                }.toByteArray())
            }
        }.toByteArray()
    }

    /** The official reply with its subtitle field replaced; null if [original] is not well-formed. */
    fun replaceSubtitle(original: ByteArray, subtitle: ByteArray): ByteArray? {
        val rest = ProtoWire.dropFields(original, setOf(REPLY_SUBTITLE)) ?: return null
        return ProtoWriter().apply {
            raw(rest)
            message(REPLY_SUBTITLE, subtitle)
        }.toByteArray()
    }
}
