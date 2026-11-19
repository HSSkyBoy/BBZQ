package io.github.bbzq.feats

import android.content.Context

internal object BbzqLevelGate {
    private const val MIN_LEVEL = 2
    private const val UNKNOWN_RETRY_MS = 10_000L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var hostClassLoader: ClassLoader? = null

    @Volatile
    private var enabled = false

    @Volatile
    private var decision: Boolean? = null

    @Volatile
    private var retryAtMs = 0L

    @Volatile
    private var logger: ((String) -> Unit)? = null

    private val lock = Any()
    private val evaluating = ThreadLocal<Boolean>()

    fun init(context: Context, classLoader: ClassLoader, log: ((String) -> Unit)? = null) {
        logger = log
        appContext = context.applicationContext ?: context
        hostClassLoader = classLoader
        enabled = true
        decision = null
        retryAtMs = 0L
    }

    fun isAllowed(): Boolean {
        if (!enabled) return true
        decision?.let { return it }
        if (evaluating.get() == true) return false
        if (System.currentTimeMillis() < retryAtMs) return false
        synchronized(lock) {
            decision?.let { return it }
            if (System.currentTimeMillis() < retryAtMs) return false
            return evaluate()
        }
    }

    private fun evaluate(): Boolean {
        val ctx = appContext ?: return false
        val cl = hostClassLoader ?: return false
        evaluating.set(true)
        try {
            val snapshot = HostAccountResolver.resolve(ctx, cl)
            if (snapshot.loggedIn && snapshot.level <= 0) {
                // 等級還讀不到，不下定論，稍後再試
                retryAtMs = System.currentTimeMillis() + UNKNOWN_RETRY_MS
                logger?.invoke("BbzqLevelGate: level not available yet, retry later")
                return false
            }
            val allowed = snapshot.loggedIn && snapshot.level >= MIN_LEVEL
            logger?.invoke("BbzqLevelGate: allowed=$allowed loggedIn=${snapshot.loggedIn} level=${snapshot.level}")
            decision = allowed
            return allowed
        } finally {
            evaluating.remove()
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
