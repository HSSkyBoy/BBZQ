package io.github.bbzq.feats.hook

import android.media.session.PlaybackState

internal object MediaSessionActions {
    fun withTrackNavigation(actions: Long): Long {
        val removed = actions and (PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD)
        return (actions xor removed) or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT
    }
}
