package io.github.bbzq.feats.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun anonymousQueryRefusesAnotherAppKeyAndEmptyQueries() {
        assertNull(SeaBangumiDetailPolicy.anonymousQuery("season_id=1&appkey=other&sign=x"))
        assertNull(SeaBangumiDetailPolicy.anonymousQuery("season_id=1"))
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
}
