package io.github.bbzq.feats.hook.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure decisions for rebuilding the `/x/v2/space` answer of an account the host can no longer open
 * (deregistered, purged or banned). Keys follow the host's `BiliSpace` / `BiliSpaceHeader` fastjson
 * models; the content comes from public web endpoints that are queried by uid only.
 */
internal object SpaceFixPolicy {
    const val SPACE_PATH = "/x/v2/space"

    const val CARD_URL = "https://api.bilibili.com/x/web-interface/card"
    const val RELATION_STAT_URL = "https://api.bilibili.com/x/relation/stat"
    const val LIVE_MASTER_URL = "https://api.live.bilibili.com/live_user/v1/Master/info"

    private const val BANNER_URL = "https://www.nkbe.top/nyazygisk/banner.png"
    private const val NO_FACE_URL = "https://i0.hdslb.com/bfs/face/member/noface.jpg"
    private const val NOTICE = "该页面使用 BBZQ 修复"
    private const val SPACE_STATUS_BANNED = -2

    /** Sections the host reads as `{count, item}` and that stay empty for a rebuilt page. */
    private val EMPTY_LISTS = listOf(
        "play_game", "season", "coin_archive", "like_archive", "audios",
        "favourite2", "comic", "ugc_season", "cheese",
    )

    /** `tab` flags: which content tabs a rebuilt page offers. */
    private val TABS_ON = setOf("archive", "article", "clip", "album", "dynamic", "audios", "opus")
    private val TABS_OFF = listOf(
        "favorite", "bangumi", "coin", "like", "community", "shop", "mall",
        "ugc_season", "comic", "cheese", "sub_comic", "activity", "series",
    )

    fun isSpacePath(encodedPath: String): Boolean = encodedPath == SPACE_PATH

    /** The uid a space request asks for, or null when it is missing or not positive. */
    fun parseMid(vmid: String?): Long? = vmid?.toLongOrNull()?.takeIf { it > 0L }

    /** True when the host's own answer cannot render a space page and should be replaced. */
    fun needsFix(body: String): Boolean {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return false
        if (json.optInt("code", -1) != 0) return true
        val card = json.optJSONObject("data")?.optJSONObject("card") ?: return true
        val unusable = card.optInt("is_deleted") == 1 || card.optInt("silence") == 1
        if (!unusable) return false
        val data = json.getJSONObject("data")
        return !data.has("setting") || data.optJSONArray("tab2")?.length().let { it == null || it == 0 }
    }

    /** The `data` of a successful web answer, or null. */
    fun dataOf(response: String?): JSONObject? {
        val json = response?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return json.takeIf { it.optInt("code", -1) == 0 }?.optJSONObject("data")
    }

