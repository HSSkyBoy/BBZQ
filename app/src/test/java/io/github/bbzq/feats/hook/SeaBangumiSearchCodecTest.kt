package io.github.bbzq.feats.hook

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaBangumiSearchCodecTest {

    @Test
    fun parseMatchesKeepsOnlyHighlightedSeasons() {
        val body = """
            {"code":0,"data":{"result":[
              {"title":"由解析服务器提供","season_id":73081},
              {"title":"<em class=\"keyword\">凡人修仙传</em>","season_id":28747},
              {"title":"猫和老鼠 旧版","season_id":357},
              {"title":"<em class=\"keyword\">无季度</em>","season_id":0}
            ]}}
        """.trimIndent()

        val matches = SeaBangumiSearchCodec.parseMatches(body)

        assertEquals(listOf(28747L), matches.map { it.optLong("season_id") })
    }

    @Test
    fun parseMatchesIgnoresErrorResponses() {
        assertTrue(SeaBangumiSearchCodec.parseMatches("""{"code":-400,"message":"bad"}""").isEmpty())
    }

    @Test
    fun encodeItemWrapsBangumiCardInFieldThirtyEight() {
        val entry = JSONObject(
            """{"title":"T","season_id":300,"goto":"bangumi","uri":"u","param":"p","media_score":null,"rating":9.5}""",
        )

        val item = fields(SeaBangumiSearchCodec.encodeItem(entry))

        assertEquals("u", item.string(1))
        assertEquals("p", item.string(2))
        assertEquals("bangumi", item.string(3))
        val card = fields(item.bytes(38))
        assertEquals("T", card.string(1))
        assertEquals(300L, card.varint(20))
        assertEquals(9.5, java.lang.Double.longBitsToDouble(card.fixed64(9)), 0.0)
    }

    @Test
    fun encodeItemForcesCardGotoAndFallsBackToSeasonParam() {
        val entry = JSONObject("""{"title":"T","season_id":42,"goto":"ogv_inline"}""")

        val item = fields(SeaBangumiSearchCodec.encodeItem(entry))

        assertEquals("bangumi", item.string(3))
        assertEquals("42", item.string(2))
    }

    @Test
    fun encodeItemsRepeatsResponseField() {
        val entries = listOf(JSONObject("""{"title":"A","season_id":1}"""), JSONObject("""{"title":"B","season_id":2}"""))

        val encoded = fields(SeaBangumiSearchCodec.encodeItems(4, entries))

        assertEquals(listOf(4, 4), encoded.map { it.first })
    }

    @Test
    fun encodeMetadataOmitsAccessKey() {
        val identity = SeaBangumiSearchCodec.ClientIdentity(
            appId = 1, build = 8110300, buvid = "XY1", mobiApp = "android", device = "phone",
            channel = "master", versionName = "8.11.0", brand = "b", model = "m", osver = "14",
        )

        val metadata = fields(SeaBangumiSearchCodec.encodeMetadata(identity))

        assertTrue(metadata.none { it.first == 1 })
        assertEquals("XY1", metadata.string(6))
        assertEquals(8110300L, metadata.varint(4))
    }

    @Test
    fun encodeNetworkWritesType() {
        assertArrayEquals(byteArrayOf(0x08, 0x01), SeaBangumiSearchCodec.encodeNetwork(1))
    }

    private fun fields(data: ByteArray): List<Pair<Int, Any>> {
        val result = mutableListOf<Pair<Int, Any>>()
        var offset = 0
        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (true) {
                val byte = data[offset++].toInt() and 0xFF
                value = value or ((byte and 0x7F).toLong() shl shift)
                if (byte < 0x80) return value
                shift += 7
            }
        }
        while (offset < data.size) {
            val tag = varint().toInt()
            val value: Any = when (tag and 7) {
                0 -> varint()
                1 -> (0 until 8).fold(0L) { acc, i -> acc or ((data[offset + i].toLong() and 0xFF) shl (8 * i)) }
                    .also { offset += 8 }
                2 -> varint().toInt().let { length -> data.copyOfRange(offset, offset + length).also { offset += length } }
                else -> error("unexpected wire type")
            }
            result += (tag ushr 3) to value
        }
        return result
    }

    private fun List<Pair<Int, Any>>.bytes(field: Int): ByteArray = first { it.first == field }.second as ByteArray

    private fun List<Pair<Int, Any>>.string(field: Int): String = String(bytes(field), Charsets.UTF_8)

    private fun List<Pair<Int, Any>>.varint(field: Int): Long = first { it.first == field }.second as Long

    private fun List<Pair<Int, Any>>.fixed64(field: Int): Long = varint(field)
}
