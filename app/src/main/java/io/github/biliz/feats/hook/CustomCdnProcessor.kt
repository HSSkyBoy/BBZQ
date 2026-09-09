package io.github.biliz.feats.hook

import android.content.SharedPreferences
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.allFields
import io.github.biliz.feats.callMethod
import java.lang.reflect.Modifier

/**
 * Core engine for safely rewriting video, audio, and backup URLs to custom UPOS CDN hosts.
 */
object CustomCdnProcessor {

    private val PC_DN_REGEX = Regex(
        """(p[0-9]*|m[0-9]*|sz|hz|bj|sh|cd|cq|fs|gz|wh|xa|tj|zz|nn)-[a-zA-Z0-9-]+\.(bilivideo|mcdn\.bilivideo|szbdyd)\.(com|cn)""",
        RegexOption.IGNORE_CASE,
    )

    fun rewriteResponse(
        response: Any?,
        prefs: SharedPreferences,
        log: (String, Throwable?) -> Unit = { _, _ -> },
    ) {
        val host = ModuleSettings.getCustomCdnHost(prefs)
        if (!ModuleSettings.isCustomCdnEnabled(prefs) || host == null || response == null) return

        runCatching {
            sequenceOf(
                response.callMethod("getVideoInfo"),
                response.callMethod("getVodInfo"),
                response.callMethod("getViewInfo"),
                response,
            ).filterNotNull().distinct().forEach { rewriteVideoInfo(it, host) }
        }.onFailure { log("CustomCdnProcessor: response rewrite failed", it) }
    }

    private fun rewriteVideoInfo(videoInfo: Any, host: String) {
        val streams = videoInfo.callMethod("getStreamListList")
            ?: videoInfo.callMethod("getStreamList")
        (streams as? Iterable<*>)?.forEach { stream ->
            stream ?: return@forEach
            listOf("getDashVideo", "getMultiDashVideo", "getSegmentVideo")
                .forEach { getter -> stream.callMethod(getter)?.let { rewriteVideoContent(it, host) } }
            stream.callMethod("getContent")?.callMethod("getValue")
                ?.let { rewriteVideoContent(it, host) }
            rewriteAudioLists(stream, host)
        }
        rewriteAudioLists(videoInfo, host)
    }

    private fun rewriteVideoContent(content: Any, host: String) {
        if (content.callMethod("getBaseUrl") is String || content.callMethod("getUrl") is String) {
            rewriteUrlItem(content, host)
        }
        val dashVideos = content.callMethod("getDashVideosList") ?: content.callMethod("getDashVideos")
        (dashVideos as? Iterable<*>)
            ?.forEach { it?.let { item -> rewriteUrlItem(item, host) } }
        val segments = content.callMethod("getSegmentList") ?: content.callMethod("getSegment")
        (segments as? Iterable<*>)
            ?.forEach { it?.let { item -> rewriteUrlItem(item, host) } }
    }

    private fun rewriteAudioLists(owner: Any, host: String) {
        listOf("getDashAudioList", "getDashAudioListList", "getAudioDashVideoList", "getDashAudio")
            .forEach { getter ->
                when (val result = owner.callMethod(getter)) {
                    is Iterable<*> -> result.forEach { it?.let { item -> rewriteUrlItem(item, host) } }
                    else -> result?.let { rewriteUrlItem(it, host) }
                }
            }
    }

    private fun rewriteUrlItem(item: Any, selectedHost: String) {
        val baseGetter = when {
            item.callMethod("getBaseUrl") is String -> "getBaseUrl"
            item.callMethod("getUrl") is String -> "getUrl"
            else -> null
        }
        val baseSetter = when (baseGetter) {
            "getBaseUrl" -> "setBaseUrl"
            "getUrl" -> "setUrl"
            else -> null
        }
        val base = (baseGetter?.let { item.callMethod(it) } as? String)
            ?: findHttpStringFieldValue(item)
            ?: return
        if (base.isBlank()) return
        val rawBackups = item.callMethod("getBackupUrlList")
            ?: item.callMethod("getBackupUrl")
            ?: findUrlListFieldValue(item)
        val backups = (rawBackups as? Iterable<*>)
            ?.filterIsInstance<String>().orEmpty()
        val source = listOf(base).plus(backups).firstOrNull { !isPCdn(it) } ?: return
        val rewrittenBase = replaceHost(source, selectedHost)
        val rewrittenBackups = buildList {
            addAll(backups.filter { !isPCdn(it) }.take(2).map { replaceHost(it, selectedHost) })
            add(source)
        }.filter { it != rewrittenBase }.distinct()

        if (baseSetter == null || !invokeOneArg(item, baseSetter, rewrittenBase)) {
            replaceStoredValue(item, base, rewrittenBase)
        }
        clearAndAddBackups(item, rawBackups, rewrittenBackups)
    }

    private fun findHttpStringFieldValue(target: Any): String? =
        target.javaClass.allFields().mapNotNull { field ->
            if (Modifier.isStatic(field.modifiers) || field.type != String::class.java) null
            else runCatching { field.get(target) as? String }.getOrNull()
        }.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    private fun findUrlListFieldValue(target: Any): Iterable<*>? =
        target.javaClass.allFields().mapNotNull { field ->
            if (Modifier.isStatic(field.modifiers)) null
            else runCatching { field.get(target) as? Iterable<*> }.getOrNull()
        }.firstOrNull { values ->
            values.all { it is String } && values.any {
                val value = it as String
                value.startsWith("http://") || value.startsWith("https://")
            }
        }

    private fun clearAndAddBackups(item: Any, original: Any?, urls: List<String>) {
        val cleared = invokeNoArg(item, "clearBackupUrl") || invokeNoArg(item, "clearBackupUrlList")
        val added = urls.isEmpty() ||
            invokeOneArg(item, "addAllBackupUrl", urls) ||
            invokeOneArg(item, "addAllBackupUrlList", urls)
        if (!cleared || !added) {
            replaceStoredValue(item, original, urls)
        }
    }

    private fun replaceStoredValue(target: Any, original: Any?, replacement: Any): Boolean {
        if (original == null) return false
        return target.javaClass.allFields().firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) && runCatching {
                val value = field.get(target)
                value === original || (original is String && value == original)
            }.getOrDefault(false)
        }?.let { field ->
            runCatching {
                field.isAccessible = true
                field.set(target, replacement)
                true
            }.getOrDefault(false)
        } ?: false
    }

    private fun invokeNoArg(target: Any, methodName: String): Boolean =
        target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }?.let { method ->
            runCatching {
                method.isAccessible = true
                method.invoke(target)
                true
            }.getOrDefault(false)
        } ?: false

    private fun invokeOneArg(target: Any, methodName: String, arg: Any): Boolean =
        target.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isAssignableFrom(arg.javaClass)
        }?.let { method ->
            runCatching {
                method.isAccessible = true
                method.invoke(target, arg)
                true
            }.getOrDefault(false)
        } ?: false

    fun isPCdn(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        return host.contains("mcdn") ||
            host.contains("szbdyd") ||
            host.contains("p2p") ||
            host.startsWith("p-") ||
            host.startsWith("m-") ||
            PC_DN_REGEX.containsMatchIn(host)
    }

    fun replaceHost(url: String, newHost: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return url
        val pathStart = url.indexOf('/', schemeEnd + 3)
        return if (pathStart == -1) {
            url.substring(0, schemeEnd + 3) + newHost
        } else {
            url.substring(0, schemeEnd + 3) + newHost + url.substring(pathStart)
        }
    }
}
