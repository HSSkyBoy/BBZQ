package io.github.bbzq.feats.hook

import android.app.Activity
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsNavigator
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostAccountResolver
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.methodsNamed
import io.github.bbzq.feats.newInstanceOrNull
import io.github.bbzq.feats.symbol.SettingsSymbols
import java.lang.reflect.Proxy
import kotlin.LazyThreadSafetyMode

class SettingHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val settingsSymbols: SettingsSymbols? by lazy(LazyThreadSafetyMode.NONE) {
        env.symbols?.settings
    }

    override fun startHook() {
        val symbols = settingsSymbols ?: run {
            log("SettingHook skipped: symbols missing")
            return
        }
        val methods = symbols.restoreFragmentMethods(classLoader)
        val preferenceClass = symbols.restorePreferenceClass(classLoader)
        if (methods.isEmpty() || preferenceClass == null) {
            log("SettingHook skipped: cached symbols failed to restore")
            return
        }

        methods.forEach { method ->
            env.hookAfter(method) { param ->
                param.thisObject?.let { fragment ->
                    runCatching { injectEntry(fragment, preferenceClass) }
                        .onFailure { log("Failed to inject BBZQ settings entry", it) }
                }
            }
        }
        log("startHook: Setting, entries=${methods.size}")
    }

    private fun injectEntry(fragment: Any, preferenceClass: Class<*>) {
        val activity = fragment.callMethod("getActivity") as? Activity ?: return
        val existing = fragment.callMethod("findPreference", ENTRY_KEY)
        val group = TARGET_GROUP_KEYS.firstNotNullOfOrNull { key ->
            fragment.callMethod("findPreference", key)
        } ?: fragment.callMethod("getPreferenceScreen") ?: return
        if (existing != null) return

        val entry = createPreference(fragment, activity, preferenceClass) ?: return
        group.callMethod("addPreference", entry)
        log("Injected BBZQ settings entry into ${fragment.javaClass.name}")
    }

    private fun createPreference(fragment: Any, activity: Activity, preferenceClass: Class<*>): Any? {
        val preference = preferenceClass.newInstanceOrNull(activity) ?: return null
        preference.callMethod("setKey", ENTRY_KEY)
        preference.callMethod("setTitle", ENTRY_TITLE)
        preference.callMethod("setSummary", ENTRY_SUMMARY)
        preference.callMethod("setPersistent", false)
        preference.callMethod("setSelectable", true)
        resolveAnchorOrder(fragment)?.let { preference.callMethod("setOrder", it + 1) }

        val setter = preference.javaClass.methodsNamed("setOnPreferenceClickListener")
            .firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isInterface }
            ?: return preference
        val listenerType = setter.parameterTypes[0]
        val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { _, method, _ ->
            if (method.name == "onPreferenceClick") {
                ModuleSettingsNavigator.open(activity, runtimeSnapshot())
                true
            } else {
                null
            }
        }
        setter.invoke(preference, listener)
        return preference
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

    private fun resolveAnchorOrder(fragment: Any): Int? {
        return ANCHOR_KEYS.firstNotNullOfOrNull { key ->
            val preference = fragment.callMethod("findPreference", key) ?: return@firstNotNullOfOrNull null
            preference.callMethod("getOrder") as? Int
        }
    }

    private companion object {
        private const val ENTRY_KEY = "bbzq_settings"
        private const val ENTRY_TITLE = "高级设置"
        private const val ENTRY_SUMMARY = "BBZQ 设置"

        private val TARGET_GROUP_KEYS = arrayOf(
            "pref_key_tools_setting",
            "categoryAdvanced",
        )
        private val ANCHOR_KEYS = arrayOf(
            "pref_key_side_center",
            "pref_clear_storage",
        )
    }
}
