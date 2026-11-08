package io.github.bbzq.feats.hook

import io.github.bbzq.feats.ResolverRegion
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure protobuf wire encoding & decoding for:
 * - `com.bapis.bilibili.app.viewunite.v1.ViewReq`
 * - `com.bapis.bilibili.app.viewunite.v1.ViewReply`
 * - `com.bapis.bilibili.pgc.gateway.view.v1.SeasonSectionsReq`
 * - `com.bapis.bilibili.pgc.gateway.view.v1.SeasonSectionsReply`
 */
internal object SeaBangumiViewCodec {

    class DecodedViewReq(
        val aid: Long,
        val bvid: String,
        val seasonId: Long,
        val epId: Long,
    )

    class DecodedSeasonSectionsReq(
        val aid: Long,
        val seasonId: Long,
        val epId: Long,
    )

    fun decodeViewReq(bytes: ByteArray): DecodedViewReq? {
        val fields = ProtoWire.readFields(bytes) ?: return null
        val aid = fields.firstOrNull { it.number == 1 && it.wireType == 0 }?.varint ?: 0L
        val bvid = fields.firstOrNull { it.number == 2 && it.wireType == 2 }?.string() ?: ""
        var seasonId = 0L
        var epId = 0L
        for (f in fields) {
            if (f.number == 9 && f.wireType == 2) {
                val entry = ProtoWire.readStringMapEntry(f.bytes) ?: continue
                if (entry.first == "season_id") {
                    seasonId = entry.second.toLongOrNull() ?: seasonId
                } else if (entry.first == "ep_id") {
                    epId = entry.second.toLongOrNull() ?: epId
                }
            }
        }
        return DecodedViewReq(aid = aid, bvid = bvid, seasonId = seasonId, epId = epId)
    }

    fun isViewReplyBlocked(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val fields = ProtoWire.readFields(bytes) ?: return true
        val ecode = fields.firstOrNull { it.number == 8 && it.wireType == 0 }?.varint ?: 0L
        if (ecode != 0L) return true
        val hasViewBase = fields.any { it.number == 1 && it.wireType == 2 }
        if (!hasViewBase) return true
        val hasSupplement = fields.any { it.number == 6 && it.wireType == 2 }
        return !hasSupplement
    }

    fun decodeSeasonSectionsReq(bytes: ByteArray): DecodedSeasonSectionsReq? {
        val fields = ProtoWire.readFields(bytes) ?: return null
        val aid = fields.firstOrNull { it.number == 1 && it.wireType == 0 }?.varint ?: 0L
        var seasonId = fields.firstOrNull { it.number == 2 && it.wireType == 0 }?.varint ?: 0L
        var epId = fields.firstOrNull { it.number == 4 && it.wireType == 0 }?.varint ?: 0L
        for (f in fields) {
            if (f.number == 9 && f.wireType == 2) {
                val entry = ProtoWire.readStringMapEntry(f.bytes) ?: continue
                if (entry.first == "season_id" && seasonId <= 0L) {
                    seasonId = entry.second.toLongOrNull() ?: seasonId
                } else if (entry.first == "ep_id" && epId <= 0L) {
                    epId = entry.second.toLongOrNull() ?: epId
                }
            }
        }
        return DecodedSeasonSectionsReq(aid = aid, seasonId = seasonId, epId = epId)
    }

    fun isSeasonSectionsBlocked(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        val fields = ProtoWire.readFields(bytes) ?: return true
        val sectionCount = fields.count { it.number == 1 && it.wireType == 2 }
        return sectionCount == 0
    }

