package io.github.bbzq.feats.hook.ad

import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.callMethod

internal fun detailStateBlockReason(request: Any, getters: List<String> = DETAIL_STATE_GETTERS): String? {
    getters.forEach { getter ->
        val value = request.callMethod(getter) ?: return@forEach
        if (value !is Collection<*> || value.isNotEmpty()) {
            return DETAIL_STATE_REASONS[getter] ?: getter
        }
    }
    return null
}

internal fun openUrlBlockReason(request: Any): String? {
    val biz = request.callMethod("getBiz") as? String
    if (biz.hasAnyToken(PROMOTION_BIZ_TOKENS)) return "openUrl:biz=${biz.safeLogValue()}"

    val extra = request.callMethod("getExtra")
    val operId = extra?.callMethod("getOperId") as? String
    if (!operId.isNullOrBlank()) return "openUrl:operId=${operId.safeLogValue()}"

    val address = request.callMethod("getAddress") as? String
    if (address.hasAnyToken(PROMOTION_URL_TOKENS)) return "openUrl:address=${address.safeLogValue()}"

    val scheme = request.callMethod("getScheme") as? String
    if (scheme.hasAnyToken(PROMOTION_URL_TOKENS)) return "openUrl:scheme=${scheme.safeLogValue()}"

    return null
}

internal fun addDanmakuBlockReason(danmakuId: String?, type: Int, extra: Any?): String? {
    if (type == ADD_CUSTOM_DANMAKU_AD_TYPE) return "type=$type"
    if (danmakuId.hasAnyToken(PROMOTION_URL_TOKENS) || danmakuId.hasAnyToken(COMMAND_TOKEN_TEXTS)) {
        return "idToken"
    }
    if (type == ADD_CUSTOM_DANMAKU_NORMAL_TYPE) {
        return extra?.let { customDanmakuExtraBlockReason(it) }
    }
    val className = extra?.javaClass?.name ?: return null
    return if (className.contains("AdFloatView") || className.contains("AdDanmaku")) {
        customDanmakuExtraBlockReason(extra)
    } else {
        null
    }
}

internal fun customDanmakuBlockReason(item: Any): String? {
    val type = (item.callMethod("getType") as? Number)?.toInt()
    if (type == ADD_CUSTOM_DANMAKU_AD_TYPE) return "customAdType"

    val danmakuId = item.callMethod("getDanmakuId") as? String
    if (danmakuId.hasAnyToken(PROMOTION_URL_TOKENS) || danmakuId.hasAnyToken(COMMAND_TOKEN_TEXTS)) {
        return "customIdToken"
    }

    val extra = item.callMethod("getExtra")
    if (extra != null) {
        customDanmakuExtraBlockReason(extra)?.let { return it }
    }

    return null
}

private fun customDanmakuExtraBlockReason(extra: Any): String? {
    val className = extra.javaClass.name
    if (className.contains("NormalExtra")) return normalExtraBlockReason(extra)
    if (className.contains("AdFloatView")) return "adFloatExtra"
    if (className.contains("AdDanmaku")) return "adDanmakuExtra"
    if (commandDanmakuBlockReason(extra) != null) return "customCommand"
    if (className.hasAnyToken(PROMOTION_BIZ_TOKENS)) return "extraClass"
    if (extra.hasPromotionToken()) return "extraToken"
    return null
}

private fun normalExtraBlockReason(extra: Any): String? {
    val metadataValues = NORMAL_EXTRA_METADATA_GETTERS.mapNotNull { getter ->
        extra.callMethod(getter)
    } + extra.javaClass.allFields()
        .filter { field -> field.name in NORMAL_EXTRA_METADATA_FIELDS }
        .mapNotNull { field -> runCatching { field.get(extra) }.getOrNull() }
        .toList()

    if (metadataValues.any { it.hasPromotionTokenValue() || it.hasCommandTokenValue() }) {
        return "normalExtraMeta"
    }

    val hasInteractiveMeta = metadataValues.any { value ->
        when (value) {
            is String -> value.isNotBlank()
            is Map<*, *> -> value.isNotEmpty()
            is Collection<*> -> value.isNotEmpty()
            else -> true
        }
    }
    val content = extra.callMethod("getContent") as? String
    if (hasInteractiveMeta && content.hasAnyToken(PROMOTION_TEXT_TOKENS)) {
        return "normalExtraContent"
    }

    return null
}

