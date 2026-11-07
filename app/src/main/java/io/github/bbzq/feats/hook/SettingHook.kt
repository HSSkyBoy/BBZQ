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
                log("SettingHook：已安裝至 ${fragmentClass.name}")
            }
        }
        installHomeAvatarLongClickHook()
        if (installed == 0) {
            log("SettingHook：跳過，未找到可用的 HelpFragment 類別")
        } else {
            log("SettingHook：已在 $installed 個 Fragment 類別中替換關於頁加入我們入口")
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
        log("SettingHook：已替換 ${fragment.javaClass.name} 中的加入我們入口為 BBZQ 設置")
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
            log("SettingHook：讀取宿主帳號 登入狀態=${account.loggedIn} UID長度=${account.uid.length} 用戶名空白=${account.userName.isBlank()}")
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
            log("SettingHook：已安裝首頁 Activity.onResume 長按頭像監聽")
        }.onFailure {
            log("SettingHook：安裝 Activity.onResume 長按頭像監聽失敗", it)
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
                log("SettingHook：已在 ${fragmentClass.name}.onViewCreated 安裝長按頭像監聽")
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
            val account = HostAccountResolver.resolve(activity, classLoader)
            if (!account.loggedIn) {
                log("SettingHook：首頁頭像被長按，但用戶未登入，忽略操作")
                return@OnLongClickListener false
            }
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            log("SettingHook：首頁頭像被長按，正在開啟 BBZQ 設置")
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
            log("SettingHook：已在 ${activity.javaClass.simpleName} 綁定長按頭像監聽器")
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
