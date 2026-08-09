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
        val helpFragment = HELP_FRAGMENT_CLASSES.firstNotNullOfOrNull(classLoader::findClassOrNull) ?: run {
            log("SettingHook skipped: HelpFragment unavailable")
            return
        }
        val activityCreated = helpFragment.methodsNamed("onActivityCreated")
            .firstOrNull { it.parameterCount == 1 } ?: run {
            log("SettingHook skipped: HelpFragment.onActivityCreated unavailable")
            return
        }
        env.hookAfter(activityCreated) { param ->
            param.thisObject?.let(::replaceJoinUsEntry)
        }
        log("startHook: Setting replaces About Bilibili join-us entry")
    }

    private fun replaceJoinUsEntry(fragment: Any) {
        val activity = fragment.callMethod("getActivity") as? Activity ?: return
        val entry = JOIN_US_KEYS.firstNotNullOfOrNull { key ->
            fragment.callMethod("findPreference", key)
        } ?: return
        entry.callMethod("setTitle", ENTRY_TITLE)
        entry.callMethod("setSummary", ENTRY_SUMMARY)
        entry.callMethod("setPersistent", false)
        entry.callMethod("setSelectable", true)

        val setter = entry.javaClass.methodsNamed("setOnPreferenceClickListener")
            .firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isInterface } ?: return
        val listenerType = setter.parameterTypes[0]
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { _, method, _ ->
            if (method.name == "onPreferenceClick") {
                ModuleSettingsNavigator.open(activity, runtimeSnapshot())
                true
            } else {
                null
            }
        }
        setter.invoke(entry, listener)
        log("Replaced About Bilibili join-us entry in ${fragment.javaClass.name}")
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
        }

    private companion object {
        private val JOIN_US_KEYS = arrayOf("JoinUs", "pref_key_joinus")
        private const val ENTRY_TITLE = "高级设置"
        private const val ENTRY_SUMMARY = "BBZQ 设置"
        private val HELP_FRAGMENT_CLASSES = arrayOf(
            "com.bilibili.app.preferences.fragment.HelpFragment",
            "com.bilibili.p4439app.preferences.fragment.HelpFragment",
        )
    }
}
