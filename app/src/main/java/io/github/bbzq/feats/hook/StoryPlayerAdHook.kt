package io.github.bbzq.feats.hook

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.MethodHookParam
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.getObjectField
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.replace
import java.lang.reflect.Method
import java.util.LinkedHashMap

class StoryPlayerAdHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var itemDebugFailedLogged = false
    private val recentStoryFeedDebugLogs =
        object : LinkedHashMap<String, Long>(DEBUG_FEED_LOG_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > DEBUG_FEED_LOG_CACHE_SIZE
        }
    private val recentStoryItemDebugLogs =
        object : LinkedHashMap<String, Long>(DEBUG_ITEM_LOG_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > DEBUG_ITEM_LOG_CACHE_SIZE
        }

    override fun startHook() {
        if (env.processName != env.packageName) return
        val enabled = ModuleSettings.isPurifyStoryVideoAdEnabled(prefs)
        val debugLogging = isDebugModule()
        if (!enabled && !debugLogging) {
            log("startHook: StoryPlayerAd disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }
        if (!enabled) {
            log("startHook: StoryPlayerAd disabled, installing debug story feed logger only")
        }

        var hookCount = 0

        val symbols = env.symbols?.storyPlayerAd?.restore(classLoader)
        if (symbols == null) {
            log("startHook: StoryPlayerAd symbols unavailable, direct hooks=0")
            return
        }

        symbols.feedGetItems?.let { hookCount += installStoryFeedResponseHook(it) }
        hookCount += installStoryPagerPlayerHook(symbols.pagerListMethods)
        if (enabled && symbols.rerankInvokeSuspend != null && symbols.kotlinUnit != null) {
            hookCount += installStoryAdRerankHook(symbols.rerankInvokeSuspend, symbols.kotlinUnit)
        }
        if (hookCount > 0) {
            isInstalled = true
            log("startHook: StoryPlayerAd installed=$hookCount")
        } else {
            log("startHook: StoryPlayerAd no hook point found")
        }
    }

    private fun installStoryFeedResponseHook(getItems: Method): Int {
        env.hookAfter(getItems) { param ->
            val result = filterReturnList(param)
            if (result != null) {
                log(
                    "StoryPlayerAd removed ${result.removed} item(s) " +
                        "reasons=${result.reasonSummary()} " +
                        "from ${getItems.declaringClass.name}.${getItems.name}",
                )
            }
        }
        log("startHook: StoryPlayerAd at ${getItems.declaringClass.name}.${getItems.name}")
        return 1
    }

    private fun installStoryPagerPlayerHook(methods: List<Method>): Int {
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                val result = filterArgumentList(param, 0)
                if (result != null) {
                    log(
                        "StoryPlayerAd removed ${result.removed} item(s) " +
                            "reasons=${result.reasonSummary()} " +
                            "from ${method.declaringClass.name}.${method.name}",
                    )
                }
            }
        }
        if (methods.isNotEmpty()) {
            log(
                "startHook: StoryPlayerAd at ${methods.first().declaringClass.name}, " +
                    "methods=${methods.joinToString(",") { it.name }}",
            )
        }
        return methods.size
    }

    private fun installStoryAdRerankHook(invokeSuspend: Method, unit: Any): Int {
        env.replace(invokeSuspend) {
            log("StoryPlayerAd disabled story ad rerank request")
            unit
        }
        log("startHook: StoryPlayerAd at ${invokeSuspend.declaringClass.name}.${invokeSuspend.name}")
        return 1
    }

    private fun filterReturnList(param: MethodHookParam): FilterResult? {
        val items = (param.result as? List<*>)?.map { it } ?: return null
        if (items.isEmpty()) return null
        logStoryItems(items)
        val result = filteredStoryList(items)
        if (result.removed == 0) return null
        if (result.items.isEmpty()) {
            log("StoryPlayerAd warning: filtering would empty feed items! Retaining first non-ad item.")
            val safeItem = items.firstOrNull { item -> item != null && !isStoryAd(item) } ?: items.first()
            param.result = listOf(safeItem)
            return result
        }
        param.result = result.items
        incrementBlockedCount(prefs, result.removed)
        return result
    }

    private fun filterArgumentList(param: MethodHookParam, index: Int): FilterResult? {
        val items = (param.args.getOrNull(index) as? List<*>)?.map { it } ?: return null
        if (items.isEmpty()) return null
        logStoryItems(items)
        val result = filteredStoryList(items)
        if (result.removed == 0) return null
        if (result.items.isEmpty()) {
            log("StoryPlayerAd warning: filtering would empty story pager list! Retaining first non-ad item to prevent activity finish.")
            val safeItem = items.firstOrNull { item -> item != null && !isStoryAd(item) } ?: items.first()
            param.args[index] = listOf(safeItem)
            return result
        }
        param.args[index] = result.items
        incrementBlockedCount(prefs, result.removed)
        return result
    }

    private fun filteredStoryList(items: List<Any?>): FilterResult {
        if (!ModuleSettings.isPurifyStoryVideoAdEnabled(prefs)) return FilterResult(items, 0)
        val selectedTags = ModuleSettings.getPurifyStoryVideoAdTags(prefs)
        if (selectedTags.isEmpty()) return FilterResult(items, 0)

        val filtered = ArrayList<Any?>(items.size)
        val reasons = linkedMapOf<String, Int>()
        var removed = 0
        items.forEach { item ->
            val reason = removeReason(item, selectedTags)
            if (reason != null) {
                removed += 1
                reasons[reason] = (reasons[reason] ?: 0) + 1
            } else {
                filtered += item
            }
        }
        return FilterResult(filtered, removed, reasons)
    }

    private fun logStoryItems(items: List<Any?>) {
        if (!isDebugModule()) return
        if (!shouldLogStoryItems(items)) return
        val itemsToLog = items.mapIndexedNotNull { index, item ->
            if (shouldLogStoryItem(item)) index to item else null
        }
        if (itemsToLog.isEmpty()) return
        val loggedSuffix = if (itemsToLog.size == items.size) "" else " logged=${itemsToLog.size}"
        log("StoryPlayerFeed items=${items.size}$loggedSuffix")
        itemsToLog.forEach { (index, item) ->
            runCatching {
                log(describeStoryItem(index, item))
            }.onFailure { throwable ->
                if (!itemDebugFailedLogged) {
                    itemDebugFailedLogged = true
                    log("StoryPlayerFeed item debug failed", throwable)
                }
            }
        }
    }

    private fun shouldLogStoryItem(item: Any?): Boolean {
        val now = System.currentTimeMillis()
        val signature = storyItemSignature(item)
        return synchronized(recentStoryItemDebugLogs) {
            val lastLoggedAt = recentStoryItemDebugLogs[signature]
            if (lastLoggedAt != null && now - lastLoggedAt < DEBUG_ITEM_LOG_DEDUP_WINDOW_MS) {
                false
            } else {
                recentStoryItemDebugLogs[signature] = now
                true
            }
        }
    }

    private fun shouldLogStoryItems(items: List<Any?>): Boolean {
        val now = System.currentTimeMillis()
        val signature = storyItemsSignature(items)
        return synchronized(recentStoryFeedDebugLogs) {
            val lastLoggedAt = recentStoryFeedDebugLogs[signature]
            if (lastLoggedAt != null && now - lastLoggedAt < DEBUG_FEED_LOG_DEDUP_WINDOW_MS) {
                false
            } else {
                recentStoryFeedDebugLogs[signature] = now
                true
            }
        }
    }

    private fun storyItemsSignature(items: List<Any?>): String =
        buildString {
            append(items.size)
            items.take(DEBUG_FEED_SIGNATURE_MAX_ITEMS).forEachIndexed { index, item ->
                append('|')
                append(index)
                append(':')
                append(storyItemSignature(item))
            }
            if (items.size > DEBUG_FEED_SIGNATURE_MAX_ITEMS) {
                append("|last:")
                append(storyItemSignature(items.lastOrNull()))
            }
        }

    private fun storyItemSignature(item: Any?): String {
        if (item == null) return "null"
        return buildString {
            append(item.javaClass.name)
            append('#')
            append(callNoArg(item, "getAid"))
            append('#')
            append(callNoArg(item, "getCid"))
            append('#')
            append(callNoArg(item, "getBvid"))
            append('#')
            append(callNoArg(item, "getGoto"))
            append('#')
            append(callNoArg(item, "getCardGoto"))
            append('#')
            append(callNoArg(item, "getUri"))
            append('#')
            append(callNoArg(item, "getTrackId"))
        }
    }

    private fun isDebugModule(): Boolean =
        (xposed.moduleApplicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun describeStoryItem(index: Int, item: Any?): String {
        if (item == null) return "StoryPlayerFeed item index=$index null"

        val isStoryDetail = item.javaClass.name == STORY_DETAIL
        val playerParams = callNoArg(item, "getPlayerParams") ?: item.getObjectField("playerParams")
        val cart = storyCartInfo(item)
        val liveRoom = callNoArg(item, "getLiveRoom") ?: item.getObjectField("liveRoom")

        return buildString {
            append("StoryPlayerFeed item")
            appendPart("index", index)
            appendPart("class", item.javaClass.name)
            appendPart("storyDetail", isStoryDetail)
            if (isStoryDetail) {
                appendPart("ad", isStoryAd(item))
                appendPart("live", isStoryLive(item))
                appendPart("roomId", readStoryRoomId(item).takeIf { it > 0L })
            }
            appendPart("goto", callNoArg(item, "getGoto"))
            appendPart("cardGoto", callNoArg(item, "getCardGoto"))
            appendPart("cardType", callNoArg(item, "getCardType"))
            appendPart("aid", callNoArg(item, "getAid"))
            appendPart("cid", callNoArg(item, "getCid"))
            appendPart("bvid", callNoArg(item, "getBvid"))
            appendPart("epId", callNoArg(item, "getEpId"))
            appendPart("seasonId", callNoArg(item, "getSeasonId"))
            appendPart("ogvType", callNoArg(item, "getOgvType"))
            appendPart("title", callNoArg(item, "getTitle"))
            appendPart("uri", callNoArg(item, "getUri"))
            appendPart("trackId", callNoArg(item, "getTrackId"))
            appendPart("duration", callNoArg(item, "getDuration"))
            appendPart("bangumi", callNoArg(item, "isBangumi"))
            appendPart("cheese", callNoArg(item, "isCheese"))
            appendPart("game", callNoArg(item, "isGame"))
            appendPart("ugc", callNoArg(item, "isUgc"))
            appendPart("music", callNoArg(item, "isMusic"))
            appendPart("cart", describeCartIcon(cart))
            appendPart("playerParams", describePlayerParams(playerParams))
            appendPart("liveRoom", describeLiveRoom(liveRoom))
        }.limitLength(MAX_LOG_LINE_LENGTH)
    }

    private fun storyCartInfo(item: Any): Any? =
        callNoArg(item, "getCartIconInfo") ?: item.getObjectField("cartIconInfo")

    private fun describeCartIcon(cart: Any?): String? {
        if (cart == null) return null
        return buildString {
            appendPart("text", callNoArg(cart, "getEntryText") ?: cart.getObjectField("entryText"))
            appendPart("title", callNoArg(cart, "getEntryTitle") ?: cart.getObjectField("entryTitle"))
            appendPart("goto", callNoArg(cart, "getEntryGoto") ?: cart.getObjectField("entryGoto"))
            appendPart("cardType", callNoArg(cart, "getCardType") ?: cart.getObjectField("cardType"))
            appendPart("uri", callNoArg(cart, "getJumpUrl") ?: cart.getObjectField("jumpUrl"))
            appendPart("valid", callNoArg(cart, "isValid"))
        }.trimStart()
    }

    private fun describePlayerParams(playerParams: Any?): String? {
        if (playerParams == null) return null
        return buildString {
            appendPart("aid", callNoArg(playerParams, "getAid") ?: playerParams.getObjectField("aid"))
            appendPart("cid", callNoArg(playerParams, "getCid") ?: playerParams.getObjectField("cid"))
            appendPart("duration", callNoArg(playerParams, "getDuration") ?: playerParams.getObjectField("duration"))
            appendPart("epId", callNoArg(playerParams, "getEpId") ?: playerParams.getObjectField("epId"))
            appendPart("musicId", callNoArg(playerParams, "getMusicId") ?: playerParams.getObjectField("musicId"))
            appendPart("rid", callNoArg(playerParams, "getRid") ?: playerParams.getObjectField("rid"))
            appendPart("seasonId", callNoArg(playerParams, "getSeasonId") ?: playerParams.getObjectField("seasonId"))
            appendPart("type", callNoArg(playerParams, "getType") ?: playerParams.getObjectField("type"))
            appendPart(
                "ugcSeasonId",
                callNoArg(playerParams, "getUgcSeasonId") ?: playerParams.getObjectField("ugcSeasonId"),
            )
        }.trimStart()
    }

    private fun describeLiveRoom(liveRoom: Any?): String? {
        if (liveRoom == null) return null
        return buildString {
            appendPart("showLiving", callNoArg(liveRoom, "isShowLiving"))
            appendPart("living", callNoArg(liveRoom, "isLiving"))
            appendPart("liveType", callNoArg(liveRoom, "getLiveType"))
            appendPart("upJumpUri", callNoArg(liveRoom, "getUpJumpUri"))
            appendPart("upPanelJumpUri", callNoArg(liveRoom, "getUpPanelJumpUri"))
            appendPart("closePagerUri", callNoArg(liveRoom, "getClosePagerUri"))
        }.trimStart()
    }

    private fun StringBuilder.appendPart(name: String, value: Any?) {
        append(' ')
        append(name)
        append('=')
        append(formatValue(value))
    }

    private fun formatValue(value: Any?): String =
        when (value) {
            null -> "null"
            is CharSequence -> value.toString().quoteAndLimit()
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { formatValue(it) }
                .limitLength(MAX_VALUE_LENGTH)
            is Boolean,
            is Number -> value.toString()
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, mapValue) ->
                "${formatValue(key)}:${formatValue(mapValue)}"
            }.limitLength(MAX_VALUE_LENGTH)
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { formatValue(it) }
                .limitLength(MAX_VALUE_LENGTH)
            else -> value.toString().limitLength(MAX_VALUE_LENGTH)
        }

    private fun String.quoteAndLimit(): String =
        "\"" + replace("\n", "\\n").replace("\r", "\\r").limitLength(MAX_VALUE_LENGTH) + "\""

    private fun String.limitLength(maxLength: Int): String =
        if (length <= maxLength) this else take(maxLength - 3) + "..."

    private fun removeReason(
        item: Any?,
        selectedTags: Set<String>,
    ): String? {
        if (item == null) return null
        if (item.javaClass.name != STORY_DETAIL) return null
        if ("ad" in selectedTags && isStoryAd(item)) return "ad"
        if ("live" in selectedTags && isStoryLive(item)) return "live"
        return null
    }

    private fun isStoryAd(item: Any): Boolean {
        val isAd = runCatching {
            item.javaClass.getDeclaredMethod("isAd").apply { isAccessible = true }.invoke(item) as? Boolean
        }.getOrNull() ?: (item.getObjectField("ad") as? Boolean) ?: false
        if (isAd) return true

        val isAdLocal = runCatching {
            item.javaClass.getDeclaredMethod("isAdLocal").apply { isAccessible = true }.invoke(item) as? Boolean
        }.getOrNull() ?: (item.getObjectField("adLocal") as? Boolean) ?: false
        if (isAdLocal) return true

        if (callNoArg(item, "getAdInfo") != null || item.getObjectField("adInfo") != null) return true
        return false
    }

    private fun isStoryLive(item: Any): Boolean {
        if (callNoArg(item, "isLive") == true) return true
        val goto = callNoArg(item, "getGoto")?.toString()
        val cardGoto = callNoArg(item, "getCardGoto")?.toString()
        if (goto.isLiveGoto() || cardGoto.isLiveGoto()) return true
        if (readStoryRoomId(item) > 0L) return true

        val liveRoom = callNoArg(item, "getLiveRoom") ?: item.getObjectField("liveRoom")
        if (isLiveRoom(liveRoom)) return true

        val uri = callNoArg(item, "getUri")?.toString() ?: item.getObjectField("uri")?.toString()
        return uri.isLiveUri()
    }

    private fun readStoryRoomId(item: Any): Long =
        callNoArg(item, "getRoomId").asLongOrNull()
            ?: readNestedLong(callNoArg(item, "getPlayerParams") ?: item.getObjectField("playerParams"), "getRid", "rid")
            ?: readNestedLong(callNoArg(item, "getPlayerArgs") ?: item.getObjectField("playerArgs"), "getRoomId", "roomId")
            ?: 0L

    private fun readNestedLong(target: Any?, getter: String, field: String): Long? =
        target?.let {
            callNoArg(it, getter).asLongOrNull()
                ?: it.getObjectField(field).asLongOrNull()
        }

    private fun isLiveRoom(liveRoom: Any?): Boolean {
        if (liveRoom == null) return false
        if (callNoArg(liveRoom, "isShowLiving") == true) return true
        if (callNoArg(liveRoom, "isLiving") == true) return true
        if (callNoArg(liveRoom, "getLiveType")?.toString().orEmpty().isNotBlank()) return true
        return listOf(
            callNoArg(liveRoom, "getUpJumpUri"),
            callNoArg(liveRoom, "getUpPanelJumpUri"),
            callNoArg(liveRoom, "getClosePagerUri"),
        ).any { it?.toString().isLiveUri() }
    }

    private fun callNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        return runCatching {
            target.javaClass.getMethod(name).invoke(target)
        }.getOrNull()
            ?: runCatching {
                target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
            }.getOrNull()
    }

    private fun Any?.asLongOrNull(): Long? =
        when (this) {
            is Number -> toLong()
            is String -> toLongOrNull()
            else -> null
        }

    private fun String?.isLiveGoto(): Boolean =
        equals(VERTICAL_LIVE_GOTO, ignoreCase = true) ||
            equals(VERTICAL_AD_LIVE_GOTO, ignoreCase = true) ||
            equals(LEGACY_LIVE_GOTO, ignoreCase = true)

    private fun String?.isLiveUri(): Boolean =
        this?.contains(LIVE_URI_PART, ignoreCase = true) == true ||
            this?.startsWith(LIVE_ROUTE_PREFIX, ignoreCase = true) == true

    private fun incrementBlockedCount(prefs: SharedPreferences, count: Int) {
        val oldValue = prefs.getInt(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT, 0)
        prefs.edit().putInt(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT, oldValue + count).apply()
    }

    private data class FilterResult(
        val items: List<Any?>,
        val removed: Int,
        val reasons: Map<String, Int> = emptyMap(),
    ) {
        fun reasonSummary(): String =
            reasons.entries.joinToString(",") { (reason, count) -> "$reason:$count" }
    }

    private companion object {
        private const val STORY_DETAIL = "com.bilibili.video.story.StoryDetail"
        private const val VERTICAL_LIVE_GOTO = "vertical_live"
        private const val VERTICAL_AD_LIVE_GOTO = "vertical_ad_live"
        private const val LEGACY_LIVE_GOTO = "live"
        private const val LIVE_URI_PART = "live.bilibili.com/"
        private const val LIVE_ROUTE_PREFIX = "bilibili://live"
        private const val MAX_VALUE_LENGTH = 300
        private const val MAX_LOG_LINE_LENGTH = 3500
        private const val DEBUG_FEED_LOG_CACHE_SIZE = 32
        private const val DEBUG_FEED_LOG_DEDUP_WINDOW_MS = 5_000L
        private const val DEBUG_FEED_SIGNATURE_MAX_ITEMS = 24
        private const val DEBUG_ITEM_LOG_CACHE_SIZE = 256
        private const val DEBUG_ITEM_LOG_DEDUP_WINDOW_MS = 5_000L
    }
}

