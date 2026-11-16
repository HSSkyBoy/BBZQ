package io.github.bbzq.feats

/** The host's own `User-Agent`, so requests made on its behalf look like the app's. */
internal object HostUserAgent {
    private const val RUNTIME_HELPER = "com.bilibili.lib.moss.utils.RuntimeHelper"
    private const val FALLBACK =
        "
"

    fun of(classLoader: ClassLoader): String {
        val reported = runCatching {
            classLoader.findClassOrNull(RUNTIME_HELPER)?.getStaticObjectField("INSTANCE")?.callMethod("ua") as? String
        }.getOrNull()
        // Header values must be printable ASCII; device model names are not always.
        return reported?.filter { it in ' '..'~' }?.takeIf { it.isNotBlank() } ?: FALLBACK
    }
}
