package io.github.biliz.feats.hook

import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.biliz.feats.BilibiliSponsorBlock
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object SkipVideoAdState {
    private val markerStates = ConcurrentHashMap<String, TimelineMarkerState>()
    private val identities = ConcurrentHashMap<String, VideoIdentity>()
    private val loadingSegmentRequests = ConcurrentHashMap.newKeySet<String>()
    private val scheduledSegmentRequests = ConcurrentHashMap.newKeySet<String>()
    private val completedSegmentRequests = ConcurrentHashMap.newKeySet<String>()
    private val failedSegmentRequests = ConcurrentHashMap<String, Long>()
    private val progressGenerations = ConcurrentHashMap<String, Long>()
    private val drawnMarkerKeys = ConcurrentHashMap.newKeySet<String>()

    private val controllerVideoKeys = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val viewVideoKeys = Collections.synchronizedMap(WeakHashMap<View, String>())
    private val markerDrawObservers = CopyOnWriteArrayList<(MarkerDrawEvent) -> Unit>()
    private val requestGeneration = AtomicLong()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun resolveVideoIdentity(bvid: String?, cid: Any?, aid: Any? = null): VideoIdentity? {
        val normalizedCid = cid.asLong()?.takeIf { it > 0L }?.toString() ?: return null
        val normalizedBvid = bvid
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: aid.asLong()?.takeIf { it > 0L }?.let(::bvidFromAid)
            ?: return null
        return VideoIdentity(normalizedBvid, normalizedCid)
    }

    fun bvidFromAid(aid: Long): String {
        val result = CharArray(12) { if (it < 3) "BV1"[it] else '0' }
        var value = ((1L shl 51) or aid) xor 23442827791579L
        var index = 11
        while (value > 0) {
            result[index--] = BV_TABLE[(value % 58).toInt()]
            value /= 58
        }
        result[3] = result[9].also { result[9] = result[3] }
        result[4] = result[7].also { result[7] = result[4] }
        return String(result)
    }

    fun activateVideo(identity: VideoIdentity): String {
        identities[identity.key] = identity
        return identity.key
    }

    fun identityForKey(key: String): VideoIdentity? =
        key.takeIf { it.isNotBlank() }?.let(identities::get)

    fun keyMatchesIdentity(key: String?, identity: VideoIdentity): Boolean {
        if (key.isNullOrBlank()) return false
        return key == identity.key || key.startsWith(sessionPrefix(identity))
    }

    fun isProgressSessionKey(key: String?): Boolean =
        key?.contains(PROGRESS_SESSION_MARKER) == true

    fun playbackIdentityKey(key: String): String =
        key.substringBefore(PROGRESS_SESSION_MARKER)

    fun bindController(controller: Any?, key: String) {
        if (controller == null || key.isBlank()) return
        synchronized(controllerVideoKeys) {
            controllerVideoKeys[controller] = key
        }
    }

    fun bindView(view: View?, key: String): String? {
        if (view == null || key.isBlank()) return null
        return synchronized(viewVideoKeys) {
            viewVideoKeys.put(view, key)
        }
    }

    fun keyForController(controller: Any?): String? {
        if (controller == null) return null
        return synchronized(controllerVideoKeys) {
            controllerVideoKeys[controller]
        }
    }

    fun stateForView(view: View?): TimelineMarkerState? {
        if (view == null) return null
        return synchronized(viewVideoKeys) {
            viewVideoKeys[view]
        }?.let(markerStates::get)
    }

    fun stateForKey(key: String): TimelineMarkerState? =
        key.takeIf { it.isNotBlank() }?.let(markerStates::get)

    fun updateDuration(key: String, nextDurationMs: Long): TimelineMarkerState? {
        if (key.isBlank() || nextDurationMs <= 0L) return markerStates[key]
        return updateState(key, durationMs = nextDurationMs, segments = null)
    }

    fun requestSegmentsAfterProgress(
        view: View?,
        controllers: List<Any?>,
        identity: VideoIdentity,
        durationMs: Long,
        enabledCategories: Set<String>,
        delayMs: Long,
        log: (String, Throwable?) -> Unit,
    ): TimelineMarkerState? {
        activateVideo(identity)
        val key = progressSessionKey(view, identity)
        identities[key] = identity
        val previousKey = bindView(view, key)
        invalidatePreviousProgressSession(previousKey, key)
        controllers.forEach { controller ->
            bindController(controller, key)
        }
        val session = ensureProgressSession(key, previousKey, durationMs)
        if (durationMs > 0L) {
            updateDuration(key, durationMs)
            if (session.started) {
                drawnMarkerKeys.remove(key)
                drawnMarkerKeys.remove(identity.key)
                updateState(identity.key, durationMs = durationMs, segments = emptyList())
            } else {
                updateDuration(identity.key, durationMs)
            }
        }
        if (durationMs <= 0L || enabledCategories.isEmpty()) {
            return stateForKey(key)
        }

        scheduleSegmentRequest(key, identity, enabledCategories, session.generation, delayMs, log)
        return stateForKey(key)
    }

    private fun invalidatePreviousProgressSession(previousKey: String?, nextKey: String) {
        if (previousKey.isNullOrBlank() || previousKey == nextKey) return
        progressGenerations.remove(previousKey)
        markerStates.remove(previousKey)
        drawnMarkerKeys.remove(previousKey)
        drawnMarkerKeys.remove(playbackIdentityKey(previousKey))
        removeRequestStateForKey(previousKey)
    }

    private fun ensureProgressSession(
        key: String,
        previousKey: String?,
        durationMs: Long,
    ): ProgressSession {
        val currentGeneration = progressGenerations[key]
        val shouldStartSession = currentGeneration == null || (previousKey != null && previousKey != key)
        if (!shouldStartSession) {
            return ProgressSession(currentGeneration, started = false)
        }

        val generation = requestGeneration.incrementAndGet()
        progressGenerations[key] = generation
        markerStates[key] = TimelineMarkerState(
            key = key,
            durationMs = durationMs.takeIf { it > 0L } ?: 0L,
            segments = emptyList(),
        )
        return ProgressSession(generation, started = true)
    }

    private fun updateState(
        key: String,
        durationMs: Long?,
        segments: List<BilibiliSponsorBlock.Segment>?,
    ): TimelineMarkerState {
        val current = markerStates[key]
        val next = TimelineMarkerState(
            key = key,
            durationMs = durationMs?.takeIf { it > 0L } ?: current?.durationMs ?: 0L,
            segments = segments ?: current?.segments ?: emptyList(),
        )
        markerStates[key] = next
        return next
    }

    private fun scheduleSegmentRequest(
        key: String,
        identity: VideoIdentity,
        enabledCategories: Set<String>,
        generation: Long,
        delayMs: Long,
        log: (String, Throwable?) -> Unit,
    ) {
        val requestKey = buildRequestKey(key, enabledCategories, generation)
        if (requestKey in loadingSegmentRequests) return
        if (requestKey in scheduledSegmentRequests) return
        if (requestKey in completedSegmentRequests) return

        val failedAt = failedSegmentRequests[requestKey]
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILED_RETRY_DELAY_MS) return
        if (!scheduledSegmentRequests.add(requestKey)) return

        mainHandler.postDelayed(
            {
                scheduledSegmentRequests.remove(requestKey)
                if (progressGenerations[key] != generation) return@postDelayed
                startSegmentFetch(key, identity, enabledCategories, generation, requestKey, log)
            },
            delayMs,
        )
    }

    private fun startSegmentFetch(
        key: String,
        identity: VideoIdentity,
        enabledCategories: Set<String>,
        generation: Long,
        requestKey: String,
        log: (String, Throwable?) -> Unit,
    ) {
        if (!loadingSegmentRequests.add(requestKey)) return

        Thread {
            try {
                var result = BilibiliSponsorBlock.FetchResult(
                    status = BilibiliSponsorBlock.FetchStatus.FAILED,
                    segments = emptyList(),
                )
                for (attempt in 0 until FETCH_RETRY_COUNT) {
                    result = BilibiliSponsorBlock(identity.bvid, identity.cid, enabledCategories).getSegments()
                    if (result.status != BilibiliSponsorBlock.FetchStatus.FAILED) break
                    if (attempt < FETCH_RETRY_COUNT - 1) Thread.sleep(FETCH_RETRY_DELAY_MS)
                }

                if (progressGenerations[key] != generation) {
                    return@Thread
                }

                if (result.status == BilibiliSponsorBlock.FetchStatus.FAILED) {
                    failedSegmentRequests[requestKey] = System.currentTimeMillis()
                    log("SkipVideoAd marker segment fetch failed for ${identity.key}", null)
                } else {
                    failedSegmentRequests.remove(requestKey)
                    completedSegmentRequests.add(requestKey)
                    updateState(key, durationMs = null, segments = result.segments)
                    updateState(identity.key, durationMs = null, segments = result.segments)
                    invalidateViewsForKey(key)
                    if (result.segments.isNotEmpty()) {
                        log("SkipVideoAd marker loaded ${result.segments.size} segment(s) for ${identity.key}", null)
                    } else {
                        log("SkipVideoAd marker segment fetch ${result.status} for ${identity.key}", null)
                    }
                }
            } catch (throwable: Throwable) {
                failedSegmentRequests[requestKey] = System.currentTimeMillis()
                log("SkipVideoAd marker segment fetch crashed for ${identity.key}", throwable)
            } finally {
                loadingSegmentRequests.remove(requestKey)
            }
        }.apply {
            name = "BILIZ-SkipVideoAdMarker"
            isDaemon = true
            start()
        }
    }

    fun markSegmentsDrawn(key: String, positionMs: Long) {
        if (key.isBlank()) return
        if (positionMs <= 0L) return
        val videoKey = playbackIdentityKey(key)
        val firstDrawForKey = drawnMarkerKeys.add(key)
        drawnMarkerKeys.add(videoKey)
        if (!firstDrawForKey) return
        val event = MarkerDrawEvent(key, videoKey, positionMs)
        markerDrawObservers.forEach { observer ->
            observer(event)
        }
    }

    fun hasDrawnSegments(key: String): Boolean {
        if (key.isBlank()) return false
        return key in drawnMarkerKeys || playbackIdentityKey(key) in drawnMarkerKeys
    }

    fun invalidateDrawnMarkersForVideo(videoKey: String) {
        if (videoKey.isBlank()) return
        drawnMarkerKeys
            .filter { playbackIdentityKey(it) == videoKey }
            .forEach(drawnMarkerKeys::remove)
    }

    fun resetProgressForVideo(videoKey: String) {
        if (videoKey.isBlank()) return
        markerStates.keys
            .filter { playbackIdentityKey(it) == videoKey }
            .forEach(markerStates::remove)
        progressGenerations.keys
            .filter { playbackIdentityKey(it) == videoKey }
            .forEach(progressGenerations::remove)
        invalidateDrawnMarkersForVideo(videoKey)
        removeRequestStateForVideo(videoKey)
    }

    fun observeMarkerDrawn(observer: (MarkerDrawEvent) -> Unit) {
        markerDrawObservers.add(observer)
    }

    private fun invalidateViewsForKey(key: String) {
        if (key.isBlank()) return
        mainHandler.post {
            val views = synchronized(viewVideoKeys) {
                viewVideoKeys.entries
                    .filter { (_, viewKey) -> viewKey == key }
                    .map { (view, _) -> view }
            }
            views.forEach(View::invalidate)
        }
    }

    private fun buildRequestKey(
        key: String,
        enabledCategories: Set<String>,
        generation: Long,
    ): String = key + "|g=" + generation + "|" + enabledCategories.sorted().joinToString(",")

    private fun removeRequestStateForKey(key: String) {
        val prefix = "$key|g="
        removeRequestState { requestKey -> requestKey.startsWith(prefix) }
    }

    private fun removeRequestStateForVideo(videoKey: String) {
        val prefix = "$videoKey|"
        removeRequestState { requestKey -> requestKey.startsWith(prefix) }
    }

    private fun removeRequestState(predicate: (String) -> Boolean) {
        scheduledSegmentRequests.filter(predicate).forEach(scheduledSegmentRequests::remove)
        loadingSegmentRequests.filter(predicate).forEach(loadingSegmentRequests::remove)
        completedSegmentRequests.filter(predicate).forEach(completedSegmentRequests::remove)
        failedSegmentRequests.keys.filter(predicate).forEach(failedSegmentRequests::remove)
    }

    private fun progressSessionKey(view: View?, identity: VideoIdentity): String {
        if (view == null) return identity.key
        return sessionPrefix(identity) + System.identityHashCode(view).toString(16)
    }

    private fun sessionPrefix(identity: VideoIdentity): String =
        identity.key + PROGRESS_SESSION_MARKER

    private fun Any?.asLong(): Long? = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

    data class VideoIdentity(
        val bvid: String,
        val cid: String,
    ) {
        val key: String = "bvid:$bvid:cid:$cid"
    }

    data class TimelineMarkerState(
        val key: String,
        val durationMs: Long,
        val segments: List<BilibiliSponsorBlock.Segment>,
    )

    data class MarkerDrawEvent(
        val key: String,
        val videoKey: String,
        val positionMs: Long,
    )

    private data class ProgressSession(
        val generation: Long,
        val started: Boolean,
    )

    private const val FETCH_RETRY_COUNT = 3
    private const val FETCH_RETRY_DELAY_MS = 1000L
    private const val FAILED_RETRY_DELAY_MS = 60_000L
    private const val PROGRESS_SESSION_MARKER = "|progress:"
    private const val BV_TABLE = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
}
