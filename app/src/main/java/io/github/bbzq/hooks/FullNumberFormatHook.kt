package io.github.bbzq.hooks

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

class FullNumberFormatHook(
    targetPackageName: String,
) : BaseHook(targetPackageName) {
    private val pendingText = WeakHashMap<TextView, PendingText>()

    override fun startHook() {
        hookTextViewSetText()
        hookMineAccountBind()
        hookSpaceAccountBind()
        hookGenericNumberFormat()
    }

    private fun hookTextViewSetText() {
        val method = TextView::class.java.getMethod("setText", CharSequence::class.java)
        xposed.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) {
                    return@intercept chain.proceed()
                }
                val textView = chain.getThisObject() as? TextView ?: return@intercept chain.proceed()
                val fullText = pendingFullText(textView) ?: return@intercept chain.proceed()
                chain.proceed(arrayOf<Any>(fullText))
            }

        log("Installed TextView.setText full-number guard")
    }

    private fun hookMineAccountBind() {
        val method = HostMethodResolver(context).resolve(
            cacheKey = "full_number_mine_account_bind_v2",
            fixedCandidates = {
                MINE_FRAGMENT_CLASS_NAMES.asSequence()
                    .mapNotNull { HostAccess.findClass(classLoader, it) }
                    .flatMap(::mineBindCandidates)
            },
            searchPackages = listOf("tv.danmaku"),
            usingStrings = listOf("following_count", "attention_count", "fans_count"),
            validate = ::isMineBindMethod,
        )

        if (method == null) {
            log("Mine account bind method not found; mine full-number hook skipped")
            return
        }

        hookBindMethod(method, "mine", ::mineTextBindings)
    }

    private fun hookSpaceAccountBind() {
        val method = HostMethodResolver(context).resolve(
            cacheKey = "full_number_space_account_bind_v2",
            fixedCandidates = {
                SPACE_FRAGMENT_CLASS_NAMES.asSequence()
                    .mapNotNull { HostAccess.findClass(classLoader, it) }
                    .flatMap(::spaceBindCandidates)
            },
            searchPackages = listOf("com.bilibili"),
            usingStrings = listOf(
                "bilibili://user_center/vip",
                "https://big.bilibili.com/mobile/index?navhide=1&from_spmid=vipicon",
            ),
            validate = ::isSpaceBindMethod,
        )

        if (method == null) {
            log("Space account bind method not found; space full-number hook skipped")
            return
        }

        hookBindMethod(method, "space", ::spaceTextBindings)
    }

    private fun hookGenericNumberFormat() {
        val method = HostMethodResolver(context).resolve(
            cacheKey = "number_format_shorten",
            fixedCandidates = {
                STABLE_CLASS_NAMES.asSequence()
                    .mapNotNull { HostAccess.findClass(classLoader, it) }
                    .flatMap(HostAccess::methods)
            },
            searchPackages = listOf("com.bilibili", "tv.danmaku"),
            usingStrings = listOf("万", "亿"),
            validate = ::isFormatterMethod,
        )

        if (method == null) {
            log("NumberFormat shorten method not found; full-number hook skipped")
            return
        }

        xposed.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) {
                    return@intercept chain.proceed()
                }
                val number = when (val raw = chain.getArg(0)) {
                    is Long -> raw
                    is Int -> raw.toLong()
                    else -> return@intercept chain.proceed()
                }
                if (number >= 0) number.toString() else chain.proceed()
            }

        log("Installed full-number hook at ${method.declaringClass.name}#${method.name}")
    }

    private fun hookBindMethod(
        method: Method,
        label: String,
        bindingsProvider: (Any?, Any?) -> List<TextBinding>,
    ) {
        xposed.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                val fragment = chain.getThisObject()
                val data = chain.getArg(0)
                if (ModuleSettings.isFullNumberFormatEnabled(prefs)) {
                    registerTextBindings(bindingsProvider(fragment, data))
                }
                val result = chain.proceed()
                if (ModuleSettings.isFullNumberFormatEnabled(prefs)) {
                    runCatching {
                        val bindings = bindingsProvider(fragment, data)
                        registerTextBindings(bindings)
                        applyTextBindings(bindings)
                        scheduleTextBindings(bindings)
                    }.onFailure {
                        log("Failed to refresh $label full-number text", it)
                    }
                }
                result
            }

        log("Installed $label full-number hook at ${method.declaringClass.name}#${method.name}")
    }

    private fun mineBindCandidates(type: Class<*>): Sequence<Method> =
        sequence {
            MINE_BIND_METHOD_NAMES.forEach { name ->
                HostAccess.method(type, listOf(name), ::isMineBindMethod)?.let { yield(it) }
            }
            yieldAll(HostAccess.methods(type).filter { method ->
                isMineBindMethod(method) && method.name.endsWith("Cf")
            })
        }.distinctBy(Method::toGenericString)

    private fun spaceBindCandidates(type: Class<*>): Sequence<Method> =
        sequence {
            SPACE_BIND_METHOD_NAMES.forEach { name ->
                HostAccess.method(type, listOf(name), ::isSpaceBindMethod)?.let { yield(it) }
            }
            yieldAll(HostAccess.methods(type).filter(::isSpaceBindMethod))
        }.distinctBy(Method::toGenericString)

    private fun mineTextBindings(fragment: Any?, accountMine: Any?): List<TextBinding> {
        if (fragment == null || accountMine == null) return emptyList()
        val root = HostAccess.invoke(fragment, "getView") as? View ?: return emptyList()
        val dynamic = HostAccess.getLong(accountMine, "dynamic") ?: return emptyList()
        val following = HostAccess.getLong(accountMine, "following") ?: return emptyList()
        val follower = HostAccess.getLong(accountMine, "follower") ?: return emptyList()

        return buildList {
            addBindings(root, listOf("following_count", "hd_following_count"), dynamic.toString())
            addBindings(root, listOf("attention_count", "hd_attention_count"), following.toString())
            addBindings(root, listOf("fans_count", "hd_fans_count"), follower.toString())
        }
    }

    private fun spaceTextBindings(fragment: Any?, memberCard: Any?): List<TextBinding> {
        if (fragment == null || memberCard == null) return emptyList()
        val root = HostAccess.invoke(fragment, "getView") as? View ?: return emptyList()
        val followers = HostAccess.getLong(memberCard, "mFollowers") ?: return emptyList()
        val followings = HostAccess.getLong(memberCard, "mFollowings") ?: return emptyList()
        val likes = HostAccess.get(memberCard, "likes")
        val likeText = likes?.let { HostAccess.getLong(it, "likeNum")?.toString() }.orEmpty()
            .ifEmpty { "-" }

        return buildList {
            addBindings(root, listOf("fans"), followers.toString())
            addBindings(root, listOf("attentions"), followings.toString())
            addBindings(root, listOf("likes"), likeText)
        }
    }

    private fun MutableList<TextBinding>.addBindings(root: View, idNames: List<String>, text: String) {
        idNames.forEach { idName ->
            findTextView(root, idName)?.let { add(TextBinding(it, text)) }
        }
    }

    private fun registerTextBindings(bindings: List<TextBinding>) {
        if (bindings.isEmpty()) return
        val expiresAt = SystemClock.uptimeMillis() + TEXT_GUARD_TTL_MS
        synchronized(pendingText) {
            bindings.forEach { binding ->
                pendingText[binding.textView] = PendingText(binding.text, expiresAt)
            }
        }
    }

    private fun applyTextBindings(bindings: List<TextBinding>) {
        bindings.forEach { binding ->
            if (binding.textView.text?.toString() != binding.text) {
                binding.textView.text = binding.text
            }
        }
    }

    private fun scheduleTextBindings(bindings: List<TextBinding>) {
        val firstView = bindings.firstOrNull()?.textView ?: return
        firstView.post { applyTextBindings(bindings) }
        firstView.postDelayed({ applyTextBindings(bindings) }, 80L)
        firstView.postDelayed({ applyTextBindings(bindings) }, 300L)
        firstView.postDelayed({
            applyTextBindings(bindings)
            clearTextBindings(bindings)
        }, TEXT_GUARD_TTL_MS)
    }

    private fun clearTextBindings(bindings: List<TextBinding>) {
        synchronized(pendingText) {
            bindings.forEach { binding ->
                val pending = pendingText[binding.textView]
                if (pending?.text == binding.text) {
                    pendingText.remove(binding.textView)
                }
            }
        }
    }

    private fun pendingFullText(textView: TextView): String? =
        synchronized(pendingText) {
            val pending = pendingText[textView] ?: return@synchronized null
            if (pending.expiresAt >= SystemClock.uptimeMillis()) {
                pending.text
            } else {
                pendingText.remove(textView)
                null
            }
        }

    private fun findTextView(root: View, idName: String): TextView? {
        val resources = root.context.resources
        val id = sequenceOf(root.context.packageName, packageName)
            .distinct()
            .firstNotNullOfOrNull { ownerPackage ->
                resources.getIdentifier(idName, "id", ownerPackage).takeIf { it != 0 }
            } ?: return null
        return root.findViewById<View>(id) as? TextView
    }

    private fun isMineBindMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (!method.declaringClass.name.contains("HomeUserCenterFragment")) return false
        if (method.returnType != Void.TYPE) return false
        val paramType = method.parameterTypes.singleOrNull() ?: return false

        return HostAccess.findField(paramType, "dynamic") != null &&
            HostAccess.findField(paramType, "following") != null &&
            HostAccess.findField(paramType, "follower") != null
    }

    private fun isSpaceBindMethod(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (!method.declaringClass.name.contains("SpaceHeaderFragment")) return false
        if (method.returnType != Void.TYPE) return false
        val paramType = method.parameterTypes.singleOrNull() ?: return false

        return HostAccess.findField(paramType, "mFollowers") != null &&
            HostAccess.findField(paramType, "mFollowings") != null &&
            HostAccess.findField(paramType, "likes") != null
    }

    private fun isFormatterMethod(method: Method): Boolean {
        if (!Modifier.isStatic(method.modifiers)) return false
        if (method.returnType != String::class.java) return false
        val params = method.parameterTypes
        if (params.isEmpty() || params.size > 2) return false
        val firstParam = params[0]
        if (
            firstParam != Long::class.javaPrimitiveType &&
            firstParam != Int::class.javaPrimitiveType
        ) {
            return false
        }

        return runCatching {
            val args: Array<Any> = if (params.size == 1) {
                if (firstParam == Int::class.javaPrimitiveType) {
                    arrayOf(10000)
                } else {
                    arrayOf(10000L)
                }
            } else {
                if (firstParam == Int::class.javaPrimitiveType) {
                    arrayOf(10000, "")
                } else {
                    arrayOf(10000L, "")
                }
            }
            val result = method.invoke(null, *args) as? String
            result?.contains("万") == true
        }.getOrDefault(false)
    }

    private companion object {
        private const val TEXT_GUARD_TTL_MS = 1_500L

        private data class TextBinding(
            val textView: TextView,
            val text: String,
        )

        private data class PendingText(
            val text: String,
            val expiresAt: Long,
        )

        private val MINE_FRAGMENT_CLASS_NAMES = listOf(
            "tv.danmaku.p9138bili.p9228ui.main2.p9247mine.HomeUserCenterFragment",
            "tv.danmaku.p9142bili.p9232ui.main2.p9251mine.HomeUserCenterFragment",
            "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment",
        )
        private val MINE_BIND_METHOD_NAMES = listOf(
            "m171581Cf",
            "m172652Cf",
        )
        private val SPACE_FRAGMENT_CLASS_NAMES = listOf(
            "com.bilibili.p4439app.authorspace.p4444ui.SpaceHeaderFragment2",
            "com.bilibili.p4443app.authorspace.p4448ui.SpaceHeaderFragment2",
            "com.bilibili.app.authorspace.ui.SpaceHeaderFragment2",
        )
        private val SPACE_BIND_METHOD_NAMES = listOf(
            "m94896mf",
            "m95038mf",
        )
        private val STABLE_CLASS_NAMES = listOf(
            "com.bilibili.lib.utils.NumberFormat",
            "com.bilibili.foundation.utils.NumberFormat",
            "com.bilibili.p4566base.p4568util.NumberFormat",
            "com.bilibili.p4570base.p4572util.NumberFormat",
        )
    }
}
