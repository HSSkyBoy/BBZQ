package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter
import java.lang.reflect.Modifier
import kotlin.jvm.functions.Function0

/**
 * Lets Bilibili's own home-feed listener decide when to load the next page.
 *
 * The host listener already debounces requests and accounts for grid spans.  We only enlarge
 * its prefetch window instead of invoking its callback directly.
 */
class HomeRecommendPreloadHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName || !ModuleSettings.isHomeRecommendPreloadEnabled(prefs)) return

        val listenerClass = HOME_LOAD_MORE_LISTENERS.firstNotNullOfOrNull { name ->
            runCatching { classLoader.loadClass(name) }.getOrNull()
                ?.takeIf { name == LEGACY_LOAD_MORE_LISTENER || isLoadMoreListener(it) }
        } ?: run {
            log("HomeRecommendPreload skipped: listener class unavailable")
            return
        }
        val constructor = listenerClass.declaredConstructors.singleOrNull { candidate ->
            candidate.parameterCount == 1 &&
                Function0::class.java.isAssignableFrom(candidate.parameterTypes.single())
        } ?: run {
            log("HomeRecommendPreload skipped: listener constructor is ambiguous")
            return
        }
        val distanceField = listenerClass.declaredFields.singleOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                (field.type == Int::class.javaPrimitiveType || field.type == Int::class.javaObjectType)
        } ?: run {
            log("HomeRecommendPreload skipped: prefetch-distance field is ambiguous")
            return
        }

        constructor.isAccessible = true
        distanceField.isAccessible = true
        env.hookAfter(constructor) { param ->
            val listener = param.thisObject ?: return@hookAfter
            runCatching {
                val current = (distanceField.get(listener) as? Number)?.toInt() ?: 0
                if (current < TARGET_PREFETCH_DISTANCE) {
                    distanceField.set(listener, TARGET_PREFETCH_DISTANCE)
                }
            }.onFailure { log("HomeRecommendPreload failed to update prefetch distance", it) }
        }
        log("HomeRecommendPreload set native prefetch distance to $TARGET_PREFETCH_DISTANCE")
    }

    private fun isLoadMoreListener(type: Class<*>): Boolean =
        type.superclass?.name == RECYCLER_SCROLL_LISTENER &&
            type.interfaces.any { it.name == RECYCLER_CHILD_ATTACH_LISTENER }

    private companion object {
        // widget.g/h/i drift across builds; 9.19.0 moved the listener to the root package as d571.
        private const val LEGACY_LOAD_MORE_LISTENER = "com.bilibili.pegasus.widget.h"
        private val HOME_LOAD_MORE_LISTENERS = listOf(
            LEGACY_LOAD_MORE_LISTENER,
            "com.bilibili.pegasus.widget.g",
            "com.bilibili.pegasus.widget.i",
            "d571",
        )
        private const val RECYCLER_SCROLL_LISTENER = "androidx.recyclerview.widget.RecyclerView\$OnScrollListener"
        private const val RECYCLER_CHILD_ATTACH_LISTENER = "androidx.recyclerview.widget.RecyclerView\$OnChildAttachStateChangeListener"
        private const val TARGET_PREFETCH_DISTANCE = 4
    }
}