private fun Any.hasPromotionToken(): Boolean {
    if (this is String) {
        return hasAnyToken(PROMOTION_TEXT_TOKENS) || hasAnyToken(PROMOTION_URL_TOKENS)
    }
    if (this is Map<*, *>) return hasAnyPromotionToken()

    if (CUSTOM_EXTRA_TOKEN_GETTERS.any { getter ->
            val value = callMethod(getter)
            value.hasPromotionTokenValue()
        }
    ) {
        return true
    }

    return javaClass.allFields().any { field ->
        val value = runCatching { field.get(this) }.getOrNull()
        value.hasPromotionTokenValue()
    }
}

private fun Any?.hasPromotionTokenValue(): Boolean =
    when (this) {
        is String -> hasAnyToken(PROMOTION_TEXT_TOKENS) || hasAnyToken(PROMOTION_URL_TOKENS)
        is Map<*, *> -> hasAnyPromotionToken()
        is Collection<*> -> take(MAX_COLLECTION_TOKEN_SCAN).any { item ->
            item is String && (item.hasAnyToken(PROMOTION_TEXT_TOKENS) || item.hasAnyToken(PROMOTION_URL_TOKENS))
        }
        else -> false
    }

private fun Any?.hasCommandTokenValue(): Boolean =
    when (this) {
        is String -> hasAnyToken(COMMAND_TOKEN_TEXTS)
        is Map<*, *> -> entries.any { (key, value) ->
            key?.toString().hasAnyToken(COMMAND_TOKEN_TEXTS) ||
                value?.toString().hasAnyToken(COMMAND_TOKEN_TEXTS)
        }
        is Collection<*> -> take(MAX_COLLECTION_TOKEN_SCAN).any { item ->
            item is String && item.hasAnyToken(COMMAND_TOKEN_TEXTS)
        }
        else -> false
    }

internal fun materialBlockReason(material: Any): String? {
    val type = (material.callMethod("getType") as? Enum<*>)?.name
    if (type in BLOCKED_MATERIAL_TYPES) return "materialType=$type"

    val fields = listOf(
        material.callMethod("getBgColor") as? String,
        material.callMethod("getText") as? String,
        material.callMethod("getUrl") as? String,
        material.callMethod("getParam") as? String,
        material.callMethod("getIcon") as? String,
        material.callMethod("getStaticIcon") as? String,
        material.callMethod("getBgPic") as? String,
        material.callMethod("getPageType")?.toString(),
    )
    val report = material.callMethod("getReport") as? Map<*, *>
    return if (
        fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) } ||
        report.hasAnyPromotionToken()
    ) {
        "materialToken"
    } else {
        null
    }
}

internal fun pointMaterialBlockReason(pointMaterial: Any): String? {
    val url = pointMaterial.callMethod("getUrl") as? String
    val source = pointMaterial.callMethod("getMaterialSource")?.toString()
    return if (url.hasAnyToken(PROMOTION_URL_TOKENS) || source.hasAnyToken(PROMOTION_TEXT_TOKENS)) {
        "pointMaterialToken"
    } else {
        null
    }
}

internal fun videoPointBlockReason(videoPoint: Any): String? {
    val fields = listOf(
        videoPoint.callMethod("getContent") as? String,
        videoPoint.callMethod("getTeamName") as? String,
        videoPoint.callMethod("getTeamType") as? String,
        videoPoint.callMethod("getCover") as? String,
        videoPoint.callMethod("getLogoUrl") as? String,
    )
    return if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
        "videoPointToken"
    } else {
        null
    }
}

internal fun operationCardBlockReason(card: Any): String? {
    val fields = listOf(
        card.callMethod("getTitle") as? String,
        card.callMethod("getContent") as? String,
        card.callMethod("getButtonText") as? String,
        card.callMethod("getButtonTitle") as? String,
        card.callMethod("getButtonSelectedTitle") as? String,
        card.callMethod("getIcon") as? String,
        card.callMethod("getUrl") as? String,
        card.callMethod("getBizType")?.toString(),
        card.callMethod("getBiz_type")?.toString(),
        card.callMethod("getCardType")?.toString(),
        card.callMethod("getParamCase")?.toString(),
        card.callMethod("getRenderCase")?.toString(),
    )
    if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
        return "operationCardToken"
    }

    val content = card.callMethod("getContent")
    if (content != null && operationCardContentBlockReason(content) != null) return "operationCardContent"

    val jump = card.callMethod("getJump")
    if (jump != null && operationNestedBlockReason(jump) != null) return "operationJump"

    if (card.callMethod("hasFollow") == true) return "operationFollow"
    if (card.callMethod("hasReserve") == true) return "operationReserve"
    if (card.callMethod("hasGame") == true) return "operationGame"
    if (card.callMethod("hasGameBackflow") == true) return "operationGameBackflow"
    if (card.callMethod("hasJump") == true) return "operationJump"
    if (card.callMethod("hasSkip") == true) return "operationSkip"

    val skip = card.callMethod("getSkip")
    if (skip != null && operationCardBlockReason(skip) != null) return "operationSkipContent"

    val standard = card.callMethod("getStandard")
    if (standard != null && operationNestedBlockReason(standard) != null) return "operationStandard"

    return null
}

