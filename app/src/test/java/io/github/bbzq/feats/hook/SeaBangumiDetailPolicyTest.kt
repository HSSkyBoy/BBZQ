package io.github.bbzq.feats.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun otherResponsesAreNotRefusals() {
        assertFalse(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":0,"message":"success","result":{"title":"x"}}"""))
        assertFalse(SeaBangumiDetailPolicy.isRegionRefusal("""{"code":-101,"message":"账号未登录"}"""))
        assertFalse(SeaBangumiDetailPolicy.isRegionRefusal("not json at all"))
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
