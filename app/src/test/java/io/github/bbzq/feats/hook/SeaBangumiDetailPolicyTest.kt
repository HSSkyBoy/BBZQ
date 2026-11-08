package io.github.bbzq.feats.hook

import io.github.bbzq.feats.AppSignature
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaBangumiDetailPolicyTest {

    @Test
    fun pgcPathsAreRecognisedAndOthersAreNot() {
        assertTrue(SeaBangumiDetailPolicy.isPgcPath("/pgc/view/v2/app/season"))
        assertTrue(SeaBangumiDetailPolicy.isPgcPath("/pgc/season/episode/web/info"))
        assertFalse(SeaBangumiDetailPolicy.isPgcPath("/x/web-interface/nav"))
        assertFalse(SeaBangumiDetailPolicy.isPgcPath("/x/pgc/view"))
    }

    @Test
    fun regionRefusalByCodeOrWording() {
        assertTrue(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":-10403,"message":"x","ttl":1}"""))
        assertTrue(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":-404,"message":"抱歉您所在地区不可观看！"}"""))
        assertTrue(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":-404,"message":"抱歉您所在地区不可观看"}"""))
    }

    @Test
    fun aMissingSeasonIsTreatedAsARegionRefusal() {
        assertTrue(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":-404,"message":"啥都木有","ttl":1}"""))
        assertEquals(-404, SeaBangumiDetailPolicy.answerCode("""{"code":-404,"message":"x"}"""))
        assertNull(SeaBangumiDetailPolicy.answerCode("not json"))
    }

    @Test
    fun otherResponsesAreNotRefusals() {
        assertFalse(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":0,"message":"success","result":{"title":"x"}}"""))
        assertFalse(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":-101,"message":"账号未登录"}"""))
        assertFalse(SeaBangumiDetailPolicy.isRegionRefusal("not json at all"))
    }

    @Test
    fun anonymousQueryDropsTheAccountAndSignsAgain() {
        val hostQuery = "season_id=28747&appkey=1d8b6e7d45233436&access_key=secret&sign=old&build=8110300&ts=1700000000"

        val query = SeaBangumiDetailPolicy.anonymousQuery(hostQuery)

        assertEquals(
            "appkey=1d8b6e7d45233436&build=8110300&season_id=28747&ts=1700000000&sign=bb24c8098d99bff31270230738af289a",
            query,
        )
        assertFalse(query!!.contains("secret"))
    }

    @Test
    fun anonymousQueryResignsWithStandardAppKeyAndRefusesEmptyQueries() {
        val query = SeaBangumiDetailPolicy.anonymousQuery("season_id=1&appkey=other&access_key=secret&sign=x")
        assertNotNull(query)
        assertTrue(query!!.contains("season_id=1"))
        assertTrue(query.contains("appkey=${AppSignature.APP_KEY}"))
        assertFalse(query.contains("access_key"))
        assertFalse(query.contains("other"))
        assertNull(SeaBangumiDetailPolicy.anonymousQuery(null))
        assertNull(SeaBangumiDetailPolicy.anonymousQuery(""))
    }

    @Test
    fun accountHeadersCoverTheCredentialsTheHostAdds() {
        assertTrue("authorization" in SeaBangumiDetailPolicy.ACCOUNT_HEADERS)
        assertTrue("x-bili-metadata-bin" in SeaBangumiDetailPolicy.ACCOUNT_HEADERS)
        assertTrue("cookie" in SeaBangumiDetailPolicy.ACCOUNT_HEADERS)
    }

    @Test
    fun replayUrlKeepsPathAndSignedQueryVerbatim() {
        assertEquals(
            "https://r.example/pgc/view/v2/app/season?season_id=1&sign=ab%2Bc",
            SeaBangumiDetailPolicy.replayUrl("https://r.example/", "/pgc/view/v2/app/season", "season_id=1&sign=ab%2Bc"),
        )
        assertEquals(
            "http://r.example:8080/pgc/view/web/season",
            SeaBangumiDetailPolicy.replayUrl("http://r.example:8080", "/pgc/view/web/season", null),
        )
    }

    @Test
    fun decodePrefixBytesHandlesTruncatedGzipWithoutEOFException() {
        // Generate a large payload (> 10KB) that compresses to > 1KB
        val largeJson = buildString {
            append("""{"code":0,"message":"success","result":{"episodes":[""")
            repeat(300) {
                append("""{"id":$it,"title":"Episode $it","desc":"A long description for episode $it to make it big"},""")
            }
            append("""{"id":999,"title":"final"}]}}""")
        }

        val gzipBytes = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(largeJson.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        assertTrue(gzipBytes.size > 2000)

        // Truncate the gzip stream to 500 bytes (simulating peekBody(4096) on a large response)
        val truncatedGzip = gzipBytes.copyOfRange(0, 500)

        val prefix = SeaBangumiDetailPolicy.decodePrefixBytes(truncatedGzip, 4096)
        assertTrue("Decoded prefix should not be empty even when gzip was truncated", prefix.isNotEmpty())
        assertTrue("Decoded prefix should contain start of JSON", prefix.startsWith("""{"code":0,"message":"success""""))
    }
}
