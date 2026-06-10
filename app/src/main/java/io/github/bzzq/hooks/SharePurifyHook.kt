package io.github.bzzq.hooks

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.bzzq.ModuleSettings
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Lightweight share purification inspired by BR/BRX:
 * strip tracking params from copied/shared URLs while keeping important location params.
 */
class SharePurifyHook(
    override val targetPackageName: String,
) : AppHook {
    override fun install(
        xposed: XposedInterface,
        packageReady: PackageReadyParam,
        log: (String, Throwable?) -> Unit,
    ) {
        val prefs = xposed.getRemotePreferences(ModuleSettings.PREFS_NAME)
        hookClipboardManager(xposed, prefs, log)
        hookActivityShareIntent(xposed, prefs, log)
    }

    private fun hookClipboardManager(
        xposed: XposedInterface,
        prefs: android.content.SharedPreferences,
        log: (String, Throwable?) -> Unit,
    ) {
        runCatching {
            val setPrimaryClip = ClipboardManager::class.java.getDeclaredMethod("setPrimaryClip", ClipData::class.java)
            xposed.hook(setPrimaryClip)
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                .intercept { chain ->
                    if (!ModuleSettings.isPurifyShareEnabled(prefs)) {
                        return@intercept chain.proceed()
                    }

                    val originalClip = chain.getArg(0) as? ClipData ?: return@intercept chain.proceed()
                    val sanitized = sanitizeClipData(originalClip)
                    if (sanitized != null) {
                        return@intercept chain.proceed(arrayOf<Any>(sanitized))
                    }
                    chain.proceed()
                }
            log("Installed share-purify clipboard hook", null)
        }.onFailure {
            log("Failed to install share-purify clipboard hook", it)
        }
    }

    private fun hookActivityShareIntent(
        xposed: XposedInterface,
        prefs: android.content.SharedPreferences,
        log: (String, Throwable?) -> Unit,
    ) {
        runCatching {
            val activityClass = Activity::class.java
            val startActivity = activityClass.getDeclaredMethod("startActivity", Intent::class.java)
            val startActivityWithBundle = activityClass.getDeclaredMethod("startActivity", Intent::class.java, Bundle::class.java)

            listOf(startActivity, startActivityWithBundle).forEach { method ->
                xposed.hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                    .intercept { chain ->
                        if (!ModuleSettings.isPurifyShareEnabled(prefs)) {
                            return@intercept chain.proceed()
                        }

                        val originalIntent = chain.getArg(0) as? Intent ?: return@intercept chain.proceed()
                        val sanitizedIntent = sanitizeIntent(originalIntent) ?: return@intercept chain.proceed()
                        val args = Array(method.parameterTypes.size) { index ->
                            if (index == 0) sanitizedIntent else chain.getArg(index)
                        }
                        chain.proceed(args)
                    }
            }
            log("Installed share-purify intent hook", null)
        }.onFailure {
            log("Failed to install share-purify intent hook", it)
        }
    }

    private fun sanitizeClipData(clipData: ClipData): ClipData? {
        if (clipData.itemCount == 0) return null
        val item = clipData.getItemAt(0)
        val text = item.text?.toString() ?: return null
        val sanitizedText = sanitizeText(text)
        if (sanitizedText == text) return null
        return ClipData.newPlainText(clipData.description?.label ?: "text", sanitizedText)
    }

    private fun sanitizeIntent(intent: Intent): Intent? {
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE && action != Intent.ACTION_CHOOSER) {
            return null
        }

        var changed = false
        val cloned = Intent(intent)

        val text = cloned.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            val sanitizedText = sanitizeText(text)
            if (sanitizedText != text) {
                cloned.putExtra(Intent.EXTRA_TEXT, sanitizedText)
                changed = true
            }
        }

        val data = cloned.data
        if (data != null) {
            val sanitizedData = sanitizeUri(data)
            if (sanitizedData != data) {
                cloned.data = sanitizedData
                changed = true
            }
        }

        return cloned.takeIf { changed }
    }

    private fun sanitizeText(text: String): String {
        return URL_REGEX.replace(text) { match ->
            val original = match.value
            val (body, suffix) = splitTrailingPunctuation(original)
            sanitizeUrl(body) + suffix
        }
    }

    private fun splitTrailingPunctuation(text: String): Pair<String, String> {
        var splitIndex = text.length
        while (splitIndex > 0 && text[splitIndex - 1] in TRAILING_PUNCTUATION) {
            splitIndex--
        }
        return text.substring(0, splitIndex) to text.substring(splitIndex)
    }

    private fun sanitizeUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val sanitizedUri = sanitizeUri(uri)
        return sanitizedUri.toString()
    }

    private fun sanitizeUri(uri: Uri): Uri {
        val host = uri.host.orEmpty()
        if (!host.contains("bilibili.com") && !host.contains("b23.tv")) return uri
        if (host.contains("b23.tv")) return uri

        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { name ->
            val value = uri.getQueryParameter(name).orEmpty()
            if (!shouldKeepQuery(name, value)) return@forEach
            builder.appendQueryParameter(name, value)
        }

        val fragment = uri.fragment.orEmpty()
        if (fragment.startsWith("reply")) {
            builder.fragment(fragment)
        } else {
            builder.encodedFragment(null)
        }
        return builder.build()
    }

    private fun shouldKeepQuery(name: String, value: String): Boolean {
        val rule = WHITELIST_QUERIES.firstOrNull { it.name == name } ?: return false
        return value.isNotEmpty() && value !in rule.ignoredValues
    }

    private data class WhitelistQuery(val name: String, val ignoredValues: Set<String> = emptySet())

    private companion object {
        private val URL_REGEX = Regex("""https?://\S+""")
        private val TRAILING_PUNCTUATION = setOf(')', '）', '】', '>', '»', ',', '.', ';', '!', '?', '，', '。', '；')
        private val WHITELIST_QUERIES = listOf(
            WhitelistQuery("start_progress"),
            WhitelistQuery("p", setOf("1")),
            WhitelistQuery("topic_id"),
            WhitelistQuery("comment_on"),
            WhitelistQuery("comment_root_id"),
            WhitelistQuery("comment_secondary_id"),
            WhitelistQuery("type"),
            WhitelistQuery("itemsId"),
        )
    }
}
