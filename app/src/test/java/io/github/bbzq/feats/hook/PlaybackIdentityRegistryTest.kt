package io.github.bbzq.feats.hook

import io.github.bbzq.feats.hook.PlaybackIdentityRegistry.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackIdentityRegistryTest {
    private class Page

    @Test
    fun returningToTheFirstPageKeepsItsOwnVideo() {
        val registry = PlaybackIdentityRegistry<Page>()
        val first = Page()
        val linked = Page()

        registry.setOwner(first)
        registry.record("BV1AAAAAAAAA", 1L)
        registry.setOwner(linked)
        registry.record("BV1BBBBBBBBB", 2L)
        registry.setOwner(first)

        assertEquals(Identity("BV1AAAAAAAAA", 1L), registry.identityOf(first))
        assertEquals(Identity("BV1BBBBBBBBB", 2L), registry.identityOf(linked))
    }

    @Test
    fun switchingVideoOnTheSamePageDropsTheOldCid() {
        val registry = PlaybackIdentityRegistry<Page>()
        val page = Page()
        registry.setOwner(page)

        registry.record("BV1AAAAAAAAA", 1L)
        registry.record("BV1BBBBBBBBB", null)

        assertEquals(Identity("BV1BBBBBBBBB", null), registry.identityOf(page))
    }

    @Test
    fun aReplyForTheSameVideoKeepsTheRequestCid() {
        val registry = PlaybackIdentityRegistry<Page>()
        val page = Page()
        registry.setOwner(page)

        registry.record("BV1AAAAAAAAA", 1L)
        registry.record("BV1AAAAAAAAA", null)

        assertEquals(Identity("BV1AAAAAAAAA", 1L), registry.identityOf(page))
    }

    @Test
    fun nothingIsRecordedWithoutAnOwner() {
        val registry = PlaybackIdentityRegistry<Page>()
        val page = Page()

        registry.record("BV1AAAAAAAAA", 1L)

        assertNull(registry.identityOf(page))
    }
}
