package io.github.biliz.feats.hook

import android.content.pm.ApplicationInfo
import android.net.Uri
import io.github.biliz.ModuleSettings
import io.github.biliz.ModuleSettingsBridge
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.MethodHookParam
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allFields
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.setIntField
import io.github.biliz.feats.setObjectField
import io.github.biliz.feats.symbol.RestoredHomeRecommendFeedSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.LinkedHashMap

class HomeRecommendAdHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var fieldWriteFailedLogged = false
    private var itemDebugFailedLogged = false
    private var titleKeywordsRaw = ""
    private var titleKeywordsCache = emptyList<String>()
    private val methodCache = mutableMapOf<Class<*>, MutableMap<String, Method?>>()
    private val serializedStringFieldCache = mutableMapOf<Class<*>, MutableMap<String, Field?>>()
    private val recentRecommendFeedDebugLogs =
        object : LinkedHashMap<String, Long>(DEBUG_FEED_LOG_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > DEBUG_FEED_LOG_CACHE_SIZE
        }
    private val recentRecommendItemDebugLogs =
        object : LinkedHashMap<String, Long>(DEBUG_ITEM_LOG_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > DEBUG_ITEM_LOG_CACHE_SIZE
        }

    override fun startHook() {
        if (env.processName != env.packageName) return
        val options = currentOptions()
        if (!options.enabled) {
            log("startHook: HomeRecommendAd disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val feedSymbols = env.symbols?.homeRecommendFeed?.restore(classLoader)
        if (feedSymbols == null) {
            log("startHook: HomeRecommendAd missing Pegasus feed symbols")
            return
        }

        var count = 0
        feedSymbols.responseGetItems.forEach { response ->
            val getItems = response.getItems
            val symbols = feedSymbols.toFilterSymbols(response.itemsField)
            env.hookAfter(getItems) { param ->
                runCatching {
                    (param.result as? List<*>)?.let { items ->
                        logRecommendItems(items, symbols)
                    }
                    val result = handleReturnList(param, symbols, currentOptions())
                    if (result != null && (result.removed > 0 || isDebugModule())) {
                        val parts = buildList {
                            if (result.removed > 0) {
                                add("removed ${result.removed} item(s) reasons=${result.reasonSummary()}")
                            }
                            if (result.rewrittenVerticalAv > 0) {
                                add("rewrote ${result.rewrittenVerticalAv} vertical_av item(s)")
                            }
                        }.joinToString(separator = " ")
                        log("HomeRecommendAd $parts from ${getItems.declaringClass.name}.${getItems.name}")
                    }
                }.onFailure {
                    log("HomeRecommendAd failed on getItems hook", it)
                }
            }
            count += 1
        }
        log("startHook: HomeRecommendAd methods=$count responseClasses=${feedSymbols.responseGetItems.size}")
    }

    private fun RestoredHomeRecommendFeedSymbols.toFilterSymbols(itemsField: Field?): FilterSymbols =
        FilterSymbols(
            getHolderType = getHolderType,
            getBizType = getBizType,
            getHolderStyle = getHolderStyle,
            isSmallCard = isSmallCard,
            getAdInfo = getAdInfo,
            getCardType = getCardType,
            getCardGoto = getCardGoto,
            getGoTo = getGoTo,
            getUri = getUri,
            adInfoClass = adInfoClass,
            itemsField = itemsField,
        )

    private fun currentOptions(): FilterOptions {
        val filterEnabled = ModuleSettings.isCustomHomeRecommendFilterEnabled(prefs)
        val blockedItems = ModuleSettings.getHiddenHomeRecommendItems(prefs)
        return homeRecommendFilterOptions(filterEnabled, blockedItems)
    }

    private fun homeRecommendFilterOptions(
        enabled: Boolean,
        blockedItems: Set<String>,
    ): FilterOptions =
        FilterOptions(
            removeAds = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_AD in blockedItems,
            removePictures = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_PICTURE in blockedItems,
            removeGamePromos = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_GAME_PROMO in blockedItems,
            removeLive = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_LIVE in blockedItems,
            removeKetang = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_KETANG in blockedItems,
            removeVerticalAv = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_VERTICAL_AV in blockedItems,
            removeLargeCover = enabled && ModuleSettings.HOME_RECOMMEND_FILTER_LARGE_COVER in blockedItems,
            titleKeywords = if (enabled) currentTitleKeywords() else emptyList(),
            openVerticalAvInDetail = ModuleSettings.isHomeRecommendVerticalAvDetailEnabled(prefs),
        )

    private fun currentTitleKeywords(): List<String> {
        val raw = ModuleSettings.getHomeRecommendTitleKeywordsText(prefs)
        if (raw != titleKeywordsRaw) {
            titleKeywordsRaw = raw
            titleKeywordsCache = ModuleSettings.parseHomeRecommendTitleKeywords(raw)
        }
        return titleKeywordsCache
    }

    private fun logRecommendItems(items: List<*>, symbols: FilterSymbols) {
        if (!isDebugModule()) return
        if (!shouldLogRecommendItems(items, symbols)) return
        val itemsToLog = items.mapIndexedNotNull { index, item ->
            if (shouldLogRecommendItem(item, symbols)) index to item else null
        }
        if (itemsToLog.isEmpty()) return
        val loggedSuffix = if (itemsToLog.size == items.size) "" else " logged=${itemsToLog.size}"
        log("HomeRecommendFeed items=${items.size}$loggedSuffix")
        itemsToLog.forEach { (index, item) ->
            runCatching {
                log(describeRecommendItem(index, item, symbols))
            }.onFailure { throwable ->
                if (!itemDebugFailedLogged) {
                    itemDebugFailedLogged = true
                    log("HomeRecommendFeed item debug failed", throwable)
                }
            }
        }
    }

    private fun shouldLogRecommendItem(item: Any?, symbols: FilterSymbols): Boolean {
        val now = System.currentTimeMillis()
        val signature = recommendItemSignature(item, symbols)
        return synchronized(recentRecommendItemDebugLogs) {
            val lastLoggedAt = recentRecommendItemDebugLogs[signature]
            if (lastLoggedAt != null && now - lastLoggedAt < DEBUG_ITEM_LOG_DEDUP_WINDOW_MS) {
                false
            } else {
                recentRecommendItemDebugLogs[signature] = now
                true
            }
        }
    }

    private fun shouldLogRecommendItems(items: List<*>, symbols: FilterSymbols): Boolean {
        val now = System.currentTimeMillis()
        val signature = recommendItemsSignature(items, symbols)
        return synchronized(recentRecommendFeedDebugLogs) {
            val lastLoggedAt = recentRecommendFeedDebugLogs[signature]
            if (lastLoggedAt != null && now - lastLoggedAt < DEBUG_FEED_LOG_DEDUP_WINDOW_MS) {
                false
            } else {
                recentRecommendFeedDebugLogs[signature] = now
                true
            }
        }
    }

    private fun recommendItemsSignature(items: List<*>, symbols: FilterSymbols): String =
        buildString {
            append(items.size)
            items.take(DEBUG_FEED_SIGNATURE_MAX_ITEMS).forEachIndexed { index, item ->
                append('|')
                append(index)
                append(':')
                append(recommendItemSignature(item, symbols))
            }
            if (items.size > DEBUG_FEED_SIGNATURE_MAX_ITEMS) {
                append("|last:")
                append(recommendItemSignature(items.lastOrNull(), symbols))
            }
        }

    private fun recommendItemSignature(item: Any?, symbols: FilterSymbols): String {
        if (item == null) return "null"
        return buildString {
            append(item.javaClass.name)
            append('#')
            append(callNoArg(item, "getHolderItemId"))
            append('#')
            append(callNoArg(item, "getIdx"))
            append('#')
            append(callNoArg(item, "getParam"))
            append('#')
            append(callNoArg(item, "getTrackId"))
            append('#')
            append(holderTypeOf(item, symbols.getHolderType))
        }
    }

    private fun isDebugModule(): Boolean =
        (xposed.moduleApplicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun describeRecommendItem(index: Int, item: Any?, symbols: FilterSymbols): String {
        if (item == null) return "HomeRecommendFeed item index=$index null"

        val holderType = holderTypeOf(item, symbols.getHolderType)
        val args = callNoArg(item, "getArgs")
        val upArgs = callNoArg(item, "getUpArgs")
        val playerArgs = callNoArg(item, "getPlayerArgs")
        val adInfo = callNoArg(item, "getAdInfo")
        val extra = callNoArg(item, "getExtra")

        return buildString {
            append("HomeRecommendFeed item")
            appendPart("index", index)
            appendPart("class", item.javaClass.name)
            appendPart("video", playerArgs != null || callNoArg(item, "getVideoId") != null)
            appendPart("ad", symbols.adInfoClass?.isInstance(item) == true || adInfo != null)
            appendPart("holderType", holderType)
            appendPart("holderStyle", callNoArg(item, "getHolderStyle"))
            appendPart("holderItemId", callNoArg(item, "getHolderItemId"))
            appendPart("idx", callNoArg(item, "getIdx"))
            appendPart("bizType", callNoArg(item, "getBizType"))
            appendPart("cardType", callNoArg(item, "getCardType"))
            appendPart("cardGoto", callNoArg(item, "getCardGoto"))
            appendPart("goTo", callNoArg(item, "getGoTo"))
            appendPart("fromType", callNoArg(item, "getFromType"))
            appendPart("id", callNoArg(item, "getId"))
            appendPart("materialId", callNoArg(item, "getMaterialId"))
            appendPart("title", callNoArg(item, "getTitle"))
            appendPart("subtitle", callNoArg(item, "getSubtitle"))
            appendPart("param", callNoArg(item, "getParam"))
            appendPart("uri", callNoArg(item, "getUri"))
            appendPart("cover", callNoArg(item, "getCover"))
            appendPart("trackId", callNoArg(item, "getTrackId"))
            appendPart("posRecUniqueId", callNoArg(item, "getPosRecUniqueId"))
            appendPart("zeroSignal", callNoArg(item, "getZeroSignal"))
            appendPart("canPlay", callNoArg(item, "getCanPlay"))
            appendPart("duration", callNoArg(item, "getDuration"))
            appendPart("videoId", callNoArg(item, "getVideoId"))
            appendPart("workId", callNoArg(item, "getWorkId"))
            appendPart("workTitle", callNoArg(item, "getWorkTitle"))
            appendPart("upperId", callNoArg(item, "getUpperId"))
            appendPart("upperName", callNoArg(item, "getUpperName"))
            appendPart("extraUri", callNoArg(item, "getExtraUri"))
            appendPart("preview", callNoArg(item, "isPreview"))
            appendPart("args", describeArgs(args))
            appendPart("upArgs", describeUpArgs(upArgs))
            appendPart("playerArgs", describePlayerArgs(playerArgs))
            appendPart("extra", describeExtra(extra))
            appendPart("adInfo", adInfo?.javaClass?.name)
        }.limitLength(MAX_LOG_LINE_LENGTH)
    }

    private fun describeArgs(args: Any?): String? {
        if (args == null) return null
        return buildString {
            appendPart("aid", callNoArg(args, "getAid"))
            appendPart("upId", callNoArg(args, "getUpId"))
            appendPart("upName", callNoArg(args, "getUpName"))
            appendPart("rid", callNoArg(args, "getRid"))
            appendPart("rname", callNoArg(args, "getRname"))
            appendPart("tid", callNoArg(args, "getTid"))
            appendPart("tname", callNoArg(args, "getTname"))
            appendPart("roomId", callNoArg(args, "getRoomId"))
            appendPart("online", callNoArg(args, "getOnline"))
            appendPart("type", callNoArg(args, "getType"))
            appendPart("trackId", callNoArg(args, "getTrackId"))
            appendPart("state", callNoArg(args, "getState"))
            appendPart("convergeType", callNoArg(args, "getConvergeType"))
            appendPart("follow", callNoArg(args, "isFollow"))
            appendPart("ipId", callNoArg(args, "getIpId"))
            appendPart("reportExtraInfo", callNoArg(args, "getReportExtraInfo"))
        }.trimStart()
    }

    private fun describeUpArgs(upArgs: Any?): String? {
        if (upArgs == null) return null
        return buildString {
            appendPart("upId", callNoArg(upArgs, "getUpId"))
            appendPart("upName", callNoArg(upArgs, "getUpName"))
            appendPart("upFace", callNoArg(upArgs, "getUpFace"))
            appendPart("selected", callNoArg(upArgs, "getSelected"))
        }.trimStart()
    }

    private fun describePlayerArgs(playerArgs: Any?): String? {
        if (playerArgs == null) return null
        return buildString {
            appendFieldPart("aid", playerArgs)
            appendFieldPart("cid", playerArgs)
            appendFieldPart("epid", playerArgs)
            appendFieldPart("pgcSeasonId", playerArgs)
            appendFieldPart("roomId", playerArgs)
            appendFieldPart("fakeDuration", playerArgs)
            appendFieldPart("isLive", playerArgs)
            appendFieldPart("isPreview", playerArgs)
            appendFieldPart("subtype", playerArgs)
            appendFieldPart("videoType", playerArgs)
            appendFieldPart("manualPlay", playerArgs)
            appendFieldPart("hidePlayButton", playerArgs)
            appendFieldPart("contentMode", playerArgs)
        }.trimStart()
    }

    private fun describeExtra(extra: Any?): String? {
        if (extra == null) return null
        return buildString {
            appendPart("indexInResponse", callNoArg(extra, "getIndexInResponse"))
            appendPart("uuid", callNoArg(extra, "getUuid"))
            appendPart("cardStartTime", callNoArg(extra, "getCardStartTime"))
            appendPart("exposed", callNoArg(extra, "isExposed"))
            appendPart("recoverCard", callNoArg(extra, "isRecoverCard"))
            appendPart("insertCardFlush", callNoArg(extra, "isInsertCardFlush"))
        }.trimStart()
    }

    private fun callNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        val method = debugNoArgMethod(target.javaClass, name) ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }

    private fun debugNoArgMethod(type: Class<*>, name: String): Method? =
        synchronized(methodCache) {
            val methods = methodCache.getOrPut(type) { mutableMapOf() }
            if (methods.containsKey(name)) {
                methods[name]
            } else {
                val method = type.methods
                    .firstOrNull {
                        it.name == name &&
                            it.parameterCount == 0 &&
                            !Modifier.isStatic(it.modifiers)
                    }
                    ?.apply { isAccessible = true }
                methods[name] = method
                method
            }
        }

    private fun StringBuilder.appendPart(name: String, value: Any?) {
        append(' ')
        append(name)
        append('=')
        append(formatValue(value))
    }

    private fun StringBuilder.appendFieldPart(name: String, target: Any) {
        appendPart(name, fieldValue(target, name))
    }

    private fun fieldValue(target: Any?, name: String): Any? =
        if (target == null) {
            null
        } else {
            runCatching {
                target.javaClass.getField(name).get(target)
            }.getOrNull()
        }

    private fun Any?.asIntOrNull(): Int? =
        when (this) {
            is Number -> toInt()
            is String -> toIntOrNull()
            else -> null
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

    private fun handleReturnList(
        param: MethodHookParam,
        symbols: FilterSymbols,
        options: FilterOptions,
    ): FilterResult? {
        if (!options.enabled) return null
        val items = param.result as? List<*> ?: return null
        if (items.isEmpty()) return null

        val shouldFilter = options.shouldFilter
        val filtered = if (shouldFilter) ArrayList<Any?>(items.size) else null
        val reasons = linkedMapOf<String, Int>()
        var removed = 0
        var rewrittenVerticalAv = 0
        items.forEach { item ->
            if (shouldFilter) {
                val reason = removeReason(item, symbols, options)
                if (reason != null) {
                    removed += 1
                    reasons[reason] = (reasons[reason] ?: 0) + 1
                    return@forEach
                }
            }
            if (options.openVerticalAvInDetail && rewriteVerticalAvToDetail(item, symbols)) {
                rewrittenVerticalAv += 1
            }
            filtered?.add(item)
        }
        if (removed == 0 && rewrittenVerticalAv == 0) return null

        if (removed > 0 && filtered != null) {
            param.result = filtered
            writeBackFilteredItems(param.thisObject, symbols.itemsField, filtered)
        }
        return FilterResult(removed, rewrittenVerticalAv, reasons)
    }

    private fun removeReason(item: Any?, symbols: FilterSymbols, options: FilterOptions): String? {
        if (item == null) return null
        val holderType = holderTypeOf(item, symbols.getHolderType)
        if (options.titleKeywords.isNotEmpty() && isTitleKeywordBlocked(item, options.titleKeywords)) {
            return "title_keyword"
        }
        if (options.removeAds) {
            if (holderType == BANNER_V8) return "banner_v8"
            if (isHomeRecommendAd(item, holderType, symbols)) return "ad"
            if (hasAdInfo(item, symbols) && isWideCard(item, symbols)) return "ad_card"
        }
        if (options.removePictures && isPictureCard(item, symbols)) return "picture"
        if (options.removeGamePromos && isGamePromoCard(item, holderType, symbols)) return "game_promo"
        if (options.removeLive && isLiveCard(item, symbols)) return "live"
        if (options.removeKetang && isKetangCard(item, symbols)) return "ketang"
        if (options.removeVerticalAv && isVerticalAvCard(item, symbols)) return "vertical_av"
        if (options.removeLargeCover && isLargeCoverCard(item, holderType, symbols)) return "large_cover"
        return null
    }

    private fun isTitleKeywordBlocked(item: Any, keywords: List<String>): Boolean {
        val title = callNoArg(item, "getTitle")?.toString()?.takeIf { it.isNotBlank() } ?: return false
        return keywords.any { keyword -> title.contains(keyword, ignoreCase = true) }
    }

    private fun holderTypeOf(item: Any, getHolderType: Method): String? =
        runCatching { getHolderType.invoke(item) as? String }.getOrNull()

    private fun isHomeRecommendAd(item: Any, holderType: String?, symbols: FilterSymbols): Boolean {
        val bizType = invokeString(symbols.getBizType, item)
        val cardType = invokeString(symbols.getCardType, item)
        val cardGoto = invokeString(symbols.getCardGoto, item)
        return bizType == BIZ_TYPE_AD ||
            holderType?.startsWith(CM_V2) == true ||
            cardType == CM_V2 ||
            cardGoto?.startsWith(AD_GOTO_PREFIX) == true
    }

    private fun isPictureCard(item: Any, symbols: FilterSymbols): Boolean {
        val cardGoto = invokeString(symbols.getCardGoto, item)
        val goTo = invokeString(symbols.getGoTo, item)
        val uri = invokeString(symbols.getUri, item)
        return cardGoto == PICTURE_GOTO ||
            goTo == PICTURE_GOTO ||
            uri?.startsWith(OPUS_URI_PREFIX) == true
    }

    private fun isLiveCard(item: Any, symbols: FilterSymbols): Boolean {
        val cardGoto = invokeString(symbols.getCardGoto, item)
        val goTo = invokeString(symbols.getGoTo, item)
        val uri = invokeString(symbols.getUri, item)
        val playerArgs = callNoArg(item, "getPlayerArgs")
        return cardGoto == LIVE_GOTO ||
            goTo == LIVE_GOTO ||
            uri?.contains(LIVE_URI_PART) == true ||
            fieldValue(playerArgs, "isLive").asIntOrNull() == 1 ||
            fieldValue(playerArgs, "videoType") == LIVE_GOTO
    }

    private fun isKetangCard(item: Any, symbols: FilterSymbols): Boolean {
        val cardGoto = invokeString(symbols.getCardGoto, item)
        val goTo = invokeString(symbols.getGoTo, item)
        val uri = invokeString(symbols.getUri, item)
        return cardGoto == KETANG_GOTO ||
            goTo == KETANG_GOTO ||
            uri?.contains(KETANG_URI_PART) == true
    }

    private fun isVerticalAvCard(item: Any, symbols: FilterSymbols): Boolean {
        val cardGoto = invokeString(symbols.getCardGoto, item)
        val goTo = invokeString(symbols.getGoTo, item)
        val uri = invokeString(symbols.getUri, item)
        return cardGoto == VERTICAL_AV_GOTO ||
            goTo == VERTICAL_AV_GOTO ||
            uri?.startsWith(STORY_URI_PREFIX) == true
    }

    private fun isLargeCoverCard(item: Any, holderType: String?, symbols: FilterSymbols): Boolean {
        val cardType = invokeString(symbols.getCardType, item)
        val cardGoto = invokeString(symbols.getCardGoto, item)
        return holderType?.startsWith(LARGE_COVER_PREFIX) == true ||
            cardType?.startsWith(LARGE_COVER_PREFIX) == true ||
            (cardGoto == INLINE_AV_V2_GOTO && isWideCard(item, symbols))
    }

    private fun isGamePromoCard(item: Any, holderType: String?, symbols: FilterSymbols): Boolean {
        val title = invokeStringMethod(item, "getTitle")
        val subtitle = invokeStringMethod(item, "getSubtitle")
        val desc = invokeStringMethod(item, "getDesc")
        val param = invokeStringMethod(item, "getParam")
        val label = invokeStringMethod(item, "getLabel")
        val badge = invokeStringMethod(item, "getBadge")
        val tag = invokeStringMethod(item, "getTag")
        val cornerMark = invokeStringMethod(item, "getCornerMark")
        val buttonText = invokeStringMethod(item, "getButtonText")
        val bizType = invokeString(symbols.getBizType, item)
        val cardType = invokeString(symbols.getCardType, item)
        val cardGoto = invokeString(symbols.getCardGoto, item)
        val goTo = invokeString(symbols.getGoTo, item)
        val uri = invokeString(symbols.getUri, item)
        val args = callNoArg(item, "getArgs")
        val upArgs = callNoArg(item, "getUpArgs")
        val extra = callNoArg(item, "getExtra")

        val text = buildList {
            addAll(
                listOfNotNull(
                    title,
                    subtitle,
                    desc,
                    param,
                    label,
                    badge,
                    tag,
                    cornerMark,
                    buttonText,
                    bizType,
                    cardType,
                    cardGoto,
                    goTo,
                    uri,
                    holderType,
                ),
            )
            addAll(collectPromoSignals(args))
            addAll(collectPromoSignals(upArgs))
            addAll(collectPromoSignals(extra))
        }
            .joinToString(separator = " ")
            .lowercase()

        if (containsGamePromoSignals(text)) {
            return true
        }

        val route = listOfNotNull(bizType, cardType, cardGoto, goTo, uri, holderType)
            .joinToString(separator = " ")
            .lowercase()
        return route.contains("game") ||
            route.contains("mini_game") ||
            route.contains("game_center") ||
            route.contains("h5_game") ||
            route.contains("promotion")
    }

    private fun hasAdInfo(item: Any, symbols: FilterSymbols): Boolean =
        symbols.adInfoClass?.isInstance(item) == true || runCatching {
            symbols.getAdInfo?.invoke(item) != null
        }.getOrDefault(false)

    private fun invokeString(method: Method?, target: Any): String? =
        runCatching { method?.invoke(target)?.toString() }.getOrNull()

    private fun rewriteVerticalAvToDetail(item: Any?, symbols: FilterSymbols): Boolean {
        if (item == null) return false
        val cardGoto = invokeString(symbols.getCardGoto, item)
        val goTo = invokeString(symbols.getGoTo, item)
        val uri = invokeString(symbols.getUri, item)
        if (cardGoto != VERTICAL_AV_GOTO && goTo != VERTICAL_AV_GOTO && uri?.startsWith(STORY_URI_PREFIX) != true) {
            return false
        }

        val detailUri = verticalAvDetailUri(item, uri) ?: return false
        item.setStringProperty("cardGoto", "card_goto", AV_GOTO)
        item.setIntField("cardGotoType", AV_GOTO.hashCode())
        item.setStringProperty("goTo", "goto", AV_GOTO)
        item.setIntField("gotoType", AV_GOTO.hashCode())
        item.setStringProperty("uri", "uri", detailUri)
        item.setObjectField("stringUriCache", null)
        item.setObjectField("uriCache", null)

        val rewrittenGoTo = invokeString(symbols.getGoTo, item)
        val rewrittenUri = invokeString(symbols.getUri, item)
        return rewrittenGoTo == AV_GOTO && rewrittenUri?.startsWith(VIDEO_URI_PREFIX) == true
    }

    private fun verticalAvDetailUri(item: Any, uri: String?): String? {
        if (uri?.startsWith(STORY_URI_PREFIX) == true) {
            return VIDEO_URI_PREFIX + uri.removePrefix(STORY_URI_PREFIX)
        }
        val param = invokeStringMethod(item, "getParam")?.takeIf { it.isNotBlank() } ?: return null
        return VIDEO_URI_PREFIX + Uri.encode(param)
    }

    private fun Any.setStringProperty(fieldName: String, serializedName: String, value: String): Boolean =
        setObjectField(fieldName, value) || setSerializedNameStringField(serializedName, value)

    private fun Any.setSerializedNameStringField(serializedName: String, value: String): Boolean {
        val field = serializedStringField(javaClass, serializedName) ?: return false
        return runCatching {
            field.set(this, value)
            true
        }.getOrDefault(false)
    }

    private fun serializedStringField(type: Class<*>, serializedName: String): Field? =
        synchronized(serializedStringFieldCache) {
            val fields = serializedStringFieldCache.getOrPut(type) { mutableMapOf() }
            if (fields.containsKey(serializedName)) {
                fields[serializedName]
            } else {
                val field = type.allFields()
                    .firstOrNull {
                        it.type == String::class.java &&
                            it.serializedNameValue() == serializedName
                    }
                fields[serializedName] = field
                field
            }
        }

    private fun Field.serializedNameValue(): String? =
        declaredAnnotations.firstNotNullOfOrNull { annotation ->
            if (annotation.annotationClass.java.name != GSON_SERIALIZED_NAME) {
                null
            } else {
                runCatching {
                    annotation.annotationClass.java
                        .getMethod("value")
                        .invoke(annotation)
                        ?.toString()
                }.getOrNull()
            }
        }

    private fun invokeStringMethod(target: Any, name: String): String? =
        runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == name && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) }
                ?.invoke(target)
                ?.toString()
        }.getOrNull()

    private fun collectPromoSignals(target: Any?): List<String> {
        if (target == null) return emptyList()
        val methods = target.javaClass.methods
            .asSequence()
            .filter {
                it.parameterCount == 0 &&
                    !Modifier.isStatic(it.modifiers) &&
                    (it.returnType == String::class.java || CharSequence::class.java.isAssignableFrom(it.returnType)) &&
                    PROMO_TEXT_METHOD_HINTS.any { hint -> it.name.contains(hint, ignoreCase = true) }
            }
            .mapNotNull { method -> runCatching { method.invoke(target)?.toString() }.getOrNull() }
            .filter { it.isNotBlank() }
            .toList()
        return methods
    }

    private fun containsGamePromoSignals(text: String): Boolean {
        val hasGameKeyword = GAME_PROMO_GAME_KEYWORDS.any(text::contains)
        if (!hasGameKeyword) return false
        return GAME_PROMO_AD_KEYWORDS.any(text::contains) ||
            text.contains("点我玩玩") ||
            text.contains("去拼多多") ||
            text.contains("领取福利")
    }

    private fun isWideCard(item: Any, symbols: FilterSymbols): Boolean {
        val getHolderStyle = symbols.getHolderStyle ?: return true
        val isSmallCard = symbols.isSmallCard ?: return true
        val smallCard = runCatching {
            val style = getHolderStyle.invoke(item) ?: return@runCatching null
            isSmallCard.invoke(style) as? Boolean
        }.getOrNull()
        return smallCard != true
    }

    private fun writeBackFilteredItems(target: Any?, field: Field?, items: List<Any?>) {
        if (target == null || field == null) return
        runCatching {
            field.set(target, items)
        }.onFailure { throwable ->
            if (!fieldWriteFailedLogged) {
                fieldWriteFailedLogged = true
                log("HomeRecommendAd could not update PegasusResponse items field", throwable)
            }
        }
    }

    private data class FilterSymbols(
        val getHolderType: Method,
        val getBizType: Method?,
        val getHolderStyle: Method?,
        val isSmallCard: Method?,
        val getAdInfo: Method?,
        val getCardType: Method?,
        val getCardGoto: Method?,
        val getGoTo: Method?,
        val getUri: Method?,
        val adInfoClass: Class<*>?,
        val itemsField: Field?,
    )

    private data class FilterOptions(
        val removeAds: Boolean,
        val removePictures: Boolean,
        val removeGamePromos: Boolean,
        val removeLive: Boolean,
        val removeKetang: Boolean,
        val removeVerticalAv: Boolean,
        val removeLargeCover: Boolean,
        val titleKeywords: List<String>,
        val openVerticalAvInDetail: Boolean,
    ) {
        val shouldFilter: Boolean = removeAds || removePictures || removeGamePromos || removeLive ||
            removeKetang || removeVerticalAv || removeLargeCover || titleKeywords.isNotEmpty()
        val enabled: Boolean = shouldFilter || openVerticalAvInDetail
    }

    private data class FilterResult(
        val removed: Int,
        val rewrittenVerticalAv: Int,
        val reasons: Map<String, Int>,
    ) {
        fun reasonSummary(): String =
            reasons.entries.joinToString(",") { (reason, count) -> "$reason:$count" }
    }

    private companion object {
        private const val BANNER_V8 = "banner_v8"
        private const val BIZ_TYPE_AD = "AD"
        private const val CM_V2 = "cm_v2"
        private const val AD_GOTO_PREFIX = "ad_"
        private const val PICTURE_GOTO = "picture"
        private const val AV_GOTO = "av"
        private const val LIVE_GOTO = "live"
        private const val KETANG_GOTO = "ketang"
        private const val VERTICAL_AV_GOTO = "vertical_av"
        private const val INLINE_AV_V2_GOTO = "inline_av_v2"
        private const val LARGE_COVER_PREFIX = "large_cover"
        private const val LIVE_URI_PART = "live.bilibili.com/"
        private const val KETANG_URI_PART = "/cheese/play/"
        private const val STORY_URI_PREFIX = "bilibili://story/"
        private const val VIDEO_URI_PREFIX = "bilibili://video/"
        private const val OPUS_URI_PREFIX = "bilibili://opus/"
        private const val GSON_SERIALIZED_NAME = "com.google.gson.annotations.SerializedName"
        private const val MAX_VALUE_LENGTH = 300
        private const val MAX_LOG_LINE_LENGTH = 3500
        private const val DEBUG_FEED_LOG_CACHE_SIZE = 32
        private const val DEBUG_FEED_LOG_DEDUP_WINDOW_MS = 5_000L
        private const val DEBUG_FEED_SIGNATURE_MAX_ITEMS = 24
        private const val DEBUG_ITEM_LOG_CACHE_SIZE = 256
        private const val DEBUG_ITEM_LOG_DEDUP_WINDOW_MS = 5_000L
        private val PROMO_TEXT_METHOD_HINTS = listOf(
            "label",
            "badge",
            "tag",
            "mark",
            "desc",
            "text",
            "title",
            "name",
            "button",
            "corner",
            "promo",
            "ad",
        )
        private val GAME_PROMO_GAME_KEYWORDS = listOf(
            "小游戏",
            "小遊戲",
            "游戏",
            "遊戲",
            "游戏中心",
            "遊戲中心",
            "点我玩玩",
        )
        private val GAME_PROMO_AD_KEYWORDS = listOf(
            "广告",
            "廣告",
            "推广",
            "推廣",
            "福利",
            "领福利",
        )
    }
}

