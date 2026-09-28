package io.github.bbzq.feats.hook

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.Hooker
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.from
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class StoryDetailRedirectHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.packageName != TARGET_PACKAGE || env.processName != env.packageName) return
        if (!ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) {
            log("startHook: StoryDetailRedirect disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }
        synchronized(lock) {
            if (hookInstalled) return
            hookInstalled = true
        }
        val backends = StoryDetailBackend.entries.filter { backend ->
            backend.activityClassName.from(classLoader)?.let(Activity::class.java::isAssignableFrom) == true
        }
        if (backends.isEmpty()) {
            synchronized(lock) { hookInstalled = false }
            log("startHook: StoryDetailRedirect skipped because no detail activity is available")
            return
        }
        val boundary = installLaunchBoundary(backends)
        if (boundary == 0) {
            synchronized(lock) { hookInstalled = false }
            log("startHook: StoryDetailRedirect skipped because no launch boundary was installed")
            return
        }
        val suppression = installAutoStorySuppression()
        val sanitizer = installIntentHandlerSanitizer()
        log(
            "startHook: StoryDetailRedirect (boundary=$boundary, autoStory=$suppression," +
                " intentSanitizer=$sanitizer, backend=${backends.joinToString("+") { it.name.lowercase() }})",
        )
    }

    private fun register(method: Method, hooker: Hooker): Boolean =
        env.hookBefore(method, hooker)

    private fun installLaunchBoundary(backends: List<StoryDetailBackend>): Int =
        Instrumentation::class.java.declaredMethods
            .filter { method ->
                method.name == "execStartActivity" && !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.count { it == Intent::class.java } == 1
            }
            .distinctBy(Method::toGenericString)
            .count { method ->
                val intentIndex = method.parameterTypes.indexOf(Intent::class.java)
                register(method) { param ->
                    val original = param.args.getOrNull(intentIndex) as? Intent ?: return@register
                    if (!StoryDetailRoutePolicy.isStrictStoryVideoRoute(original.data?.toString())) return@register
                    if (!ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) return@register
                    val caller = param.args.firstOrNull()
                    if (isDetailActivity(caller)) return@register
                    when (val rewritten = StoryDetailIntentFactory.rewrite(original, backends)) {
                        is StoryDetailIntentFactory.Rewrite.Applied -> {
                            param.args[intentIndex] = rewritten.intent
                            if (loggedResults.add("applied-${rewritten.backend.name}")) {
                                log("StoryDetailRedirect applied at activity launch, backend=${rewritten.backend.name.lowercase()}")
                            }
                        }
                        is StoryDetailIntentFactory.Rewrite.Skipped -> logSkipOnce(original, rewritten.reason)
                    }
                }
            }

    private fun installAutoStorySuppression(): Int {
        val boolValue = BOOL_VALUE_CLASS.from(classLoader) ?: return 0
        val playConfig = PLAY_CONFIG_CLASS.from(classLoader) ?: return 0
        val defaultValue = runCatching {
            boolValue.declaredMethods.firstOrNull { method ->
                method.name == "getDefaultInstance" && Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 && method.returnType == boolValue
            }?.apply { isAccessible = true }?.invoke(null)
        }.getOrNull()?.takeIf(boolValue::isInstance) ?: return 0
        return AUTO_STORY_GETTERS.count { name ->
            val method = playConfig.declaredMethods.firstOrNull { method ->
                method.name == name && !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 && method.returnType == boolValue
            } ?: return@count false
            register(method) { param ->
                if (ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) param.result = defaultValue
            }
        }
    }

    private fun installIntentHandlerSanitizer(): Int {
        val owner = INTENT_HANDLER_CLASS.from(classLoader)
            ?.takeIf(Activity::class.java::isAssignableFrom) ?: return 0
        val method = runCatching { owner.getDeclaredMethod("onCreate", Bundle::class.java) }
            .getOrNull()?.takeIf { !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE } ?: return 0
        val installed = register(method) { param ->
            if (!ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) return@register
            val activity = param.thisObject as? Activity ?: return@register
            val original = activity.intent ?: return@register
            val uri = original.data?.toString()?.let(StoryDetailRoutePolicy::sanitizeIntentHandlerUri)
                ?: return@register
            // 此处只清理 Story 提示，目标页面仍由启动边界统一改写。
            activity.intent = Intent(original).apply { data = Uri.parse(uri) }
        }
        return if (installed) 1 else 0
    }

    private fun logSkipOnce(intent: Intent, reason: StoryLaunchSkip?) {
        val label = reason?.name?.lowercase() ?: "build-or-validate-failed"
        if (!loggedResults.add(label)) return
        val shape = runCatching {
            val uri = intent.data
            val queryKeys = uri?.queryParameterNames.orEmpty().take(MAX_LOGGED_KEYS)
                .joinToString(",") { it.take(MAX_LOGGED_KEY_LENGTH) }
            val extras = intent.extras
            val extraKeys = extras?.keySet().orEmpty().take(MAX_LOGGED_KEYS).joinToString(",") { key ->
                @Suppress("DEPRECATION")
                val type = runCatching { extras?.get(key)?.javaClass?.simpleName }.getOrNull() ?: "null"
                "${key.take(MAX_LOGGED_KEY_LENGTH)}:$type"
            }
            "root=${uri?.scheme}://${uri?.host} hasPath=${!uri?.path?.trim('/').isNullOrEmpty()}" +
                " queryKeys=[$queryKeys] extraKeys=[$extraKeys]"
        }.getOrDefault("shape-unavailable")
        log("StoryDetailRedirect kept host intent, reason=$label $shape")
    }

    private fun isDetailActivity(who: Any?): Boolean {
        val name = who?.javaClass?.name ?: return false
        return name.contains("UnitedBizDetailsActivity") || name.contains("VideoDetailsActivity")
    }

    private companion object {
        private const val TARGET_PACKAGE = "tv.danmaku.bili"
        private const val INTENT_HANDLER_CLASS = "tv.danmaku.bili.ui.intent.IntentHandlerActivity"
        private const val BOOL_VALUE_CLASS = "com.bapis.bilibili.app.distribution.BoolValue"
        private const val PLAY_CONFIG_CLASS = "com.bapis.bilibili.app.distribution.setting.play.PlayConfig"
        private const val MAX_LOGGED_KEYS = 24
        private const val MAX_LOGGED_KEY_LENGTH = 40
        private val AUTO_STORY_GETTERS = listOf("getLandscapeAutoStory", "getShouldAutoStory")
        private val lock = Any()
        private var hookInstalled = false
        private val loggedResults = ConcurrentHashMap.newKeySet<String>()
    }
}