    /**
     * Parses the resolver's JSON response for `/pgc/view/v2/app/season` or `/pgc/view/web/season`.
     * Records parsed episodes into [SeaBangumiSession].
     */
    fun parseSeasonJson(body: String, region: ResolverRegion? = null): JSONObject? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("code", -1) != 0) return null
        val result = root.optJSONObject("result") ?: root.optJSONObject("data") ?: return null

        val seasonId = result.optLong("season_id", 0L)
        if (seasonId > 0L && region != null) {
            SeaBangumiSession.recordSeasonJson(seasonId, body, region)
        }

        val episodes = result.optJSONArray("episodes").objects()
        episodes.forEachIndexed { index, ep ->
            val epId = ep.optLong("id", ep.optLong("ep_id", 0L))
            val aid = ep.optLong("aid", 0L)
            val cid = ep.optLong("cid", 0L)
            if (epId > 0L) {
                SeaBangumiSession.recordEpisode(
                    epId = epId,
                    seasonId = seasonId,
                    aid = aid,
                    cid = cid,
                    region = region,
                    isFirstInSeason = (index == 0),
                )
            }
        }

        val sectionArray = result.optJSONArray("section")
            ?: result.optJSONObject("modules")?.optJSONArray("section")
        sectionArray.objects().forEach { sec ->
            sec.optJSONArray("episodes").objects().forEach { ep ->
                val epId = ep.optLong("id", ep.optLong("ep_id", 0L))
                val aid = ep.optLong("aid", 0L)
                val cid = ep.optLong("cid", 0L)
                if (epId > 0L) {
                    SeaBangumiSession.recordEpisode(
                        epId = epId,
                        seasonId = seasonId,
                        aid = aid,
                        cid = cid,
                        region = region,
                        isFirstInSeason = false,
                    )
                }
            }
        }

        return result
    }

    /**
     * Encodes `com.bapis.bilibili.app.viewunite.v1.ViewReply` protobuf from season JSON.
     */
    fun buildViewReply(
        seasonJson: JSONObject,
        targetEpId: Long = 0L,
        relaxLimits: Boolean = true,
    ): ByteArray {
        val seasonId = seasonJson.optLong("season_id", 0L)
        val title = seasonJson.optString("title")
        val cover = seasonJson.optString("cover")
        val evaluate = seasonJson.optString("evaluate", seasonJson.optString("brief"))
        val totalEp = seasonJson.optLong("total", seasonJson.optLong("total_ep", 0L))
        val episodes = seasonJson.optJSONArray("episodes").objects()

        val activeEp = episodes.firstOrNull { it.optLong("id", it.optLong("ep_id", 0L)) == targetEpId }
            ?: episodes.firstOrNull()
            ?: JSONObject()

        val epId = activeEp.optLong("id", activeEp.optLong("ep_id", 0L))
        val aid = activeEp.optLong("aid", seasonJson.optLong("aid", 0L))
        val cid = activeEp.optLong("cid", 0L)
        val bvid = activeEp.optString("bvid", seasonJson.optString("bvid"))
        val duration = activeEp.optLong("duration", 0L) / 1000L

        val viewBase = ProtoWriter().apply {
            int(1, 2L) // biz_type: BIZ_TYPE_PGC = 2
            int(2, 1L) // page_type: ACTIVITY_PAGE = 1
        }.toByteArray()

        val arcRights = ProtoWriter().apply {
            int(1, 1L) // allow_download = 1
            int(3, 1L) // can_watch = 1
            int(8, 1L) // cache_auth = 1
        }.toByteArray()

        val arc = ProtoWriter().apply {
            int(1, aid)
            int(2, cid)
            int(3, duration)
            string(5, bvid)
            message(7, arcRights)
            string(8, cover)
            string(10, title)
        }.toByteArray()

        val tab = encodeTab(seasonJson, episodes, relaxLimits)
        val supplement = encodeSupplement(seasonJson, aid, relaxLimits)

        return ProtoWriter().apply {
            message(1, viewBase)
            message(2, arc)
            message(5, tab)
            message(6, supplement)
            intAlways(8, 0L) // ecode = CODE_DEFAULT (0)
        }.toByteArray()
    }

    /**
     * Encodes `com.bapis.bilibili.pgc.gateway.view.v1.SeasonSectionsReply` protobuf.
     */
    fun buildSeasonSectionsReply(
        seasonJson: JSONObject,
        relaxLimits: Boolean = true,
    ): ByteArray {
        val writer = ProtoWriter()
        val positiveEpisodes = seasonJson.optJSONArray("episodes").objects()
        if (positiveEpisodes.isNotEmpty()) {
            val sectionId = seasonJson.optLong("positive_section_id", 1L)
            val secBytes = encodeSectionData(
                id = 1L,
                sectionId = sectionId,
                title = "正片",
                episodes = positiveEpisodes,
                relaxLimits = relaxLimits,
            )
            writer.message(1, secBytes)
        }

        val extraSections = seasonJson.optJSONArray("section")
            ?: seasonJson.optJSONObject("modules")?.optJSONArray("section")
        extraSections.objects().forEachIndexed { idx, sec ->
            val secEpisodes = sec.optJSONArray("episodes").objects()
            if (secEpisodes.isNotEmpty()) {
                val secBytes = encodeSectionData(
                    id = (idx + 2).toLong(),
                    sectionId = sec.optLong("id", (idx + 2).toLong()),
                    title = sec.optString("title", "选集"),
                    episodes = secEpisodes,
                    relaxLimits = relaxLimits,
                )
                writer.message(1, secBytes)
            }
        }
        return writer.toByteArray()
    }

    private fun encodeTab(
        seasonJson: JSONObject,
        episodes: List<JSONObject>,
        relaxLimits: Boolean,
    ): ByteArray {
        val introModules = ArrayList<ByteArray>()

        // 1. OGV_TITLE module (type = 2)
        val ogvTitle = ProtoWriter().apply {
            string(1, seasonJson.optString("title"))
        }.toByteArray()
        introModules += ProtoWriter().apply {
            int(1, 2L) // ModuleType.OGV_TITLE
            message(6, ogvTitle)
        }.toByteArray()

        // 2. OGV_INTRODUCTION module (type = 1)
        val stat = seasonJson.optJSONObject("stat")
        val ogvIntro = ProtoWriter().apply {
            string(1, stat?.optString("favorites", "").orEmpty())
            string(2, seasonJson.optJSONObject("rating")?.optString("score", "").orEmpty())
            string(8, seasonJson.optString("type_name", seasonJson.optString("share_sub_title")))
            string(9, seasonJson.optJSONArray("areas").objects().joinToString(" / ") { it.optString("name") })
            string(10, seasonJson.optJSONArray("styles").objects().joinToString(" / ") { it.optString("name") })
            string(11, seasonJson.optString("alias"))
            string(12, seasonJson.optString("evaluate", seasonJson.optString("brief")))
        }.toByteArray()
        introModules += ProtoWriter().apply {
            int(1, 1L) // ModuleType.OGV_INTRODUCTION
            message(2, ogvIntro)
        }.toByteArray()

        // 3. KING_POSITION module (type = 5) with CACHE (6) button
        val kingPosList = listOf(1, 3, 4, 5, 6).map { posType ->
            ProtoWriter().apply {
                int(3, posType.toLong())
            }.toByteArray()
        }
        val kingPosModule = ProtoWriter().apply {
            kingPosList.forEach { message(1, it) }
        }.toByteArray()
        introModules += ProtoWriter().apply {
            int(1, 5L) // ModuleType.KING_POSITION
            message(4, kingPosModule)
        }.toByteArray()

        // 4. POSITIVE episodes section (type = 13)
        if (episodes.isNotEmpty()) {
            val sectionData = encodeSectionData(
                id = 1L,
                sectionId = seasonJson.optLong("positive_section_id", 1L),
                title = "选集",
                episodes = episodes,
                relaxLimits = relaxLimits,
            )
            introModules += ProtoWriter().apply {
                int(1, 13L) // ModuleType.POSITIVE
                message(12, sectionData)
            }.toByteArray()
        }

        // IntroductionTab (field 2)
        val introductionTab = ProtoWriter().apply {
            string(1, "简介")
            introModules.forEach { message(2, it) }
        }.toByteArray()

        // TabModule (field 1)
        val tabModule = ProtoWriter().apply {
            int(1, 1L) // TAB_INTRODUCTION = 1
            message(2, introductionTab)
        }.toByteArray()

        // Tab (field 5 of ViewReply)
        return ProtoWriter().apply {
            message(1, tabModule)
        }.toByteArray()
    }

    private fun encodeSectionData(
        id: Long,
        sectionId: Long,
        title: String,
        episodes: List<JSONObject>,
        relaxLimits: Boolean,
    ): ByteArray {
        val styleBytes = ProtoWriter().apply {
            int(1, 1L) // line = 1
        }.toByteArray()

        return ProtoWriter().apply {
            int(1, id)
            int(2, sectionId)
            string(3, title)
            int(4, 1L) // can_ord_desc = 1
            episodes.forEachIndexed { idx, ep ->
                message(7, encodeViewEpisode(ep, idx + 1, relaxLimits))
            }
            message(9, styleBytes)
            int(13, 0L) // type = 0
        }.toByteArray()
    }

    private fun encodeViewEpisode(ep: JSONObject, epIndex: Int, relaxLimits: Boolean): ByteArray {
        val epId = ep.optLong("id", ep.optLong("ep_id", 0L))
        val aid = ep.optLong("aid", 0L)
        val cid = ep.optLong("cid", 0L)
        val title = ep.optString("title", epIndex.toString())
        val longTitle = ep.optString("long_title")
        val showTitle = ep.optString("show_title", title)
        val cover = ep.optString("cover")
        val bvid = ep.optString("bvid")
        val link = ep.optString("link")
        val shareUrl = ep.optString("share_url")
        val duration = ep.optLong("duration", 0L) / 1000L

        val epRights = ProtoWriter().apply {
            int(1, 1L) // allow_download = 1
            int(3, 1L) // can_watch = 1
            int(5, 1L) // allow_dm = 1
            int(8, 1L) // cache_auth = 1
        }.toByteArray()

        return ProtoWriter().apply {
            int(1, epId)
            string(2, ep.optString("badge"))
            int(3, ep.optLong("badge_type", 0L))
            int(5, duration)
            int(6, if (relaxLimits) 2L else ep.optLong("status", 2L)) // status = 2 is playable
            string(7, cover)
            int(8, aid)
            string(9, title)
            string(10, ep.optString("movie_title"))
            string(11, ep.optString("subtitle"))
            string(12, longTitle)
            int(14, cid)
            string(15, "bangumi")
            string(16, shareUrl)
            string(17, ep.optString("share_copy"))
            string(18, ep.optString("short_link"))
            string(20, ep.optString("release_date"))
            message(22, epRights)
            string(24, bvid)
            string(26, link)
            int(31, epIndex.toLong())
            int(32, 1L)
            string(44, showTitle)
        }.toByteArray()
    }

    private fun encodeSupplement(seasonJson: JSONObject, aid: Long, relaxLimits: Boolean): ByteArray {
        val seasonId = seasonJson.optLong("season_id", 0L)
        val mediaId = seasonJson.optLong("media_id", 0L)
        val seasonType = seasonJson.optLong("season_type", 1L)
        val title = seasonJson.optString("title")
        val cover = seasonJson.optString("cover")
        val squareCover = seasonJson.optString("square_cover", cover)
        val shareUrl = seasonJson.optString("share_url")
        val shortLink = seasonJson.optString("short_link")
        val totalEp = seasonJson.optLong("total", seasonJson.optLong("total_ep", 0L))

        val rights = ProtoWriter().apply {
            int(1, 1L) // allow_download = 1
            int(3, 1L) // can_watch = 1
            int(16, 1L) // new_allow_download = 1
        }.toByteArray()

        val userStatus = ProtoWriter().apply {
            int(1, 1L) // show = 1
            int(2, seasonJson.optJSONObject("user_status")?.optLong("follow", 0L) ?: 0L)
            int(4, if (relaxLimits) 1L else (seasonJson.optJSONObject("user_status")?.optLong("pay", 0L) ?: 0L))
        }.toByteArray()

        val ogvData = ProtoWriter().apply {
            int(1, mediaId)
            int(2, seasonId)
            int(3, seasonType)
            int(4, seasonJson.optLong("show_season_type", seasonType))
            message(5, rights)
            message(6, userStatus)
            int(7, aid)
            int(9, 2L) // mode = 2
            int(14, totalEp)
            int(17, seasonJson.optLong("status", 2L))
            string(20, cover)
            string(21, squareCover)
            string(22, shareUrl)
            string(23, shortLink)
            string(24, title)
            int(27, 1L) // has_can_play_ep = 1
        }.toByteArray()

        val viewPgcAny = ProtoWriter().apply {
            message(1, ogvData)
        }.toByteArray()

        // google.protobuf.Any (type_url = 1, value = 2)
        return ProtoWriter().apply {
            string(1, "type.googleapis.com/bilibili.app.viewunite.pgcanymodel.ViewPgcAny")
            message(2, viewPgcAny)
        }.toByteArray()
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
