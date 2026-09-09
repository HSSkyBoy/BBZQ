package io.github.biliz.feats.hook

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import io.github.biliz.ModuleSettings
import io.github.biliz.R
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookBefore
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class FreeCopyHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        ensureActivityTracking()
        val method = ClipboardManager::class.java.getDeclaredMethod("setPrimaryClip", ClipData::class.java)
        env.hookBefore(method) { param ->
            runCatching {
                if (!isRemoveDirectCopyEnabled()) return@runCatching
                if (allowOriginalCopy.get() == true) return@runCatching

                val clip = param.args.firstOrNull() as? ClipData ?: return@runCatching
                val text = extractText(clip) ?: return@runCatching
                if (shouldBypassCopy(text)) return@runCatching

                if (isEnhancedCopyEnabled()) {
                    showCopyDialog(text, clip)
                }
                param.result = null
            }.onFailure { throwable ->
                log("FreeCopy hook error", throwable)
            }
        }
        log("startHook: FreeCopy, methods=1")
    }

    private fun ensureActivityTracking() {
        val application = env.hostContext as? Application ?: return
        if (!callbacksRegistered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    topActivity = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    if (topActivity?.get() === activity) {
                        topActivity = null
                    }
                }
            },
        )
    }

    private fun isRemoveDirectCopyEnabled(): Boolean =
        ModuleSettings.isDisableLongPressCopyEnabled(prefs)

    private fun isEnhancedCopyEnabled(): Boolean =
        isRemoveDirectCopyEnabled() && ModuleSettings.isEnhanceLongPressCopyEnabled(prefs)

    private fun extractText(clip: ClipData): CharSequence? =
        clip.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(env.hostContext)
            ?.takeIf { it.isNotBlank() }

    private fun shouldBypassCopy(text: CharSequence): Boolean {
        val value = text.toString().trim()
        if (value.matches(BV_ID_REGEX)) return true
        if (isLikelyShareText(value)) return true
        return false
    }

    private fun isLikelyShareText(value: String): Boolean =
        (value.startsWith("http://") || value.startsWith("https://")) &&
            SHARE_LINK_HOSTS.any { value.contains(it, ignoreCase = true) }

    private fun showCopyDialog(text: CharSequence, clip: ClipData) {
        val activity = topActivity?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            log("FreeCopy skipped dialog because no foreground activity is available")
            return
        }

        activity.runOnUiThread {
            runCatching {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

                val moduleContext = env.moduleContext ?: run {
                    log("FreeCopy skipped dialog because module context is unavailable")
                    return@runCatching
                }
                val dialogTitle = moduleContext.getString(R.string.free_copy_title)
                val originalCopyText = moduleContext.getString(R.string.free_copy_original_button)

                val themeId = activity.resources.getIdentifier("AppTheme.Dialog.Alert", "style", activity.packageName)
                val builder = if (themeId != 0) {
                    AlertDialog.Builder(activity, themeId)
                } else {
                    AlertDialog.Builder(activity)
                }

                val dialog = builder
                    .setTitle(dialogTitle)
                    .setMessage(text)
                    .setPositiveButton(originalCopyText) { _, _ ->
                        val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        runWithOriginalCopy {
                            clipboard.setPrimaryClip(clip)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()

                dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
            }.onFailure { throwable ->
                log("FreeCopy dialog error", throwable)
            }
        }
    }

    private fun runWithOriginalCopy(block: () -> Unit) {
        allowOriginalCopy.set(true)
        try {
            block()
        } finally {
            allowOriginalCopy.remove()
        }
    }

    private companion object {
        private val callbacksRegistered = AtomicBoolean(false)
        private val allowOriginalCopy = ThreadLocal<Boolean>()
        private val BV_ID_REGEX = Regex("""BV[a-zA-Z0-9]{10}""")
        private val SHARE_LINK_HOSTS = listOf("bilibili.com", "b23.tv", "bili2233.cn")

        @Volatile
        private var topActivity: WeakReference<Activity>? = null
    }
}

