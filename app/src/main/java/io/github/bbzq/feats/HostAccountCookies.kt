package io.github.bbzq.feats

import android.content.Context
import okhttp3.Headers

/** The web login cookies of the host account, which the web endpoints on a resolver look at. */
object HostAccountCookies {
    /** A `Cookie` header value such as `SESSDATA=...; bili_jct=...`, or empty without a login. */
    fun header(context: Context): String = runCatching {
        val accountsClass = context.classLoader.loadClass("com.bilibili.lib.accounts.BiliAccounts")
        val accounts = accountsClass.getMethod("get", Context::class.java).invoke(null, context)
        val info = accountsClass.getMethod("getAccountCookie").invoke(accounts) ?: return@runCatching ""
        val cookies = info.javaClass.getField("cookies").get(info) as? List<*> ?: return@runCatching ""
        cookies.mapNotNull { bean ->
            bean ?: return@mapNotNull null
            val name = bean.javaClass.getField("name").get(bean)?.toString()?.trim()
            val value = bean.javaClass.getField("value").get(bean)?.toString()?.trim()
            if (name.isNullOrBlank() || value.isNullOrBlank()) null else "$name=$value"
        }.joinToString("; ")
    }.getOrDefault("")

    /** Headers for a resolver request: the cookie only when the user chose to share the account. */
    fun headers(context: Context?, share: Boolean): Headers {
        val builder = Headers.Builder()
        if (share && context != null) {
            header(context).takeIf { it.isNotEmpty() }?.let { builder.addUnsafeNonAscii("Cookie", it) }
        }
        return builder.build()
    }
}
