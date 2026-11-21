package io.github.bbzq.feats.hook.system

import android.text.Layout
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.intercept
import java.lang.reflect.Method

class CourseIntroCrashGuardHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var reported = false

    override fun startHook() {
        if (env.processName != env.packageName || isInstalled) return
        val view = classLoader.findClassOrNull(EXPANDABLE_TEXT_VIEW) ?: return
        val cutters = view.declaredClasses.flatMap { it.declaredMethods.asList() }.filter(::isTextCutter)
        if (cutters.isEmpty()) {
            log("CourseIntroCrashGuard: cutting method not found, hook skipped")
            return
        }
        cutters.forEach { method ->
            env.intercept(method) { chain ->
                try {
                    chain.proceed()
                } catch (e: StringIndexOutOfBoundsException) {
                    if (!reported) {
                        reported = true
                        log("CourseIntroCrashGuard: stale layout, intro left uncut", e)
                    }
                    chain.getArgs().first()
                }
            }
        }
        isInstalled = true
        log("CourseIntroCrashGuard: guarding ${cutters.size} method(s)")
    }

    private fun isTextCutter(method: Method): Boolean {
        val params = method.parameterTypes
        return method.returnType == CharSequence::class.java &&
            params.size == 5 &&
            params[0] == CharSequence::class.java &&
            params[1] == Layout::class.java &&
            params[3] == Int::class.javaPrimitiveType &&
            params[4] == Boolean::class.javaPrimitiveType
    }

    private companion object {
        const val EXPANDABLE_TEXT_VIEW = "com.bilibili.ship.theseus.cheese.widget.ExpandableTextView"
    }
}
