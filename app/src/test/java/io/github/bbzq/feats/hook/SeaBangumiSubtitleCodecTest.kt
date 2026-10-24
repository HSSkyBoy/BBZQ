package io.github.bbzq.feats.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaBangumiSubtitleCodecTest {

    private val answer = """
        {"code":0,"data":{"subtitle":{"lan":"","subtitles":[
          {"id":11,"id_str":"11","lan":"th","lan_doc":"ไทย","subtitle_url":"//i0.hdslb.com/bfs/subtitle/a.json","type":0,"ai_type":0,"ai_status":0},
          {"id":12,"id_str":"12","lan":"zh-Hant","lan_doc":"繁體","subtitle_url":"https://i0.hdslb.com/bfs/subtitle/b.json"},
          {"id":13,"lan":"en","lan_doc":"English","subtitle_url":""}
        ]}}}
    """.trimIndent()

    @Test
    fun parseKeepsTracksWithAnAddressAndNormalisesIt() {
        val entries = SeaBangumiSubtitleCodec.parse(answer)

        assertEquals(listOf("th", "zh-Hant"), entries.map { it.lan })
        assertEquals("https://i0.hdslb.com/bfs/subtitle/a.json", entries[0].url)
    }

    @Test
    fun parseIgnoresRefusalsAndMissingSubtitleBlocks() {
        assertTrue(SeaBangumiSubtitleCodec.parse("""{"code":-10403}""").isEmpty())
        assertTrue(SeaBangumiSubtitleCodec.parse("""{"code":0,"data":{}}""").isEmpty())
    }

    @Test
    fun defaultTrackPrefersChinese() {
        val entries = SeaBangumiSubtitleCodec.parse(answer)

        assertEquals("zh-Hant", SeaBangumiSubtitleCodec.defaultEntry(entries)?.lan)
        assertNull(SeaBangumiSubtitleCodec.defaultEntry(emptyList()))
    }

    @Test
    fun replaceSubtitleSwapsOnlyTheSubtitleField() {
        val original = ProtoWriter().apply {
            int(1, 1)
            message(3, ProtoWriter().apply { string(1, "old") }.toByteArray())
            message(4, byteArrayOf(0x0A, 0x00))
        }.toByteArray()
        val subtitle = SeaBangumiSubtitleCodec.encodeSubtitle(SeaBangumiSubtitleCodec.parse(answer))!!

        val replaced = SeaBangumiSubtitleCodec.replaceSubtitle(original, subtitle)!!

        assertTrue(
            ProtoWire.dropFields(original, setOf(3))!!.contentEquals(ProtoWire.dropFields(replaced, setOf(3))!!),
        )
        assertTrue(String(replaced, Charsets.ISO_8859_1).contains("zh-Hant"))
        assertTrue(!String(replaced, Charsets.ISO_8859_1).contains("old"))
        assertNull(SeaBangumiSubtitleCodec.replaceSubtitle(byteArrayOf(0x0A, 0x7F), subtitle))
    }
}
