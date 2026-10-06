package io.github.bbzq.feats.hook

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the resolver's ordinary `pgc/player/web/playurl` result into the `video_info` of a
 * `pgc.gateway.player.v2.PlayViewReply`, so the host player can consume it unchanged.
 */
internal object SeaBangumiPlayCodec {
    private const val REPLY_VIDEO_INFO = 1
    private const val REPLY_PLAY_CONF = 2
    private const val REPLY_VIEW_INFO = 5

    /** Region wording used by Bilibili's blocking dialogs and errors, in both scripts. */
    private val REGION_WORDING = Regex("地区|地區")
    private val REGION_CODES = setOf(-10403, 6002003)

    /** The dialog type the host itself uses for a copyright-region refusal. */
    const val AREA_LIMIT_DIALOG = "area_limit"

    /**
     * Whether an official PGC play reply cannot be played in this region: it carries no streams at
     * all, or one of its dialogs says `area_limit`. A reply without streams is treated as refused
     * whatever the dialog says, because a resolver may still be able to serve the episode.
     */
    fun isRefusedReply(streamCount: Int, dialogTypes: List<String?>): Boolean =
        streamCount <= 0 || dialogTypes.any { it == AREA_LIMIT_DIALOG }

    fun isRegionBlocked(code: Int, message: String?): Boolean =
        code in REGION_CODES || (message != null && REGION_WORDING.containsMatchIn(message))

    /** The top-level `code` of a resolver answer, for logs; null if the body is not JSON. */
    fun answerCode(body: String): Int? = runCatching { JSONObject(body).optInt("code") }.getOrNull()

    /**
     * The stream description of a successful playurl answer that carries at least one DASH video.
     * Some endpoints wrap it in `video_info`; others put it directly under `result`.
     */
    fun playableResult(body: String): JSONObject? {
        val root = JSONObject(body)
        if (root.optInt("code", -1) != 0) return null
        val result = root.optJSONObject("result") ?: root.optJSONObject("data") ?: return null
        val node = result.optJSONObject("video_info") ?: result
        val dash = node.optJSONObject("dash") ?: return null
        return node.takeIf { dash.optJSONArray("video").objects().any { it.stream() != null } }
    }

    /**
     * Builds a reply from the official [original] one, swapping in the resolver streams and
     * dropping the blocking `view_info` dialog. With [relaxPlayLimits] the `play_conf` ability
     * switches (mini window, background play, cast...) are dropped too, which enables them all.
     * Returns null if [original] is not well-formed.
     */
    fun buildReply(
        original: ByteArray,
        result: JSONObject,
        relaxPlayLimits: Boolean = false,
        cdnHost: String? = null,
    ): ByteArray? {
        val videoInfo = encodeVideoInfo(result, cdnHost) ?: return null
        val dropped = if (relaxPlayLimits) setOf(REPLY_VIDEO_INFO, REPLY_VIEW_INFO, REPLY_PLAY_CONF)
        else setOf(REPLY_VIDEO_INFO, REPLY_VIEW_INFO)
        val rest = ProtoWire.dropFields(original, dropped) ?: return null
        return ProtoWriter().apply {
            message(REPLY_VIDEO_INFO, videoInfo)
            raw(rest)
        }.toByteArray()
    }

    /** With [cdnHost] the main address of every stream moves to that host; backups stay as given. */
    fun encodeVideoInfo(result: JSONObject, cdnHost: String? = null): ByteArray? {
        val dash = result.optJSONObject("dash") ?: return null
        val audios = dash.optJSONArray("audio").objects().mapNotNull { audio -> audio.stream(cdnHost)?.let { audio to it } }
        val bestAudio = audios.maxByOrNull { (audio, _) -> audio.optLong("bandwidth") }?.first
        val formats = result.optJSONArray("support_formats").objects().associateBy { it.optInt("quality") }
        val videos = dash.optJSONArray("video").objects().mapNotNull { video ->
            video.stream(cdnHost)?.let { video to it }
        }
        if (videos.isEmpty()) return null

        return ProtoWriter().apply {
            int(1, result.optLong("quality"))
            string(2, result.optString("format"))
            int(3, result.optLong("timelength"))
            int(4, result.optLong("video_codecid"))
            videos.forEach { (video, url) ->
                val quality = video.optInt("id")
                message(5, ProtoWriter().apply {
                    message(1, encodeStreamInfo(quality, formats[quality], result))
                    message(2, encodeDashVideo(video, url, bestAudio?.optLong("id") ?: 0L))
                }.toByteArray())
            }
            audios.forEach { (audio, url) -> message(6, encodeDashItem(audio, url)) }
            result.optJSONObject("dash")?.optJSONObject("dolby")?.let { dolby ->
                val dolbyAudios = dolby.optJSONArray("audio").objects().mapNotNull { audio -> audio.stream()?.let { audio to it } }
                if (dolby.optInt("type") != 0 && dolbyAudios.isNotEmpty()) {
                    message(7, ProtoWriter().apply {
                        int(1, dolby.optLong("type"))
                        dolbyAudios.forEach { (audio, url) -> message(2, encodeDashItem(audio, url)) }
                    }.toByteArray())
                }
            }
        }.toByteArray()
    }

    private fun encodeStreamInfo(quality: Int, format: JSONObject?, result: JSONObject): ByteArray =
        ProtoWriter().apply {
            int(1, quality.toLong())
            string(2, format?.optString("format").orEmpty().ifEmpty { result.optString("format") })
            string(3, format?.optString("description").orEmpty())
            bool(6, format?.optBoolean("need_vip") == true)
            bool(7, format?.optBoolean("need_login") == true)
            bool(8, true)
            int(10, format?.optLong("attribute") ?: 0L)
            string(11, format?.optString("new_description").orEmpty())
            string(12, format?.optString("display_desc").orEmpty())
            string(13, format?.optString("superscript").orEmpty())
        }.toByteArray()

    private fun encodeDashVideo(video: JSONObject, url: Stream, audioId: Long): ByteArray =
        ProtoWriter().apply {
            string(1, url.base)
            url.backups.forEach { string(2, it) }
            int(3, video.optLong("bandwidth"))
            int(4, video.optLong("codecid"))
            string(5, video.optString("md5"))
            int(6, video.optLong("size"))
            int(7, audioId)
            string(9, video.optString("frame_rate", video.optString("frameRate")))
            int(10, video.optLong("width"))
            int(11, video.optLong("height"))
        }.toByteArray()

    private fun encodeDashItem(item: JSONObject, url: Stream): ByteArray =
        ProtoWriter().apply {
            int(1, item.optLong("id"))
            string(2, url.base)
            url.backups.forEach { string(3, it) }
            int(4, item.optLong("bandwidth"))
            int(5, item.optLong("codecid"))
            string(6, item.optString("md5"))
            int(7, item.optLong("size"))
        }.toByteArray()

    private class Stream(val base: String, val backups: List<String>)

    /** The playable address of a DASH entry; the resolver emits both snake and camel spellings. */
    private fun JSONObject.stream(cdnHost: String? = null): Stream? {
        val original = optString("base_url").ifEmpty { optString("baseUrl") }
        if (original.isEmpty()) return null
        val base = if (cdnHost.isNullOrEmpty()) original else CustomCdnProcessor.replaceHost(original, cdnHost)
        val backupArray = optJSONArray("backup_url") ?: optJSONArray("backupUrl")
        val backups = (0 until (backupArray?.length() ?: 0)).mapNotNull { backupArray?.optString(it)?.takeIf(String::isNotEmpty) }
        return Stream(base, backups)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
