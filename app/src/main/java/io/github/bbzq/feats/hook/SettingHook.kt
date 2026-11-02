package io.github.bbzq.feats.hook

import android.app.Activity
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsNavigator
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostAccountResolver
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookAfterMethod
import io.github.bbzq.feats.methodsNamed
import java.lang.reflect.Proxy

class SettingHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        var installed = 0
        HELP_FRAGMENT_CLASSES.mapNotNull(classLoader::findClassOrNull).distinct().forEach { fragmentClass ->
            val activityCreated = fragmentClass.methodsNamed("onActivityCreated")
                .firstOrNull { it.parameterCount == 1 }
            if (activityCreated != null) {
                env.hookAfter(activityCreated) { param ->
                    param.thisObject?.let(::replaceJoinUsEntry)
                }
                installed++
                log("SettingHook installed on ${fragmentClass.name}")
            }
        }
        installHomeAvatarLongClickHook()
        if (installed == 0) {
            log("SettingHook skipped: No HelpFragment classes available")
        } else {
            log("startHook: Setting replaces About Bilibili join-us entry in $installed fragment class(es)")
        }
    }

    private fun replaceJoinUsEntry(fragment: Any) {
        val activity = fragment.callMethod("getActivity") as? Activity ?: return
        val entry = findJoinUsPreference(fragment, activity) ?: return
        entry.callMethod("setTitle", ENTRY_TITLE)
        entry.callMethod("setSummary", ENTRY_SUMMARY)
        entry.callMethod("setPersistent", false)
        entry.callMethod("setSelectable", true)
        runCatching { entry.callMethod("setVisible", true) }

        val setter = entry.javaClass.methodsNamed("setOnPreferenceClickListener")
            .firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isInterface } ?: return
        val listenerType = setter.parameterTypes[0]
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
            when (method.name) {
                "onPreferenceClick" -> {
                    ModuleSettingsNavigator.open(activity, runtimeSnapshot())
                    true
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "BbzqOnPreferenceClickListener"
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        }
        setter.invoke(entry, listener)
        log("Replaced About Bilibili join-us entry in ${fragment.javaClass.name}")
    }

    private fun findJoinUsPreference(fragment: Any, activity: Activity): Any? {
        JOIN_US_KEYS.firstNotNullOfOrNull { key ->
            fragment.callMethod("findPreference", key)
        }?.let { return it }

        val resId = runCatching {
            activity.resources.getIdentifier("pref_key_joinus", "string", activity.packageName)
        }.getOrNull() ?: 0
        if (resId != 0) {
            val keyStr = runCatching { activity.getString(resId) }.getOrNull()
            if (keyStr != null) {
                fragment.callMethod("findPreference", keyStr)?.let { return it }
            }
        }

        return findJoinUsFromPreferenceGroup(fragment.callMethod("getPreferenceScreen"))
    }

    private fun findJoinUsFromPreferenceGroup(group: Any?): Any? {
        if (group == null) return null
        val count = (group.callMethod("getPreferenceCount") as? Number)?.toInt() ?: return null
        for (i in 0 until count) {
            val pref = group.callMethod("getPreference", i) ?: continue
            val key = pref.callMethod("getKey")?.toString().orEmpty()
            val title = pref.callMethod("getTitle")?.toString().orEmpty()
            if (key in JOIN_US_KEYS || key.contains("join", ignoreCase = true) || title.contains("加入")) {
                return pref
            }
            if (pref.javaClass.name.contains("PreferenceGroup") || pref.javaClass.name.contains("PreferenceCategory")) {
                val found = findJoinUsFromPreferenceGroup(pref)
                if (found != null) return found
            }
        }
        return null
    }

    private fun runtimeSnapshot() =
        RuntimeEnvironmentInfo.runtimeSnapshotBundle(
            hostContext = env.hostContext,
            processName = env.processName,
            xposedApiVersion = runCatching { xposed.apiVersion.toString() }.getOrDefault("unknown"),
            xposedFrameworkName = runCatching { xposed.frameworkName }.getOrDefault("unknown"),
            xposedFrameworkVersion = runCatching { xposed.frameworkVersion }.getOrDefault("unknown"),
            xposedFrameworkVersionCode = runCatching { xposed.frameworkVersionCode.toString() }.getOrDefault("unknown"),
            xposedFrameworkProperties = runCatching { xposed.frameworkProperties.toString() }.getOrDefault("unknown"),
            observedPrefs = prefs,
        ).apply {
            val account = HostAccountResolver.resolve(env.hostContext, classLoader)
            log("Setting: host account loggedIn=${account.loggedIn} uidLength=${account.uid.length} nameBlank=${account.userName.isBlank()}")
            putString(ModuleSettings.KEY_HOST_ACCOUNT_UID, if (account.loggedIn) account.uid else "")
            putString(ModuleSettings.KEY_HOST_ACCOUNT_NAME, if (account.loggedIn) account.userName else "")
            if (account.loggedIn && account.accessKey.isNotBlank()) {
                putString(ModuleSettings.KEY_LAST_ACCESS_KEY, account.accessKey)
            }
        }

    private fun installHomeAvatarLongClickHook() {
        // 1. Hook MainActivity.onResume
        runCatching {
            env.hookAfterMethod(Activity::class.java, "onResume") { param ->
                val activity = param.thisObject as? Activity ?: return@hookAfterMethod
                if (isHomeActivity(activity.javaClass.name)) {
                    attachAvatarLongClick(activity)
                }
            }
            log("SettingHook installed: Home Activity.onResume avatar long click watcher")
        }.onFailure {
            log("SettingHook: Failed to install Activity.onResume avatar long click watcher", it)
        }

        // 2. Hook BaseMainFrameFragment / MainFragment.onViewCreated
        HOME_MAIN_FRAGMENTS.mapNotNull(classLoader::findClassOrNull).distinct().forEach { fragmentClass ->
            val onViewCreated = fragmentClass.methodsNamed("onViewCreated")
                .firstOrNull { it.parameterCount == 2 && it.parameterTypes[0] == View::class.java }
            if (onViewCreated != null) {
                env.hookAfter(onViewCreated) { param ->
                    val fragment = param.thisObject ?: return@hookAfter
                    val view = param.args[0] as? View ?: return@hookAfter
                    val activity = fragment.callMethod("getActivity") as? Activity ?: return@hookAfter
                    attachAvatarLongClick(activity, view)
                }
                log("SettingHook installed avatar watcher on ${fragmentClass.name}.onViewCreated")
            }
        }
    }

    private fun isHomeActivity(className: String): Boolean =
        className in HOME_ACTIVITIES || className.contains("MainActivity", ignoreCase = true)

    private fun attachAvatarLongClick(activity: Activity, root: View? = null) {
        val targetRoot = root ?: runCatching { activity.window?.peekDecorView() ?: activity.window?.decorView }.getOrNull() ?: return
        bindAvatarLongClickToViewTree(activity, targetRoot)
        targetRoot.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                bindAvatarLongClickToViewTree(activity, targetRoot)
            }
        }
    }

    private fun bindAvatarLongClickToViewTree(activity: Activity, root: View) {
        val avatarId = resolveResId(activity, "avatar", FALLBACK_AVATAR_ID)
        val avatarLayoutId = resolveResId(activity, "avatar_layout", FALLBACK_AVATAR_LAYOUT_ID)

        val onLongClick = View.OnLongClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            log("Home avatar long clicked, opening BBZQ settings")
            ModuleSettingsNavigator.open(activity, runtimeSnapshot())
            true
        }

        var bound = false
        if (avatarId != 0) {
            root.findViewById<View>(avatarId)?.let { avatarView ->
                avatarView.setOnLongClickListener(onLongClick)
                bound = true
            }
        }
        if (avatarLayoutId != 0) {
            root.findViewById<View>(avatarLayoutId)?.let { layoutView ->
                layoutView.setOnLongClickListener(onLongClick)
                bound = true
            }
        }
        if (bound) {
            log("Home avatar long click listener attached in ${activity.javaClass.simpleName}")
        }
    }

    private fun resolveResId(activity: Activity, name: String, fallback: Int): Int {
        val resId = runCatching {
            activity.resources.getIdentifier(name, "id", activity.packageName)
        }.getOrNull() ?: 0
        return if (resId != 0) resId else fallback
    }

    private companion object {
        private const val FALLBACK_AVATAR_ID = 0x7f09021c
        private const val FALLBACK_AVATAR_LAYOUT_ID = 0x7f090237
        private val HOME_ACTIVITIES = setOf(
            "tv.danmaku.bili.MainActivityV2",
            "com.bilibili.app.MainActivity",
        )
        private val HOME_MAIN_FRAGMENTS = setOf(
            "tv.danmaku.bili.ui.main2.basic.BaseMainFrameFragment",
            "tv.danmaku.p9138bili.p9228ui.main2.basic.BaseMainFrameFragment",
            "tv.danmaku.bili.ui.main2.MainFragment",
            "tv.danmaku.p9138bili.p9228ui.main2.MainFragment",
        )
        private val JOIN_US_KEYS = arrayOf("JoinUs", "pref_key_joinus")
        private const val ENTRY_TITLE = "高级设置"
        private const val ENTRY_SUMMARY = "BBZQ 设置"
        private val HELP_FRAGMENT_CLASSES = arrayOf(
            "com.bilibili.app.preferences.fragment.HelpFragment",
            "com.bilibili.p4439app.preferences.fragment.HelpFragment",
            "com.bilibili.app.preferences.fragment.WideHelpFragment",
            "com.bilibili.p4439app.preferences.fragment.WideHelpFragment",
        )
    }
}
