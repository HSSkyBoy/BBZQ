package io.github.bbzq.feats.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import io.github.bbzq.feats.callMethod
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.LinkedHashMap

internal object StorySceneDetector {
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null
    @Volatile
    private var registered = false

    private const val MAX_TRACKED_STORY_KEYS = 64
    private val storyKeys = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(MAX_TRACKED_STORY_KEYS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > MAX_TRACKED_STORY_KEYS
            }
        }
    )

    fun register(context: Context) {
        val app = context.applicationContext as? Application ?: return
        if (registered) return
        synchronized(this) {
            if (registered) return
            registered = true
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (isStoryActivity(activity)) {
                        currentActivityRef = WeakReference(activity)
                    }
                }
                override fun onActivityStarted(activity: Activity) {
                    if (isStoryActivity(activity)) {
                        currentActivityRef = WeakReference(activity)
                    }
                }
                override fun onActivityResumed(activity: Activity) {
                    currentActivityRef = WeakReference(activity)
                }
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivityRef?.get() === activity) {
                        currentActivityRef = null
                    }
                }
            })
        }
    }

    fun isStoryActivity(activity: Activity?): Boolean {
        if (activity == null) return false
        val name = activity.javaClass.name
        return isStoryActivityClassName(name)
    }

    fun isStoryActivityClassName(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return className.contains("Story", ignoreCase = true)
    }

    fun isStoryForeground(): Boolean {
        val act = currentActivityRef?.get() ?: return false
        if (act.isFinishing || act.isDestroyed) return false
        return isStoryActivity(act)
    }

    fun isStoryCall(maxDepth: Int = 15): Boolean {
        if (isStoryForeground()) return true
        val trace = Thread.currentThread().stackTrace
        val limit = minOf(trace.size, maxDepth)
        for (i in 0 until limit) {
            val name = trace[i].className
            if (name.contains("story", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun isStoryRequest(request: Any?): Boolean {
        if (request == null) return false
        if (isStoryForeground()) {
            recordStoryKey(request)
            return true
        }
        val spmid = runCatching { request.callMethod("getSpmid") as? String }.getOrNull()
        if (spmid?.contains("story", ignoreCase = true) == true) {
            recordStoryKey(request)
            return true
        }
        val fromSpmid = runCatching { request.callMethod("getFromSpmid") as? String }.getOrNull()
        if (fromSpmid?.contains("story", ignoreCase = true) == true) {
            recordStoryKey(request)
            return true
        }
        val extra = runCatching {
            (request.callMethod("getExtraContentMap") as? Map<*, *>)
                ?: (request.callMethod("getExtraContent") as? Map<*, *>)
        }.getOrNull()
        if (extra != null) {
            for ((k, v) in extra) {
                val key = k?.toString()?.lowercase() ?: ""
                val value = v?.toString()?.lowercase() ?: ""
                if (key.contains("story") || value.contains("story") ||
                    (key.contains("scene") && value.contains("story")) ||
                    (key == "from" && value.contains("story"))) {
                    recordStoryKey(request)
                    return true
                }
            }
        }
        return false
    }

    fun isStoryResponse(response: Any?): Boolean {
        if (response == null) return false
        if (isStoryForeground()) return true

        val videoInfo = runCatching {
            response.callMethod("getVideoInfo") ?: response.callMethod("getVodInfo")
        }.getOrNull()

        val bvid = runCatching {
            (videoInfo?.callMethod("getBvid") as? String)?.takeIf { it.isNotBlank() }
                ?: (response.callMethod("getBvid") as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull()
        if (bvid != null && storyKeys.containsKey(bvid)) return true

        val aid = runCatching {
            (videoInfo?.callMethod("getAid") as? Number)?.toLong()?.takeIf { it > 0 }
                ?: (response.callMethod("getAid") as? Number)?.toLong()?.takeIf { it > 0 }
        }.getOrNull()
        if (aid != null && storyKeys.containsKey(aid.toString())) return true

        val cid = runCatching {
            (videoInfo?.callMethod("getCid") as? Number)?.toLong()?.takeIf { it > 0 }
                ?: (response.callMethod("getCid") as? Number)?.toLong()?.takeIf { it > 0 }
        }.getOrNull()
        if (cid != null && storyKeys.containsKey(cid.toString())) return true

        return false
    }

    fun recordStoryKey(key: String) {
        if (key.isNotBlank()) {
            storyKeys[key] = true
        }
    }

    fun recordStoryKey(request: Any?) {
        if (request == null) return
        runCatching {
            val bvid = (request.callMethod("getBvid") as? String)?.takeIf { it.isNotBlank() }
            if (bvid != null) recordStoryKey(bvid)

            val aid = (request.callMethod("getAid") as? Number)?.toLong()?.takeIf { it > 0 }
            if (aid != null) recordStoryKey(aid.toString())

            val vod = request.callMethod("getVod")
            val vodAid = (vod?.callMethod("getAid") as? Number)?.toLong()?.takeIf { it > 0 }
            if (vodAid != null) recordStoryKey(vodAid.toString())

            val cid = (request.callMethod("getCid") as? Number)?.toLong()?.takeIf { it > 0 }
                ?: (vod?.callMethod("getCid") as? Number)?.toLong()?.takeIf { it > 0 }
            if (cid != null) recordStoryKey(cid.toString())
        }
    }

    fun setForegroundActivityForTesting(activity: Activity?) {
        currentActivityRef = activity?.let { WeakReference(it) }
    }

    fun clearForTesting() {
        currentActivityRef = null
        storyKeys.clear()
        registered = false
    }
}
