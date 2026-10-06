package io.github.bbzq.feats.hook

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts the SEA resolver's ordinary search JSON back into polymer search `Item` protobuf,
 * and encodes the gRPC metadata the resolver forwards upstream.
 */
internal object SeaBangumiSearchCodec {
    private const val KEYWORD_MARK = "<em class=\"keyword\">"
    private val CARD_GOTOS = setOf("bangumi", "movie")

    data class ClientIdentity(
        val appId: Int,
        val build: Int,
        val buvid: String,
        val mobiApp: String,
        val device: String,
        val channel: String,
        val versionName: String,
        val brand: String,
        val model: String,
        val osver: String,
    )

    /**
     * The resolver answers an unmatched keyword with unrelated recommendations (and prepends its own
     * banner card), so only titles carrying the keyword highlight are real hits.
     */
    fun parseMatches(body: String): List<JSONObject> {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return emptyList()
        val result = root.optJSONObject("data")?.optJSONArray("result") ?: return emptyList()
        return result.objects().filter {
            it.text("title").contains(KEYWORD_MARK) && it.optLong("season_id") > 0
        }
    }

    /** Encodes [entries] as repeated `Item` occurrences of the response field [fieldNumber]. */
    fun encodeItems(fieldNumber: Int, entries: List<JSONObject>): ByteArray {
        val out = ProtoWriter()
        entries.forEach { out.message(fieldNumber, encodeItem(it)) }
        return out.toByteArray()
    }

    fun encodeItem(entry: JSONObject): ByteArray {
        val style = entry.text("style")
        val card = ProtoWriter().apply {
            string(1, entry.text("title"))
            string(2, entry.text("cover"))
            int(3, entry.optLong("media_type"))
            int(4, entry.optLong("play_state"))
            string(5, entry.text("areas", "area"))
            string(6, style)
            entry.text("styles").takeIf { it != style }?.let { string(7, it) }
            string(8, entry.text("cv"))
            double(9, entry.optJSONObject("media_score")?.number("score") ?: entry.number("rating"))
            int(10, entry.optJSONObject("media_score")?.optLong("user_count") ?: entry.optLong("vote"))
            string(11, entry.text("target"))
            string(12, entry.text("staff"))
            string(13, entry.text("prompt", "desc"))
            int(14, entry.optLong("pubtime"))
            string(15, entry.text("season_type_name"))
            entry.optJSONArray("episodes").objects().forEach { message(16, encodeEpisode(it)) }
            int(17, entry.optLong("is_selection"))
            int(18, entry.optLong("is_atten"))
            string(19, entry.text("label"))
            int(20, entry.optLong("season_id"))
            string(21, entry.text("all_net_name", "out_name"))
            string(22, entry.text("all_net_icon", "out_icon"))
            string(23, entry.text("all_net_url", "out_url"))
            entry.optJSONArray("badges").objects().forEach { message(24, encodeReasonStyle(it)) }
            int(25, entry.optLong("is_out"))
            entry.optJSONArray("episodes_new").objects().forEach { message(26, encodeEpisodeNew(it)) }
            entry.optJSONObject("watch_button")?.let { button ->
                message(27, ProtoWriter().apply {
                    string(1, button.text("title"))
                    string(2, button.text("link"))
                }.toByteArray())
            }
            string(28, entry.text("selection_style"))
            entry.optJSONObject("check_more")?.let { more ->
                message(29, ProtoWriter().apply {
                    string(1, more.text("content"))
                    string(2, more.text("uri"))
                }.toByteArray())
            }
            entry.optJSONObject("follow_button")?.let { message(30, encodeFollowButton(it)) }
            entry.optJSONObject("style_label")?.let { message(31, encodeReasonStyle(it)) }
            entry.optJSONArray("badges_v2").objects().forEach { message(32, encodeReasonStyle(it)) }
            string(33, entry.text("styles_v2"))
        }
        val goto = entry.text("goto").takeIf { it in CARD_GOTOS } ?: "bangumi"
        return ProtoWriter().apply {
            string(1, entry.text("uri", "url"))
            string(2, entry.text("param").ifEmpty { entry.optLong("season_id").toString() })
            string(3, goto)
            string(4, entry.text("linktype"))
            string(6, entry.text("trackid"))
            message(38, card.toByteArray())
        }.toByteArray()
    }

    fun encodeDevice(identity: ClientIdentity): ByteArray = ProtoWriter().apply {
        int(1, identity.appId.toLong())
        int(2, identity.build.toLong())
        string(3, identity.buvid)
        string(4, identity.mobiApp)
        string(5, "android")
        string(6, identity.device)
        string(7, identity.channel)
        string(8, identity.brand)
        string(9, identity.model)
        string(10, identity.osver)
        string(13, identity.versionName)
    }.toByteArray()

    /** Deliberately leaves out access_key: the search does not need the account. */
    fun encodeMetadata(identity: ClientIdentity): ByteArray = ProtoWriter().apply {
        string(2, identity.mobiApp)
        string(3, identity.device)
        int(4, identity.build.toLong())
        string(5, identity.channel)
        string(6, identity.buvid)
        string(7, "android")
    }.toByteArray()

    fun encodeNetwork(type: Int): ByteArray = ProtoWriter().apply { int(1, type.toLong()) }.toByteArray()

    private fun encodeEpisode(item: JSONObject): ByteArray = ProtoWriter().apply {
        string(1, item.text("uri", "url"))
        string(2, item.text("param"))
        string(3, item.text("index_title", "title"))
        item.optJSONArray("badges").objects().forEach { message(4, encodeReasonStyle(it)) }
        int(5, item.optLong("position"))
    }.toByteArray()

    private fun encodeEpisodeNew(item: JSONObject): ByteArray = ProtoWriter().apply {
        string(1, item.text("title"))
        string(2, item.text("uri", "url"))
        string(3, item.text("param"))
        int(4, item.optLong("is_new"))
        item.optJSONArray("badges").objects().forEach { message(5, encodeReasonStyle(it)) }
        int(6, item.optLong("type"))
        int(7, item.optLong("position"))
        string(8, item.text("cover"))
        string(9, item.text("label"))
    }.toByteArray()

    private fun encodeReasonStyle(item: JSONObject): ByteArray = ProtoWriter().apply {
        string(1, item.text("text"))
        string(2, item.text("text_color"))
        string(3, item.text("text_color_night"))
        string(4, item.text("bg_color"))
        string(5, item.text("bg_color_night"))
        string(6, item.text("border_color"))
        string(7, item.text("border_color_night"))
        int(8, item.optLong("bg_style"))
    }.toByteArray()

    private fun encodeFollowButton(item: JSONObject): ByteArray = ProtoWriter().apply {
        string(1, item.text("icon"))
        item.optJSONObject("texts")?.let { texts ->
            texts.keys().forEach { key ->
                message(2, ProtoWriter().apply {
                    string(1, key)
                    string(2, texts.text(key))
                }.toByteArray())
            }
        }
        string(3, item.text("status_report"))
    }.toByteArray()

    /** First non-empty string among [keys]; JSON null and non-string values count as empty. */
    private fun JSONObject.text(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> (opt(key) as? String)?.takeIf { it.isNotEmpty() } }.orEmpty()

    private fun JSONObject.number(key: String): Double =
        optDouble(key, 0.0).takeUnless { it.isNaN() } ?: 0.0

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
