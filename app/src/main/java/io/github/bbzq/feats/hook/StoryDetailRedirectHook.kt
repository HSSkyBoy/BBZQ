package io.github.bbzq.feats.hook

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.from
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

class StoryDetailRedirectHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) {
            log("startHook: StoryDetailRedirect disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }
        synchronized(lock) {
            if (hookInstalled) return
            hookInstalled = true
        }

        val backends = Backend.entries.filter { it.activityClassName.from(classLoader) != null }
        if (backends.isEmpty()) {
            synchronized(lock) { hookInstalled = false }
            log("startHook: StoryDetailRedirect skipped because no detail activity is available")
            return
        }

        val installed = installLaunchBoundary(backends)
        if (installed == 0) {
            synchronized(lock) { hookInstalled = false }
            log("startHook: StoryDetailRedirect skipped because Instrumentation.execStartActivity is unavailable")
            return
        }

        val suppressed = installAutoStorySuppression()
        log(
            "startHook: StoryDetailRedirect at Instrumentation.execStartActivity" +
                " (boundary=$installed, autoStory=$suppressed, backend=" +
                backends.joinToString("+") { it.name.lowercase() } + ")",
        )
    }

    private fun installLaunchBoundary(backends: List<Backend>): Int {
        var installed = 0
        Instrumentation::class.java.declaredMethods
            .filter { method ->
                method.name == "execStartActivity" &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.count { it == Intent::class.java } == 1
            }
            .distinctBy(Method::toGenericString)
            .forEach { method ->
                val intentIndex = method.parameterTypes.indexOf(Intent::class.java)
                if (intentIndex < 0) return@forEach
                env.hookBefore(method) { param ->
                    val intent = param.args.getOrNull(intentIndex) as? Intent ?: return@hookBefore
                    if (!isStoryRoute(intent.data?.toString())) return@hookBefore
                    if (!ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) return@hookBefore
                    val rewritten = rewrite(intent, backends)
                    if (rewritten == null) return@hookBefore
                    param.args[intentIndex] = rewritten
                }
                installed += 1
            }
        return installed
    }