    /**
     * Builds the `data` of a space answer, or null when no source knows the account.
     * [card] is the web card's data. [live] and [stat] are consulted only when it is missing, since
     * purged accounts answer -404 on the card while still having live-room and relation records.
     */
    fun buildSpace(mid: Long, card: JSONObject?, live: JSONObject?, stat: JSONObject?): JSONObject? {
        val profile = card?.optJSONObject("card")
        val liveInfo = live?.optJSONObject("info")
        val roomId = live?.optLong("room_id") ?: 0L

        if (profile == null) {
            val known = !liveInfo?.optString("uname").isNullOrEmpty() ||
                roomId > 0L ||
                (stat?.optInt("follower") ?: 0) > 0 ||
                (stat?.optInt("following") ?: 0) > 0
            if (!known) return null
        }

        val following = profile?.nonNegative("attention") ?: stat?.optInt("following") ?: 0
        val followers = profile?.nonNegative("fans")
            ?: card?.nonNegative("follower")
            ?: live?.nonNegative("follower_num")
            ?: stat?.optInt("follower") ?: 0
        val status = profile?.optInt("spacesta") ?: 0
        val notice = if (status == SPACE_STATUS_BANNED) "【$NOTICE·账号封禁中】" else "【$NOTICE】"

        val member = JSONObject()
            .put("mid", mid.toString())
            .put("name", firstText(profile, "name") ?: firstText(liveInfo, "uname") ?: "账号已注销 ($mid)")
            .put("sex", firstText(profile, "sex") ?: genderOf(liveInfo))
            .put("face", firstText(profile, "face") ?: firstText(liveInfo, "face") ?: NO_FACE_URL)
            .put("rank", firstText(profile, "rank") ?: "10000")
            .put("approve", profile?.optBoolean("approve") ?: false)
            .put("regtime", profile?.optLong("regtime") ?: 0L)
            .put("spacesta", status)
            .put("fans", followers)
            .put("attention", following)
            .put("friend", profile?.optInt("friend", following) ?: following)
            .put("article", card?.optInt("article_count") ?: 0)
            .put("sign", notice + profile?.optString("sign").orEmpty())
            .put("description", NOTICE)
            .put("attentions", JSONObject.NULL)
            .put("silence", 0)
            .put("end_time", 0)
            .put("silence_url", "")
            .put("is_deleted", 0)
            .put("relation", JSONObject().put("status", 1))
            .put("likes", JSONObject().put("like_num", card?.optLong("like_num") ?: 0L).put("skr_tip", NOTICE))
            .put("pr_info", JSONObject())
            .put("profession", JSONObject())
            .put("honours", honours())
            .copyOf(profile, "DisplayRank", "birthday", "place")
            .put("level_info", levelOf(profile?.optJSONObject("level_info")))
            .put("pendant", shaped(profile?.optJSONObject("pendant"), PENDANT_SHAPE))
            .put("nameplate", shaped(profile?.optJSONObject("nameplate"), NAMEPLATE_SHAPE))
            .put("official_verify", officialOf(profile, liveInfo))
            .put("vip", vipOf(profile?.optJSONObject("vip")))

        return JSONObject()
            .put("relation", -999)
            .put("guest_relation", -999)
            .put("default_tab", "video")
            .put("is_params", true)
            .put("card", member)
            .put("setting", settings())
            .put("tab", tabs())
            .put("tab2", tabEntries())
            .put(
                "images",
                JSONObject().put("imgUrl", BANNER_URL).put("night_imgurl", BANNER_URL)
                    .put("has_garb", true).put("goods_available", true),
            )
            .put("live", liveEntry(roomId))
            .put(
                "archive",
                JSONObject()
                    .put("count", card?.optInt("archive_count") ?: 0)
                    .put("item", JSONArray())
                    .put("order", orderEntries()),
            )
            .put("series", JSONObject().put("item", JSONArray()))
            .put("article", emptySection().put("lists_count", 0).put("lists", JSONArray()))
            .put("fans_effect", JSONObject())
            .also { data -> EMPTY_LISTS.forEach { data.put(it, emptySection()) } }
    }

    /** Wraps [data] in the envelope the host expects from `/x/v2/space`. */
    fun envelope(data: JSONObject): String =
        JSONObject().put("code", 0).put("message", "OK").put("ttl", 1).put("data", data).toString()

    // --- sections ---------------------------------------------------------------------------

    /** Fixed shapes the host expects, as key to the value used when the source lacks it. */
    private val PENDANT_SHAPE = mapOf<String, Any>(
        "pid" to 0, "name" to "", "image" to "", "expire" to 0L, "image_enhance" to "", "image_enhance_frame" to "",
    )
    private val NAMEPLATE_SHAPE = mapOf<String, Any>(
        "nid" to 0, "name" to "", "image" to "", "image_small" to "", "level" to "", "condition" to "",
    )
    private val LEVEL_KEYS = listOf("current_level", "current_min", "current_exp", "identity")

    private fun emptySection() = JSONObject().put("count", 0).put("item", JSONArray())

    private fun honours() = JSONObject()
        .put("colour", JSONObject().put("dark", "#CE8620").put("normal", "#F0900B"))
        .put("tags", JSONObject.NULL)

    private fun settings() = JSONObject().apply {
        listOf(
            "fav_video", "coins_video", "likes_video", "bangumi", "played_game", "groups", "comic",
            "bbq", "dress_up", "disable_following", "close_space_medal", "only_show_wearing",
        ).forEach { put(it, 0) }
        put("live_playback", 1)
    }

    private fun tabs() = JSONObject().apply {
        TABS_ON.forEach { put(it, true) }
        TABS_OFF.forEach { put(it, false) }
    }

    private fun tabEntries() = JSONArray()
        .put(tabEntry("动态", "dynamic"))
        .put(tabEntry("投稿", "contribute").put("items", JSONArray().put(tabEntry("视频", "video"))))

