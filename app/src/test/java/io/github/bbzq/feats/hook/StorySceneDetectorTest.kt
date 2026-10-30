package io.github.bbzq.feats.hook

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class TopLevelStoryActivity : Activity() {
    override fun isFinishing(): Boolean = false
    override fun isDestroyed(): Boolean = false
}

private class TopLevelDetailActivity : Activity() {
    override fun isFinishing(): Boolean = false
    override fun isDestroyed(): Boolean = false
}

class StorySceneDetectorTest {

    @Before
    fun setUp() {
        StorySceneDetector.clearForTesting()
    }

    @After
    fun tearDown() {
        StorySceneDetector.clearForTesting()
    }

    @Test
    fun testIsStoryActivityClassName() {
        assertTrue(StorySceneDetector.isStoryActivityClassName("com.bilibili.video.story.StoryVideoActivity"))
        assertTrue(StorySceneDetector.isStoryActivityClassName("com.bilibili.video.story.StoryFragment"))
        assertTrue(StorySceneDetector.isStoryActivityClassName("StoryActivity"))
        
        assertFalse(StorySceneDetector.isStoryActivityClassName("com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity"))
        assertFalse(StorySceneDetector.isStoryActivityClassName("tv.danmaku.bili.ui.video.VideoDetailActivity"))
        assertFalse(StorySceneDetector.isStoryActivityClassName(null))
        assertFalse(StorySceneDetector.isStoryActivityClassName(""))
    }

    @Test
    fun testIsStoryForeground() {
        assertFalse(StorySceneDetector.isStoryForeground())

        val storyActivity = TopLevelStoryActivity()
        StorySceneDetector.setForegroundActivityForTesting(storyActivity)
        assertTrue(StorySceneDetector.isStoryForeground())

        val detailActivity = TopLevelDetailActivity()
        StorySceneDetector.setForegroundActivityForTesting(detailActivity)
        assertFalse(StorySceneDetector.isStoryForeground())

        StorySceneDetector.setForegroundActivityForTesting(null)
        assertFalse(StorySceneDetector.isStoryForeground())
    }

    private class MockPlayViewRequest(
        val spmid: String? = null,
        val fromSpmid: String? = null,
        val extraContent: Map<String, String>? = null,
        val bvid: String? = null,
        val aid: Long? = null,
        val cid: Long? = null,
    )

    private class MockPlayViewResponse(
        val bvid: String? = null,
        val aid: Long? = null,
        val cid: Long? = null,
    )

    @Test
    fun testIsStoryRequestAndResponseDetection() {
        val normalReq = MockPlayViewRequest(
            spmid = "main.ugc-video-detail.0.0",
            bvid = "BV1testNormal",
            aid = 123456L,
            cid = 789012L,
        )
        assertFalse(StorySceneDetector.isStoryRequest(normalReq))

        val storyReq = MockPlayViewRequest(
            spmid = "main.switch-mode.story.0",
            bvid = "BV1testStory",
            aid = 654321L,
            cid = 987654L,
        )
        assertTrue(StorySceneDetector.isStoryRequest(storyReq))

        val storyResp = MockPlayViewResponse(
            bvid = "BV1testStory",
            aid = 654321L,
            cid = 987654L,
        )
        assertTrue(StorySceneDetector.isStoryResponse(storyResp))

        val normalResp = MockPlayViewResponse(
            bvid = "BV1testNormal",
            aid = 123456L,
            cid = 789012L,
        )
        assertFalse(StorySceneDetector.isStoryResponse(normalResp))
    }

    @Test
    fun testIsStoryRequestFromExtraContent() {
        val storyExtraReq = MockPlayViewRequest(
            extraContent = mapOf("from_scene" to "story_video"),
            bvid = "BV1storyFromExtra",
        )
        assertTrue(StorySceneDetector.isStoryRequest(storyExtraReq))

        val storyResp = MockPlayViewResponse(bvid = "BV1storyFromExtra")
        assertTrue(StorySceneDetector.isStoryResponse(storyResp))
    }
}
