package io.github.bbzq.feats.hook

import android.content.Context
import io.github.bbzq.AccessKeyRepository
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.findClassOrNull
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class AccessKeyHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (env.processName != env.packageName) return

        AccessKeyRepository.register {
            runCatching { readAccessKey() }
                .onFailure { log("AccessKey read failed", it) }
                .getOrNull()
        }
        log("AccessKeyHook installed")
    }

    private fun readAccessKey(): String? {
        val accountClass = ACCOUNT_CLASS_NAMES.firstNotNullOfOrNull(classLoader::findClassOrNull) ?: run {
            log("AccessKey: BiliAccounts class not found")
            return null
        }

        val getMethod = accountClass.findAccountGetter() ?: run {
            log("AccessKey: static getter not found on ${accountClass.name}")
            return null
        }
        val account = runCatching {
            val args = if (getMethod.parameterCount == 0) emptyArray() else arrayOf<Any?>(env.hostContext)
            ReflectionUtils.safeInvoke(getMethod, null, *args) as? Any
        }.getOrNull() ?: return null

        val accessKeyMethod = accountClass.findAccessKeyMethod() ?: run {
            log("AccessKey: getAccessKey method not found on ${accountClass.name}")
            return null
        }
        return (accessKeyMethod.invoke(account) as? String)
            ?.takeIf(AccessKeyRepository::looksLikeAccessKey)
    }

    private fun Class<*>.findAccountGetter(): Method? =
        allMethods()
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.returnType == this &&
                    (method.parameterCount == 0 || method.hasContextParameter())
            }
            .sortedWith(compareBy<Method> { if (it.name == "get") 0 else 1 }.thenBy { it.parameterCount })
            .firstOrNull()
            ?.apply { isAccessible = true }

    private fun Class<*>.findAccessKeyMethod(): Method? =
        allMethods()
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == String::class.java
            }
            .sortedWith(
                compareBy<Method> {
                    when (it.name) {
                        "getAccessKey" -> 0
                        "accessKey" -> 1
                        else -> 2
                    }
                }.thenBy { it.name },
            )
            .firstOrNull { it.name.contains("access", ignoreCase = true) }
            ?.apply { isAccessible = true }

    private fun Method.hasContextParameter(): Boolean =
        parameterCount == 1 && Context::class.java.isAssignableFrom(parameterTypes[0])

    private companion object {
        private val ACCOUNT_CLASS_NAMES = arrayOf(
            "com.bilibili.lib.accounts.BiliAccounts",
            "com.bilibili.app.accounts.BiliAccounts",
            "com.bilibili.p4439app.accounts.BiliAccounts",
        )
    }
}