private fun operationCardContentBlockReason(content: Any): String? {
    val fields = listOf(
        content.callMethod("getTitle") as? String,
        content.callMethod("getSubtitle") as? String,
        content.callMethod("getButtonTitle") as? String,
        content.callMethod("getButtonSelectedTitle") as? String,
        content.callMethod("getIcon") as? String,
    )
    return if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
        "operationContentToken"
    } else {
        null
    }
}

private fun operationNestedBlockReason(value: Any): String? {
    val fields = listOf(
        value.callMethod("getTitle") as? String,
        value.callMethod("getSubtitle") as? String,
        value.callMethod("getDesc") as? String,
        value.callMethod("getContent") as? String,
        value.callMethod("getButtonText") as? String,
        value.callMethod("getButtonTitle") as? String,
        value.callMethod("getUrl") as? String,
        value.callMethod("getUri") as? String,
        value.callMethod("getLink") as? String,
        value.callMethod("getSchema") as? String,
        value.callMethod("getScheme") as? String,
    )
    return if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
        "nestedToken"
    } else {
        null
    }
}

internal fun contractCardBlockReason(card: Any): String? {
    val text = card.callMethod("getText")
    val fields = listOf(
        text?.callMethod("getTitle") as? String,
        text?.callMethod("getSubtitle") as? String,
        text?.callMethod("getInlineTitle") as? String,
    )
    if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
        return "contractTextToken"
    }
    if (card.callMethod("getIsFollowDisplay").isNonZeroNumber()) return "contractFollow"
    if (card.callMethod("isFollowDisplay").isNonZeroNumber()) return "contractFollow"
    return null
}

internal fun commandDanmakuBlockReason(item: Any): String? {
    val command = (item.callMethod("getCommand") as? String)?.uppercase()
    if (command in BLOCKED_COMMANDS) return "command=$command"
    if (command.hasAnyToken(COMMAND_TOKEN_TEXTS)) return "commandToken"

    val content = item.callMethod("getContent") as? String
    val metadataValues = COMMAND_METADATA_GETTERS.mapNotNull { getter ->
        item.callMethod(getter)
    }
    if (content.hasAnyToken(PROMOTION_TEXT_TOKENS) || metadataValues.any { it.hasPromotionTokenValue() || it.hasCommandTokenValue() }) {
        return "commandToken"
    }
    if (content.hasAnyToken(PROMOTION_URL_TOKENS)) {
        return "commandUrl"
    }
    return null
}

internal fun isAdDanmakuFloatView(item: Any): Boolean {
    if (item.callMethod("isFloatView") == true) return true
    if (item.callMethod("isFloatViewActivities") == true) return true
    if (item.callMethod("isFloatViewPermanent") == true) return true
    return item.javaClass.name in AD_FLOAT_VIEW_TYPES
}

private fun String?.hasAnyToken(tokens: Set<String>): Boolean {
    if (isNullOrBlank()) return false
    val lower = lowercase()
    return tokens.any { lower.contains(it) }
}

private fun Map<*, *>?.hasAnyPromotionToken(): Boolean {
    if (isNullOrEmpty()) return false
    return entries.any { (key, value) ->
        key?.toString().hasAnyToken(PROMOTION_TEXT_TOKENS) ||
            key?.toString().hasAnyToken(PROMOTION_URL_TOKENS) ||
            value?.toString().hasAnyToken(PROMOTION_TEXT_TOKENS) ||
            value?.toString().hasAnyToken(PROMOTION_URL_TOKENS)
    }
}

private fun String?.safeLogValue(): String =
    orEmpty()
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(MAX_LOG_VALUE_LENGTH)

private fun Any?.isNonZeroNumber(): Boolean {
    val value = (this as? Number)?.toInt() ?: return false
    return value != 0
}

