package io.github.bbzq.feats.hook

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class MediaSessionActionsTest {
    private val skip = PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT
    private val seek = PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD

    @Test fun addsTrackNavigationWhenNoActionsArePresent() {
        assertEquals(skip, MediaSessionActions.withTrackNavigation(0L))
    }

    @Test fun removesRewindAndFastForwardButPreservesPlaybackAndSeeking() {
        val retained = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO
        assertEquals(retained or skip, MediaSessionActions.withTrackNavigation(retained or seek))
    }

    @Test fun preservesAllOtherBitsAndIsIdempotent() {
        val random = Random(102)
        repeat(500) {
            val input = random.nextLong()
            val output = MediaSessionActions.withTrackNavigation(input)
            assertEquals(0L, output and seek)
            assertEquals(skip, output and skip)
            assertEquals(0L, (input xor output) and (seek or skip).inv())
            assertEquals(output, MediaSessionActions.withTrackNavigation(output))
        }
    }
}
