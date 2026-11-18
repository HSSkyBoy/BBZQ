package io.github.bbzq.feats

import android.content.Context

internal object BbzqLevelGate {
    private const val MIN_LEVEL = 2
    private const val CACHE_TTL_MS = 2_000L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var hostClassLoader: ClassLoader? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var cachedAllowed = false

    @Volatile
    private var cachedAtMs = 0L

    private val lock = Any()
    private val evaluating = ThreadLocal<Boolean>()

    fun init(context: Context, classLoader: ClassLoader) {
        appContext = context.applicationContext ?: context
        hostClassLoader = classLoader
        enabled = true
        cachedAtMs = 0L
    }

    fun isAllowed(): Boolean {
        if (!enabled) return true
        if (evaluating.get() == true) return false
        val now = System.currentTimeMillis()
        if (cachedAtMs > 0L && now - cachedAtMs < CACHE_TTL_MS) {
            return cachedAllowed
        }
        synchronized(lock) {
            val innerNow = System.currentTimeMillis()
            if (cachedAtMs > 0L && innerNow - cachedAtMs < CACHE_TTL_MS) {
                return cachedAllowed
            }
            evaluating.set(true)
            return try {
                val ctx = appContext ?: return false
                val cl = hostClassLoader ?: return false
                val snapshot = HostAccountResolver.resolve(ctx, cl)
                val allowed = snapshot.loggedIn && snapshot.level >= MIN_LEVEL
                cachedAllowed = allowed
                cachedAtMs = innerNow
                allowed
            } finally {
                evaluating.remove()
            }
        }
    }

    fun denialReason(): String? {
        if (!enabled) return null
        val ctx = appContext ?: return null
        val cl = hostClassLoader ?: return null
        val snapshot = HostAccountResolver.resolve(ctx, cl)
        return when {
            !snapshot.loggedIn -> "請先登入 B 站帳號（需 Lv2 及以上）"
            snapshot.level < MIN_LEVEL -> "僅限 2 級及以上帳號使用（目前 Lv.${snapshot.level}）"
            else -> null
        }
    }
}