    private fun installAutoStorySuppression(): Int {
        val boolValue = BOOL_VALUE_CLASS.from(classLoader) ?: return 0
        val playConfig = PLAY_CONFIG_CLASS.from(classLoader) ?: return 0
        val defaultValue = runCatching {
            boolValue.declaredMethods.firstOrNull { method ->
                method.name == "getDefaultInstance" &&
                    Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == boolValue
            }?.also { it.isAccessible = true }?.invoke(null)
        }.getOrNull()?.takeIf(boolValue::isInstance) ?: return 0

        var installed = 0
        AUTO_STORY_GETTERS.forEach { name ->
            val method = playConfig.declaredMethods.firstOrNull { method ->
                method.name == name &&
                    !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == boolValue
            } ?: return@forEach
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isStoryVideoAsDetailEnabled(prefs)) return@hookBefore
                param.result = defaultValue
            }
            installed += 1
        }
        return installed
    }

    private fun rewrite(intent: Intent, backends: List<Backend>): Intent? = runCatching {
        val original = intent.data?.toString() ?: return@runCatching skip("no-data-uri")
        val componentPackage = intent.component?.packageName
        if (componentPackage != null && componentPackage != TARGET_PACKAGE) {
            return@runCatching skip("cross-package")
        }
        if (intent.`package` != null && intent.`package` != TARGET_PACKAGE) {
            return@runCatching skip("cross-package")
        }
        val storyRoot = storyRootFor(original) ?: return@runCatching skip("not-story-route")
        if (original.length > MAX_ROUTE_LENGTH) return@runCatching skip("route-too-long")

        val extras = intentIdentities(intent) ?: return@runCatching skip("malformed-intent-identity")
        val route = parseRoute(original, storyRoot)
            ?: fallbackRoute(original, storyRoot, extras)
            ?: return@runCatching skip("malformed-route")

        val identities = LinkedHashSet(route.identities).apply { addAll(extras) }
        if (identities.groupBy { it.isBv }.any { it.value.size > 1 }) {
            return@runCatching skip("identity-conflict")
        }

        var cid: Long? = null
        var cidResolved = false
        backends.forEach { backend ->
            when (backend) {
                Backend.LEGACY -> {
                    val built = buildLegacy(intent, original, route, storyRoot, backend)
                    if (built != null) return@runCatching built
                }

                Backend.UNITED -> {
                    val identity = route.primary
                    if (identity.isBv) return@forEach
                    val aid = identity.value.toLongOrNull()?.takeIf { it > 0L } ?: return@forEach
                    if (!cidResolved) {
                        cidResolved = true
                        cid = resolveCid(intent)
                    }
                    val resolved = cid ?: return@forEach
                    val built = buildUnited(intent, original, aid, resolved, backend)
                    if (built != null) return@runCatching built
                }
            }
        }
        skip("build-or-validate-failed")
    }.getOrElse { throwable ->
        log("StoryDetailRedirect rewrite failed", throwable)
        null
    }

    private fun skip(reason: String): Intent? {
        if (loggedSkips.add(reason)) log("StoryDetailRedirect kept host intent, reason=$reason")
        return null
    }

    private fun buildLegacy(
        intent: Intent,
        original: String,
        route: ParsedRoute,
        storyRoot: String,
        backend: Backend,
    ): Intent? {
        val detailUri = legacyDetailUri(original, route, storyRoot)
        val rewritten = Intent(intent).apply {
            data = Uri.parse(detailUri)
            component = ComponentName(TARGET_PACKAGE, backend.activityClassName)
        }
        return rewritten.takeIf {
            it.data?.toString() == detailUri &&
                it.component?.packageName == TARGET_PACKAGE &&
                it.component?.className == backend.activityClassName
        }
    }

    private fun buildUnited(
        intent: Intent,
        original: String,
        aid: Long,
        cid: Long,
        backend: Backend,
    ): Intent? {
        val targetUrl = "$UNITED_VIDEO_ROOT/$aid"
        val fromSpmid = uniqueQueryValue(original, FROM_SPMID_QUERY)
            ?.takeIf { it.length <= MAX_FROM_SPMID_LENGTH }
        val detailUri = buildString {
            append(targetUrl).append('?')
            if (fromSpmid != null) {
                append(FROM_SPMID_QUERY).append('=').append(encode(fromSpmid)).append('&')
            }
            append("aid=").append(aid).append("&bvid=")
        }
        val preloadToken = nextPreloadToken()
        val rewritten = Intent(intent).apply {
            UNITED_REPLACED_EXTRAS.forEach { removeExtra(it) }
            data = Uri.parse(detailUri)
            component = ComponentName(TARGET_PACKAGE, backend.activityClassName)
            putExtra(PLAYER_PRELOAD_EXTRA, preloadToken)
            putExtra(BLROUTER_TARGET_URL_EXTRA, targetUrl)
            putExtra(BLROUTER_PAGE_NAME_EXTRA, UNITED_VIDEO_PAGE)
            putExtra(BLROUTER_MATCH_RULE_EXTRA, UNITED_VIDEO_PAGE)
            putExtra(JUMP_FROM_EXTRA, DETAIL_SOURCE)
            putExtra(AID_EXTRA, aid)
            putExtra(CID_EXTRA, cid)
            putExtra(BVID_EXTRA, "")
            putExtra(FROM_EXTRA, DETAIL_SOURCE)
        }
        return rewritten.takeIf {
            it.data?.toString() == detailUri &&
                it.component?.packageName == TARGET_PACKAGE &&
                it.component?.className == backend.activityClassName &&
                it.getStringExtra(BLROUTER_TARGET_URL_EXTRA) == targetUrl &&
                it.getStringExtra(PLAYER_PRELOAD_EXTRA) == preloadToken &&
                it.getLongExtra(AID_EXTRA, 0L) == aid &&
                it.getLongExtra(CID_EXTRA, 0L) == cid &&
                it.getStringExtra(BVID_EXTRA) == "" &&
                it.getIntExtra(JUMP_FROM_EXTRA, 0) == DETAIL_SOURCE &&
                it.getIntExtra(FROM_EXTRA, 0) == DETAIL_SOURCE
        }
    }

    private fun resolveCid(intent: Intent): Long? {
        val uri = intent.data
        parsePreloadCid(runCatching { uri?.getQueryParameter(PLAYER_PRELOAD_EXTRA) }.getOrNull())
            ?.let { return it }
        parsePreloadCid(extraToken(intent, PLAYER_PRELOAD_EXTRA))?.let { return it }
        extraToken(intent, CID_EXTRA)?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        return runCatching { uri?.getQueryParameter(CID_EXTRA) }
            .getOrNull()
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun parsePreloadCid(raw: String?): Long? {
        val json = raw?.takeIf { it.length in 2..MAX_PLAYER_PRELOAD_LENGTH } ?: return null
        return runCatching { JSONObject(json).optLong("cid", -1L).takeIf { it > 0L } }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun extraToken(intent: Intent, key: String): String? = runCatching {
        val extras = intent.extras ?: return null
        when (val value = extras.get(key)) {
            is String -> value.takeIf { it.isNotEmpty() }
            is Long -> value.toString()
            is Int -> value.toString()
            else -> null
        }
    }.getOrNull()

    private fun intentIdentities(intent: Intent): Set<VideoIdentity>? {
        val identities = LinkedHashSet<VideoIdentity>()
        listOf(
            extraToken(intent, BVID_EXTRA) to true,
            extraToken(intent, AID_EXTRA) to false,
            extraToken(intent, AVID_EXTRA) to false,
        ).forEach { (raw, expectBv) ->
            if (raw == null) return@forEach
            val identity = canonical(raw) ?: return null
            if (identity.isBv != expectBv) return null
            identities += identity
        }
        return identities
    }

    private fun canonical(raw: String?): VideoIdentity? {
        val token = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (BV_PATTERN.matches(token)) return VideoIdentity(true, "BV" + token.substring(2))
        if (!AID_PATTERN.matches(token)) return null
        val digits = if (token.startsWith("av", ignoreCase = true)) token.substring(2) else token
        val value = digits.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return VideoIdentity(false, value.toString())
    }

    private fun parseRoute(raw: String, root: String): ParsedRoute? {
        if (raw.length > MAX_ROUTE_LENGTH || !isRouteFor(raw, root)) return null
        val suffixStart = indexOfSuffix(raw, root.length)
        val pathEnd = if (suffixStart >= 0) suffixStart else raw.length
        val path = raw.substring(root.length, pathEnd)
        val rawToken = when {
            path.isEmpty() -> null
            !path.startsWith('/') || path.length == 1 || '/' in path.substring(1) -> return null
            else -> path.substring(1)
        }
        val pathIdentity = if (rawToken == null) null else canonical(rawToken) ?: return null
        val query = queryIdentities(raw) ?: return null
        val identities = LinkedHashSet<VideoIdentity>().apply {
            pathIdentity?.let(::add)
            addAll(query)
        }
        if (identities.groupBy { it.isBv }.any { it.value.size > 1 }) return null
        val primary = pathIdentity
            ?: identities.firstOrNull { it.isBv }
            ?: identities.firstOrNull()
            ?: return null
        return ParsedRoute(primary, identities, rawToken)
    }

    private fun fallbackRoute(
        raw: String,
        root: String,
        extras: Set<VideoIdentity>,
    ): ParsedRoute? {
        if (raw.length > MAX_ROUTE_LENGTH || !isRouteFor(raw, root)) return null
        val suffixStart = indexOfSuffix(raw, root.length)
        val pathEnd = if (suffixStart >= 0) suffixStart else raw.length
        if (raw.substring(root.length, pathEnd).isNotEmpty()) return null
        if (queryIdentities(raw)?.isEmpty() != true) return null
        val fallback = extras.singleOrNull { !it.isBv } ?: extras.singleOrNull() ?: return null
        return ParsedRoute(fallback, setOf(fallback), null)
    }

    private fun queryIdentities(raw: String): Set<VideoIdentity>? {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return emptySet()
        val fragmentStart = raw.indexOf('#', queryStart + 1).takeIf { it >= 0 } ?: raw.length
        val identities = LinkedHashSet<VideoIdentity>()
        raw.substring(queryStart + 1, fragmentStart).split('&').forEach { component ->
            val delimiter = component.indexOf('=')
            if (delimiter <= 0) return@forEach
            val name = decode(component.substring(0, delimiter))?.lowercase() ?: return@forEach
            if (name !in VIDEO_ID_QUERY_KEYS) return@forEach
            val value = decode(component.substring(delimiter + 1)) ?: return null
            val identity = canonical(value) ?: return null
            if (name == BVID_EXTRA && !identity.isBv) return null
            if (name != BVID_EXTRA && identity.isBv) return null
            identities += identity
        }
        return identities
    }

    private fun legacyDetailUri(raw: String, route: ParsedRoute, storyRoot: String): String {
        val sanitized = sanitize(raw)
        if (route.rawToken != null) return VIDEO_ROOT + sanitized.substring(storyRoot.length)
        val suffixStart = indexOfSuffix(sanitized, storyRoot.length)
        val suffix = if (suffixStart >= 0) sanitized.substring(suffixStart) else ""
        return "$VIDEO_ROOT/${route.primary.value}$suffix"
    }

    private fun sanitize(raw: String): String {
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
        val name = decode(encodedName)?.lowercase() ?: return false
        if (name !in STORY_ROUTING_QUERY_KEYS) return false
        val encodedValue = if (delimiter >= 0) component.substring(delimiter + 1) else ""
        return decode(encodedValue)?.equals("story", ignoreCase = true) == true
    }

    private fun uniqueQueryValue(raw: String, expectedName: String): String? {
        val queryStart = raw.indexOf('?')
        if (queryStart < 0) return null
        val fragmentStart = raw.indexOf('#', queryStart + 1).takeIf { it >= 0 } ?: raw.length
        return raw.substring(queryStart + 1, fragmentStart)
            .split('&')
            .mapNotNull { component ->
                val delimiter = component.indexOf('=')
                if (delimiter <= 0) return@mapNotNull null
                val name = decode(component.substring(0, delimiter)) ?: return null
                if (!name.equals(expectedName, ignoreCase = true)) return@mapNotNull null
                decode(component.substring(delimiter + 1)) ?: return null
            }
            .distinct()
            .singleOrNull()
    }

    private fun isStoryRoute(raw: String?): Boolean = storyRootFor(raw) != null

    private fun storyRootFor(raw: String?): String? = STORY_ROOTS.firstOrNull { isRouteFor(raw, it) }

    private fun isRouteFor(raw: String?, root: String): Boolean {
        if (raw == null || raw.length < root.length || !raw.startsWith(root, ignoreCase = true)) {
            return false
        }
        return raw.length == root.length || raw[root.length] in ROUTE_SUFFIX_CHARS
    }

    private fun indexOfSuffix(raw: String, startIndex: Int): Int {
        for (index in startIndex until raw.length) {
            if (raw[index] in ROUTE_SUFFIX_CHARS) return index
        }
        return -1
    }

    private fun decode(raw: String): String? =
        runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull()

    private fun encode(raw: String): String =
        URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")

    private fun nextPreloadToken(): String {
        val next = preloadSequence.incrementAndGet()
        if (next >= MAX_PRELOAD_TOKEN) preloadSequence.set(1L)
        return next.toString()
    }

    private enum class Backend(val activityClassName: String) {
        UNITED("com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity"),
        LEGACY("com.bilibili.video.videodetail.VideoDetailsActivity"),
    }

    private data class VideoIdentity(val isBv: Boolean, val value: String)

    private data class ParsedRoute(
        val primary: VideoIdentity,
        val identities: Set<VideoIdentity>,
        val rawToken: String?,
    )

    private companion object {
        private const val TARGET_PACKAGE = "tv.danmaku.bili"
        private const val STORY_ROOT = "bilibili://story"
        private const val STORY_TRANSLUCENT_ROOT = "bilibili://story_translucent"
        private const val VIDEO_ROOT = "bilibili://video"
        private const val UNITED_VIDEO_ROOT = "bilibili://united_video"
        private const val UNITED_VIDEO_PAGE = "bilibili://united_video/"
        private const val BOOL_VALUE_CLASS = "com.bapis.bilibili.app.distribution.BoolValue"
        private const val PLAY_CONFIG_CLASS =
            "com.bapis.bilibili.app.distribution.setting.play.PlayConfig"
        private const val PLAYER_PRELOAD_EXTRA = "player_preload"
        private const val BLROUTER_TARGET_URL_EXTRA = "blrouter.targeturl"
        private const val BLROUTER_PAGE_NAME_EXTRA = "blrouter.pagename"
        private const val BLROUTER_MATCH_RULE_EXTRA = "blrouter.matchrule"
        private const val JUMP_FROM_EXTRA = "jumpFrom"
        private const val AID_EXTRA = "aid"
        private const val AVID_EXTRA = "avid"
        private const val CID_EXTRA = "cid"
        private const val BVID_EXTRA = "bvid"
        private const val FROM_EXTRA = "from"
        private const val FROM_SPMID_QUERY = "from_spmid"
        private const val DETAIL_SOURCE = 7
        private const val MAX_ROUTE_LENGTH = 262_144
        private const val MAX_PLAYER_PRELOAD_LENGTH = 131_072
        private const val MAX_FROM_SPMID_LENGTH = 512
        private const val MAX_PRELOAD_TOKEN = 999_999_998L

        private val ROUTE_SUFFIX_CHARS = charArrayOf('/', '?', '#')
        private val STORY_ROOTS = listOf(STORY_TRANSLUCENT_ROOT, STORY_ROOT)
        private val STORY_ROUTING_QUERY_KEYS = setOf("-arouter", "-atype")
        private val VIDEO_ID_QUERY_KEYS = setOf(AID_EXTRA, AVID_EXTRA, BVID_EXTRA)
        private val AUTO_STORY_GETTERS = listOf("getLandscapeAutoStory", "getShouldAutoStory")
        private val UNITED_REPLACED_EXTRAS = listOf(
            PLAYER_PRELOAD_EXTRA,
            BLROUTER_TARGET_URL_EXTRA,
            BLROUTER_PAGE_NAME_EXTRA,
            BLROUTER_MATCH_RULE_EXTRA,
            JUMP_FROM_EXTRA,
            AID_EXTRA,
            AVID_EXTRA,
            CID_EXTRA,
            BVID_EXTRA,
            FROM_EXTRA,
        )
        private val BV_PATTERN = Regex("BV[0-9A-Za-z]{6,30}", RegexOption.IGNORE_CASE)
        private val AID_PATTERN = Regex("(?:av)?[0-9]{1,19}", RegexOption.IGNORE_CASE)

        private val lock = Any()
        private var hookInstalled = false
        private val preloadSequence = AtomicLong(
            (System.nanoTime() and 0x3fff_ffffL).coerceAtLeast(1L),
        )
        private val loggedSkips: MutableSet<String> =
            Collections.synchronizedSet(mutableSetOf<String>())
    }
}
