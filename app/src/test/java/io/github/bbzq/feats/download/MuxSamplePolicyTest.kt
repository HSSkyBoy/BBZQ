package io.github.bbzq.feats.download

import android.media.MediaExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class MuxSamplePolicyTest {
    @Test fun acceptsOnlyCompleteUnencryptedSamples() {
        assertEquals(MuxSampleKind.NORMAL, MuxSamplePolicy.classify(0))
        assertEquals(MuxSampleKind.KEY_FRAME, MuxSamplePolicy.classify(MediaExtractor.SAMPLE_FLAG_SYNC))
    }

    @Test fun encryptedAndPartialSamplesAreNotMislabelledAsCodecConfigOrEos() {
        for (flags in listOf(
            MediaExtractor.SAMPLE_FLAG_ENCRYPTED,
            MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME,
            MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_ENCRYPTED,
            MediaExtractor.SAMPLE_FLAG_SYNC or MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME,
        )) {
            assertEquals(MuxSampleKind.UNSUPPORTED, MuxSamplePolicy.classify(flags))
        }
    }

    @Test fun rejectsUnknownFlags() {
        for (flags in (2..255).toList() + listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(MuxSampleKind.UNSUPPORTED, MuxSamplePolicy.classify(flags))
        }
    }
}
