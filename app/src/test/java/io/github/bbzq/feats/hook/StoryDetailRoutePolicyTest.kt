package io.github.bbzq.feats.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryDetailRoutePolicyTest {
    @Test
    fun `builds complete united launch contract only for numeric story with cid`() {
        val plan = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = "bilibili://story/123456789?from_spmid=main.1.0.0&" +
                    "player_preload=%7B%22cid%22%3A987654321%7D&-Arouter=story",
                componentPackage = "tv.danmaku.bili",
                aid = "123456789",
                preloadCid = 987654321L
            ),
            StoryDetailBackend.UNITED
        ) as StoryActivityLaunchPlan.United

        assertEquals(
            "bilibili://united_video/123456789?from_spmid=main.1.0.0&" +
                "aid=123456789&bvid=",
            plan.detailUri
        )
        assertEquals("bilibili://united_video/123456789", plan.targetUrl)
        assertEquals(123456789L, plan.aid)
        assertEquals(987654321L, plan.cid)
    }

    @Test
    fun `reports a bounded reason for every united skip`() {
        assertEquals(
            StoryLaunchSkip.MISSING_PRELOAD_CID,
            skipOf(
                StoryActivityLaunchSnapshot(
                    dataUri = "bilibili://story/123456789",
                    preloadCid = null
                ),
                StoryDetailBackend.UNITED
            )
        )
        assertEquals(
            StoryLaunchSkip.BV_ONLY_UNITED,
            skipOf(
                StoryActivityLaunchSnapshot(
                    dataUri = "bilibili://story/BV1xx411c7mD",
                    preloadCid = 987654321L
                ),
                StoryDetailBackend.UNITED
            )
        )
        assertEquals(
            StoryLaunchSkip.NOT_STORY_ROUTE,
            skipOf(
                StoryActivityLaunchSnapshot(dataUri = "bilibili://video/123456789"),
                StoryDetailBackend.UNITED
            )
        )
        assertEquals(
            StoryLaunchSkip.NO_DATA_URI,
            skipOf(
                StoryActivityLaunchSnapshot(dataUri = null),
                StoryDetailBackend.UNITED
            )
        )
        assertEquals(
            StoryLaunchSkip.CROSS_PACKAGE,
            skipOf(
                StoryActivityLaunchSnapshot(
                    dataUri = "bilibili://story/123456789",
                    componentPackage = "com.example.other",
                    preloadCid = 1L
                ),
                StoryDetailBackend.UNITED
            )
        )
        assertEquals(
            StoryLaunchSkip.IDENTITY_CONFLICT,
            skipOf(
                StoryActivityLaunchSnapshot(
                    dataUri = "bilibili://story/123456789",
                    aid = "987654321",
                    preloadCid = 1L
                ),
                StoryDetailBackend.UNITED
            )
        )
    }

    @Test
    fun `handles story translucent on both backends`() {
        val legacy = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = "bilibili://story_translucent/BV1xx411c7mD?from=feed&-Atype=story"
            ),
            StoryDetailBackend.LEGACY
        ) as StoryActivityLaunchPlan.Legacy
        assertEquals("bilibili://video/BV1xx411c7mD?from=feed", legacy.detailUri)

        val united = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = "bilibili://story_translucent/123456789",
                preloadCid = 987654321L
            ),
            StoryDetailBackend.UNITED
        ) as StoryActivityLaunchPlan.United
        assertEquals(123456789L, united.aid)
        assertEquals(987654321L, united.cid)
    }

    @Test
    fun `recovers identity from query string and intent extras without a path token`() {
        val fromQuery = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = "bilibili://story?aid=123456789",
                preloadCid = 987654321L
            ),
            StoryDetailBackend.UNITED
        ) as StoryActivityLaunchPlan.United
        assertEquals(123456789L, fromQuery.aid)

        val fromExtras = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = "bilibili://story",
                aid = "123456789",
                preloadCid = 987654321L
            ),
            StoryDetailBackend.UNITED
        ) as StoryActivityLaunchPlan.United
        assertEquals(123456789L, fromExtras.aid)

        assertEquals(
            StoryLaunchSkip.NO_IDENTITY,
            skipOf(
                StoryActivityLaunchSnapshot(
                    dataUri = "bilibili://story",
                    preloadCid = 987654321L
                ),
                StoryDetailBackend.UNITED
            )
        )
    }

    @Test
    fun `handles a real world story uri with an inlined dash manifest`() {
        val dashPadding = "%22base_url%22%3A%22https%3A%2F%2Fupos-sz-mirrorcos.bilivideo.com" +
            "%2Fupgcxcode%2F90%2F37%2F41429503790%2F41429503790-1-100022.m4s%22%2C"
        val preload = "%7B%22expire_time%22%3A1788360455%2C%22cid%22%3A41429503790%2C" +
            "%22video_codecid%22%3A7%2C%22dash%22%3A%7B" +
            dashPadding.repeat(120) +
            "%22end%22%3A1%7D%7D"
        val uri = "bilibili://story/117184055543713?story_item=%7B%7D&player_height=4660&" +
            "player_preload=$preload"
        assertTrue(uri.length > 8_192)
        assertTrue(StoryDetailRoutePolicy.isStrictStoryVideoRoute(uri))

        val plan = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = uri,
                componentPackage = "tv.danmaku.bili",
                preloadCid = 41429503790L
            ),
            StoryDetailBackend.UNITED
        ) as StoryActivityLaunchPlan.United
        assertEquals(117184055543713L, plan.aid)
        assertEquals(41429503790L, plan.cid)

        val oversized = "bilibili://story/117184055543713?x=" + "a".repeat(300_000)
        assertTrue(StoryDetailRoutePolicy.isStrictStoryVideoRoute(oversized))
        assertEquals(
            StoryLaunchSkip.ROUTE_TOO_LONG,
            skipOf(
                StoryActivityLaunchSnapshot(dataUri = oversized, preloadCid = 1L),
                StoryDetailBackend.UNITED
            )
        )
    }

    @Test
    fun `falls back to legacy detail activity contract without united cid`() {
        val plan = planOf(
            StoryActivityLaunchSnapshot(
                dataUri = "bilibili://story/BV1xx411c7mD?from=feed&-Atype=story",
                targetPackage = "tv.danmaku.bili"
            ),
            StoryDetailBackend.LEGACY
        ) as StoryActivityLaunchPlan.Legacy

        assertEquals("bilibili://video/BV1xx411c7mD?from=feed", plan.detailUri)
    }

    @Test
    fun `parses bounded preload cid and only sanitizes forced story hints`() {
        assertEquals(
            987654321L,
            StoryDetailRoutePolicy.parsePlayerPreloadCid(
                "{\"cid\":987654321,\"quality\":80}"
            )
        )
        assertNull(StoryDetailRoutePolicy.parsePlayerPreloadCid("{\"cid\":0}"))
        assertNull(StoryDetailRoutePolicy.parsePlayerPreloadCid("not-json"))
        assertEquals(
            "bilibili://story/123456789?from=feed#page",
            StoryDetailRoutePolicy.sanitizeIntentHandlerUri(
                "bilibili://story/123456789?-%41router=story&from=feed&-Atype=story#page"
            )
        )
        assertNull(
            StoryDetailRoutePolicy.sanitizeIntentHandlerUri(
                "https://www.bilibili.com/video/BV1xx411c7mD?-Atype=story"
            )
        )
    }

    @Test fun rejectsLookalikeRoutesAndOversizedRoutesWithBoundedReasons() {
        for (uri in listOf("bilibili://story_extra/123", "bilibili://storyevil/123", "https://story/123")) {
            assertFalse(StoryDetailRoutePolicy.isStrictStoryVideoRoute(uri))
        }
        val oversized = "bilibili://story/123?x=" + "x".repeat(262_144)
        assertTrue(StoryDetailRoutePolicy.isStrictStoryVideoRoute(oversized))
        assertEquals(StoryLaunchSkip.ROUTE_TOO_LONG, skipOf(StoryActivityLaunchSnapshot(oversized), StoryDetailBackend.LEGACY))
    }

    @Test fun conflictingAndInvalidIdentityNeverFallsBackToAnUnrelatedVideo() {
        for (route in listOf(
            "bilibili://story/123?aid=456",
            "bilibili://story?aid=123&aid=456",
            "bilibili://story/123?aid=not-an-id",
            "bilibili://story/123?bvid=123",
            "bilibili://story/not-an-id",
            "bilibili://story/123/456",
            "bilibili://story/9223372036854775808",
        )) {
            assertTrue(StoryDetailRoutePolicy.planActivityLaunch(
                StoryActivityLaunchSnapshot(route, aid = "123", preloadCid = 7L), StoryDetailBackend.UNITED,
            ) is StoryActivityLaunchOutcome.Skipped)
        }
    }

    @Test fun keepsCrossPackageTargetsAndRejectsWrongExtraNamespaces() {
        val base = StoryActivityLaunchSnapshot("bilibili://story/123", preloadCid = 7L)
        assertEquals(StoryLaunchSkip.CROSS_PACKAGE, skipOf(base.copy(targetPackage = "com.example.other"), StoryDetailBackend.UNITED))
        for (invalid in listOf(base.copy(aid = "BV1xx411c7mD"), base.copy(bvid = "123"), base.copy(avid = "bad"))) {
            assertEquals(StoryLaunchSkip.MALFORMED_INTENT_IDENTITY, skipOf(invalid, StoryDetailBackend.UNITED))
        }
    }

    @Test fun unitedDropsStoryStateAndDoesNotPickConflictingTrackingValues() {
        val plan = planOf(StoryActivityLaunchSnapshot(
            "bilibili://story/123?from_spmid=one&from_spmid=two&story_item=opaque&player_preload=opaque#story",
            preloadCid = 456L,
        ), StoryDetailBackend.UNITED) as StoryActivityLaunchPlan.United
        assertEquals("bilibili://united_video/123?aid=123&bvid=", plan.detailUri)
        assertEquals(456L, plan.cid)
    }

    @Test fun legacyPreservesQueryEncodingAndFragments() {
        val uri = "bilibili://story/123?x=a%2Bb&-Atype=video&-%41router=story&x=1#anchor"
        val plan = planOf(StoryActivityLaunchSnapshot(uri), StoryDetailBackend.LEGACY)
        assertEquals("bilibili://video/123?x=a%2Bb&-Atype=video&x=1#anchor", plan.detailUri)
        assertEquals("bilibili://story/123?x=a%2Bb&-Atype=video&x=1#anchor", StoryDetailRoutePolicy.sanitizeIntentHandlerUri(uri))
    }

    @Test fun acceptsLargePreloadJsonButRejectsInvalidAndOversizedPayloads() {
        val json = "{\"cid\":456,\"padding\":\"" + "x".repeat(30_000) + "\"}"
        assertEquals(456L, StoryDetailRoutePolicy.parsePlayerPreloadCid(json))
        for (raw in listOf("{\"cid\":-1}", "{}", "not-json", "{\"cid\":456,\"x\":\"" + "x".repeat(131_072) + "\"}")) {
            assertNull(StoryDetailRoutePolicy.parsePlayerPreloadCid(raw))
        }
    }

    @Test fun globalRedirectTakesPrecedenceWithoutChangingHomeOnlyPreference() {
        assertTrue(StoryDetailRoutePolicy.useHomeCardRewrite(true, false))
        assertFalse(StoryDetailRoutePolicy.useHomeCardRewrite(true, true))
        assertFalse(StoryDetailRoutePolicy.useHomeCardRewrite(false, true))
        assertFalse(StoryDetailRoutePolicy.useHomeCardRewrite(false, false))
    }

    private fun planOf(snapshot: StoryActivityLaunchSnapshot, backend: StoryDetailBackend): StoryActivityLaunchPlan =
        (StoryDetailRoutePolicy.planActivityLaunch(snapshot, backend) as StoryActivityLaunchOutcome.Planned).plan

    private fun skipOf(snapshot: StoryActivityLaunchSnapshot, backend: StoryDetailBackend): StoryLaunchSkip =
        (StoryDetailRoutePolicy.planActivityLaunch(snapshot, backend) as StoryActivityLaunchOutcome.Skipped).reason
}