    private fun tabEntry(title: String, param: String) =
        JSONObject().put("title", title).put("param", param).put("icon", "")

    private fun orderEntries() = JSONArray()
        .put(JSONObject().put("title", "最新发布").put("value", "pubdate"))
        .put(JSONObject().put("title", "最多播放").put("value", "click"))

    private fun liveEntry(roomId: Long): JSONObject {
        val on = roomId > 0L
        return JSONObject()
            .put("roomStatus", if (on) 1 else 0)
            .put("roundStatus", 0)
            .put("liveStatus", 0)
            .put("url", if (on) "https://live.bilibili.com/$roomId" else "")
            .put("roomid", roomId)
            .put("title", "")
            .put("cover", "")
            .put("link", "")
            .put("online", 0)
            .put("online_hidden", 0)
            .put("broadcast_type", 0)
    }

    private fun levelOf(src: JSONObject?) = JSONObject().apply {
        LEVEL_KEYS.forEach { put(it, src?.optInt(it) ?: 0) }
        // The host reads `next_exp` as a string and the web card sends "--" or a number.
        put("next_exp", src?.opt("next_exp")?.takeIf { it != JSONObject.NULL }?.toString() ?: "0")
    }

    /** An object with exactly the keys of [shape], each taken from [src] when it has a value. */
    private fun shaped(src: JSONObject?, shape: Map<String, Any>) = JSONObject().apply {
        shape.forEach { (key, fallback) -> put(key, src?.opt(key)?.takeIf { it != JSONObject.NULL } ?: fallback) }
    }

    private fun officialOf(profile: JSONObject?, liveInfo: JSONObject?): JSONObject {
        val verify = profile?.optJSONObject("official_verify") ?: liveInfo?.optJSONObject("official_verify")
        val official = profile?.optJSONObject("Official")
        val type = verify?.optInt("type", -1) ?: official?.optInt("type", -1) ?: -1
        val desc = firstText(verify, "desc") ?: firstText(official, "title").orEmpty()
        val role = official?.optInt("role", if (type >= 0) 1 else 0) ?: 3
        return JSONObject().put("type", type).put("desc", desc).put("title", desc).put("role", role)
    }

    /** The web card names VIP fields `type`/`status`/`due_date`; the host wants `vipType`/... */
    private fun vipOf(src: JSONObject?): JSONObject {
        val label = src?.optJSONObject("label")
        val goto = label?.opt("label_goto").let { if (it is JSONObject) it.optString("mobile") else (it as? String).orEmpty() }
        val image = firstText(label, "image")
            ?: firstText(label, "img_label_uri_hans_static")
            ?: firstText(label, "img_label_uri_hans").orEmpty()
        fun pick(host: String, web: String): Int = src?.optInt(host, src.optInt(web)) ?: 0
        return JSONObject()
            .put("vipType", pick("vipType", "type"))
            .put("vipStatus", pick("vipStatus", "status"))
            .put("themeType", pick("themeType", "theme_type"))
            .put("vipDueDate", src?.optLong("vipDueDate", src.optLong("due_date")) ?: 0L)
            .put("dueRemark", "")
            .put("accessStatus", 0)
            .put("vipStatusWarn", "")
            .put(
                "label",
                JSONObject()
                    .copyOf(label, "path", "text", "label_theme", "text_color", "bg_color", "border_color")
                    .put("bg_style", label?.optInt("bg_style") ?: 0)
                    .put("image", image)
                    .put("label_goto", goto),
            )
    }

    // --- helpers ----------------------------------------------------------------------------

    private fun genderOf(info: JSONObject?): String = when (info?.optInt("gender", -1)) {
        1 -> "男"
        2 -> "女"
        else -> "保密"
    }

    /** The first non-empty string under [key], or null. */
    private fun firstText(obj: JSONObject?, key: String): String? =
        obj?.optString(key)?.takeIf { it.isNotEmpty() }

    /** An int field that is present and not negative, else null (so `?:` can fall through). */
    private fun JSONObject.nonNegative(key: String): Int? = optInt(key, -1).takeIf { it >= 0 }

    /** Copies the string [keys] from [src] (empty string when absent). */
    private fun JSONObject.copyOf(src: JSONObject?, vararg keys: String): JSONObject {
        keys.forEach { put(it, src?.optString(it).orEmpty()) }
        return this
    }
}
