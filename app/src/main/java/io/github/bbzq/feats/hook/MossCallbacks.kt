package io.github.bbzq.feats.hook

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

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
}

/** What the last resolver-served PGC playback was, so the follow-up danmaku/subtitle request knows. */
internal object SeaBangumiSession {
    class Served(val epId: Long, val cid: Long, val region: io.github.bbzq.feats.ResolverRegion, val at: Long)

    @Volatile
    private var latest: Served? = null

    fun record(epId: Long, cid: Long, region: io.github.bbzq.feats.ResolverRegion) {
        latest = Served(epId, cid, region, System.currentTimeMillis())
    }

    /** The recent served playback that belongs to [cid]; an unknown cid on either side still matches. */
    fun recentFor(cid: Long, now: Long = System.currentTimeMillis()): Served? =
        latest?.takeIf { now - it.at <= FRESH_MILLIS && (it.cid == 0L || cid == 0L || it.cid == cid) }

    private const val FRESH_MILLIS = 5 * 60 * 1000L
}
