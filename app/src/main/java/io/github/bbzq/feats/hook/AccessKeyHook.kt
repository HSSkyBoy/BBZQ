package io.github.bbzq.feats.hook

import android.content.Context
import io.github.bbzq.AccessKeyRepository
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.utils.ReflectionUtils
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

        // Proactively probe and sync to SharedPreferences if logged in
        runCatching {
            val key = readAccessKey()
            if (!key.isNullOrBlank()) {
                prefs.edit().putString(ModuleSettings.KEY_LAST_ACCESS_KEY, key).apply()
                log("AccessKey cached to preferences on hook startup")
            }
        }.onFailure {
            log("Initial AccessKey sync check failed", it)
        }

        log("AccessKeyHook installed")
    }

    private fun readAccessKey(): String? {
        // Strategy 1: AccountRuntime.getAccounts().getAccessKey() / loadAccessToken()
        resolveViaAccountRuntime()?.let { return it }

        // Strategy 2: BiliAccounts.get(context).loadAccessTokenString() / getAccessKey()
        resolveViaBiliAccounts()?.let { return it }

        return null
    }

    private fun resolveViaAccountRuntime(): String? {
        val runtimeClass = RUNTIME_CLASS_NAMES.firstNotNullOfOrNull(classLoader::findClassOrNull) ?: return null
        val accountsObj = runCatching {
            val getter = runtimeClass.allMethods().firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    (method.name == "getAccounts" || method.returnType.name.contains("IAccount"))
            }
            if (getter != null) {
                ReflectionUtils.safeInvoke(getter, null)
            } else {
                runtimeClass.declaredFields.firstOrNull { it.name == "accounts" }?.apply { isAccessible = true }?.get(null)
            }
        }.getOrNull() ?: return null

        return extractKeyFromAccountObject(accountsObj)
    }

    private fun resolveViaBiliAccounts(): String? {
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

        return extractKeyFromAccountObject(account)
    }

    private fun extractKeyFromAccountObject(obj: Any): String? {
        // 1. Try direct string return methods: getAccessKey, loadAccessTokenString, accessKey
        val directMethods = obj.javaClass.allMethods().filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.returnType == String::class.java &&
                (method.name == "getAccessKey" || method.name == "loadAccessTokenString" ||
                    method.name.contains("accessKey", ignoreCase = true))
        }.sortedBy { if (it.name == "getAccessKey") 0 else 1 }

        for (method in directMethods) {
            val key = runCatching {
                method.isAccessible = true
                method.invoke(obj) as? String
            }.getOrNull()
            if (key != null && AccessKeyRepository.looksLikeAccessKey(key)) {
                return key
            }
        }

        // 2. Try loadAccessToken() or getAccessToken() model object
        val tokenModel = runCatching {
            obj.callMethod("loadAccessToken") ?: obj.callMethod("getAccessToken")
        }.getOrNull()

        if (tokenModel != null) {
            // Check mAccessKey field
            val fieldVal = runCatching {
                tokenModel.javaClass.declaredFields.firstOrNull { it.name == "mAccessKey" || it.name == "access_token" }
                    ?.apply { isAccessible = true }
                    ?.get(tokenModel) as? String
            }.getOrNull()
            if (fieldVal != null && AccessKeyRepository.looksLikeAccessKey(fieldVal)) {
                return fieldVal
            }

            // Check getAccessKey() method on model
            val tokenKey = runCatching {
                tokenModel.callMethod("getAccessKey") as? String
            }.getOrNull()
            if (tokenKey != null && AccessKeyRepository.looksLikeAccessKey(tokenKey)) {
                return tokenKey
            }
        }

        // 3. Fallback: search any method on obj returning 32-char hex string
        return obj.javaClass.findAccessKeyMethod()?.let { method ->
            runCatching { method.invoke(obj) as? String }.getOrNull()
                ?.takeIf(AccessKeyRepository::looksLikeAccessKey)
        }
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
                        "loadAccessTokenString" -> 1
                        "accessKey" -> 2
                        else -> 3
                    }
                }.thenBy { it.name },
            )
            .firstOrNull { it.name.contains("access", ignoreCase = true) }
            ?.apply { isAccessible = true }

    private fun Method.hasContextParameter(): Boolean =
        parameterCount == 1 && Context::class.java.isAssignableFrom(parameterTypes[0])

    private companion object {
        private val RUNTIME_CLASS_NAMES = arrayOf(
            "com.bilibili.lib.accounts.AccountRuntime",
            "com.bilibili.app.accounts.AccountRuntime",
            "com.bilibili.p4439app.accounts.AccountRuntime",
        )

        private val ACCOUNT_CLASS_NAMES = arrayOf(
            "com.bilibili.lib.accounts.BiliAccounts",
            "com.bilibili.app.accounts.BiliAccounts",
            "com.bilibili.p4439app.accounts.BiliAccounts",
        )
    }
}
