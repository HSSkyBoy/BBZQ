package io.github.bbzq.feats.download

import android.media.MediaExtractor

internal enum class MuxSampleKind { NORMAL, KEY_FRAME, UNSUPPORTED }

internal object MuxSamplePolicy {
    fun classify(flags: Int): MuxSampleKind = when (flags) {
        0 -> MuxSampleKind.NORMAL
        MediaExtractor.SAMPLE_FLAG_SYNC -> MuxSampleKind.KEY_FRAME
        else -> MuxSampleKind.UNSUPPORTED
    }
}
