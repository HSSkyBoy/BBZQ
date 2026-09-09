package io.github.biliz.feats.hook

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookAfterMethod
import java.util.Collections
import java.util.WeakHashMap

class AutoLikeHook(env: RoamingEnv) : BaseRoamingHook(env) {

    private val likedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    override fun startHook() {
        if (env.processName != env.packageName) return

        env.hookAfterMethod(Activity::class.java, "onResume") { param ->
            val activity = param.thisObject as? Activity ?: return@hookAfterMethod
            if (!ModuleSettings.isAutoLikeVideoDetailEnabled(prefs)) return@hookAfterMethod
            if (!isVideoPlayerActivity(activity)) return@hookAfterMethod
            if (likedActivities.contains(activity)) return@hookAfterMethod

            scheduleAutoLike(activity)
        }

        log("AutoLikeHook installed (dynamic switch enabled)")
        isInstalled = true
    }

    private fun isVideoPlayerActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("VideoDetail", ignoreCase = true) ||
            name.contains("UnitedBizDetailsActivity", ignoreCase = true) ||
            name.contains("DetailActivity", ignoreCase = true) ||
            name.contains("StoryVideoActivity", ignoreCase = true)
    }

    private fun scheduleAutoLike(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        val delays = longArrayOf(300L, 800L, 1600L, 2600L)
        delays.forEach { delay ->
            decor.postDelayed({
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                if (likedActivities.contains(activity)) return@postDelayed
                val success = tryPerformLike(activity)
                if (success) {
                    likedActivities.add(activity)
                }
            }, delay)
        }
    }

    private fun tryPerformLike(activity: Activity): Boolean {
        val root = activity.window?.decorView ?: return false
        val likeView = findLikeView(root) ?: return false

        if (likeView.isSelected) {
            // Already liked
            return true
        }

        val clicked = if (likeView.isClickable) {
            likeView.performClick()
        } else {
            val parent = likeView.parent as? View
            if (parent != null && parent.isClickable) {
                parent.performClick()
            } else {
                likeView.performClick()
            }
        }
        log("AutoLiked video in ${activity.javaClass.simpleName}, clicked=$clicked")
        return clicked
    }

    private fun findLikeView(view: View): View? {
        val desc = view.contentDescription?.toString()?.lowercase() ?: ""
        val tag = view.tag?.toString()?.lowercase() ?: ""
        val resName = if (view.id != View.NO_ID) {
            runCatching { view.resources.getResourceEntryName(view.id).lowercase() }.getOrDefault("")
        } else ""

        val isLikeTarget = desc.contains("点赞") ||
            desc.contains("赞") ||
            desc.contains("like") ||
            tag.contains("like") ||
            tag.contains("digg") ||
            resName.contains("like") ||
            resName.contains("thumb_up")

        if (isLikeTarget) {
            return view
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = findLikeView(view.getChildAt(i))
                if (child != null) return child
            }
        }

        return null
    }
}
