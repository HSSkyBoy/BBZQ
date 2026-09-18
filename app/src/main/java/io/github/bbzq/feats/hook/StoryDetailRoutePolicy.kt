package io.github.bbzq.feats.hook

import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONObject

internal data class StoryVideoId(
    val kind: Kind,
    val value: String
) {
    enum class Kind { BV, AID }
}

internal object StoryDetailRoutePolicy {
    fun useHomeCardRewrite(homeEnabled: Boolean, redirectEnabled: Boolean): Boolean =
        homeEnabled && !redirectEnabled

    private const val STORY_URI_ROOT = "bilibili://story"
    private const val STORY_TRANSLUCENT_URI_ROOT = "bilibili://story_translucent"
    private const val VIDEO_URI_ROOT = "bilibili://video"
    private const val VIDEO_URI_PREFIX = "$VIDEO_URI_ROOT/"

    private const val MAX_ROUTE_LENGTH = 262_144
    private val BV_PATTERN = Regex("BV[0-9A-Za-z]{6,30}", RegexOption.IGNORE_CASE)
    private val AID_PATTERN = Regex("(?:av)?[0-9]{1,19}", RegexOption.IGNORE_CASE)

    fun planActivityLaunch(
        snapshot: StoryActivityLaunchSnapshot,
        backend: StoryDetailBackend
    ): StoryActivityLaunchOutcome {
        fun skip(reason: StoryLaunchSkip) =
            StoryActivityLaunchOutcome.Skipped(reason)
        if (snapshot.componentPackage != null && snapshot.componentPackage != TARGET_PACKAGE) {
            return skip(StoryLaunchSkip.CROSS_PACKAGE)
        }
        if (snapshot.targetPackage != null && snapshot.targetPackage != TARGET_PACKAGE) {
            return skip(StoryLaunchSkip.CROSS_PACKAGE)
        }
        val original = snapshot.dataUri ?: return skip(StoryLaunchSkip.NO_DATA_URI)

        val storyRoot = storyRootFor(original) ?: return skip(StoryLaunchSkip.NOT_STORY_ROUTE)

        if (original.length > MAX_ROUTE_LENGTH) {
            return skip(StoryLaunchSkip.ROUTE_TOO_LONG)
        }

        val extraIdentities = parseIntentIdentities(
            StoryIntentRouteSnapshot(
                dataUri = original,
                componentPackage = snapshot.componentPackage,
                targetPackage = snapshot.targetPackage,
                aid = snapshot.aid,
                avid = snapshot.avid,
                bvid = snapshot.bvid
            )
        ) ?: return skip(StoryLaunchSkip.MALFORMED_INTENT_IDENTITY)

        val route = parseUnambiguousRoute(original, storyRoot)
            ?: run {
                if (!permitsFallbackIdentity(original, storyRoot)) {
                    return skip(StoryLaunchSkip.MALFORMED_ROUTE)
                }
                val fallback = extraIdentities
                    .singleOrNull { it.kind == StoryVideoId.Kind.AID }
                    ?: extraIdentities.singleOrNull()
                    ?: return skip(StoryLaunchSkip.NO_IDENTITY)
                ParsedRoute(fallback, setOf(fallback), rawToken = null)
            }

        val identities = linkedSetOf<StoryVideoId>().apply {
            addAll(route.identities)
            addAll(extraIdentities)
        }
        if (identities.groupBy(StoryVideoId::kind).any { it.value.size > 1 }) {
            return skip(StoryLaunchSkip.IDENTITY_CONFLICT)
        }

        val plan = when (backend) {
            StoryDetailBackend.LEGACY -> StoryActivityLaunchPlan.Legacy(
                detailUri = rewriteStoryRoute(original, route, storyRoot)
            )

            StoryDetailBackend.UNITED -> {
                val identity = route.primaryIdentity
                if (identity.kind != StoryVideoId.Kind.AID) {
                    return skip(StoryLaunchSkip.BV_ONLY_UNITED)
                }
                val aid = identity.value.toLongOrNull()?.takeIf { it > 0L }
                    ?: return skip(StoryLaunchSkip.NO_IDENTITY)
                val cid = snapshot.preloadCid?.takeIf { it > 0L }
                    ?: return skip(StoryLaunchSkip.MISSING_PRELOAD_CID)
                val targetUrl = "$UNITED_VIDEO_URI_ROOT/$aid"
                val fromSpmid = uniqueQueryValue(original, FROM_SPMID_QUERY)
                    ?.takeIf { it.length <= MAX_FROM_SPMID_LENGTH }
                val detailUri = buildString {
                    append(targetUrl).append('?')
                    if (fromSpmid != null) {
                        append(FROM_SPMID_QUERY)
                            .append('=')
                            .append(encodeQueryComponent(fromSpmid))
                            .append('&')
                    }
                    append("aid=").append(aid).append("&bvid=")
                }
                StoryActivityLaunchPlan.United(
                    detailUri = detailUri,
                    targetUrl = targetUrl,
                    aid = aid,
                    cid = cid
                )
            }
        }
        return StoryActivityLaunchOutcome.Planned(plan)
    }

