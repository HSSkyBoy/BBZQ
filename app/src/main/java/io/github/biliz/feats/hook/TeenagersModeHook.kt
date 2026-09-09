package io.github.biliz.feats.hook

import android.app.Activity
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookAfter

class TeenagersModeHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isBlockTeenagersModeDialogEnabled(prefs)) return

        val methods = env.symbols?.teenagersMode?.restore(classLoader)?.onCreateMethods.orEmpty()
        var count = 0
        methods.forEach { method ->
            env.hookAfter(method) { param ->
                val activity = param.thisObject as? Activity ?: return@hookAfter
                if (!isTeenagersModeActivity(activity)) return@hookAfter
                activity.finish()
                log("Teenagers mode dialog has been closed: ${activity.javaClass.name}")
            }
            count++
        }
        if (count > 0) {
            log("TeenagersModeHook installed, methods=$count")
        } else {
            log("TeenagersModeHook: Activity not found")
        }
    }

    private fun isTeenagersModeActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("TeenagersMode", ignoreCase = true) ||
            name.contains("TeensParentControl", ignoreCase = true)
    }
}

