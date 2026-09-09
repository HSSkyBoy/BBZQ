package io.github.biliz.feats.hook

import android.content.ClipData
import android.content.ClipboardManager
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.getObjectField
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore
import io.github.biliz.feats.hookBeforeAllConstructors
import io.github.biliz.feats.setObjectField
import io.github.biliz.feats.symbol.RestoredShareSymbols
import java.lang.reflect.Method

class ShareHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!isShareTransformEnabled(isMiniProgramEnabled())) return

        val symbols = env.symbols?.share?.restore(classLoader)
        if (symbols == null) {
            log("startHook: Share skipped because symbols are unavailable")
            return
        }
        var count = 0
        count += hookLegacyShareClickResult(symbols)
        count += hookLegacyShareChannels(symbols)
        count += hookShareClickResult(symbols)
        count += hookShareBaseInfo(symbols)
        count += hookModernShareContent(symbols)
        count += hookModernCopyContent(symbols)
        count += hookCopyToClipboardUtility(symbols)
        count += hookClipboardFallback()

        if (count > 0) {
            isInstalled = true
        }
        log("startHook: Share, methods=$count")
    }

    private fun hookLegacyShareClickResult(symbols: RestoredShareSymbols): Int {
        var count = 0
        symbols.legacyGetLink?.let { method ->
            env.hookAfter(method) { param ->
                val link = param.result as? String ?: return@hookAfter
                val transformAv = isMiniProgramEnabled()
                if (!isShareTransformEnabled(transformAv)) return@hookAfter

                val purified = purifyLink(link, transformAv)
                if (purified == link) return@hookAfter
                param.thisObject?.setObjectField("link", purified)
                param.result = purified
            }
            count++
        }
        symbols.legacyGetContent?.let { method ->
            env.hookAfter(method) { param ->
                val content = param.result as? String ?: return@hookAfter
                val transformAv = isMiniProgramEnabled()
                if (!isShareTransformEnabled(transformAv)) return@hookAfter

                val transformed = purifyText(content, transformAv)
                if (transformed == content) return@hookAfter
                param.thisObject?.setObjectField("content", transformed)
                param.result = transformed
            }
            count++
        }
        symbols.legacyGetShareMode?.let { method ->
            env.hookAfter(method) { param ->
                if (!isMiniProgramEnabled()) return@hookAfter
                if ((param.result as? Int)?.let { it in MINI_PROGRAM_MODE_VALUES } != true) return@hookAfter
                param.result = LINK_MODE_VALUE
                val target = param.thisObject ?: return@hookAfter
                rewriteLegacyBiliShareTitle(target)
            }
            count++
        }
        count += hookPurifiedStringSetter(symbols.legacySetLink, ::purifyLink)
        count += hookPurifiedStringSetter(symbols.legacySetContent, ::purifyText)
        symbols.legacySetShareMode?.let { method ->
            env.hookBefore(method) { param ->
                if (!isMiniProgramEnabled()) return@hookBefore
                val mode = param.args.firstOrNull() as? Int ?: return@hookBefore
                if (mode !in MINI_PROGRAM_MODE_VALUES) return@hookBefore
                param.args[0] = LINK_MODE_VALUE
                param.thisObject?.let(::rewriteLegacyBiliShareTitle)
            }
            count++
        }
        return count
    }

    private fun hookLegacyShareChannels(symbols: RestoredShareSymbols): Int {
        var count = 0
        count += hookPurifiedStringGetter(symbols.shareChannelsGetCopyLink, ::purifyLink)
        count += hookPurifiedStringGetter(symbols.shareChannelsGetJumpLink, ::purifyLink)
        count += hookPurifiedStringGetter(symbols.shareChannelsGetText, ::purifyText)
        count += hookPurifiedStringSetter(symbols.shareChannelsSetCopyLink, ::purifyLink)
        count += hookPurifiedStringSetter(symbols.shareChannelsSetJumpLink, ::purifyLink)
        count += hookPurifiedStringSetter(symbols.shareChannelsSetText, ::purifyText)
        count += hookPurifiedStringGetter(symbols.shareChannelItemGetJumpLink, ::purifyLink)
        count += hookPurifiedStringSetter(symbols.shareChannelItemSetJumpLink, ::purifyLink)
        return count
    }

    private fun hookShareClickResult(symbols: RestoredShareSymbols): Int {
        val type = symbols.shareClickResultClass ?: return 0
        return env.hookBeforeAllConstructors(type) { param ->
            rewriteShareClickResultArgs(param.args)
        }
    }

    private fun hookShareBaseInfo(symbols: RestoredShareSymbols): Int {
        val type = symbols.shareBaseInfoClass ?: return 0
        return env.hookBeforeAllConstructors(type) { param ->
            rewriteShareBaseInfoArgs(param.args)
        }
    }

    private fun hookModernShareContent(symbols: RestoredShareSymbols): Int {
        var count = 0
        symbols.shareContentClass?.let { type ->
            count += env.hookBeforeAllConstructors(type) { param ->
                rewriteShareContentArgs(param.args)
            }
            count += hookCopyMethods(symbols.shareContentCopyMethods, ::rewriteShareContentArgs)
            count += hookPurifiedStringGetter(symbols.shareContentGetLink, ::purifyLink)
            count += hookPurifiedStringGetter(symbols.shareContentGetContent, ::purifyText)
            symbols.shareContentGetMode?.let { method ->
                env.hookAfter(method) { param ->
                    if (!isMiniProgramEnabled()) return@hookAfter
                    param.result = normalizeShareMode(param.result)
                }
                count++
            }
        }
        symbols.shareBiliContentClass?.let { type ->
            count += env.hookBeforeAllConstructors(type) { param ->
                rewriteShareBiliContentArgs(param.args)
            }
            count += hookCopyMethods(symbols.shareBiliContentCopyMethods, ::rewriteShareBiliContentArgs)
            count += hookPurifiedStringGetter(symbols.shareBiliContentGetDescription, ::purifyText)
            count += hookPurifiedStringGetter(symbols.shareBiliContentGetContentUrl, ::purifyLink)
        }
        return count
    }

    private fun hookModernCopyContent(symbols: RestoredShareSymbols): Int {
        val type = symbols.copyContentClass ?: return 0
        var count = env.hookBeforeAllConstructors(type) { param ->
            val transformAv = isMiniProgramEnabled()
            if (!isShareTransformEnabled(transformAv)) return@hookBeforeAllConstructors
            val content = param.args.firstOrNull() as? String ?: return@hookBeforeAllConstructors
            param.args[0] = purifyText(content, transformAv)
        }
        symbols.copyContentGetters.forEach { method ->
            count += hookPurifiedStringGetter(method, ::purifyText)
        }
        return count
    }

    private fun hookCopyToClipboardUtility(symbols: RestoredShareSymbols): Int {
        symbols.copyUtilityMethods.forEach { method ->
            env.hookBefore(method) { param ->
                val transformAv = isMiniProgramEnabled()
                if (!isShareTransformEnabled(transformAv)) return@hookBefore
                val content = param.args.firstOrNull() as? String ?: return@hookBefore
                param.args[0] = purifyText(content, transformAv)
            }
        }
        return symbols.copyUtilityMethods.size
    }

    private fun hookCopyMethods(methods: List<Method>, rewriter: (MutableList<Any?>) -> Unit): Int {
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                rewriter(param.args)
            }
        }
        return methods.size
    }

    private fun hookPurifiedStringGetter(
        method: Method?,
        purifier: (String, Boolean) -> String,
    ): Int {
        if (method == null) return 0
        env.hookAfter(method) { param ->
            val transformAv = isMiniProgramEnabled()
            if (!isShareTransformEnabled(transformAv)) return@hookAfter
            val value = param.result as? String ?: return@hookAfter
            val purified = purifier(value, transformAv)
            if (purified != value) param.result = purified
        }
        return 1
    }

    private fun hookPurifiedStringSetter(
        method: Method?,
        purifier: (String, Boolean) -> String,
    ): Int {
        if (method == null) return 0
        env.hookBefore(method) { param ->
            val transformAv = isMiniProgramEnabled()
            if (!isShareTransformEnabled(transformAv)) return@hookBefore
            val value = param.args.firstOrNull() as? String ?: return@hookBefore
            val purified = purifier(value, transformAv)
            if (purified != value) param.args[0] = purified
        }
        return 1
    }

    private fun hookClipboardFallback(): Int {
        val method = ClipboardManager::class.java.getDeclaredMethod("setPrimaryClip", ClipData::class.java)
        env.hookBefore(method) { param ->
            val transformAv = isMiniProgramEnabled()
            if (!isShareTransformEnabled(transformAv)) return@hookBefore

            val clip = param.args.firstOrNull() as? ClipData ?: return@hookBefore
            val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                ?: return@hookBefore
            val purified = purifyText(text, transformAv)
            if (purified != text) {
                param.args[0] = ClipData.newPlainText(clip.description.label, purified)
            }
        }
        return 1
    }

    private fun rewriteShareContentArgs(args: MutableList<Any?>) {
        val transformAv = isMiniProgramEnabled()
        if (!isShareTransformEnabled(transformAv)) return
        if (args.isNotEmpty()) args[0] = normalizeShareMode(args[0])
        rewriteStringArg(args, 2, transformAv, ::purifyText)
        rewriteStringArg(args, 3, transformAv, ::purifyLink)
        rewriteBiliShareTitle(args, titleIndex = 1, contentIndex = 2, transformAv)
    }

    private fun rewriteShareClickResultArgs(args: MutableList<Any?>) {
        val transformAv = isMiniProgramEnabled()
        if (!isShareTransformEnabled(transformAv)) return
        if (args.size < SHARE_CLICK_RESULT_ARG_COUNT || args[0] !is Int) return

        rewriteStringArg(args, 2, transformAv, ::purifyText)
        rewriteStringArg(args, 3, transformAv, ::purifyLink)
        if (transformAv && (args[7] as? Int)?.let { it in MINI_PROGRAM_MODE_VALUES } == true) {
            args[7] = LINK_MODE_VALUE
        }
        rewriteBiliShareTitle(args, titleIndex = 5, contentIndex = 2, transformAv)
    }

    private fun rewriteShareBaseInfoArgs(args: MutableList<Any?>) {
        val transformAv = isMiniProgramEnabled()
        if (!isShareTransformEnabled(transformAv)) return
        rewriteStringArg(args, 1, transformAv, ::purifyText)
        rewriteStringArg(args, 2, transformAv, ::purifyLink)
        rewriteBiliShareTitle(args, titleIndex = 0, contentIndex = 1, transformAv)
    }

    private fun rewriteShareBiliContentArgs(args: MutableList<Any?>) {
        val transformAv = isMiniProgramEnabled()
        if (!isShareTransformEnabled(transformAv)) return
        rewriteStringArg(args, 1, transformAv, ::purifyText)
        rewriteStringArg(args, 3, transformAv, ::purifyLink)
    }

    private fun rewriteLegacyBiliShareTitle(target: Any) {
        val content = target.getObjectField("content")
        if (target.getObjectField("title") == BILI_TITLE) {
            target.setObjectField("title", content)
            target.setObjectField("content", BILIZ_SHARE_TEXT)
            return
        }
        (content as? String)
            ?.takeIf { it.startsWith(WATCHED_PREFIX) && !it.contains(BILIZ_SHARE_TEXT) }
            ?.let { target.setObjectField("content", "$it\n$BILIZ_SHARE_TEXT") }
    }

    private fun rewriteBiliShareTitle(
        args: MutableList<Any?>,
        titleIndex: Int,
        contentIndex: Int,
        transformAv: Boolean,
    ) {
        if (!transformAv || args.size <= maxOf(titleIndex, contentIndex)) return
        val content = args[contentIndex]
        if (args[titleIndex] == BILI_TITLE) {
            args[titleIndex] = content
            args[contentIndex] = BILIZ_SHARE_TEXT
            return
        }
        (content as? String)
            ?.takeIf { it.startsWith(WATCHED_PREFIX) && !it.contains(BILIZ_SHARE_TEXT) }
            ?.let { args[contentIndex] = "$it\n$BILIZ_SHARE_TEXT" }
    }

    private fun rewriteStringArg(
        args: MutableList<Any?>,
        index: Int,
        transformAv: Boolean,
        purifier: (String, Boolean) -> String,
    ) {
        val value = args.getOrNull(index) as? String ?: return
        args[index] = purifier(value, transformAv)
    }

    private fun normalizeShareMode(mode: Any?): Any? {
        if (!isMiniProgramEnabled() || mode == null) return mode
        if (!mode.toString().contains("MiniProgram", ignoreCase = true)) return mode
        return mode.javaClass.enumConstants
            ?.firstOrNull { it.toString() == "Link" }
            ?: mode
    }

    private fun purifyText(text: String, transformAv: Boolean): String =
        ShareLinkPurifier.purifyText(text, transformAv)

    private fun purifyLink(url: String, transformAv: Boolean): String =
        ShareLinkPurifier.purifyLink(url, transformAv)

    private fun isMiniProgramEnabled(): Boolean =
        prefs.getBoolean(ModuleSettings.KEY_MINI_PROGRAM_ENABLED, false)

    private fun isPurifyShareEnabled(): Boolean =
        ModuleSettings.isPurifyShareEnabled(prefs)

    private fun isShareTransformEnabled(transformAv: Boolean): Boolean =
        isPurifyShareEnabled() || transformAv

    private companion object {
        private const val BILI_TITLE = "\u54d4\u54e9\u54d4\u54e9"
        private const val BILIZ_SHARE_TEXT = "\u7531 BILIZ \u5206\u4eab"
        private const val WATCHED_PREFIX = "\u5df2\u89c2\u770b"
        private const val SHARE_CLICK_RESULT_ARG_COUNT = 13
        private const val LINK_MODE_VALUE = 3
        private val MINI_PROGRAM_MODE_VALUES = setOf(6, 7)
    }
}

