package io.github.biliz.feats.hook

import android.view.View
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allFields
import io.github.biliz.feats.allMethods
import io.github.biliz.feats.callMethod
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookAfterAllConstructors
import io.github.biliz.feats.symbol.RestoredSkipVideoAdAutoLikeSymbols
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object SkipVideoAdAutoLike {
    private val installed = AtomicBoolean(false)
    private val candidates = CopyOnWriteArrayList<LikeCandidate>()
    private val detailStateOwnerFields = ConcurrentHashMap<Class<*>, FieldLookup>()
    private val storyOwnerFields = ConcurrentHashMap<Class<*>, FieldLookup>()
    private val storyActionOwnerTypes = ConcurrentHashMap<Class<*>, Boolean>()
    @Volatile private var detailLikeStateOwnerClassName: String? = null
    @Volatile private var storyActionOwnerClassName: String? = null

    fun install(env: RoamingEnv): Int {
        if (installed.get()) return 0
        val symbols = env.symbols?.skipVideoAdAutoLike?.restore(env.classLoader)
        if (symbols == null) {
            env.log("SkipVideoAd auto-like skipped because symbols are unavailable")
            return 0
        }
        if (!installed.compareAndSet(false, true)) return 0
        detailLikeStateOwnerClassName = symbols.detailLikeStateOwnerClass?.name
        storyActionOwnerClassName = symbols.storyActionOwnerClass?.name
        var count = 0
        count += hookDetailLikeComponent(env, symbols)
        symbols.storyWidgetClasses.forEach { type ->
            count += hookStoryLikeWidget(env, type, symbols.storyBindMethods.filter { it.declaringClass == type })
        }
        symbols.geminiLikeWidgetClass?.let { count += hookViewConstructors(env, it, LikeKind.GEMINI) }
        env.log("SkipVideoAd auto-like hooks installed, methods=$count")
        return count
    }

    fun likeCurrentVideo(log: (String, Throwable?) -> Unit): AutoLikeResult =
        runCatching {
            pruneCandidates()
            candidates.toList().asReversed().forEach { candidate ->
                val view = candidate.viewRef.get() ?: return@forEach
                if (!candidate.canReceiveAutoLikeAction(view)) return@forEach

                val liked = candidate.isLiked()
                if (liked == true) return AutoLikeResult.ALREADY_LIKED
                if (liked != false) return@forEach

                return if (view.performClick()) {
                    AutoLikeResult.PERFORMED
                } else {
                    AutoLikeResult.NO_CANDIDATE
                }
            }
            AutoLikeResult.NO_CANDIDATE
        }.onFailure {
            log("SkipVideoAd auto-like failed", it)
        }.getOrDefault(AutoLikeResult.NO_CANDIDATE)

    private fun hookDetailLikeComponent(env: RoamingEnv, symbols: RestoredSkipVideoAdAutoLikeSymbols): Int {
        val method = symbols.detailLikeInflateMethod ?: return 0
        env.hookAfter(method) { param ->
            val root = param.result?.callMethod("getRoot") as? View ?: return@hookAfter
            val stateOwner = param.thisObject?.detailLikeStateOwner() ?: return@hookAfter
            remember(root, stateOwner, LikeKind.DETAIL)
        }
        return 1
    }

    private fun hookViewConstructors(
        env: RoamingEnv,
        type: Class<*>,
        kind: LikeKind,
    ): Int =
        env.hookAfterAllConstructors(type) { param ->
            val view = param.thisObject as? View ?: return@hookAfterAllConstructors
            remember(view, null, kind)
        }

    private fun hookStoryLikeWidget(env: RoamingEnv, type: Class<*>, bindMethods: List<Method>): Int {
        var count = hookViewConstructors(env, type, LikeKind.STORY)
        bindMethods.forEach { method ->
            env.hookAfter(method) { param ->
                val view = param.thisObject as? View ?: return@hookAfter
                remember(view, param.args.firstOrNull(), LikeKind.STORY)
            }
            count++
        }
        return count
    }

    private fun remember(view: View, stateOwner: Any?, kind: LikeKind) {
        candidates.toList().forEach { candidate ->
            val existing = candidate.viewRef.get()
            if (existing == null || existing === view) {
                candidates.remove(candidate)
            }
        }
        candidates += LikeCandidate(
            viewRef = WeakReference(view),
            stateOwnerRef = stateOwner?.let(::WeakReference),
            kind = kind,
        )
    }

    private fun pruneCandidates() {
        candidates.toList().forEach { candidate ->
            if (candidate.viewRef.get() == null) {
                candidates.remove(candidate)
            }
        }
    }

    private fun LikeCandidate.canReceiveAutoLikeAction(view: View): Boolean {
        return when (kind) {
            LikeKind.DETAIL -> view.isAttachedToWindow && view.isShown && view.isEnabled && view.hasOnClickListeners()
            LikeKind.STORY -> view.hasOnClickListeners() && storyOwner(view)?.callMethod("isActive") == true
            LikeKind.GEMINI -> view.isAttachedToWindow && view.isEnabled && view.hasOnClickListeners()
        }
    }

    private fun Any.detailLikeStateOwner(): Any? =
        cachedField(detailStateOwnerFields, javaClass) {
            val ownerClassName = detailLikeStateOwnerClassName ?: return@cachedField null
            javaClass.allFields().firstOrNull { it.type.name == ownerClassName }
        }?.let { field ->
            runCatching { field.get(this) }.getOrNull()
        }

    private fun LikeCandidate.isLiked(): Boolean? =
        when (kind) {
            LikeKind.DETAIL -> stateOwnerRef?.get()?.callMethod("c") as? Boolean
            LikeKind.STORY -> viewRef.get()?.let { view -> storyOwner(view)?.storyLikeState() }
            LikeKind.GEMINI -> viewRef.get()?.geminiLikeState()
        }

    private fun LikeCandidate.storyOwner(view: View): Any? =
        view.storyActionOwner() ?: stateOwnerRef?.get()

    private fun Any.storyLikeState(): Boolean? {
        val data = callMethod("getData") ?: return null
        val requestUser = data.callMethod("getRequestUser") ?: return null
        return requestUser.callMethod("getLike") as? Boolean
    }

    private fun View.storyActionOwner(): Any? =
        cachedField(storyOwnerFields, javaClass) {
            javaClass.allFields().firstOrNull { it.type.isStoryActionOwnerType() }
        }?.let { field ->
            runCatching { field.get(this) }.getOrNull()
        }

    private fun Class<*>.isStoryActionOwnerType(): Boolean {
        storyActionOwnerTypes[this]?.let { return it }
        val result = isStoryActionOwnerTypeUncached()
        storyActionOwnerTypes[this] = result
        return result
    }

    private fun Class<*>.isStoryActionOwnerTypeUncached(): Boolean {
        if (name == storyActionOwnerClassName) return true
        val methods = runCatching { allMethods().toList() }.getOrDefault(emptyList())
        return methods.any {
            it.name == "getData" && it.parameterCount == 0
        } && methods.any {
            it.name == "isActive" &&
                it.parameterCount == 0 &&
                (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.javaObjectType)
        }
    }

    private fun cachedField(
        cache: ConcurrentHashMap<Class<*>, FieldLookup>,
        type: Class<*>,
        finder: () -> Field?,
    ): Field? {
        cache[type]?.let { return it.field }
        val field = runCatching { finder() }.getOrNull()
        cache[type] = FieldLookup(field)
        return field
    }

    private fun View.geminiLikeState(): Boolean? =
        (callMethod("getMLikeStateFlow")?.callMethod("getValue") as? Boolean)
            ?: isSelected.takeIf { hasOnClickListeners() }

    enum class AutoLikeResult {
        PERFORMED,
        ALREADY_LIKED,
        NO_CANDIDATE,
    }

    private enum class LikeKind {
        DETAIL,
        STORY,
        GEMINI,
    }

    private data class LikeCandidate(
        val viewRef: WeakReference<View>,
        val stateOwnerRef: WeakReference<Any>?,
        val kind: LikeKind,
    )

    private data class FieldLookup(val field: Field?)
}
