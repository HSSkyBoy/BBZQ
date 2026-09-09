package io.github.biliz.feats.hook

import android.app.Activity
import android.content.Intent
import android.net.Uri
import io.github.biliz.ModuleSettings
import io.github.biliz.ModuleSettingsBridge
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.from
import io.github.biliz.feats.hookAfterMethod

class StoryDefaultLaunchHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isStoryVideoDefaultLaunchEnabled(prefs)) {
            log("startHook: StoryDefaultLaunch disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }
        synchronized(lock) {
            if (hookInstalled) return
            hookInstalled = true
        }

        val mainActivity = MAIN_ACTIVITY_CLASS.from(classLoader)
        if (mainActivity == null) {
            synchronized(lock) {
                hookInstalled = false
            }
            log("startHook: StoryDefaultLaunch skipped because $MAIN_ACTIVITY_CLASS is unavailable")
            return
        }

        val count = env.hookAfterMethod(mainActivity, "onResume") { param ->
            val activity = param.thisObject as? Activity ?: return@hookAfterMethod
            if (activity.javaClass.name != MAIN_ACTIVITY_CLASS) return@hookAfterMethod
            if (!isLauncherStart(activity)) return@hookAfterMethod
            scheduleLaunch(activity)
        }
        if (count == 0) {
            synchronized(lock) {
                hookInstalled = false
            }
            log("startHook: StoryDefaultLaunch skipped because MainActivityV2.onResume is unavailable")
            return
        }
        log("startHook: StoryDefaultLaunch at $MAIN_ACTIVITY_CLASS.onResume")
    }

    private fun scheduleLaunch(activity: Activity) {
        if (!ModuleSettings.isStoryVideoDefaultLaunchEnabled(prefs)) return
        if (!isLauncherStart(activity)) return
        synchronized(lock) {
            if (launched || launchScheduled) return
            launchScheduled = true
        }
        val root = activity.window?.decorView
        val scheduled = root?.postDelayed(
            {
                synchronized(lock) {
                    launchScheduled = false
                }
                launchStory(activity)
            },
            LAUNCH_DELAY_MS,
        ) == true
        if (!scheduled) {
            synchronized(lock) {
                launchScheduled = false
            }
        }
    }

    private fun launchStory(activity: Activity) {
        if (!ModuleSettings.isStoryVideoDefaultLaunchEnabled(prefs)) return
        if (activity.javaClass.name != MAIN_ACTIVITY_CLASS) return
        if (!isLauncherStart(activity)) return
        if (activity.isFinishing || activity.isDestroyed) return

        synchronized(lock) {
            if (launched) return
            launched = true
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(STORY_URI)).apply {
            setClassName(env.packageName, STORY_TRANSPARENT_ACTIVITY_CLASS)
            putExtra("jumpFrom", "6")
            putExtra("from_spmid", STORY_FROM_SPMID)
        }
        runCatching {
            activity.startActivity(intent)
            log("StoryDefaultLaunch started story feed")
        }.onFailure {
            synchronized(lock) {
                launched = false
            }
            log("StoryDefaultLaunch failed to start story feed", it)
        }
    }

    private fun isLauncherStart(activity: Activity): Boolean {
        val intent = activity.intent ?: return false
        return intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            intent.data == null
    }

    private companion object {
        private const val MAIN_ACTIVITY_CLASS = "tv.danmaku.bili.MainActivityV2"
        private const val STORY_TRANSPARENT_ACTIVITY_CLASS = "com.bilibili.video.story.StoryTransparentActivity"
        private const val STORY_FROM_SPMID = "main.switch-mode.story.0"
        private const val STORY_URI =
            "bilibili://story_translucent/116092110376100" +
                "?from_spmid=main.switch-mode.story.0" +
                "&player_share=1" +
                "&bundle_key_player_shared_type=0" +
                "&bundle_key_player_shared_id=248556216" +
                "&request_from=1" +
                "&display_id=2" +
                "&video_aspect=1.6657407"
        private const val LAUNCH_DELAY_MS = 600L

        private val lock = Any()
        private var hookInstalled = false
        private var launchScheduled = false
        private var launched = false
    }
}
