package io.github.biliz.feats.hook

/**
 * Global synchronized state for the player quality pipeline to bridge
 * PlayView response rewriting, preloading, and player core quality arbitration.
 */
object QualityState {
    @Volatile
    var currentBvid: String = ""

    @Volatile
    var currentCid: Long = 0L

    /**
     * The highest/preferred quality selected for the currently loaded video stream (e.g. 120, 116, 112).
     * Used by VideoQualityHook to ensure Bilibili's internal quality strategy does not downgrade to 1080P.
     */
    @Volatile
    var preferredQuality: Long = 0L

    @Volatile
    var availableQualities: List<Long> = emptyList()

    fun reset() {
        currentBvid = ""
        currentCid = 0L
        preferredQuality = 0L
        availableQualities = emptyList()
    }
}
