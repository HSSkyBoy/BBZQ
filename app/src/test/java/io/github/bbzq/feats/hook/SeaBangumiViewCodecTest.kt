package io.github.bbzq.feats.hook

import io.github.bbzq.feats.ResolverRegion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaBangumiViewCodecTest {

    private val sampleSeasonJson = """
    {
        "code": 0,
        "message": "success",
        "result": {
            "season_id": 28747,
            "media_id": 28228345,
            "season_type": 1,
            "title": "进击的巨人 最终季",
            "cover": "http://i0.hdslb.com/bfs/bangumi/image/cover.jpg",
            "evaluate": "调查兵团来到了大陆...",
            "total": 16,
            "episodes": [
                {
                    "id": 348123,
                    "aid": 111111,
                    "cid": 222222,
                    "title": "1",
                    "long_title": "海的彼岸",
                    "cover": "http://i0.hdslb.com/bfs/bangumi/ep1.jpg",
                    "duration": 1420000,
                    "bvid": "BV1xx411c7mD"
                },
                {
                    "id": 348124,
                    "aid": 111112,
                    "cid": 222223,
                    "title": "2",
                    "long_title": "暗夜的列车",
                    "cover": "http://i0.hdslb.com/bfs/bangumi/ep2.jpg",
                    "duration": 1425000,
                    "bvid": "BV1xx411c7mE"
                }
            ]
        }
    }
    """.trimIndent()

    @Test
    fun parseSeasonJsonRecordsSessionAndExtractsEpisodes() {
        val result = SeaBangumiViewCodec.parseSeasonJson(sampleSeasonJson, ResolverRegion.TW)
        assertNotNull(result)
        assertEquals(28747L, result!!.optLong("season_id"))

        val firstEp = SeaBangumiSession.firstEpisodeForSeason(28747L)
        assertNotNull(firstEp)
        assertEquals(348123L, firstEp!!.epId)
        assertEquals(111111L, firstEp.aid)
        assertEquals(222222L, firstEp.cid)
        assertEquals(ResolverRegion.TW, firstEp.region)

        val ep2 = SeaBangumiSession.episodeForEpId(348124L)
        assertNotNull(ep2)
        assertEquals(222223L, ep2!!.cid)
    }

    @Test
    fun encodeAndDecodeViewReq() {
        // Build a ViewReq protobuf with aid=0, extra_content = {"season_id": "28747"}
        val extraEntry = ProtoWriter().apply {
            string(1, "season_id")
            string(2, "28747")
        }.toByteArray()

        val reqBytes = ProtoWriter().apply {
            int(1, 0L)
            message(9, extraEntry)
        }.toByteArray()

        val decoded = SeaBangumiViewCodec.decodeViewReq(reqBytes)
        assertNotNull(decoded)
        assertEquals(28747L, decoded!!.seasonId)
        assertEquals(0L, decoded.epId)
        assertEquals(0L, decoded.aid)
    }

    @Test
    fun buildViewReplyProducesPlayableAndDownloadableProtobuf() {
        val seasonJson = JSONObject(sampleSeasonJson).getJSONObject("result")
        val replyBytes = SeaBangumiViewCodec.buildViewReply(seasonJson, targetEpId = 348123L)

        assertFalse(SeaBangumiViewCodec.isViewReplyBlocked(replyBytes))

        val fields = ProtoWire.readFields(replyBytes)
        assertNotNull(fields)
        // Check view_base
        val viewBaseField = fields!!.firstOrNull { it.number == 1 && it.wireType == 2 }
        assertNotNull(viewBaseField)
        val viewBaseFields = ProtoWire.readFields(viewBaseField!!.bytes)
        assertEquals(2L, viewBaseFields!!.firstOrNull { it.number == 1 }?.varint) // BIZ_TYPE_PGC

        // Check arc
        val arcField = fields.firstOrNull { it.number == 2 && it.wireType == 2 }
        assertNotNull(arcField)
        val arcFields = ProtoWire.readFields(arcField!!.bytes)
        assertEquals(111111L, arcFields!!.firstOrNull { it.number == 1 }?.varint)
        assertEquals(222222L, arcFields.firstOrNull { it.number == 2 }?.varint)

        // Check ecode
        val ecode = fields.firstOrNull { it.number == 8 && it.wireType == 0 }?.varint ?: -1L
        assertEquals(0L, ecode)
    }

    @Test
    fun buildSeasonSectionsReplyEncodesSectionsWithEpisodes() {
        val seasonJson = JSONObject(sampleSeasonJson).getJSONObject("result")
        val replyBytes = SeaBangumiViewCodec.buildSeasonSectionsReply(seasonJson)

        assertFalse(SeaBangumiViewCodec.isSeasonSectionsBlocked(replyBytes))
        val fields = ProtoWire.readFields(replyBytes)
        assertNotNull(fields)
        assertTrue(fields!!.any { it.number == 1 && it.wireType == 2 })
    }
}
