package io.github.bbzq.feats.hook

import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Remembers which video each player page is showing. A play request is credited to the page that
 * most recently came to the front, so opening another video from a link and coming back does not
 * leave the first page with the second one's id.
 */
internal class PlaybackIdentityRegistry<K : Any> {
    data class Identity(val bvid: String?, val cid: Long?)

    private val identities = WeakHashMap<K, Identity>()
    private var owner = WeakReference<K>(null)

    /** The page that the next recorded playback belongs to. */
    @Synchronized
    fun setOwner(page: K) {
        owner = WeakReference(page)
    }

    /**
     * Merges a play request or reply into the owner's identity. A different video drops the old
     * cid, so a bvid is never paired with another video's cid.
     */
    @Synchronized
    fun record(bvid: String?, cid: Long?) {
        val page = owner.get() ?: return
        val previous = identities[page]
        val sameVideo = bvid == null || bvid == previous?.bvid
        identities[page] = Identity(
            bvid = bvid ?: previous?.bvid,
            cid = cid ?: previous?.cid?.takeIf { sameVideo },
        )
    }

    @Synchronized
    fun identityOf(page: K): Identity? = identities[page]
}
