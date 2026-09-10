package io.github.bbzq.feats.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomCdnProcessorTest {

    @Test
    fun testIsPCdn() {
        // PCDN 網域名稱應被判定為 true
        assertTrue(CustomCdnProcessor.isPCdn("https://szbdyd.bilivideo.com/live-bvc/123.flv"))
        assertTrue(CustomCdnProcessor.isPCdn("http://mcdn.bilivideo.cn:8080/p2p/segment.m4s"))
        assertTrue(CustomCdnProcessor.isPCdn("https://p-node1.bilivideo.com/video.m4s"))
        assertTrue(CustomCdnProcessor.isPCdn("https://m-node2.bilivideo.com/video.m4s"))
        assertTrue(CustomCdnProcessor.isPCdn("https://p1-dx-bilivideo.szbdyd.com/video.m4s"))
        assertTrue(CustomCdnProcessor.isPCdn("http://sz-node1-bilivideo.com/video.m4s"))

        // 常規官方/雲廠商 CDN 網域名稱應判定為 false
        assertFalse(CustomCdnProcessor.isPCdn("https://upos-sz-mirrorali.bilivideo.com/upgcxcode/123.mp4"))
        assertFalse(CustomCdnProcessor.isPCdn("https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/123.mp4"))
        assertFalse(CustomCdnProcessor.isPCdn("https://cn-hk-eq-bcache-01.bilivideo.com/upgcxcode/123.mp4"))
        assertFalse(CustomCdnProcessor.isPCdn("https://upos-hz-mirrorakam.akamaized.net/upgcxcode/123.mp4"))
    }

    @Test
    fun testReplaceHost() {
        val targetHost = "upos-sz-mirrorcos.bilivideo.com"

        val originalHttps = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/45/67/12345/12345.m4s?e=123&deadline=456"
        val expectedHttps = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/45/67/12345/12345.m4s?e=123&deadline=456"
        assertEquals(expectedHttps, CustomCdnProcessor.replaceHost(originalHttps, targetHost))

        val originalHttp = "http://upos-sz-mirrorhw.bilivideo.com:8080/upgcxcode/video.mp4"
        val expectedHttp = "http://upos-sz-mirrorcos.bilivideo.com/upgcxcode/video.mp4"
        assertEquals(expectedHttp, CustomCdnProcessor.replaceHost(originalHttp, targetHost))

        val rootUrl = "https://upos-sz-mirrorali.bilivideo.com"
        val expectedRoot = "https://upos-sz-mirrorcos.bilivideo.com"
        assertEquals(expectedRoot, CustomCdnProcessor.replaceHost(rootUrl, targetHost))
    }
}