internal const val MAX_LOG_VALUE_LENGTH = 80
private const val MAX_COLLECTION_TOKEN_SCAN = 12
private const val ADD_CUSTOM_DANMAKU_AD_TYPE = 101
private const val ADD_CUSTOM_DANMAKU_NORMAL_TYPE = 104
internal val DETAIL_STATE_GETTERS = listOf(
    "getFollowStates",
    "getReserveState",
    "getClockInState",
    "getVoteState",
)
private val DETAIL_STATE_REASONS = mapOf(
    "getFollowStates" to "followGuide",
    "getReserveState" to "reserve",
    "getClockInState" to "checkIn",
    "getUpChargeState" to "upCharge",
    "getVoteState" to "vote",
    "getQuizState" to "quiz",
)
private val BLOCKED_COMMANDS = setOf(
    "#ATTENTION#",
    "#GOODSLIKE#",
    "#GOODSLIKEVOTE#",
    "#GRADE#",
    "#LINK#",
    "#QUIZ#",
    "#SSCHECKIN#",
    "#VOTE#",
    "#WATCH_LATER#",
    "#WATCH_LATER_V2#",
    "#WATCHLATER#",
    "#WATCH-LATER#",
)
private val COMMAND_TOKEN_TEXTS = setOf(
    "#attention#",
    "#goodslike#",
    "#goodslikevote#",
    "#grade#",
    "#link#",
    "#quiz#",
    "#sscheckin#",
    "#vote#",
    "#watch_later#",
    "#watch_later_v2#",
    "#watchlater#",
    "#watch-later#",
    "command_dm",
    "commanddm",
    "watch_later",
    "watchlater",
    "watch-later",
)
private val BLOCKED_MATERIAL_TYPES = setOf(
    "ACTIVITY",
    "ACTIVITY_ICON",
    "GENERAL_TYPE",
)
private val COMMAND_METADATA_GETTERS = listOf(
    "getAction",
    "getCommand",
    "getDanmakuId",
    "getExtra",
    "getIdStr",
    "getIdstr",
    "getLink",
    "getParam",
    "getScheme",
    "getUri",
    "getUrl",
)
private val NORMAL_EXTRA_METADATA_GETTERS = listOf(
    "getAction",
    "getAnimation",
    "getBiz",
    "getBizType",
    "getExtra",
    "getLink",
    "getParam",
    "getScheme",
    "getUri",
    "getUrl",
)
private val NORMAL_EXTRA_METADATA_FIELDS = setOf(
    "action",
    "animation",
    "biz",
    "bizType",
    "extra",
    "link",
    "param",
    "scheme",
    "uri",
    "url",
)
private val CUSTOM_EXTRA_TOKEN_GETTERS = listOf(
    "getAdNotes",
    "getAdTagText",
    "getBgPic",
    "getButtonText",
    "getButtonTitle",
    "getButton_text",
    "getContent",
    "getDesc",
    "getExtra",
    "getGotBtnText",
    "getGotNotes",
    "getImageUrl",
    "getImage_url",
    "getLink",
    "getNotes",
    "getParam",
    "getPriceDesc",
    "getQuestion",
    "getScheme",
    "getStaticIcon",
    "getSubtitle",
    "getSucceedBtnText",
    "getSucceedNotes",
    "getTitle",
    "getUri",
    "getUrl",
)
private val AD_FLOAT_VIEW_TYPES = setOf(
    "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatView",
    "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewPermanent",
    "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewAnswer",
    "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewGot",
    "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewCommerce",
    "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewCommon",
)
private val PROMOTION_BIZ_TOKENS = setOf(
    "ad",
    "commercial",
    "commerce",
    "goods",
    "mall",
    "shop",
    "game",
    "campaign",
    "promotion",
    "marketing",
    "watch-later",
    "watch_later",
    "watchlater",
)
private val PROMOTION_TEXT_TOKENS = setOf(
    "ad",
    "advert",
    "campaign",
    "commercial",
    "commerce",
    "game",
    "goods",
    "mall",
    "marketing",
    "promotion",
    "reserve",
    "shop",
    "watch-later",
    "watch later",
    "watch_later",
    "watchlater",
    "活动",
    "广告",
    "预约",
    "商业",
    "商品",
    "商城",
    "游戏",
    "稍后再看",
    "稍后看",
    "稍后观看",
    "投票",
    "打卡",
    "关注",
)
private val PROMOTION_URL_TOKENS = setOf(
    "cm.bilibili.com",
    "mall.bilibili.com",
    "game.bilibili.com",
    "show.bilibili.com",
    "adpage",
    "ad_",
    "/ad/",
    "compose/watch_later",
    "watch-later",
    "watch_later",
    "watch_later_v2",
    "watchlater",
    "bilibili://ad",
    "bilibili://mall",
    "bilibili://game",
    "bilibili://pegasus",
    "bilibili://user_center/watch_later",
    "bilibili://main/playset/watch-later",
    "activity://playset/watch-later",
    "bilibili.com/blackboard/activity",
)