    fun sanitizeIntentHandlerUri(uri: String): String? {
        if (uri.length > MAX_ROUTE_LENGTH || storyRootFor(uri) == null) return null
        return sanitizeStoryRoutingHints(uri).takeUnless { it == uri }
    }

    fun isStrictStoryVideoRoute(uri: String?): Boolean =
        uri != null && storyRootFor(uri) != null

    fun parsePlayerPreloadCid(raw: String?): Long? {
        val json = raw?.takeIf { it.length in 2..MAX_PLAYER_PRELOAD_LENGTH } ?: return null
        return runCatching { JSONObject(json).optLong("cid", -1L).takeIf { it > 0L } }
            .getOrNull()
    }

    internal fun canonicalIdentity(raw: String?): StoryVideoId? {
        val token = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (BV_PATTERN.matches(token)) {
            return StoryVideoId(
                StoryVideoId.Kind.BV,
                "BV" + token.substring(2)
            )
        }
        if (!AID_PATTERN.matches(token)) return null
        val digits = if (token.startsWith("av", ignoreCase = true)) token.substring(2) else token
        val value = digits.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return StoryVideoId(StoryVideoId.Kind.AID, value.toString())
    }

    private fun StoryVideoId.routeToken(): String = when (kind) {
        StoryVideoId.Kind.BV -> value
        StoryVideoId.Kind.AID -> value
    }

    private fun parseRoute(raw: String?, root: String): ParsedRoute? {
        val route = raw?.takeIf { it.length <= MAX_ROUTE_LENGTH && isRouteFor(it, root) }
            ?: return null
        val suffixStart = route.indexOfFirstFrom(root.length) { it == '?' || it == '#' }
        val pathEnd = suffixStart.takeIf { it >= 0 } ?: route.length
        val path = route.substring(root.length, pathEnd)
        val rawToken = when {
            path.isEmpty() -> null
            !path.startsWith('/') || path.length == 1 || '/' in path.substring(1) -> return null
            else -> path.substring(1)
        }
        val pathIdentity = rawToken?.let(::canonicalIdentity) ?: if (rawToken == null) null else return null
        val query = parseQueryIdentities(route) ?: return null
        val identities = linkedSetOf<StoryVideoId>().apply {
            pathIdentity?.let(::add)
            addAll(query)
        }
        val primary = pathIdentity
            ?: identities.firstOrNull { it.kind == StoryVideoId.Kind.BV }
            ?: identities.firstOrNull()
            ?: return null
        return ParsedRoute(primary, identities, rawToken)
    }

    private fun parseUnambiguousRoute(raw: String?, root: String): ParsedRoute? {
        val route = parseRoute(raw, root) ?: return null
        if (route.identities.groupBy(StoryVideoId::kind).any { it.value.size > 1 }) {
            return null
        }
        return route
    }

    private fun storyRootFor(raw: String?): String? =
        STORY_URI_ROOTS.firstOrNull { isRouteFor(raw, it) }

