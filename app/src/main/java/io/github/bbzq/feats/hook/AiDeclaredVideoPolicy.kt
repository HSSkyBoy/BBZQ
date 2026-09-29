package io.github.bbzq.feats.hook

import java.net.URLDecoder
import org.json.JSONObject

internal data class AiRelateCandidate(
    val isVideo: Boolean,
    val aid: Long,
    val uri: String?,
    val authorMid: Long,
)

internal object AiDeclaredVideoPolicy {
    private const val VIDEO_ROUTE_PREFIX = "bilibili://video/"
    private const val HISTORY_SPMID_PREFIX = "main.my-history"
    private const val CREATION_TAGS_KEY = "creation_tags"
    private const val CREATION_TAGS_WINDOW = 1024
    private const val DECODED_CACHE_SIZE = 64
    private val AI_VERB = Regex("ai.{0,4}(生成|合成|创作|制作)")

    private val decodedResults = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > DECODED_CACHE_SIZE
    }

    fun isAiDeclaration(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val normalized = title.filterNot(Char::isWhitespace).lowercase()
        return "aigc" in normalized || "人工智能" in normalized || AI_VERB.containsMatchIn(normalized)
    }

    fun creationTagsDeclareAigc(tags: String?): Boolean {
        if (tags.isNullOrBlank()) return false
        return tags.split(',').any { tag ->
            tag.split('-').any { it.trim().equals("aigc", ignoreCase = true) }
        }
    }

    fun feedUriDeclaresAigc(uri: String?): Boolean {
        if (uri.isNullOrEmpty() || !mayDeclareAigc(uri)) return false
        synchronized(decodedResults) { decodedResults[uri] }?.let { return it }
        val result = runCatching {
            val encoded = queryParameter(uri, "player_preload") ?: return@runCatching false
            val preload = JSONObject(URLDecoder.decode(encoded, "UTF-8"))
            val feature = preload.optString("qn_feature").takeIf(String::isNotBlank)
                ?: return@runCatching false
            creationTagsDeclareAigc(JSONObject(feature).optString(CREATION_TAGS_KEY))
        }.getOrDefault(false)
        synchronized(decodedResults) { decodedResults[uri] = result }
        return result
    }

    fun mayDeclareAigc(uri: String): Boolean {
        val key = uri.indexOf(CREATION_TAGS_KEY)
        if (key < 0) return false
        val limit = key + CREATION_TAGS_KEY.length + CREATION_TAGS_WINDOW
        return uri.indexOf("aigc", key).let { it in 0 until limit } ||
            uri.indexOf("AIGC", key).let { it in 0 until limit }
    }

    fun isPassiveRequest(spmid: String?): Boolean =
        spmid != null && spmid.startsWith(HISTORY_SPMID_PREFIX)

    fun aidFromParam(param: String?): Long? = param?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    fun isVideoRoute(uri: String?): Boolean = uri != null && uri.startsWith(VIDEO_ROUTE_PREFIX)

    fun isReplacementCandidate(
        candidate: AiRelateCandidate,
        currentAid: Long,
        currentOwnerMid: Long,
        isKnownAi: (Long) -> Boolean,
    ): Boolean {
        if (!candidate.isVideo || candidate.aid <= 0 || candidate.aid == currentAid) return false
        if (!isVideoRoute(candidate.uri)) return false
        if (isKnownAi(candidate.aid)) return false
        return currentOwnerMid <= 0 || candidate.authorMid != currentOwnerMid
    }

    fun queryParameter(uri: String, name: String): String? {
        val query = uri.substringAfter('?', "").takeIf(String::isNotEmpty) ?: return null
        val prefix = "$name="
        return query.split('&').firstOrNull { it.startsWith(prefix) }?.substring(prefix.length)
    }
}

internal object AiDeclaredVideoRegistry {
    const val MAX_AIDS = 2048

    @Volatile
    private var aids: LinkedHashSet<Long> = LinkedHashSet()

    fun contains(aid: Long?): Boolean = aid != null && aid > 0 && aid in aids

    fun isEmpty(): Boolean = aids.isEmpty()

    @Synchronized
    fun add(aid: Long?): Boolean {
        if (aid == null || aid <= 0) return false
        val snapshot = aids
        if (aid in snapshot) return false
        val next = LinkedHashSet<Long>(snapshot)
        next += aid
        while (next.size > MAX_AIDS) next.remove(next.first())
        aids = next
        return true
    }

    @Synchronized
    fun clear() {
        aids = LinkedHashSet()
    }
}

internal class AiRedirectGuard(
    private val maxRedirects: Int = 3,
    private val windowMillis: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val stamps = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock()
        while (stamps.isNotEmpty() && now - stamps.first() > windowMillis) stamps.removeFirst()
        if (stamps.size >= maxRedirects) return false
        stamps.addLast(now)
        return true
    }
}
