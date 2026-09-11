package io.github.bbzq.feats.hook

import android.app.Activity
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsNavigator
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostAccountResolver
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
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
            putString(ModuleSettings.KEY_HOST_ACCOUNT_UID, if (account.loggedIn) account.uid else "")
            putString(ModuleSettings.KEY_HOST_ACCOUNT_NAME, if (account.loggedIn) account.userName else "")
            if (account.loggedIn && account.accessKey.isNotBlank()) {
                putString(ModuleSettings.KEY_LAST_ACCESS_KEY, account.accessKey)
            }
        }

    private companion object {
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