    private fun parseIntentIdentities(
        snapshot: StoryIntentRouteSnapshot
    ): Set<StoryVideoId>? {
        val identities = linkedSetOf<StoryVideoId>()
        listOf(
            snapshot.bvid to StoryVideoId.Kind.BV,
            snapshot.aid to StoryVideoId.Kind.AID,
            snapshot.avid to StoryVideoId.Kind.AID
        ).forEach { (raw, expectedKind) ->
            if (raw == null) return@forEach
            val identity = canonicalIdentity(raw) ?: return null
            if (identity.kind != expectedKind) return null
            identities += identity
        }
        return identities
    }

    private fun permitsFallbackIdentity(raw: String?, root: String): Boolean {
        val route = raw?.takeIf { it.length <= MAX_ROUTE_LENGTH && isRouteFor(it, root) }
            ?: return false
        val suffixStart = route.indexOfFirstFrom(root.length) { it == '?' || it == '#' }
        val pathEnd = suffixStart.takeIf { it >= 0 } ?: route.length
        if (route.substring(root.length, pathEnd).isNotEmpty()) return false
        return parseQueryIdentities(route)?.isEmpty() == true
    }

    private data class ParsedRoute(
        val primaryIdentity: StoryVideoId,
        val identities: Set<StoryVideoId>,
        val rawToken: String?
    )

    private fun isRouteFor(raw: String?, root: String): Boolean {
        if (raw == null || raw.length < root.length || !raw.startsWith(root, ignoreCase = true)) {
            return false
        }
        return raw.length == root.length || raw[root.length] in charArrayOf('/', '?', '#')
    }

    private fun rewriteStoryRoute(raw: String, route: ParsedRoute, storyRoot: String): String {
        val sanitized = sanitizeStoryRoutingHints(raw)
        if (route.rawToken != null) {
            return VIDEO_URI_ROOT + sanitized.substring(storyRoot.length)
        }
        val suffixStart = sanitized.indexOfFirstFrom(storyRoot.length) {
            it == '?' || it == '#'
        }
        val suffix = if (suffixStart >= 0) sanitized.substring(suffixStart) else ""
        return VIDEO_URI_PREFIX + route.primaryIdentity.routeToken() + suffix
    }

    private fun sanitizeStoryRoutingHints(raw: String): String {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return raw
        val fragmentStart = raw.indexOf('#', queryStart + 1).takeIf { it >= 0 } ?: raw.length
        val components = raw.substring(queryStart + 1, fragmentStart).split('&')
        val retained = components.filterNot(::isStoryRoutingHint)
        if (retained.size == components.size) return raw
        return buildString(raw.length) {
            append(raw, 0, queryStart)
            if (retained.isNotEmpty()) append('?').append(retained.joinToString("&"))
            if (fragmentStart < raw.length) append(raw.substring(fragmentStart))
        }
    }

    private fun isStoryRoutingHint(component: String): Boolean {
        val delimiter = component.indexOf('=')
        val encodedName = if (delimiter >= 0) component.substring(0, delimiter) else component
        val name = decodeQueryComponent(encodedName)?.lowercase() ?: return false

        if (name !in STORY_ROUTING_QUERY_KEYS) return false
        val encodedValue = if (delimiter >= 0) component.substring(delimiter + 1) else ""
        val value = decodeQueryComponent(encodedValue) ?: return false
        return value.equals("story", ignoreCase = true)
    }

    private fun parseQueryIdentities(raw: String): Set<StoryVideoId>? {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return emptySet()
        val fragmentStart = raw.indexOf('#', queryStart + 1).takeIf { it >= 0 } ?: raw.length
        val identities = linkedSetOf<StoryVideoId>()
        raw.substring(queryStart + 1, fragmentStart).split('&').forEach { component ->
            val delimiter = component.indexOf('=')
            if (delimiter <= 0) return@forEach
            val name = decodeQueryComponent(component.substring(0, delimiter))
                ?.lowercase() ?: return@forEach
            if (name !in VIDEO_ID_QUERY_KEYS) return@forEach
            val value = decodeQueryComponent(component.substring(delimiter + 1)) ?: return null
            val identity = canonicalIdentity(value) ?: return null
            if (name == "bvid" && identity.kind != StoryVideoId.Kind.BV) return null
            if (name != "bvid" && identity.kind != StoryVideoId.Kind.AID) return null
            identities += identity
        }
        return identities
    }

