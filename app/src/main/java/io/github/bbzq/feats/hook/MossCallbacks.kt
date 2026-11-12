package io.github.bbzq.feats.hook

import io.github.bbzq.feats.ResolverRegion
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Holds back host callbacks that arrive while a resolver lookup is still in flight. */
internal class Gate {
    private val queue = ArrayList<() -> Unit>()
    private var pending = false

    @Synchronized
    fun enqueueIfPending(task: () -> Unit): Boolean {
        if (!pending) return false
        queue += task
        return true
    }

    @Synchronized
    fun start() {
        pending = true
    }

    /** Runs the callbacks that were held back, in arrival order, then lets new ones through again. */
    fun finish() {
        while (true) {
            val next = synchronized(this) {
                if (queue.isEmpty()) {
                    pending = false
                    null
                } else {
                    queue.removeAt(0)
                }
            } ?: return
            runCatching(next)
        }
    }
}

internal object MossCallbacks {
    fun invoke(target: Any, method: Method, args: Array<Any?>): Any? =
        try {
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }

    fun invokeByName(
        target: Any,
        type: Class<*>,
        name: String,
        log: (String, Throwable?) -> Unit,
        vararg args: Any?,
    ) {
        val method = type.methods.firstOrNull { it.name == name && it.parameterCount == args.size } ?: return
        runCatching { invoke(target, method, arrayOf(*args)) }
            .onFailure { log("MossCallbacks: handler.$name failed", it) }
    }

    /** What a proxied handler method must return when it is not forwarded. */
    fun defaultValue(method: Method): Any? = when (method.returnType) {
        java.lang.Long.TYPE -> 0L
        java.lang.Integer.TYPE -> 0
        java.lang.Boolean.TYPE -> false
        else -> null
    }

    private val KNOWN_KMP_MOSS_CALLBACKS = setOf("cbx0", "Kh1.h")

    fun isKmpMossCallback(iface: Class<*>): Boolean =
        iface.name in KNOWN_KMP_MOSS_CALLBACKS ||
            (iface.isInterface &&
                iface.declaredMethods.any { it.name == "onNext" && it.parameterCount == 1 } &&
                iface.declaredMethods.any { it.name == "onError" })
}

/**
 * Shared session state for resolver-served PGC bangumi seasons and episodes across
 * View, SeasonSectionsForCache, PlayViewUnite, PlayView, and DmView (subtitles).
 */
internal object SeaBangumiSession {
    class Served(val epId: Long, val cid: Long, val region: ResolverRegion, val at: Long)

    class EpisodeMeta(
        val epId: Long,
        val seasonId: Long,
        val aid: Long,
        val cid: Long,
        val region: ResolverRegion?,
    )

    class CachedSeason(
        val seasonId: Long,
        val jsonBody: String,
        val region: ResolverRegion,
        val at: Long,
    )

    @Volatile
    private var latest: Served? = null

    private val byEpId = ConcurrentHashMap<Long, EpisodeMeta>()
    private val byCid = ConcurrentHashMap<Long, EpisodeMeta>()
    private val firstEpBySeason = ConcurrentHashMap<Long, EpisodeMeta>()
    private val seasonCache = ConcurrentHashMap<Long, CachedSeason>()

    fun record(epId: Long, cid: Long, region: ResolverRegion, seasonId: Long = 0L, aid: Long = 0L) {
        latest = Served(epId, cid, region, System.currentTimeMillis())
        if (epId > 0L) {
            val prev = byEpId[epId]
            val merged = EpisodeMeta(
                epId = epId,
                seasonId = if (seasonId > 0L) seasonId else (prev?.seasonId ?: 0L),
                aid = if (aid > 0L) aid else (prev?.aid ?: 0L),
                cid = if (cid > 0L) cid else (prev?.cid ?: 0L),
                region = region,
            )
            byEpId[epId] = merged
            if (merged.cid > 0L) byCid[merged.cid] = merged
        }
    }

    fun recordEpisode(
        epId: Long,
        seasonId: Long,
        aid: Long,
        cid: Long,
        region: ResolverRegion?,
        isFirstInSeason: Boolean = false,
    ) {
        if (epId <= 0L) return
        val prev = byEpId[epId]
        val meta = EpisodeMeta(
            epId = epId,
            seasonId = if (seasonId > 0L) seasonId else (prev?.seasonId ?: 0L),
            aid = if (aid > 0L) aid else (prev?.aid ?: 0L),
            cid = if (cid > 0L) cid else (prev?.cid ?: 0L),
            region = region ?: prev?.region,
        )
        byEpId[epId] = meta
        if (meta.cid > 0L) byCid[meta.cid] = meta
        if (seasonId > 0L && (isFirstInSeason || !firstEpBySeason.containsKey(seasonId))) {
            firstEpBySeason[seasonId] = meta
        }
    }

    fun recordSeasonJson(seasonId: Long, jsonBody: String, region: ResolverRegion) {
        if (seasonId <= 0L || jsonBody.isBlank()) return
        seasonCache[seasonId] = CachedSeason(seasonId, jsonBody, region, System.currentTimeMillis())
    }

    fun cachedSeason(seasonId: Long, now: Long = System.currentTimeMillis()): CachedSeason? =
        seasonCache[seasonId]?.takeIf { now - it.at <= SEASON_FRESH_MILLIS }

    fun episodeForEpId(epId: Long): EpisodeMeta? = if (epId > 0L) byEpId[epId] else null

    fun episodeForCid(cid: Long): EpisodeMeta? = if (cid > 0L) byCid[cid] else null

    fun firstEpisodeForSeason(seasonId: Long): EpisodeMeta? = if (seasonId > 0L) firstEpBySeason[seasonId] else null

    /** The recent served playback that belongs to [cid]; an unknown cid on either side still matches. */
    fun recentFor(cid: Long, now: Long = System.currentTimeMillis()): Served? {
        latest?.takeIf { now - it.at <= FRESH_MILLIS && (it.cid == 0L || cid == 0L || it.cid == cid) }?.let {
            return it
        }
        if (cid > 0L) {
            val meta = byCid[cid]
            val region = meta?.region ?: latest?.region
            if (meta != null && region != null) {
                return Served(meta.epId, meta.cid, region, now)
            }
        }
        return null
    }

    private const val FRESH_MILLIS = 5 * 60 * 1000L
    private const val SEASON_FRESH_MILLIS = 15 * 60 * 1000L
}
