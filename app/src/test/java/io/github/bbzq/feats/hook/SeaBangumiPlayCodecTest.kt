package io.github.bbzq.feats.hook

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaBangumiPlayCodecTest {

    private val playurl = """
        {"code":0,"message":"success","result":{
          "quality":32,"format":"flv480","timelength":1203198,"video_codecid":7,
          "support_formats":[
            {"quality":80,"format":"flv","description":"高清 1080P","need_login":true,"new_description":"1080P 高清","display_desc":"1080P"},
            {"quality":32,"format":"flv480","description":"清晰 480P","display_desc":"480P"}
          ],
          "dash":{"video":[
            {"id":32,"base_url":"https://v/32.m4s","backupUrl":["https://b/32.m4s"],"bandwidth":646351,"codecid":7,
             "size":97210950,"md5":"abc","width":852,"height":480,"frameRate":"25.000"}
          ],"audio":[
            {"id":30216,"baseUrl":"https://a/16.m4s","bandwidth":65682,"codecid":0},
            {"id":30280,"base_url":"https://a/80.m4s","bandwidth":196147,"codecid":0}
          ],"dolby":{"type":0,"audio":[]}}}}
    """.trimIndent()

    @Test
    fun playableResultRequiresAtLeastOneVideoStream() {
        assertNotNull(SeaBangumiPlayCodec.playableResult(playurl))
        assertNull(SeaBangumiPlayCodec.playableResult("""{"code":-10403,"message":"地区"}"""))
        assertNull(SeaBangumiPlayCodec.playableResult("""{"code":0,"result":{"dash":{"video":[],"audio":[]}}}"""))
    }

    @Test
    fun regionRefusalIsRecognisedByCodeOrWording() {
        assertTrue(SeaBangumiPlayCodec.isRegionBlocked(6002003, null))
        assertTrue(SeaBangumiPlayCodec.isRegionBlocked(0, "抱歉您所在地区不可观看！"))
        assertTrue(SeaBangumiPlayCodec.isRegionBlocked(0, "抱歉您所在地區不可觀看"))
        assertFalse(SeaBangumiPlayCodec.isRegionBlocked(-101, "账号未登录"))
        assertFalse(SeaBangumiPlayCodec.isRegionBlocked(0, null))
    }

    @Test
    fun videoInfoCarriesStreamsAndBestAudio() {
        val info = fields(SeaBangumiPlayCodec.encodeVideoInfo(JSONObject(playurl).getJSONObject("result"))!!)

        assertEquals(32L, info.varint(1))
        assertEquals("flv480", info.string(2))
        assertEquals(1203198L, info.varint(3))
        assertEquals(1, info.count(5))
        assertEquals(2, info.count(6))

        val stream = fields(info.bytes(5))
        val streamInfo = fields(stream.bytes(1))
        assertEquals(32L, streamInfo.varint(1))
        assertEquals("清晰 480P", streamInfo.string(3))
        val video = fields(stream.bytes(2))
        assertEquals("https://v/32.m4s", video.string(1))
        assertEquals("https://b/32.m4s", video.string(2))
        assertEquals(30280L, video.varint(7))
        assertEquals("25.000", video.string(9))
        assertEquals(852L, video.varint(10))
        assertEquals(480L, video.varint(11))
    }

    @Test
    fun buildReplyReplacesVideoInfoAndViewInfoButKeepsOtherFields() {
        val original = ProtoWriter().apply {
            message(1, ProtoWriter().apply { int(1, 16) }.toByteArray())
            message(2, byteArrayOf(0x08, 0x01))
            message(5, ProtoWriter().apply { message(1, ProtoWriter().apply { string(2, "blocked") }.toByteArray()) }.toByteArray())
            message(7, byteArrayOf(0x0A, 0x00))
        }.toByteArray()

        val reply = fields(SeaBangumiPlayCodec.buildReply(original, JSONObject(playurl).getJSONObject("result"))!!)

        assertEquals(listOf(1, 2, 7), reply.map { it.first })
        assertEquals(32L, fields(reply.bytes(1)).varint(1))
        assertTrue(reply.bytes(2).contentEquals(byteArrayOf(0x08, 0x01)))
    }

    @Test
    fun buildReplyRejectsMalformedOriginal() {
        assertNull(SeaBangumiPlayCodec.buildReply(byteArrayOf(0x0A, 0x7F), JSONObject(playurl).getJSONObject("result")))
    }

    @Test
    fun dropFieldsKeepsUnlistedBytesVerbatim() {
        val data = ProtoWriter().apply {
            int(1, 300)
            string(2, "keep")
            double(3, 1.5)
        }.toByteArray()

        val kept = ProtoWire.dropFields(data, setOf(1))!!

        assertEquals(listOf(2, 3), fields(kept).map { it.first })
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
                1 -> data.copyOfRange(offset, offset + 8).also { offset += 8 }
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

    private fun List<Pair<Int, Any>>.count(field: Int): Int = count { it.first == field }
}