    private fun decodeQueryComponent(raw: String): String? =
        runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull()

    private fun encodeQueryComponent(raw: String): String =
        URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")

    private fun uniqueQueryValue(raw: String, expectedName: String): String? {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return null
        val fragmentStart = raw.indexOf('#', queryStart + 1).takeIf { it >= 0 } ?: raw.length
        val values = raw.substring(queryStart + 1, fragmentStart)
            .split('&')
            .mapNotNull { component ->
                val delimiter = component.indexOf('=')
                if (delimiter <= 0) return@mapNotNull null
                val name = decodeQueryComponent(component.substring(0, delimiter)) ?: return null
                if (!name.equals(expectedName, ignoreCase = true)) return@mapNotNull null
                decodeQueryComponent(component.substring(delimiter + 1)) ?: return null
            }
            .distinct()
        return values.singleOrNull()
    }

    private inline fun String.indexOfFirstFrom(
        startIndex: Int,
        predicate: (Char) -> Boolean
    ): Int {
        for (index in startIndex until length) if (predicate(this[index])) return index
        return -1
    }

    private val STORY_ROUTING_QUERY_KEYS = setOf("-arouter", "-atype")
    private val VIDEO_ID_QUERY_KEYS = setOf("aid", "avid", "bvid")
    private val STORY_URI_ROOTS = listOf(STORY_TRANSLUCENT_URI_ROOT, STORY_URI_ROOT)
    private const val UNITED_VIDEO_URI_ROOT = "bilibili://united_video"
    private const val FROM_SPMID_QUERY = "from_spmid"
    private const val MAX_FROM_SPMID_LENGTH = 512

    private const val MAX_PLAYER_PRELOAD_LENGTH = 131_072
    private const val TARGET_PACKAGE = "tv.danmaku.bili"

}

internal data class StoryIntentRouteSnapshot(
    val dataUri: String?,
    val componentPackage: String? = null,
    val componentClass: String? = null,
    val targetPackage: String? = null,
    val aid: String? = null,
    val avid: String? = null,
    val bvid: String? = null
)

internal enum class StoryDetailBackend(val activityClassName: String) {
    UNITED("com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity"),
    LEGACY("com.bilibili.video.videodetail.VideoDetailsActivity")
}

internal data class StoryActivityLaunchSnapshot(
    val dataUri: String?,
    val componentPackage: String? = null,
    val targetPackage: String? = null,
    val aid: String? = null,
    val avid: String? = null,
    val bvid: String? = null,

    val preloadCid: Long? = null
)

internal enum class StoryLaunchSkip {
    NO_DATA_URI,
    CROSS_PACKAGE,
    NOT_STORY_ROUTE,
    ROUTE_TOO_LONG,
    MALFORMED_ROUTE,
    NO_IDENTITY,
    MALFORMED_INTENT_IDENTITY,
    IDENTITY_CONFLICT,
    BV_ONLY_UNITED,
    MISSING_PRELOAD_CID
}

internal sealed interface StoryActivityLaunchOutcome {
    data class Planned(val plan: StoryActivityLaunchPlan) : StoryActivityLaunchOutcome
    data class Skipped(val reason: StoryLaunchSkip) : StoryActivityLaunchOutcome
}

internal sealed interface StoryActivityLaunchPlan {
    val detailUri: String

    data class Legacy(
        override val detailUri: String
    ) : StoryActivityLaunchPlan

    data class United(
        override val detailUri: String,
        val targetUrl: String,
        val aid: Long,
        val cid: Long
    ) : StoryActivityLaunchPlan
}
