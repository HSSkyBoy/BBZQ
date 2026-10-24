package io.github.bbzq.feats

import android.content.Context
import io.github.bbzq.AccessKeyRepository
import java.lang.reflect.Modifier

internal object HostAccountResolver {
    fun resolve(context: Context, classLoader: ClassLoader): Snapshot {
        return runCatching {
            val accountClass = ACCOUNT_CLASS_NAMES.firstNotNullOfOrNull(classLoader::findClassOrNull)
                ?: return@runCatching Snapshot.LOGGED_OUT
            val getMethod = accountClass.allMethods().firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.returnType == accountClass && method.parameterCount == 1 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0])
            } ?: return@runCatching Snapshot.LOGGED_OUT
            val account = getMethod.invoke(null, context) ?: return@runCatching Snapshot.LOGGED_OUT
            val loginMethods = accountClass.allMethods().filter { method ->
                method.parameterCount == 0 && method.returnType == Boolean::class.javaPrimitiveType &&
                    (method.name == "isLogin" || Modifier.isStatic(method.modifiers))
            }.toList()
            val loggedIn = loginMethods.any { method ->
                runCatching {
                    method.invoke(if (Modifier.isStatic(method.modifiers)) null else account) == true
                }.getOrDefault(false)
            }
            if (!loggedIn) return@runCatching Snapshot.LOGGED_OUT

            val profile = resolveProfile(classLoader)
            val uid = accountClass.allMethods().asSequence()
                .filter { method ->
                    method.parameterCount == 0 && method.returnType == Long::class.javaPrimitiveType &&
                        (method.name == "mid" || Modifier.isStatic(method.modifiers))
                }
                .mapNotNull { method ->
                    runCatching {
                        method.invoke(if (Modifier.isStatic(method.modifiers)) null else account) as? Long
                    }.getOrNull()
                }
                .firstOrNull { it > 0L }
                ?: (profile?.callMethod("getMid") as? Long)?.takeIf { it > 0L }
            val accessKey = resolveAccessKey(accountClass, account)
            Snapshot(
                loggedIn = true,
                uid = uid?.toString().orEmpty(),
                userName = profile?.callMethod("getUserName")?.toString().orEmpty(),
                accessKey = accessKey,
            )
        }.getOrDefault(Snapshot.LOGGED_OUT)
    }

    private fun resolveAccessKey(accountClass: Class<*>, account: Any): String {
        return runCatching {
            accountClass.allMethods().asSequence()
                .filter { method ->
                    method.parameterCount == 0 && method.returnType == String::class.java &&
                        (method.name == "getAccessKey" || method.name == "loadAccessTokenString" ||
                            method.name.contains("accessKey", ignoreCase = true))
                }
                .mapNotNull { method ->
                    runCatching {
                        method.invoke(if (Modifier.isStatic(method.modifiers)) null else account) as? String
                    }.getOrNull()
                }
                .firstOrNull { AccessKeyRepository.looksLikeAccessKey(it) }
                .orEmpty()
        }.getOrDefault("")
    }

    private fun resolveProfile(classLoader: ClassLoader): Any? = runCatching {
        val managerClass = ACCOUNT_INFO_CLASS_NAMES.firstNotNullOfOrNull(classLoader::findClassOrNull)
            ?: return@runCatching null
        val manager = managerClass.allMethods().firstOrNull { method ->
            Modifier.isStatic(method.modifiers) && method.name == "get" && method.parameterCount == 0
        }?.invoke(null) ?: return@runCatching null
        manager.callMethod("getAccountInfoFromCache")
    }.getOrNull()

    data class Snapshot(
        val loggedIn: Boolean,
        val uid: String,
        val userName: String,
        val accessKey: String = "",
    ) {
        companion object {
            val LOGGED_OUT = Snapshot(false, "", "", "")
        }
    }

    private val ACCOUNT_CLASS_NAMES = arrayOf(
        "com.bilibili.lib.accounts.BiliAccounts",
        "com.bilibili.app.accounts.BiliAccounts",
        "com.bilibili.p4439app.accounts.BiliAccounts",
    )

    private val ACCOUNT_INFO_CLASS_NAMES = arrayOf(
        "com.bilibili.lib.accountinfo.BiliAccountInfo",
        "com.bilibili.app.accountinfo.BiliAccountInfo",
    )
}
