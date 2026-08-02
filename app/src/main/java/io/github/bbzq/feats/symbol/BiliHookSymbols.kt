package io.github.bbzq.feats.symbol

import android.content.Context
import android.view.View
import io.github.bbzq.feats.allMethods
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class BiliHookSymbols(
    val cacheSchemaVersion: Int = CACHE_SCHEMA_VERSION,
    val dexKitRuleVersion: Int = DexKitRuleVersions.CURRENT,
    val fingerprint: String,
    val hookPoints: List<HookPointStatus>,
    val scanErrors: List<String>,
    val splashAd: SplashAdSymbols? = null,
    val share: ShareSymbols? = null,
    val rewardAd: RewardAdSymbols? = null,
    val tryFreeQuality: TryFreeQualitySymbols? = null,
    val teenagersMode: TeenagersModeSymbols? = null,
    val account: AccountSymbols? = null,
    val settings: SettingsSymbols? = null,
    val blockUpdate: BlockUpdateSymbols? = null,
    val mineProfile: MineProfileSymbols? = null,
    val downloadThread: DownloadThreadSymbols? = null,
    val storyPlayerAd: StoryPlayerAdSymbols? = null,
    val storyFullscreen: StoryFullscreenSymbols? = null,
    val storyDanmaku: StoryDanmakuSymbols? = null,
    val storyComponentAlpha: StoryComponentAlphaSymbols? = null,
    val videoDetailBannerAd: VideoDetailBannerAdSymbols? = null,
    val homeTopBar: HomeTopBarSymbols? = null,
    val bottomBar: BottomBarSymbols? = null,
    val homeRecommendFeed: HomeRecommendFeedSymbols? = null,
    val homeRecommendTabs: HomeRecommendTabSymbols? = null,
    val homeComponentHide: HomeComponentHideSymbols? = null,
    val videoComment: VideoCommentSymbols? = null,
    val skipVideoAd: SkipVideoAdSymbols? = null,
    val skipVideoAdProgress: SkipVideoAdProgressSymbols? = null,
    val skipVideoAdAutoLike: SkipVideoAdAutoLikeSymbols? = null,
    val chronosPromotion: ChronosPromotionSymbols? = null,
    val fullNumberFormat: FullNumberFormatSymbols? = null,
    val tripleSpeed: TripleSpeedSymbols? = null,
    val customTheme: CustomThemeSymbols? = null,
    val customSkin: CustomSkinSymbols? = null,
) {
    fun isUsableWith(expectedFingerprint: String): Boolean =
        cacheSchemaVersion == CACHE_SCHEMA_VERSION &&
            dexKitRuleVersion == DexKitRuleVersions.CURRENT &&
            fingerprint == expectedFingerprint

    fun toJson(): JSONObject = JSONObject()
        .put("cacheSchemaVersion", cacheSchemaVersion)
        .put("dexKitRuleVersion", dexKitRuleVersion)
        .put("fingerprint", fingerprint)
        .put("hookPoints", hookPoints.toJsonArray { it.toJson() })
        .put("scanErrors", scanErrors.toJsonArray())
        .putOpt("splashAd", splashAd?.toJson())
        .putOpt("share", share?.toJson())
        .putOpt("rewardAd", rewardAd?.toJson())
        .putOpt("tryFreeQuality", tryFreeQuality?.toJson())
        .putOpt("teenagersMode", teenagersMode?.toJson())
        .putOpt("account", account?.toJson())
        .putOpt("settings", settings?.toJson())
        .putOpt("blockUpdate", blockUpdate?.toJson())
        .putOpt("mineProfile", mineProfile?.toJson())
        .putOpt("downloadThread", downloadThread?.toJson())
        .putOpt("storyPlayerAd", storyPlayerAd?.toJson())
        .putOpt("storyFullscreen", storyFullscreen?.toJson())
        .putOpt("storyDanmaku", storyDanmaku?.toJson())
        .putOpt("storyComponentAlpha", storyComponentAlpha?.toJson())
        .putOpt("videoDetailBannerAd", videoDetailBannerAd?.toJson())
        .putOpt("homeTopBar", homeTopBar?.toJson())
        .putOpt("bottomBar", bottomBar?.toJson())
        .putOpt("homeRecommendFeed", homeRecommendFeed?.toJson())
        .putOpt("homeRecommendTabs", homeRecommendTabs?.toJson())
        .putOpt("homeComponentHide", homeComponentHide?.toJson())
        .putOpt("videoComment", videoComment?.toJson())
        .putOpt("skipVideoAd", skipVideoAd?.toJson())
        .putOpt("skipVideoAdProgress", skipVideoAdProgress?.toJson())
        .putOpt("skipVideoAdAutoLike", skipVideoAdAutoLike?.toJson())
        .putOpt("chronosPromotion", chronosPromotion?.toJson())
        .putOpt("fullNumberFormat", fullNumberFormat?.toJson())
        .putOpt("tripleSpeed", tripleSpeed?.toJson())
        .putOpt("customTheme", customTheme?.toJson())
        .putOpt("customSkin", customSkin?.toJson())

    companion object {
        const val CACHE_SCHEMA_VERSION = 34

        fun fromJson(raw: String?): BiliHookSymbols? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(raw)
                BiliHookSymbols(
                    cacheSchemaVersion = obj.optInt("cacheSchemaVersion", 0),
                    dexKitRuleVersion = obj.optInt("dexKitRuleVersion", 0),
                    fingerprint = obj.optString("fingerprint"),
                    hookPoints = obj.optJSONArray("hookPoints").toList { HookPointStatus.fromJson(it) },
                    scanErrors = obj.optJSONArray("scanErrors").toStringList(),
                    splashAd = obj.optJSONObject("splashAd")?.let(SplashAdSymbols::fromJson),
                    share = obj.optJSONObject("share")?.let(ShareSymbols::fromJson),
                    rewardAd = obj.optJSONObject("rewardAd")?.let(RewardAdSymbols::fromJson),
                    tryFreeQuality = obj.optJSONObject("tryFreeQuality")?.let(TryFreeQualitySymbols::fromJson),
                    teenagersMode = obj.optJSONObject("teenagersMode")?.let(TeenagersModeSymbols::fromJson),
                    account = obj.optJSONObject("account")?.let(AccountSymbols::fromJson),
                    settings = obj.optJSONObject("settings")?.let(SettingsSymbols::fromJson),
                    blockUpdate = obj.optJSONObject("blockUpdate")?.let(BlockUpdateSymbols::fromJson),
                    mineProfile = obj.optJSONObject("mineProfile")?.let(MineProfileSymbols::fromJson),
                    downloadThread = obj.optJSONObject("downloadThread")?.let(DownloadThreadSymbols::fromJson),
                    storyPlayerAd = obj.optJSONObject("storyPlayerAd")?.let(StoryPlayerAdSymbols::fromJson),
                    storyFullscreen = obj.optJSONObject("storyFullscreen")?.let(StoryFullscreenSymbols::fromJson),
                    storyDanmaku = obj.optJSONObject("storyDanmaku")?.let(StoryDanmakuSymbols::fromJson),
                    storyComponentAlpha = obj.optJSONObject("storyComponentAlpha")
                        ?.let(StoryComponentAlphaSymbols::fromJson),
                    videoDetailBannerAd = obj.optJSONObject("videoDetailBannerAd")
                        ?.let(VideoDetailBannerAdSymbols::fromJson),
                    homeTopBar = obj.optJSONObject("homeTopBar")?.let(HomeTopBarSymbols::fromJson),
                    bottomBar = obj.optJSONObject("bottomBar")?.let(BottomBarSymbols::fromJson),
                    homeRecommendFeed = obj.optJSONObject("homeRecommendFeed")?.let(HomeRecommendFeedSymbols::fromJson),
                    homeRecommendTabs = obj.optJSONObject("homeRecommendTabs")?.let(HomeRecommendTabSymbols::fromJson),
                    homeComponentHide = obj.optJSONObject("homeComponentHide")?.let(HomeComponentHideSymbols::fromJson),
                    videoComment = obj.optJSONObject("videoComment")?.let(VideoCommentSymbols::fromJson),
                    skipVideoAd = obj.optJSONObject("skipVideoAd")?.let(SkipVideoAdSymbols::fromJson),
                    skipVideoAdProgress = obj.optJSONObject("skipVideoAdProgress")
                        ?.let(SkipVideoAdProgressSymbols::fromJson),
                    skipVideoAdAutoLike = obj.optJSONObject("skipVideoAdAutoLike")
                        ?.let(SkipVideoAdAutoLikeSymbols::fromJson),
                    chronosPromotion = obj.optJSONObject("chronosPromotion")?.let(ChronosPromotionSymbols::fromJson),
                    fullNumberFormat = obj.optJSONObject("fullNumberFormat")?.let(FullNumberFormatSymbols::fromJson),
                    tripleSpeed = obj.optJSONObject("tripleSpeed")?.let(TripleSpeedSymbols::fromJson),
                    customTheme = obj.optJSONObject("customTheme")?.let(CustomThemeSymbols::fromJson),
                    customSkin = obj.optJSONObject("customSkin")?.let(CustomSkinSymbols::fromJson),
                )
            }.getOrNull()
        }
    }
}

object DexKitRuleVersions {
    const val CURRENT = 52
}

data class HookPointStatus(
    val id: String,
    val state: HookPointState,
    val missing: String = "-",
    val target: String = "-",
    val evidence: String = "-",
) {
    fun toLine(): String =
        "HookPoint[$id] state=${state.name} missing=$missing target=$target evidence=$evidence"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("state", state.name)
        .put("missing", missing)
        .put("target", target)
        .put("evidence", evidence)

    companion object {
        fun found(id: String, target: String, evidence: String): HookPointStatus =
            HookPointStatus(id, HookPointState.FOUND, target = target, evidence = evidence)

        fun missing(id: String, reason: String): HookPointStatus =
            HookPointStatus(id, HookPointState.MISSING, missing = reason)

        fun optional(id: String, reason: String): HookPointStatus =
            HookPointStatus(id, HookPointState.OPTIONAL, missing = reason)

        fun error(id: String, reason: String): HookPointStatus =
            HookPointStatus(id, HookPointState.ERROR, missing = reason)

        fun fromJson(obj: JSONObject): HookPointStatus = HookPointStatus(
            id = obj.optString("id"),
            state = HookPointState.from(obj.optString("state")),
            missing = obj.optString("missing", "-"),
            target = obj.optString("target", "-"),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

enum class HookPointState {
    FOUND,
    OPTIONAL,
    MISSING,
    ERROR;

    companion object {
        fun from(raw: String?): HookPointState =
            entries.firstOrNull { it.name == raw } ?: MISSING
    }
}

data class SplashAdSymbols(
    val parserMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("parserMethods", parserMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSplashAdSymbols? {
        return RestoredSplashAdSymbols(parserMethods.restoreAll(classLoader) ?: return null)
    }

    companion object {
        fun fromJson(obj: JSONObject): SplashAdSymbols = SplashAdSymbols(
            parserMethods = obj.optJSONArray("parserMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSplashAdSymbols(
    val parserMethods: List<Method>,
)

data class ShareSymbols(
    val legacyGetLink: MethodDescriptor? = null,
    val legacyGetContent: MethodDescriptor? = null,
    val legacyGetShareMode: MethodDescriptor? = null,
    val legacySetLink: MethodDescriptor? = null,
    val legacySetContent: MethodDescriptor? = null,
    val legacySetShareMode: MethodDescriptor? = null,
    val shareChannelsGetCopyLink: MethodDescriptor? = null,
    val shareChannelsGetJumpLink: MethodDescriptor? = null,
    val shareChannelsGetText: MethodDescriptor? = null,
    val shareChannelsSetCopyLink: MethodDescriptor? = null,
    val shareChannelsSetJumpLink: MethodDescriptor? = null,
    val shareChannelsSetText: MethodDescriptor? = null,
    val shareChannelItemGetJumpLink: MethodDescriptor? = null,
    val shareChannelItemSetJumpLink: MethodDescriptor? = null,
    val shareClickResultClassName: String? = null,
    val shareBaseInfoClassName: String? = null,
    val shareContentClassName: String? = null,
    val shareContentCopyMethods: List<MethodDescriptor>,
    val shareContentGetLink: MethodDescriptor? = null,
    val shareContentGetContent: MethodDescriptor? = null,
    val shareContentGetMode: MethodDescriptor? = null,
    val shareBiliContentClassName: String? = null,
    val shareBiliContentCopyMethods: List<MethodDescriptor>,
    val shareBiliContentGetDescription: MethodDescriptor? = null,
    val shareBiliContentGetContentUrl: MethodDescriptor? = null,
    val copyContentClassName: String? = null,
    val copyContentGetters: List<MethodDescriptor>,
    val copyUtilityMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("legacyGetLink", legacyGetLink?.toJson())
        .putOpt("legacyGetContent", legacyGetContent?.toJson())
        .putOpt("legacyGetShareMode", legacyGetShareMode?.toJson())
        .putOpt("legacySetLink", legacySetLink?.toJson())
        .putOpt("legacySetContent", legacySetContent?.toJson())
        .putOpt("legacySetShareMode", legacySetShareMode?.toJson())
        .putOpt("shareChannelsGetCopyLink", shareChannelsGetCopyLink?.toJson())
        .putOpt("shareChannelsGetJumpLink", shareChannelsGetJumpLink?.toJson())
        .putOpt("shareChannelsGetText", shareChannelsGetText?.toJson())
        .putOpt("shareChannelsSetCopyLink", shareChannelsSetCopyLink?.toJson())
        .putOpt("shareChannelsSetJumpLink", shareChannelsSetJumpLink?.toJson())
        .putOpt("shareChannelsSetText", shareChannelsSetText?.toJson())
        .putOpt("shareChannelItemGetJumpLink", shareChannelItemGetJumpLink?.toJson())
        .putOpt("shareChannelItemSetJumpLink", shareChannelItemSetJumpLink?.toJson())
        .putOpt("shareClickResultClassName", shareClickResultClassName)
        .putOpt("shareBaseInfoClassName", shareBaseInfoClassName)
        .putOpt("shareContentClassName", shareContentClassName)
        .put("shareContentCopyMethods", shareContentCopyMethods.toJsonArray { it.toJson() })
        .putOpt("shareContentGetLink", shareContentGetLink?.toJson())
        .putOpt("shareContentGetContent", shareContentGetContent?.toJson())
        .putOpt("shareContentGetMode", shareContentGetMode?.toJson())
        .putOpt("shareBiliContentClassName", shareBiliContentClassName)
        .put("shareBiliContentCopyMethods", shareBiliContentCopyMethods.toJsonArray { it.toJson() })
        .putOpt("shareBiliContentGetDescription", shareBiliContentGetDescription?.toJson())
        .putOpt("shareBiliContentGetContentUrl", shareBiliContentGetContentUrl?.toJson())
        .putOpt("copyContentClassName", copyContentClassName)
        .put("copyContentGetters", copyContentGetters.toJsonArray { it.toJson() })
        .put("copyUtilityMethods", copyUtilityMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredShareSymbols =
        RestoredShareSymbols(
            legacyGetLink = legacyGetLink.restoreOptional(classLoader),
            legacyGetContent = legacyGetContent.restoreOptional(classLoader),
            legacyGetShareMode = legacyGetShareMode.restoreOptional(classLoader),
            legacySetLink = legacySetLink.restoreOptional(classLoader),
            legacySetContent = legacySetContent.restoreOptional(classLoader),
            legacySetShareMode = legacySetShareMode.restoreOptional(classLoader),
            shareChannelsGetCopyLink = shareChannelsGetCopyLink.restoreOptional(classLoader),
            shareChannelsGetJumpLink = shareChannelsGetJumpLink.restoreOptional(classLoader),
            shareChannelsGetText = shareChannelsGetText.restoreOptional(classLoader),
            shareChannelsSetCopyLink = shareChannelsSetCopyLink.restoreOptional(classLoader),
            shareChannelsSetJumpLink = shareChannelsSetJumpLink.restoreOptional(classLoader),
            shareChannelsSetText = shareChannelsSetText.restoreOptional(classLoader),
            shareChannelItemGetJumpLink = shareChannelItemGetJumpLink.restoreOptional(classLoader),
            shareChannelItemSetJumpLink = shareChannelItemSetJumpLink.restoreOptional(classLoader),
            shareClickResultClass = shareClickResultClassName?.let(classLoader::loadClassOrNull),
            shareBaseInfoClass = shareBaseInfoClassName?.let(classLoader::loadClassOrNull),
            shareContentClass = shareContentClassName?.let(classLoader::loadClassOrNull),
            shareContentCopyMethods = shareContentCopyMethods.restoreAvailable(classLoader),
            shareContentGetLink = shareContentGetLink.restoreOptional(classLoader),
            shareContentGetContent = shareContentGetContent.restoreOptional(classLoader),
            shareContentGetMode = shareContentGetMode.restoreOptional(classLoader),
            shareBiliContentClass = shareBiliContentClassName?.let(classLoader::loadClassOrNull),
            shareBiliContentCopyMethods = shareBiliContentCopyMethods.restoreAvailable(classLoader),
            shareBiliContentGetDescription = shareBiliContentGetDescription.restoreOptional(classLoader),
            shareBiliContentGetContentUrl = shareBiliContentGetContentUrl.restoreOptional(classLoader),
            copyContentClass = copyContentClassName?.let(classLoader::loadClassOrNull),
            copyContentGetters = copyContentGetters.restoreAvailable(classLoader),
            copyUtilityMethods = copyUtilityMethods.restoreAvailable(classLoader),
        )

    companion object {
        fun fromJson(obj: JSONObject): ShareSymbols = ShareSymbols(
            legacyGetLink = obj.optJSONObject("legacyGetLink")?.let(MethodDescriptor::fromJson),
            legacyGetContent = obj.optJSONObject("legacyGetContent")?.let(MethodDescriptor::fromJson),
            legacyGetShareMode = obj.optJSONObject("legacyGetShareMode")?.let(MethodDescriptor::fromJson),
            legacySetLink = obj.optJSONObject("legacySetLink")?.let(MethodDescriptor::fromJson),
            legacySetContent = obj.optJSONObject("legacySetContent")?.let(MethodDescriptor::fromJson),
            legacySetShareMode = obj.optJSONObject("legacySetShareMode")?.let(MethodDescriptor::fromJson),
            shareChannelsGetCopyLink = obj.optJSONObject("shareChannelsGetCopyLink")?.let(MethodDescriptor::fromJson),
            shareChannelsGetJumpLink = obj.optJSONObject("shareChannelsGetJumpLink")?.let(MethodDescriptor::fromJson),
            shareChannelsGetText = obj.optJSONObject("shareChannelsGetText")?.let(MethodDescriptor::fromJson),
            shareChannelsSetCopyLink = obj.optJSONObject("shareChannelsSetCopyLink")?.let(MethodDescriptor::fromJson),
            shareChannelsSetJumpLink = obj.optJSONObject("shareChannelsSetJumpLink")?.let(MethodDescriptor::fromJson),
            shareChannelsSetText = obj.optJSONObject("shareChannelsSetText")?.let(MethodDescriptor::fromJson),
            shareChannelItemGetJumpLink = obj.optJSONObject("shareChannelItemGetJumpLink")
                ?.let(MethodDescriptor::fromJson),
            shareChannelItemSetJumpLink = obj.optJSONObject("shareChannelItemSetJumpLink")
                ?.let(MethodDescriptor::fromJson),
            shareClickResultClassName = obj.optString("shareClickResultClassName").takeIf { it.isNotBlank() },
            shareBaseInfoClassName = obj.optString("shareBaseInfoClassName").takeIf { it.isNotBlank() },
            shareContentClassName = obj.optString("shareContentClassName").takeIf { it.isNotBlank() },
            shareContentCopyMethods = obj.optJSONArray("shareContentCopyMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            shareContentGetLink = obj.optJSONObject("shareContentGetLink")?.let(MethodDescriptor::fromJson),
            shareContentGetContent = obj.optJSONObject("shareContentGetContent")?.let(MethodDescriptor::fromJson),
            shareContentGetMode = obj.optJSONObject("shareContentGetMode")?.let(MethodDescriptor::fromJson),
            shareBiliContentClassName = obj.optString("shareBiliContentClassName").takeIf { it.isNotBlank() },
            shareBiliContentCopyMethods = obj.optJSONArray("shareBiliContentCopyMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            shareBiliContentGetDescription = obj.optJSONObject("shareBiliContentGetDescription")
                ?.let(MethodDescriptor::fromJson),
            shareBiliContentGetContentUrl = obj.optJSONObject("shareBiliContentGetContentUrl")
                ?.let(MethodDescriptor::fromJson),
            copyContentClassName = obj.optString("copyContentClassName").takeIf { it.isNotBlank() },
            copyContentGetters = obj.optJSONArray("copyContentGetters").toList { MethodDescriptor.fromJson(it) },
            copyUtilityMethods = obj.optJSONArray("copyUtilityMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredShareSymbols(
    val legacyGetLink: Method?,
    val legacyGetContent: Method?,
    val legacyGetShareMode: Method?,
    val legacySetLink: Method?,
    val legacySetContent: Method?,
    val legacySetShareMode: Method?,
    val shareChannelsGetCopyLink: Method?,
    val shareChannelsGetJumpLink: Method?,
    val shareChannelsGetText: Method?,
    val shareChannelsSetCopyLink: Method?,
    val shareChannelsSetJumpLink: Method?,
    val shareChannelsSetText: Method?,
    val shareChannelItemGetJumpLink: Method?,
    val shareChannelItemSetJumpLink: Method?,
    val shareClickResultClass: Class<*>?,
    val shareBaseInfoClass: Class<*>?,
    val shareContentClass: Class<*>?,
    val shareContentCopyMethods: List<Method>,
    val shareContentGetLink: Method?,
    val shareContentGetContent: Method?,
    val shareContentGetMode: Method?,
    val shareBiliContentClass: Class<*>?,
    val shareBiliContentCopyMethods: List<Method>,
    val shareBiliContentGetDescription: Method?,
    val shareBiliContentGetContentUrl: Method?,
    val copyContentClass: Class<*>?,
    val copyContentGetters: List<Method>,
    val copyUtilityMethods: List<Method>,
)

data class RewardAdSymbols(
    val activityOnCreate: MethodDescriptor? = null,
    val activityOnResume: MethodDescriptor? = null,
    val activityOnStop: MethodDescriptor? = null,
    val headerSetTotalTime: MethodDescriptor? = null,
    val headerSetElapsedTime: MethodDescriptor? = null,
    val headerStartTimer: MethodDescriptor? = null,
    val countDownSetTotalTime: MethodDescriptor? = null,
    val countDownSetElapsedTime: MethodDescriptor? = null,
    val jumpClockField: FieldDescriptor? = null,
    val miniGameRewardShow: MethodDescriptor? = null,
    val miniGameRewardEmitEvent: MethodDescriptor? = null,
    val miniGameRewardCallback: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("activityOnCreate", activityOnCreate?.toJson())
        .putOpt("activityOnResume", activityOnResume?.toJson())
        .putOpt("activityOnStop", activityOnStop?.toJson())
        .putOpt("headerSetTotalTime", headerSetTotalTime?.toJson())
        .putOpt("headerSetElapsedTime", headerSetElapsedTime?.toJson())
        .putOpt("headerStartTimer", headerStartTimer?.toJson())
        .putOpt("countDownSetTotalTime", countDownSetTotalTime?.toJson())
        .putOpt("countDownSetElapsedTime", countDownSetElapsedTime?.toJson())
        .putOpt("jumpClockField", jumpClockField?.toJson())
        .putOpt("miniGameRewardShow", miniGameRewardShow?.toJson())
        .putOpt("miniGameRewardEmitEvent", miniGameRewardEmitEvent?.toJson())
        .putOpt("miniGameRewardCallback", miniGameRewardCallback?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredRewardAdSymbols =
        RestoredRewardAdSymbols(
            activityOnCreate = activityOnCreate.restoreOptional(classLoader),
            activityOnResume = activityOnResume.restoreOptional(classLoader),
            activityOnStop = activityOnStop.restoreOptional(classLoader),
            headerSetTotalTime = headerSetTotalTime.restoreOptional(classLoader),
            headerSetElapsedTime = headerSetElapsedTime.restoreOptional(classLoader),
            headerStartTimer = headerStartTimer.restoreOptional(classLoader),
            countDownSetTotalTime = countDownSetTotalTime.restoreOptional(classLoader),
            countDownSetElapsedTime = countDownSetElapsedTime.restoreOptional(classLoader),
            jumpClockField = jumpClockField.restoreOptional(classLoader),
            miniGameRewardShow = miniGameRewardShow.restoreOptional(classLoader),
            miniGameRewardEmitEvent = miniGameRewardEmitEvent.restoreOptional(classLoader),
            miniGameRewardCallback = miniGameRewardCallback.restoreOptional(classLoader),
        )

    companion object {
        fun fromJson(obj: JSONObject): RewardAdSymbols = RewardAdSymbols(
            activityOnCreate = obj.optJSONObject("activityOnCreate")?.let(MethodDescriptor::fromJson),
            activityOnResume = obj.optJSONObject("activityOnResume")?.let(MethodDescriptor::fromJson),
            activityOnStop = obj.optJSONObject("activityOnStop")?.let(MethodDescriptor::fromJson),
            headerSetTotalTime = obj.optJSONObject("headerSetTotalTime")?.let(MethodDescriptor::fromJson),
            headerSetElapsedTime = obj.optJSONObject("headerSetElapsedTime")?.let(MethodDescriptor::fromJson),
            headerStartTimer = obj.optJSONObject("headerStartTimer")?.let(MethodDescriptor::fromJson),
            countDownSetTotalTime = obj.optJSONObject("countDownSetTotalTime")?.let(MethodDescriptor::fromJson),
            countDownSetElapsedTime = obj.optJSONObject("countDownSetElapsedTime")?.let(MethodDescriptor::fromJson),
            jumpClockField = obj.optJSONObject("jumpClockField")?.let(FieldDescriptor::fromJson),
            miniGameRewardShow = obj.optJSONObject("miniGameRewardShow")?.let(MethodDescriptor::fromJson),
            miniGameRewardEmitEvent = obj.optJSONObject("miniGameRewardEmitEvent")?.let(MethodDescriptor::fromJson),
            miniGameRewardCallback = obj.optJSONObject("miniGameRewardCallback")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredRewardAdSymbols(
    val activityOnCreate: Method?,
    val activityOnResume: Method?,
    val activityOnStop: Method?,
    val headerSetTotalTime: Method?,
    val headerSetElapsedTime: Method?,
    val headerStartTimer: Method?,
    val countDownSetTotalTime: Method?,
    val countDownSetElapsedTime: Method?,
    val jumpClockField: Field?,
    val miniGameRewardShow: Method?,
    val miniGameRewardEmitEvent: Method?,
    val miniGameRewardCallback: Method?,
)

data class TryFreeQualitySymbols(
    val playViewMethods: List<MethodDescriptor>,
    val getIsNeedTrialMethods: List<MethodDescriptor>,
    val setIsNeedTrialMethods: List<MethodDescriptor>,
    val getVipFreeMethods: List<MethodDescriptor>,
    val getNeedVipMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("playViewMethods", playViewMethods.toJsonArray { it.toJson() })
        .put("getIsNeedTrialMethods", getIsNeedTrialMethods.toJsonArray { it.toJson() })
        .put("setIsNeedTrialMethods", setIsNeedTrialMethods.toJsonArray { it.toJson() })
        .put("getVipFreeMethods", getVipFreeMethods.toJsonArray { it.toJson() })
        .put("getNeedVipMethods", getNeedVipMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredTryFreeQualitySymbols? {
        return RestoredTryFreeQualitySymbols(
            playViewMethods = playViewMethods.restoreAll(classLoader) ?: return null,
            getIsNeedTrialMethods = getIsNeedTrialMethods.restoreAll(classLoader) ?: return null,
            setIsNeedTrialMethods = setIsNeedTrialMethods.restoreAll(classLoader) ?: return null,
            getVipFreeMethods = getVipFreeMethods.restoreAll(classLoader) ?: return null,
            getNeedVipMethods = getNeedVipMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): TryFreeQualitySymbols = TryFreeQualitySymbols(
            playViewMethods = obj.optJSONArray("playViewMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            getIsNeedTrialMethods = obj.optJSONArray("getIsNeedTrialMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            setIsNeedTrialMethods = obj.optJSONArray("setIsNeedTrialMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            getVipFreeMethods = obj.optJSONArray("getVipFreeMethods").toList { MethodDescriptor.fromJson(it) },
            getNeedVipMethods = obj.optJSONArray("getNeedVipMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredTryFreeQualitySymbols(
    val playViewMethods: List<Method>,
    val getIsNeedTrialMethods: List<Method>,
    val setIsNeedTrialMethods: List<Method>,
    val getVipFreeMethods: List<Method>,
    val getNeedVipMethods: List<Method>,
)

data class TeenagersModeSymbols(
    val onCreateMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("onCreateMethods", onCreateMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredTeenagersModeSymbols? {
        return RestoredTeenagersModeSymbols(onCreateMethods.restoreAll(classLoader) ?: return null)
    }

    companion object {
        fun fromJson(obj: JSONObject): TeenagersModeSymbols = TeenagersModeSymbols(
            onCreateMethods = obj.optJSONArray("onCreateMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredTeenagersModeSymbols(
    val onCreateMethods: List<Method>,
)

data class AccountSymbols(
    val accountClassName: String,
    val getMethod: MethodDescriptor,
    val accessKeyMethod: MethodDescriptor,
    val isLoginMethod: MethodDescriptor? = null,
    val midMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accountClassName", accountClassName)
        .put("getMethod", getMethod.toJson())
        .put("accessKeyMethod", accessKeyMethod.toJson())
        .putOpt("isLoginMethod", isLoginMethod?.toJson())
        .putOpt("midMethod", midMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredAccountSymbols? {
        val accountClass = classLoader.loadClassOrNull(accountClassName) ?: return null
        val get = getMethod.restore(accountClass) ?: return null
        val access = accessKeyMethod.restore(accountClass) ?: return null
        val isLogin = isLoginMethod?.restore(accountClass)
            ?: accountClass.allMethods().firstOrNull {
                it.name == "isLogin" && it.parameterCount == 0 &&
                    (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.javaObjectType)
            }
            ?: return null
        val mid = midMethod?.restore(accountClass)
            ?: accountClass.allMethods().firstOrNull {
                it.name == "mid" && it.parameterCount == 0 &&
                    (it.returnType == Long::class.javaPrimitiveType || it.returnType == Long::class.javaObjectType)
            }
        if (!Modifier.isStatic(get.modifiers)) return null
        if (get.returnType != accountClass) return null
        if (access.returnType != String::class.java || Modifier.isStatic(access.modifiers)) return null
        return RestoredAccountSymbols(accountClass, get, access, isLogin, mid)
    }

    companion object {
        fun fromJson(obj: JSONObject): AccountSymbols = AccountSymbols(
            accountClassName = obj.optString("accountClassName"),
            getMethod = MethodDescriptor.fromJson(obj.getJSONObject("getMethod")),
            accessKeyMethod = MethodDescriptor.fromJson(obj.getJSONObject("accessKeyMethod")),
            isLoginMethod = obj.optJSONObject("isLoginMethod")?.let(MethodDescriptor::fromJson),
            midMethod = obj.optJSONObject("midMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredAccountSymbols(
    val accountClass: Class<*>,
    val getMethod: Method,
    val accessKeyMethod: Method,
    val isLoginMethod: Method,
    val midMethod: Method?,
)

data class SettingsSymbols(
    val fragmentMethods: List<MethodDescriptor>,
    val preferenceClassName: String,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fragmentMethods", fragmentMethods.toJsonArray { it.toJson() })
        .put("preferenceClassName", preferenceClassName)
        .put("evidence", evidence)

    fun restoreFragmentMethods(classLoader: ClassLoader): List<Method> =
        fragmentMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }

    fun restorePreferenceClass(classLoader: ClassLoader): Class<*>? =
        classLoader.loadClassOrNull(preferenceClassName)

    companion object {
        fun fromJson(obj: JSONObject): SettingsSymbols = SettingsSymbols(
            fragmentMethods = obj.optJSONArray("fragmentMethods").toList { MethodDescriptor.fromJson(it) },
            preferenceClassName = obj.optString("preferenceClassName"),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class BlockUpdateSymbols(
    val checkMethod: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("checkMethod", checkMethod.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredBlockUpdateSymbols? {
        val owner = classLoader.loadClassOrNull(checkMethod.declaringClassName) ?: return null
        val method = checkMethod.restore(owner) ?: return null
        return RestoredBlockUpdateSymbols(method)
    }

    companion object {
        fun fromJson(obj: JSONObject): BlockUpdateSymbols = BlockUpdateSymbols(
            checkMethod = MethodDescriptor.fromJson(obj.getJSONObject("checkMethod")),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredBlockUpdateSymbols(
    val checkMethod: Method,
)

data class MineProfileSymbols(
    val fragmentClassName: String,
    val vipViewClassName: String,
    val vipField: FieldDescriptor,
    val managerBindingField: FieldDescriptor? = null,
    val bindingRootField: FieldDescriptor? = null,
    val onResume: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fragmentClassName", fragmentClassName)
        .put("vipViewClassName", vipViewClassName)
        .put("vipField", vipField.toJson())
        .putOpt("managerBindingField", managerBindingField?.toJson())
        .putOpt("bindingRootField", bindingRootField?.toJson())
        .put("onResume", onResume.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredMineProfileSymbols? {
        val fragmentClass = classLoader.loadClassOrNull(fragmentClassName) ?: return null
        val vipViewClass = classLoader.loadClassOrNull(vipViewClassName) ?: return null
        val field = vipField.restore(fragmentClass) ?: return null
        val method = onResume.restore(fragmentClass) ?: return null
        if (!vipViewClass.isAssignableFrom(field.type)) return null
        val bindingField = managerBindingField?.restore(vipViewClass)
        val rootField = bindingRootField?.let { descriptor ->
            val owner = bindingField?.type ?: return null
            descriptor.restore(owner)
        }
        return RestoredMineProfileSymbols(fragmentClass, vipViewClass, field, bindingField, rootField, method)
    }

    companion object {
        fun fromJson(obj: JSONObject): MineProfileSymbols = MineProfileSymbols(
            fragmentClassName = obj.optString("fragmentClassName"),
            vipViewClassName = obj.optString("vipViewClassName"),
            vipField = FieldDescriptor.fromJson(obj.getJSONObject("vipField")),
            managerBindingField = obj.optJSONObject("managerBindingField")?.let(FieldDescriptor::fromJson),
            bindingRootField = obj.optJSONObject("bindingRootField")?.let(FieldDescriptor::fromJson),
            onResume = MethodDescriptor.fromJson(obj.getJSONObject("onResume")),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredMineProfileSymbols(
    val fragmentClass: Class<*>,
    val vipViewClass: Class<*>,
    val vipField: Field,
    val managerBindingField: Field?,
    val bindingRootField: Field?,
    val onResume: Method,
) {
    fun resolveVipView(fragment: Any): View? {
        val holder = vipField.get(fragment) ?: return null
        if (holder is View) return holder
        val binding = managerBindingField?.get(holder) ?: return null
        return bindingRootField?.get(binding) as? View
    }
}

data class DownloadThreadSymbols(
    val listeners: List<DownloadThreadListenerSymbols>,
    val reportMethod: MethodDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("listeners", listeners.toJsonArray { it.toJson() })
        .putOpt("reportMethod", reportMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredDownloadThreadSymbols {
        val restoredListeners = listeners.mapNotNull { it.restore(classLoader) }
        val restoredReport = reportMethod?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        return RestoredDownloadThreadSymbols(restoredListeners, restoredReport)
    }

    companion object {
        fun fromJson(obj: JSONObject): DownloadThreadSymbols = DownloadThreadSymbols(
            listeners = obj.optJSONArray("listeners").toList { DownloadThreadListenerSymbols.fromJson(it) },
            reportMethod = obj.optJSONObject("reportMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class DownloadThreadListenerSymbols(
    val className: String,
    val constructor: ConstructorDescriptor,
    val onClick: MethodDescriptor,
    val textViewField: FieldDescriptor,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("className", className)
        .put("constructor", constructor.toJson())
        .put("onClick", onClick.toJson())
        .put("textViewField", textViewField.toJson())

    fun restore(classLoader: ClassLoader): RestoredDownloadThreadListenerSymbols? {
        val type = classLoader.loadClassOrNull(className) ?: return null
        val ctor = constructor.restore(type) ?: return null
        val click = onClick.restore(type) ?: return null
        val fieldOwner = classLoader.loadClassOrNull(textViewField.declaringClassName) ?: return null
        val field = textViewField.restore(fieldOwner) ?: return null
        return RestoredDownloadThreadListenerSymbols(type, ctor, click, field)
    }

    companion object {
        fun fromJson(obj: JSONObject): DownloadThreadListenerSymbols = DownloadThreadListenerSymbols(
            className = obj.optString("className"),
            constructor = ConstructorDescriptor.fromJson(obj.getJSONObject("constructor")),
            onClick = MethodDescriptor.fromJson(obj.getJSONObject("onClick")),
            textViewField = FieldDescriptor.fromJson(obj.getJSONObject("textViewField")),
        )
    }
}

data class RestoredDownloadThreadSymbols(
    val listeners: List<RestoredDownloadThreadListenerSymbols>,
    val reportMethod: Method?,
)

data class RestoredDownloadThreadListenerSymbols(
    val listenerClass: Class<*>,
    val constructor: Constructor<*>,
    val onClick: Method,
    val textViewField: Field,
)

data class StoryPlayerAdSymbols(
    val feedGetItems: MethodDescriptor?,
    val pagerListMethods: List<MethodDescriptor>,
    val rerankInvokeSuspend: MethodDescriptor?,
    val kotlinUnitField: FieldDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("feedGetItems", feedGetItems?.toJson())
        .put("pagerListMethods", pagerListMethods.toJsonArray { it.toJson() })
        .putOpt("rerankInvokeSuspend", rerankInvokeSuspend?.toJson())
        .putOpt("kotlinUnitField", kotlinUnitField?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryPlayerAdSymbols? {
        val feed = feedGetItems?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val pager = pagerListMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (pager.size != pagerListMethods.size) return null
        val rerank = rerankInvokeSuspend?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val unit = kotlinUnitField?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)?.get(null)
        }
        if (rerankInvokeSuspend != null && rerank == null) return null
        if (kotlinUnitField != null && unit == null) return null
        return RestoredStoryPlayerAdSymbols(feed, pager, rerank, unit)
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryPlayerAdSymbols = StoryPlayerAdSymbols(
            feedGetItems = obj.optJSONObject("feedGetItems")?.let(MethodDescriptor::fromJson),
            pagerListMethods = obj.optJSONArray("pagerListMethods").toList { MethodDescriptor.fromJson(it) },
            rerankInvokeSuspend = obj.optJSONObject("rerankInvokeSuspend")?.let(MethodDescriptor::fromJson),
            kotlinUnitField = obj.optJSONObject("kotlinUnitField")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredStoryPlayerAdSymbols(
    val feedGetItems: Method?,
    val pagerListMethods: List<Method>,
    val rerankInvokeSuspend: Method?,
    val kotlinUnit: Any?,
)

data class StoryFullscreenSymbols(
    val onCreate: MethodDescriptor,
    val onWindowFocusChanged: MethodDescriptor,
    val onResume: MethodDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("onCreate", onCreate.toJson())
        .put("onWindowFocusChanged", onWindowFocusChanged.toJson())
        .putOpt("onResume", onResume?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryFullscreenSymbols? {
        val create = onCreate.restoreOptional(classLoader) ?: return null
        val focus = onWindowFocusChanged.restoreOptional(classLoader) ?: return null
        val resume = onResume.restoreOptional(classLoader)
        if (onResume != null && resume == null) return null
        return RestoredStoryFullscreenSymbols(
            onCreate = create,
            onWindowFocusChanged = focus,
            onResume = resume,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryFullscreenSymbols = StoryFullscreenSymbols(
            onCreate = MethodDescriptor.fromJson(obj.getJSONObject("onCreate")),
            onWindowFocusChanged = MethodDescriptor.fromJson(obj.getJSONObject("onWindowFocusChanged")),
            onResume = obj.optJSONObject("onResume")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredStoryFullscreenSymbols(
    val onCreate: Method,
    val onWindowFocusChanged: Method,
    val onResume: Method?,
)

data class StoryDanmakuSymbols(
    val commentShowMethods: List<MethodDescriptor>,
    val commentHideMethods: List<MethodDescriptor>,
    val introCommentShowMethod: MethodDescriptor?,
    val introCommentDismissMethod: MethodDescriptor?,
    val setDanmakuOpacity: MethodDescriptor,
    val updateCanvas: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("commentShowMethods", commentShowMethods.toJsonArray { it.toJson() })
        .put("commentHideMethods", commentHideMethods.toJsonArray { it.toJson() })
        .putOpt("introCommentShowMethod", introCommentShowMethod?.toJson())
        .putOpt("introCommentDismissMethod", introCommentDismissMethod?.toJson())
        .put("setDanmakuOpacity", setDanmakuOpacity.toJson())
        .put("updateCanvas", updateCanvas.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryDanmakuSymbols? {
        val showMethods = commentShowMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (showMethods.size != commentShowMethods.size) return null
        val hideMethods = commentHideMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (hideMethods.size != commentHideMethods.size) return null
        val introShowMethod = introCommentShowMethod.restoreOptional(classLoader)
        if (introCommentShowMethod != null && introShowMethod == null) return null
        val introDismissMethod = introCommentDismissMethod.restoreOptional(classLoader)
        if (introCommentDismissMethod != null && introDismissMethod == null) return null
        val setOpacity = setDanmakuOpacity.restoreOptional(classLoader) ?: return null
        val canvas = updateCanvas.restoreOptional(classLoader) ?: return null
        return RestoredStoryDanmakuSymbols(
            commentShowMethods = showMethods,
            commentHideMethods = hideMethods,
            introCommentShowMethod = introShowMethod,
            introCommentDismissMethod = introDismissMethod,
            setDanmakuOpacity = setOpacity,
            updateCanvas = canvas,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryDanmakuSymbols = StoryDanmakuSymbols(
            commentShowMethods = obj.optJSONArray("commentShowMethods").toList { MethodDescriptor.fromJson(it) },
            commentHideMethods = obj.optJSONArray("commentHideMethods").toList { MethodDescriptor.fromJson(it) },
            introCommentShowMethod = obj.optJSONObject("introCommentShowMethod")?.let(MethodDescriptor::fromJson),
            introCommentDismissMethod = obj.optJSONObject("introCommentDismissMethod")
                ?.let(MethodDescriptor::fromJson),
            setDanmakuOpacity = MethodDescriptor.fromJson(obj.getJSONObject("setDanmakuOpacity")),
            updateCanvas = MethodDescriptor.fromJson(obj.getJSONObject("updateCanvas")),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredStoryDanmakuSymbols(
    val commentShowMethods: List<Method>,
    val commentHideMethods: List<Method>,
    val introCommentShowMethod: Method?,
    val introCommentDismissMethod: Method?,
    val setDanmakuOpacity: Method,
    val updateCanvas: Method,
)

data class StoryComponentAlphaSymbols(
    val infoConstructors: List<ConstructorDescriptor>,
    val rightConstructors: List<ConstructorDescriptor>,
    val bottomConstructors: List<ConstructorDescriptor>,
    val seekBarConstructors: List<ConstructorDescriptor>,
    val fragmentOnCreateView: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("infoConstructors", infoConstructors.toJsonArray { it.toJson() })
        .put("rightConstructors", rightConstructors.toJsonArray { it.toJson() })
        .put("bottomConstructors", bottomConstructors.toJsonArray { it.toJson() })
        .put("seekBarConstructors", seekBarConstructors.toJsonArray { it.toJson() })
        .put("fragmentOnCreateView", fragmentOnCreateView.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryComponentAlphaSymbols? {
        val infoType = classLoader.loadClassOrNull(STORY_INFO_MODULE) ?: return null
        val rightType = classLoader.loadClassOrNull(STORY_RIGHT_MODULE) ?: return null
        val bottomType = classLoader.loadClassOrNull(STORY_BOTTOM_MODULE) ?: return null
        val seekBarType = classLoader.loadClassOrNull(STORY_SEEK_BAR) ?: return null
        return RestoredStoryComponentAlphaSymbols(
            infoConstructors = infoConstructors.mapNotNull { it.restore(infoType) }
                .takeIf { it.size == infoConstructors.size } ?: return null,
            rightConstructors = rightConstructors.mapNotNull { it.restore(rightType) }
                .takeIf { it.size == rightConstructors.size } ?: return null,
            bottomConstructors = bottomConstructors.mapNotNull { it.restore(bottomType) }
                .takeIf { it.size == bottomConstructors.size } ?: return null,
            seekBarConstructors = seekBarConstructors.mapNotNull { it.restore(seekBarType) }
                .takeIf { it.size == seekBarConstructors.size } ?: return null,
            fragmentOnCreateView = fragmentOnCreateView.restoreOptional(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryComponentAlphaSymbols = StoryComponentAlphaSymbols(
            infoConstructors = obj.optJSONArray("infoConstructors").toList { ConstructorDescriptor.fromJson(it) },
            rightConstructors = obj.optJSONArray("rightConstructors").toList { ConstructorDescriptor.fromJson(it) },
            bottomConstructors = obj.optJSONArray("bottomConstructors").toList { ConstructorDescriptor.fromJson(it) },
            seekBarConstructors = obj.optJSONArray("seekBarConstructors").toList { ConstructorDescriptor.fromJson(it) },
            fragmentOnCreateView = MethodDescriptor.fromJson(obj.getJSONObject("fragmentOnCreateView")),
            evidence = obj.optString("evidence", "-"),
        )

        private const val STORY_INFO_MODULE = "com.bilibili.video.story.module.StoryInfoModule"
        private const val STORY_RIGHT_MODULE = "com.bilibili.video.story.module.StoryRightModule"
        private const val STORY_BOTTOM_MODULE = "com.bilibili.video.story.module.StoryBottomModule"
        private const val STORY_SEEK_BAR = "com.bilibili.video.story.view.StorySeekBar"
    }
}

data class RestoredStoryComponentAlphaSymbols(
    val infoConstructors: List<Constructor<*>>,
    val rightConstructors: List<Constructor<*>>,
    val bottomConstructors: List<Constructor<*>>,
    val seekBarConstructors: List<Constructor<*>>,
    val fragmentOnCreateView: Method,
)

data class VideoDetailBannerAdSymbols(
    val getVideoDetail: MethodDescriptor?,
    val videoDetailTypeName: String?,
    val underPlayerTypeName: String?,
    val relateTypeName: String?,
    val merchandiseTypeName: String?,
    val pausedPageTypeName: String?,
    val adPanelTypeName: String?,
    val requestPausedPage: MethodDescriptor?,
    val getPausedPagePanel: MethodDescriptor?,
    val getBrandPausedPagePanel: MethodDescriptor?,
    val relateGameComponentTypeName: String?,
    val simpleViewEntryConstructor: ConstructorDescriptor?,
    val createViewEntry: MethodDescriptor?,
    val bindToView: MethodDescriptor?,
    val kotlinUnitField: FieldDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("getVideoDetail", getVideoDetail?.toJson())
        .putOpt("videoDetailTypeName", videoDetailTypeName)
        .putOpt("underPlayerTypeName", underPlayerTypeName)
        .putOpt("relateTypeName", relateTypeName)
        .putOpt("merchandiseTypeName", merchandiseTypeName)
        .putOpt("pausedPageTypeName", pausedPageTypeName)
        .putOpt("adPanelTypeName", adPanelTypeName)
        .putOpt("requestPausedPage", requestPausedPage?.toJson())
        .putOpt("getPausedPagePanel", getPausedPagePanel?.toJson())
        .putOpt("getBrandPausedPagePanel", getBrandPausedPagePanel?.toJson())
        .putOpt("relateGameComponentTypeName", relateGameComponentTypeName)
        .putOpt("simpleViewEntryConstructor", simpleViewEntryConstructor?.toJson())
        .putOpt("createViewEntry", createViewEntry?.toJson())
        .putOpt("bindToView", bindToView?.toJson())
        .putOpt("kotlinUnitField", kotlinUnitField?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoDetailBannerAdSymbols? {
        val getVideoDetailMethod = getVideoDetail?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val videoDetailType = videoDetailTypeName?.let(classLoader::loadClassOrNull)
        val underPlayerType = underPlayerTypeName?.let(classLoader::loadClassOrNull)
        val relateType = relateTypeName?.let(classLoader::loadClassOrNull)
        val merchandiseType = merchandiseTypeName?.let(classLoader::loadClassOrNull)
        val pausedPageType = pausedPageTypeName?.let(classLoader::loadClassOrNull)
        val adPanelType = adPanelTypeName?.let(classLoader::loadClassOrNull)
        val requestPausedPageMethod = requestPausedPage?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val getPausedPagePanelMethod = getPausedPagePanel?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val getBrandPausedPagePanelMethod = getBrandPausedPagePanel?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val relateGameComponentType = relateGameComponentTypeName?.let(classLoader::loadClassOrNull)
        if (getVideoDetail != null && (getVideoDetailMethod == null || videoDetailType == null)) {
            return null
        }
        if (underPlayerTypeName != null && underPlayerType == null) {
            return null
        }
        if (requestPausedPage != null && (pausedPageType == null || requestPausedPageMethod == null)) {
            return null
        }
        if ((getPausedPagePanel != null || getBrandPausedPagePanel != null) && adPanelType == null) {
            return null
        }
        if (getPausedPagePanel != null && getPausedPagePanelMethod == null) {
            return null
        }
        if (getBrandPausedPagePanel != null && getBrandPausedPagePanelMethod == null) {
            return null
        }

        val constructor = simpleViewEntryConstructor?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val create = createViewEntry?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val bind = bindToView?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val unit = kotlinUnitField?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)?.get(null)
        }
        if (createViewEntry != null && (relateGameComponentType == null || constructor == null || create == null || bind == null || unit == null)) {
            return null
        }

        return RestoredVideoDetailBannerAdSymbols(
            getVideoDetail = getVideoDetailMethod,
            videoDetailType = videoDetailType,
            underPlayerType = underPlayerType,
            relateType = relateType,
            merchandiseType = merchandiseType,
            pausedPageType = pausedPageType,
            adPanelType = adPanelType,
            requestPausedPage = requestPausedPageMethod,
            getPausedPagePanel = getPausedPagePanelMethod,
            getBrandPausedPagePanel = getBrandPausedPagePanelMethod,
            relateGameComponentType = relateGameComponentType,
            simpleViewEntryConstructor = constructor,
            createViewEntry = create,
            bindToView = bind,
            kotlinUnit = unit,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoDetailBannerAdSymbols = VideoDetailBannerAdSymbols(
            getVideoDetail = obj.optJSONObject("getVideoDetail")?.let(MethodDescriptor::fromJson),
            videoDetailTypeName = obj.optString("videoDetailTypeName").takeIf { it.isNotBlank() },
            underPlayerTypeName = obj.optString("underPlayerTypeName").takeIf { it.isNotBlank() },
            relateTypeName = obj.optString("relateTypeName").takeIf { it.isNotBlank() },
            merchandiseTypeName = obj.optString("merchandiseTypeName").takeIf { it.isNotBlank() },
            pausedPageTypeName = obj.optString("pausedPageTypeName").takeIf { it.isNotBlank() },
            adPanelTypeName = obj.optString("adPanelTypeName").takeIf { it.isNotBlank() },
            requestPausedPage = obj.optJSONObject("requestPausedPage")?.let(MethodDescriptor::fromJson),
            getPausedPagePanel = obj.optJSONObject("getPausedPagePanel")?.let(MethodDescriptor::fromJson),
            getBrandPausedPagePanel = obj.optJSONObject("getBrandPausedPagePanel")?.let(MethodDescriptor::fromJson),
            relateGameComponentTypeName = obj.optString("relateGameComponentTypeName").takeIf { it.isNotBlank() },
            simpleViewEntryConstructor = obj.optJSONObject("simpleViewEntryConstructor")?.let(ConstructorDescriptor::fromJson),
            createViewEntry = obj.optJSONObject("createViewEntry")?.let(MethodDescriptor::fromJson),
            bindToView = obj.optJSONObject("bindToView")?.let(MethodDescriptor::fromJson),
            kotlinUnitField = obj.optJSONObject("kotlinUnitField")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredVideoDetailBannerAdSymbols(
    val getVideoDetail: Method?,
    val videoDetailType: Class<*>?,
    val underPlayerType: Class<*>?,
    val relateType: Class<*>?,
    val merchandiseType: Class<*>?,
    val pausedPageType: Class<*>?,
    val adPanelType: Class<*>?,
    val requestPausedPage: Method?,
    val getPausedPagePanel: Method?,
    val getBrandPausedPagePanel: Method?,
    val relateGameComponentType: Class<*>?,
    val simpleViewEntryConstructor: Constructor<*>?,
    val createViewEntry: Method?,
    val bindToView: Method?,
    val kotlinUnit: Any?,
)

data class HomeTopBarSymbols(
    val gameMenuMethod: MethodDescriptor?,
    val baseOnViewCreated: MethodDescriptor?,
    val defaultWordMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("gameMenuMethod", gameMenuMethod?.toJson())
        .putOpt("baseOnViewCreated", baseOnViewCreated?.toJson())
        .put("defaultWordMethods", defaultWordMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeTopBarSymbols? {
        val gameMenu = gameMenuMethod?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val onViewCreated = baseOnViewCreated?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val defaultWord = defaultWordMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (defaultWord.size != defaultWordMethods.size) return null
        return RestoredHomeTopBarSymbols(gameMenu, onViewCreated, defaultWord)
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeTopBarSymbols = HomeTopBarSymbols(
            gameMenuMethod = obj.optJSONObject("gameMenuMethod")?.let(MethodDescriptor::fromJson),
            baseOnViewCreated = obj.optJSONObject("baseOnViewCreated")?.let(MethodDescriptor::fromJson),
            defaultWordMethods = obj.optJSONArray("defaultWordMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredHomeTopBarSymbols(
    val gameMenuMethod: Method?,
    val baseOnViewCreated: Method?,
    val defaultWordMethods: List<Method>,
)

data class BottomBarSymbols(
    val tabHostSetTabsMethods: List<MethodDescriptor>,
    val tabHostGetTabsMethods: List<MethodDescriptor>,
    val baseOnViewCreatedMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tabHostSetTabsMethods", tabHostSetTabsMethods.toJsonArray { it.toJson() })
        .put("tabHostGetTabsMethods", tabHostGetTabsMethods.toJsonArray { it.toJson() })
        .put("baseOnViewCreatedMethods", baseOnViewCreatedMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredBottomBarSymbols? {
        val setTabs = tabHostSetTabsMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        val getTabs = tabHostGetTabsMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        val onViewCreated = baseOnViewCreatedMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (
            setTabs.size != tabHostSetTabsMethods.size ||
            getTabs.size != tabHostGetTabsMethods.size ||
            onViewCreated.size != baseOnViewCreatedMethods.size
        ) return null
        return RestoredBottomBarSymbols(setTabs, getTabs, onViewCreated)
    }

    companion object {
        fun fromJson(obj: JSONObject): BottomBarSymbols = BottomBarSymbols(
            tabHostSetTabsMethods = obj.optJSONArray("tabHostSetTabsMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            tabHostGetTabsMethods = obj.optJSONArray("tabHostGetTabsMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            baseOnViewCreatedMethods = obj.optJSONArray("baseOnViewCreatedMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredBottomBarSymbols(
    val tabHostSetTabsMethods: List<Method>,
    val tabHostGetTabsMethods: List<Method>,
    val baseOnViewCreatedMethods: List<Method>,
)

data class HomeRecommendTabSymbols(
    val buildTabsMethod: MethodDescriptor,
    val idField: FieldDescriptor,
    val titleField: FieldDescriptor,
    val uriField: FieldDescriptor,
    val reporterIdField: FieldDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("buildTabsMethod", buildTabsMethod.toJson())
        .put("idField", idField.toJson())
        .put("titleField", titleField.toJson())
        .put("uriField", uriField.toJson())
        .putOpt("reporterIdField", reporterIdField?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeRecommendTabSymbols? {
        val methodOwner = classLoader.loadClassOrNull(buildTabsMethod.declaringClassName) ?: return null
        val idOwner = classLoader.loadClassOrNull(idField.declaringClassName) ?: return null
        val titleOwner = classLoader.loadClassOrNull(titleField.declaringClassName) ?: return null
        val uriOwner = classLoader.loadClassOrNull(uriField.declaringClassName) ?: return null
        return RestoredHomeRecommendTabSymbols(
            buildTabsMethod = buildTabsMethod.restore(methodOwner) ?: return null,
            idField = idField.restore(idOwner) ?: return null,
            titleField = titleField.restore(titleOwner) ?: return null,
            uriField = uriField.restore(uriOwner) ?: return null,
            reporterIdField = reporterIdField.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeRecommendTabSymbols = HomeRecommendTabSymbols(
            buildTabsMethod = MethodDescriptor.fromJson(obj.getJSONObject("buildTabsMethod")),
            idField = FieldDescriptor.fromJson(obj.getJSONObject("idField")),
            titleField = FieldDescriptor.fromJson(obj.getJSONObject("titleField")),
            uriField = FieldDescriptor.fromJson(obj.getJSONObject("uriField")),
            reporterIdField = obj.optJSONObject("reporterIdField")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredHomeRecommendTabSymbols(
    val buildTabsMethod: Method,
    val idField: Field,
    val titleField: Field,
    val uriField: Field,
    val reporterIdField: Field?,
)

data class HomeRecommendFeedSymbols(
    val responseGetItems: List<PegasusResponseGetItemsSymbols>,
    val getHolderType: MethodDescriptor,
    val getBizType: MethodDescriptor?,
    val getHolderStyle: MethodDescriptor?,
    val isSmallCard: MethodDescriptor?,
    val getAdInfo: MethodDescriptor?,
    val getCardType: MethodDescriptor?,
    val getCardGoto: MethodDescriptor?,
    val getGoTo: MethodDescriptor?,
    val getUri: MethodDescriptor?,
    val adInfoClassName: String?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("responseGetItems", responseGetItems.toJsonArray { it.toJson() })
        .put("getHolderType", getHolderType.toJson())
        .putOpt("getBizType", getBizType?.toJson())
        .putOpt("getHolderStyle", getHolderStyle?.toJson())
        .putOpt("isSmallCard", isSmallCard?.toJson())
        .putOpt("getAdInfo", getAdInfo?.toJson())
        .putOpt("getCardType", getCardType?.toJson())
        .putOpt("getCardGoto", getCardGoto?.toJson())
        .putOpt("getGoTo", getGoTo?.toJson())
        .putOpt("getUri", getUri?.toJson())
        .putOpt("adInfoClassName", adInfoClassName)
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeRecommendFeedSymbols? {
        val responses = responseGetItems.mapNotNull { it.restore(classLoader) }
        if (responses.size != responseGetItems.size) return null
        val holderOwner = classLoader.loadClassOrNull(getHolderType.declaringClassName) ?: return null
        return RestoredHomeRecommendFeedSymbols(
            responseGetItems = responses,
            getHolderType = getHolderType.restore(holderOwner) ?: return null,
            getBizType = getBizType.restoreOptional(classLoader),
            getHolderStyle = getHolderStyle.restoreOptional(classLoader),
            isSmallCard = isSmallCard.restoreOptional(classLoader),
            getAdInfo = getAdInfo.restoreOptional(classLoader),
            getCardType = getCardType.restoreOptional(classLoader),
            getCardGoto = getCardGoto.restoreOptional(classLoader),
            getGoTo = getGoTo.restoreOptional(classLoader),
            getUri = getUri.restoreOptional(classLoader),
            adInfoClass = adInfoClassName?.let(classLoader::loadClassOrNull),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeRecommendFeedSymbols = HomeRecommendFeedSymbols(
            responseGetItems = obj.optJSONArray("responseGetItems").toList {
                PegasusResponseGetItemsSymbols.fromJson(it)
            },
            getHolderType = MethodDescriptor.fromJson(obj.getJSONObject("getHolderType")),
            getBizType = obj.optJSONObject("getBizType")?.let(MethodDescriptor::fromJson),
            getHolderStyle = obj.optJSONObject("getHolderStyle")?.let(MethodDescriptor::fromJson),
            isSmallCard = obj.optJSONObject("isSmallCard")?.let(MethodDescriptor::fromJson),
            getAdInfo = obj.optJSONObject("getAdInfo")?.let(MethodDescriptor::fromJson),
            getCardType = obj.optJSONObject("getCardType")?.let(MethodDescriptor::fromJson),
            getCardGoto = obj.optJSONObject("getCardGoto")?.let(MethodDescriptor::fromJson),
            getGoTo = obj.optJSONObject("getGoTo")?.let(MethodDescriptor::fromJson),
            getUri = obj.optJSONObject("getUri")?.let(MethodDescriptor::fromJson),
            adInfoClassName = obj.optString("adInfoClassName").takeIf { it.isNotBlank() },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class PegasusResponseGetItemsSymbols(
    val getItems: MethodDescriptor,
    val itemsField: FieldDescriptor?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("getItems", getItems.toJson())
        .putOpt("itemsField", itemsField?.toJson())

    fun restore(classLoader: ClassLoader): RestoredPegasusResponseGetItemsSymbols? {
        val owner = classLoader.loadClassOrNull(getItems.declaringClassName) ?: return null
        return RestoredPegasusResponseGetItemsSymbols(
            getItems = getItems.restore(owner) ?: return null,
            itemsField = itemsField.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): PegasusResponseGetItemsSymbols = PegasusResponseGetItemsSymbols(
            getItems = MethodDescriptor.fromJson(obj.getJSONObject("getItems")),
            itemsField = obj.optJSONObject("itemsField")?.let(FieldDescriptor::fromJson),
        )
    }
}

data class RestoredHomeRecommendFeedSymbols(
    val responseGetItems: List<RestoredPegasusResponseGetItemsSymbols>,
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
)

data class RestoredPegasusResponseGetItemsSymbols(
    val getItems: Method,
    val itemsField: Field?,
)

data class HomeComponentHideSymbols(
    val fragmentLifecycleMethods: List<MethodDescriptor>,
    val baseHomeFragmentMethods: List<MethodDescriptor>,
    val componentCatalogMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fragmentLifecycleMethods", fragmentLifecycleMethods.toJsonArray { it.toJson() })
        .put("baseHomeFragmentMethods", baseHomeFragmentMethods.toJsonArray { it.toJson() })
        .putOpt("componentCatalogMethod", componentCatalogMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeComponentHideSymbols? {
        val fragmentMethods = fragmentLifecycleMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (fragmentMethods.size != fragmentLifecycleMethods.size) return null
        val methods = baseHomeFragmentMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (methods.size != baseHomeFragmentMethods.size) return null
        return RestoredHomeComponentHideSymbols(
            fragmentLifecycleMethods = fragmentMethods,
            baseHomeFragmentMethods = methods,
            componentCatalogMethod = componentCatalogMethod?.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeComponentHideSymbols = HomeComponentHideSymbols(
            fragmentLifecycleMethods = obj.optJSONArray("fragmentLifecycleMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            baseHomeFragmentMethods = obj.optJSONArray("baseHomeFragmentMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            componentCatalogMethod = obj.optJSONObject("componentCatalogMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredHomeComponentHideSymbols(
    val fragmentLifecycleMethods: List<Method>,
    val baseHomeFragmentMethods: List<Method>,
    val componentCatalogMethod: Method?,
)

data class FullNumberFormatSymbols(
    val formatterMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatterMethods", formatterMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredFullNumberFormatSymbols? {
        return RestoredFullNumberFormatSymbols(
            formatterMethods = formatterMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): FullNumberFormatSymbols = FullNumberFormatSymbols(
            formatterMethods = obj.optJSONArray("formatterMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredFullNumberFormatSymbols(
    val formatterMethods: List<Method>,
)

data class VideoCommentSymbols(
    val disableCommentConstructors: List<ConstructorDescriptor>,
    val quickReplyViewModelMethods: List<MethodDescriptor>,
    val quickReplyDialogMethods: List<MethodDescriptor>,
    val voteWidgetMethods: List<MethodDescriptor>,
    val followWidgetMethods: List<MethodDescriptor>,
    val headerDecorativeMethods: List<MethodDescriptor>,
    val searchUrlsMethod: MethodDescriptor?,
    val emptyPageHooks: List<VideoCommentEmptyPageSymbols>,
    val mainListOnNextMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("disableCommentConstructors", disableCommentConstructors.toJsonArray { it.toJson() })
        .put("quickReplyViewModelMethods", quickReplyViewModelMethods.toJsonArray { it.toJson() })
        .put("quickReplyDialogMethods", quickReplyDialogMethods.toJsonArray { it.toJson() })
        .put("voteWidgetMethods", voteWidgetMethods.toJsonArray { it.toJson() })
        .put("followWidgetMethods", followWidgetMethods.toJsonArray { it.toJson() })
        .put("headerDecorativeMethods", headerDecorativeMethods.toJsonArray { it.toJson() })
        .putOpt("searchUrlsMethod", searchUrlsMethod?.toJson())
        .put("emptyPageHooks", emptyPageHooks.toJsonArray { it.toJson() })
        .put("mainListOnNextMethods", mainListOnNextMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoCommentSymbols? {
        val constructors = disableCommentConstructors.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (constructors.size != disableCommentConstructors.size) return null
        val emptyPages = emptyPageHooks.mapNotNull { it.restore(classLoader) }
        if (emptyPages.size != emptyPageHooks.size) return null
        return RestoredVideoCommentSymbols(
            disableCommentConstructors = constructors,
            quickReplyViewModelMethods = quickReplyViewModelMethods.restoreAll(classLoader) ?: return null,
            quickReplyDialogMethods = quickReplyDialogMethods.restoreAll(classLoader) ?: return null,
            voteWidgetMethods = voteWidgetMethods.restoreAll(classLoader) ?: return null,
            followWidgetMethods = followWidgetMethods.restoreAll(classLoader) ?: return null,
            headerDecorativeMethods = headerDecorativeMethods.restoreAll(classLoader) ?: return null,
            searchUrlsMethod = searchUrlsMethod.restoreOptional(classLoader),
            emptyPageHooks = emptyPages,
            mainListOnNextMethods = mainListOnNextMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoCommentSymbols = VideoCommentSymbols(
            disableCommentConstructors = obj.optJSONArray("disableCommentConstructors").toList {
                ConstructorDescriptor.fromJson(it)
            },
            quickReplyViewModelMethods = obj.optJSONArray("quickReplyViewModelMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            quickReplyDialogMethods = obj.optJSONArray("quickReplyDialogMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            voteWidgetMethods = obj.optJSONArray("voteWidgetMethods").toList { MethodDescriptor.fromJson(it) },
            followWidgetMethods = obj.optJSONArray("followWidgetMethods").toList { MethodDescriptor.fromJson(it) },
            headerDecorativeMethods = obj.optJSONArray("headerDecorativeMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            searchUrlsMethod = obj.optJSONObject("searchUrlsMethod")?.let(MethodDescriptor::fromJson),
            emptyPageHooks = obj.optJSONArray("emptyPageHooks").toList {
                VideoCommentEmptyPageSymbols.fromJson(it)
            },
            mainListOnNextMethods = obj.optJSONArray("mainListOnNextMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class VideoCommentEmptyPageSymbols(
    val getEmptyPage: MethodDescriptor,
    val defaultInstance: FieldDescriptor,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("getEmptyPage", getEmptyPage.toJson())
        .put("defaultInstance", defaultInstance.toJson())

    fun restore(classLoader: ClassLoader): RestoredVideoCommentEmptyPageSymbols? {
        val methodOwner = classLoader.loadClassOrNull(getEmptyPage.declaringClassName) ?: return null
        val fieldOwner = classLoader.loadClassOrNull(defaultInstance.declaringClassName) ?: return null
        val field = defaultInstance.restore(fieldOwner) ?: return null
        return RestoredVideoCommentEmptyPageSymbols(
            getEmptyPage = getEmptyPage.restore(methodOwner) ?: return null,
            defaultInstance = field.get(null) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoCommentEmptyPageSymbols = VideoCommentEmptyPageSymbols(
            getEmptyPage = MethodDescriptor.fromJson(obj.getJSONObject("getEmptyPage")),
            defaultInstance = FieldDescriptor.fromJson(obj.getJSONObject("defaultInstance")),
        )
    }
}

data class RestoredVideoCommentSymbols(
    val disableCommentConstructors: List<Constructor<*>>,
    val quickReplyViewModelMethods: List<Method>,
    val quickReplyDialogMethods: List<Method>,
    val voteWidgetMethods: List<Method>,
    val followWidgetMethods: List<Method>,
    val headerDecorativeMethods: List<Method>,
    val searchUrlsMethod: Method?,
    val emptyPageHooks: List<RestoredVideoCommentEmptyPageSymbols>,
    val mainListOnNextMethods: List<Method>,
)

data class RestoredVideoCommentEmptyPageSymbols(
    val getEmptyPage: Method,
    val defaultInstance: Any,
)

data class SkipVideoAdSymbols(
    val playViewMethods: List<MethodDescriptor>,
    val playerCoreCurrentPositionMethods: List<MethodDescriptor>,
    val playerCoreStateMethods: List<MethodDescriptor>,
    val playerCoreSeekMethods: List<MethodDescriptor>,
    val cardCurrentPositionMethods: List<MethodDescriptor>,
    val cardStateMethods: List<MethodDescriptor>,
    val cardSeekMethods: List<MethodDescriptor>,
    val storyCurrentPositionMethods: List<MethodDescriptor>,
    val storyStateMethods: List<MethodDescriptor>,
    val storySeekMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("playViewMethods", playViewMethods.toJsonArray { it.toJson() })
        .put("playerCoreCurrentPositionMethods", playerCoreCurrentPositionMethods.toJsonArray { it.toJson() })
        .put("playerCoreStateMethods", playerCoreStateMethods.toJsonArray { it.toJson() })
        .put("playerCoreSeekMethods", playerCoreSeekMethods.toJsonArray { it.toJson() })
        .put("cardCurrentPositionMethods", cardCurrentPositionMethods.toJsonArray { it.toJson() })
        .put("cardStateMethods", cardStateMethods.toJsonArray { it.toJson() })
        .put("cardSeekMethods", cardSeekMethods.toJsonArray { it.toJson() })
        .put("storyCurrentPositionMethods", storyCurrentPositionMethods.toJsonArray { it.toJson() })
        .put("storyStateMethods", storyStateMethods.toJsonArray { it.toJson() })
        .put("storySeekMethods", storySeekMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSkipVideoAdSymbols? {
        return RestoredSkipVideoAdSymbols(
            playViewMethods = playViewMethods.restoreAll(classLoader) ?: return null,
            playerCoreCurrentPositionMethods = playerCoreCurrentPositionMethods.restoreAll(classLoader) ?: return null,
            playerCoreStateMethods = playerCoreStateMethods.restoreAll(classLoader) ?: return null,
            playerCoreSeekMethods = playerCoreSeekMethods.restoreAll(classLoader) ?: return null,
            cardCurrentPositionMethods = cardCurrentPositionMethods.restoreAll(classLoader) ?: return null,
            cardStateMethods = cardStateMethods.restoreAll(classLoader) ?: return null,
            cardSeekMethods = cardSeekMethods.restoreAll(classLoader) ?: return null,
            storyCurrentPositionMethods = storyCurrentPositionMethods.restoreAll(classLoader) ?: return null,
            storyStateMethods = storyStateMethods.restoreAll(classLoader) ?: return null,
            storySeekMethods = storySeekMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): SkipVideoAdSymbols = SkipVideoAdSymbols(
            playViewMethods = obj.optJSONArray("playViewMethods").toList { MethodDescriptor.fromJson(it) },
            playerCoreCurrentPositionMethods = obj.optJSONArray("playerCoreCurrentPositionMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            playerCoreStateMethods = obj.optJSONArray("playerCoreStateMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            playerCoreSeekMethods = obj.optJSONArray("playerCoreSeekMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            cardCurrentPositionMethods = obj.optJSONArray("cardCurrentPositionMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            cardStateMethods = obj.optJSONArray("cardStateMethods").toList { MethodDescriptor.fromJson(it) },
            cardSeekMethods = obj.optJSONArray("cardSeekMethods").toList { MethodDescriptor.fromJson(it) },
            storyCurrentPositionMethods = obj.optJSONArray("storyCurrentPositionMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            storyStateMethods = obj.optJSONArray("storyStateMethods").toList { MethodDescriptor.fromJson(it) },
            storySeekMethods = obj.optJSONArray("storySeekMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSkipVideoAdSymbols(
    val playViewMethods: List<Method>,
    val playerCoreCurrentPositionMethods: List<Method>,
    val playerCoreStateMethods: List<Method>,
    val playerCoreSeekMethods: List<Method>,
    val cardCurrentPositionMethods: List<Method>,
    val cardStateMethods: List<Method>,
    val cardSeekMethods: List<Method>,
    val storyCurrentPositionMethods: List<Method>,
    val storyStateMethods: List<Method>,
    val storySeekMethods: List<Method>,
)

data class SkipVideoAdProgressSymbols(
    val progressOnDraw: MethodDescriptor?,
    val storyOnStartMethods: List<MethodDescriptor>,
    val inlineUpdateMethods: List<MethodDescriptor>,
    val panelWidgetKtClassName: String?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("progressOnDraw", progressOnDraw?.toJson())
        .put("storyOnStartMethods", storyOnStartMethods.toJsonArray { it.toJson() })
        .put("inlineUpdateMethods", inlineUpdateMethods.toJsonArray { it.toJson() })
        .putOpt("panelWidgetKtClassName", panelWidgetKtClassName)
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSkipVideoAdProgressSymbols? {
        return RestoredSkipVideoAdProgressSymbols(
            progressOnDraw = progressOnDraw.restoreOptional(classLoader),
            storyOnStartMethods = storyOnStartMethods.restoreAll(classLoader) ?: return null,
            inlineUpdateMethods = inlineUpdateMethods.restoreAll(classLoader) ?: return null,
            panelWidgetKtClass = panelWidgetKtClassName?.let(classLoader::loadClassOrNull),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): SkipVideoAdProgressSymbols = SkipVideoAdProgressSymbols(
            progressOnDraw = obj.optJSONObject("progressOnDraw")?.let(MethodDescriptor::fromJson),
            storyOnStartMethods = obj.optJSONArray("storyOnStartMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            inlineUpdateMethods = obj.optJSONArray("inlineUpdateMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            panelWidgetKtClassName = obj.optString("panelWidgetKtClassName").takeIf { it.isNotBlank() },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSkipVideoAdProgressSymbols(
    val progressOnDraw: Method?,
    val storyOnStartMethods: List<Method>,
    val inlineUpdateMethods: List<Method>,
    val panelWidgetKtClass: Class<*>?,
)

data class SkipVideoAdAutoLikeSymbols(
    val detailLikeInflateMethod: MethodDescriptor?,
    val detailLikeStateOwnerClassName: String?,
    val storyWidgetClassNames: List<String>,
    val storyActionOwnerClassName: String?,
    val storyBindMethods: List<MethodDescriptor>,
    val geminiLikeWidgetClassName: String?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("detailLikeInflateMethod", detailLikeInflateMethod?.toJson())
        .putOpt("detailLikeStateOwnerClassName", detailLikeStateOwnerClassName)
        .put("storyWidgetClassNames", storyWidgetClassNames.toJsonArray())
        .putOpt("storyActionOwnerClassName", storyActionOwnerClassName)
        .put("storyBindMethods", storyBindMethods.toJsonArray { it.toJson() })
        .putOpt("geminiLikeWidgetClassName", geminiLikeWidgetClassName)
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSkipVideoAdAutoLikeSymbols? {
        return RestoredSkipVideoAdAutoLikeSymbols(
            detailLikeInflateMethod = detailLikeInflateMethod.restoreOptional(classLoader),
            detailLikeStateOwnerClass = detailLikeStateOwnerClassName?.let(classLoader::loadClassOrNull),
            storyWidgetClasses = storyWidgetClassNames.mapNotNull(classLoader::loadClassOrNull)
                .takeIf { it.size == storyWidgetClassNames.size } ?: return null,
            storyActionOwnerClass = storyActionOwnerClassName?.let(classLoader::loadClassOrNull),
            storyBindMethods = storyBindMethods.restoreAll(classLoader) ?: return null,
            geminiLikeWidgetClass = geminiLikeWidgetClassName?.let(classLoader::loadClassOrNull),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): SkipVideoAdAutoLikeSymbols = SkipVideoAdAutoLikeSymbols(
            detailLikeInflateMethod = obj.optJSONObject("detailLikeInflateMethod")?.let(MethodDescriptor::fromJson),
            detailLikeStateOwnerClassName = obj.optString("detailLikeStateOwnerClassName").takeIf { it.isNotBlank() },
            storyWidgetClassNames = obj.optJSONArray("storyWidgetClassNames").toStringList(),
            storyActionOwnerClassName = obj.optString("storyActionOwnerClassName").takeIf { it.isNotBlank() },
            storyBindMethods = obj.optJSONArray("storyBindMethods").toList { MethodDescriptor.fromJson(it) },
            geminiLikeWidgetClassName = obj.optString("geminiLikeWidgetClassName").takeIf { it.isNotBlank() },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSkipVideoAdAutoLikeSymbols(
    val detailLikeInflateMethod: Method?,
    val detailLikeStateOwnerClass: Class<*>?,
    val storyWidgetClasses: List<Class<*>>,
    val storyActionOwnerClass: Class<*>?,
    val storyBindMethods: List<Method>,
    val geminiLikeWidgetClass: Class<*>?,
)

data class ChronosPromotionSymbols(
    val classSymbols: List<NamedClassSymbol>,
    val methodGroups: List<NamedMethodGroup>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("classSymbols", classSymbols.toJsonArray { it.toJson() })
        .put("methodGroups", methodGroups.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredChronosPromotionSymbols? {
        val classes = classSymbols.mapNotNull { symbol ->
            classLoader.loadClassOrNull(symbol.className)?.let { symbol.id to it }
        }.toMap()
        if (classes.size != classSymbols.size) return null
        val methods = methodGroups.mapNotNull { group ->
            val restored = group.methods.restoreAll(classLoader) ?: return@mapNotNull null
            group.id to restored
        }.toMap()
        if (methods.size != methodGroups.size) return null
        return RestoredChronosPromotionSymbols(classes, methods)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChronosPromotionSymbols = ChronosPromotionSymbols(
            classSymbols = obj.optJSONArray("classSymbols").toList { NamedClassSymbol.fromJson(it) },
            methodGroups = obj.optJSONArray("methodGroups").toList { NamedMethodGroup.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class NamedClassSymbol(
    val id: String,
    val className: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("className", className)

    companion object {
        fun fromJson(obj: JSONObject): NamedClassSymbol = NamedClassSymbol(
            id = obj.optString("id"),
            className = obj.optString("className"),
        )
    }
}

data class NamedMethodGroup(
    val id: String,
    val methods: List<MethodDescriptor>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("methods", methods.toJsonArray { it.toJson() })

    companion object {
        fun fromJson(obj: JSONObject): NamedMethodGroup = NamedMethodGroup(
            id = obj.optString("id"),
            methods = obj.optJSONArray("methods").toList { MethodDescriptor.fromJson(it) },
        )
    }
}

data class RestoredChronosPromotionSymbols(
    val classes: Map<String, Class<*>>,
    val methods: Map<String, List<Method>>,
) {
    fun clazz(id: String): Class<*>? = classes[id]
    fun methods(id: String): List<Method> = methods[id].orEmpty()
    fun firstMethod(id: String): Method? = methods(id).firstOrNull()
}

data class ConstructorDescriptor(
    val declaringClassName: String,
    val parameterTypeNames: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("declaringClassName", declaringClassName)
        .put("parameterTypeNames", parameterTypeNames.toJsonArray())

    fun restore(owner: Class<*>): Constructor<*>? {
        if (owner.name != declaringClassName) return null
        return owner.declaredConstructors.firstOrNull { constructor ->
            constructor.parameterTypes.map { it.name } == parameterTypeNames
        }?.apply { isAccessible = true }
    }

    companion object {
        fun of(constructor: Constructor<*>): ConstructorDescriptor = ConstructorDescriptor(
            declaringClassName = constructor.declaringClass.name,
            parameterTypeNames = constructor.parameterTypes.map { it.name },
        )

        fun fromJson(obj: JSONObject): ConstructorDescriptor = ConstructorDescriptor(
            declaringClassName = obj.optString("declaringClassName"),
            parameterTypeNames = obj.optJSONArray("parameterTypeNames").toStringList(),
        )
    }
}

data class TripleSpeedSymbols(
    val experimentReaderMethod: MethodDescriptor,
    val qualitySpeedResetMethod: MethodDescriptor? = null,
    val highFrameRateSpeedGuardMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("experimentReaderMethod", experimentReaderMethod.toJson())
        .putOpt("qualitySpeedResetMethod", qualitySpeedResetMethod?.toJson())
        .putOpt("highFrameRateSpeedGuardMethod", highFrameRateSpeedGuardMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredTripleSpeedSymbols? {
        val method = experimentReaderMethod.restoreOptional(classLoader) ?: return null
        return RestoredTripleSpeedSymbols(
            experimentReaderMethod = method,
            qualitySpeedResetMethod = qualitySpeedResetMethod.restoreOptional(classLoader),
            highFrameRateSpeedGuardMethod = highFrameRateSpeedGuardMethod.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): TripleSpeedSymbols = TripleSpeedSymbols(
            experimentReaderMethod = MethodDescriptor.fromJson(obj.getJSONObject("experimentReaderMethod")),
            qualitySpeedResetMethod = obj.optJSONObject("qualitySpeedResetMethod")?.let(MethodDescriptor::fromJson),
            highFrameRateSpeedGuardMethod = obj.optJSONObject("highFrameRateSpeedGuardMethod")
                ?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredTripleSpeedSymbols(
    val experimentReaderMethod: Method,
    val qualitySpeedResetMethod: Method?,
    val highFrameRateSpeedGuardMethod: Method?,
)

data class CustomSkinSymbols(
    val resolverMethod: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("resolverMethod", resolverMethod.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): Method? = resolverMethod.restoreOptional(classLoader)

    companion object {
        fun fromJson(obj: JSONObject): CustomSkinSymbols = CustomSkinSymbols(
            resolverMethod = MethodDescriptor.fromJson(obj.getJSONObject("resolverMethod")),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class CustomThemeSymbols(
    val themeHelperClassName: String,
    val themeHelperColorArray: FieldDescriptor,
    val themeNameClassName: String,
    val themeNameField: FieldDescriptor,
    val builtInThemesClassName: String? = null,
    val builtInThemesField: FieldDescriptor? = null,
    val themeColorsClassName: String? = null,
    val skinListMethod: MethodDescriptor,
    val themeListClickClassName: String,
    val skinClassName: String,
    val themeProcessorClassName: String,
    val themeResetMethods: List<MethodDescriptor>,
    val themeIdHelperClassName: String? = null,
    val themeIdHelperColorId: FieldDescriptor? = null,
    val columnHelperClassName: String? = null,
    val columnHelperColorArray: FieldDescriptor? = null,
    val evidence: String,
    val skinResponseClassName: String? = null,
    val skinResponseUserGarbSetter: MethodDescriptor? = null,
    val skinResponseLoadEquipSetter: MethodDescriptor? = null,
    val skinResolveMethod: MethodDescriptor? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("themeHelperClassName", themeHelperClassName)
        .put("themeHelperColorArray", themeHelperColorArray.toJson())
        .put("themeNameClassName", themeNameClassName)
        .put("themeNameField", themeNameField.toJson())
        .putOpt("builtInThemesClassName", builtInThemesClassName)
        .putOpt("builtInThemesField", builtInThemesField?.toJson())
        .putOpt("themeColorsClassName", themeColorsClassName)
        .put("skinListMethod", skinListMethod.toJson())
        .put("themeListClickClassName", themeListClickClassName)
        .put("skinClassName", skinClassName)
        .put("themeProcessorClassName", themeProcessorClassName)
        .put("themeResetMethods", themeResetMethods.toJsonArray { it.toJson() })
        .putOpt("themeIdHelperClassName", themeIdHelperClassName)
        .putOpt("themeIdHelperColorId", themeIdHelperColorId?.toJson())
        .putOpt("columnHelperClassName", columnHelperClassName)
        .putOpt("columnHelperColorArray", columnHelperColorArray?.toJson())
        .put("evidence", evidence)
        .putOpt("skinResponseClassName", skinResponseClassName)
        .putOpt("skinResponseUserGarbSetter", skinResponseUserGarbSetter?.toJson())
        .putOpt("skinResponseLoadEquipSetter", skinResponseLoadEquipSetter?.toJson())
        .putOpt("skinResolveMethod", skinResolveMethod?.toJson())

    fun restore(classLoader: ClassLoader): RestoredCustomThemeSymbols? {
        val themeHelperClass = classLoader.loadClassOrNull(themeHelperClassName) ?: return null
        val themeNameClass = classLoader.loadClassOrNull(themeNameClassName) ?: return null
        val builtInThemesClass = builtInThemesClassName?.let(classLoader::loadClassOrNull)
        val themeColorsClass = themeColorsClassName?.let(classLoader::loadClassOrNull)
        val skinListMethod = skinListMethod.restoreOptional(classLoader) ?: return null
        val themeListClickClass = classLoader.loadClassOrNull(themeListClickClassName) ?: return null
        val skinClass = classLoader.loadClassOrNull(skinClassName) ?: return null
        val themeProcessorClass = classLoader.loadClassOrNull(themeProcessorClassName) ?: return null
        val colorArray = themeHelperColorArray.restore(themeHelperClass) ?: return null
        val themeName = themeNameField.restore(themeNameClass) ?: return null
        val allThemes = builtInThemesField?.let { descriptor -> builtInThemesClass?.let(descriptor::restore) }
        val resetMethods = themeResetMethods.restoreAll(classLoader) ?: return null
        val themeIdHelper = themeIdHelperClassName?.let(classLoader::loadClassOrNull)
        val themeIdColorId = themeIdHelperColorId?.let { themeIdHelper?.let(it::restore) }
        val columnHelper = columnHelperClassName?.let(classLoader::loadClassOrNull)
        val columnColorArray = columnHelperColorArray?.let { columnHelper?.let(it::restore) }
        val skinResponseClass = skinResponseClassName?.let(classLoader::loadClassOrNull)
        val userGarbSetter = skinResponseUserGarbSetter?.restoreOptional(classLoader)
        val loadEquipSetter = skinResponseLoadEquipSetter?.restoreOptional(classLoader)
        val skinResolveMethod = skinResolveMethod?.restoreOptional(classLoader)
        return RestoredCustomThemeSymbols(
            themeHelperClass, colorArray, themeName, allThemes, themeColorsClass,
            skinListMethod, themeListClickClass, skinClass, themeProcessorClass, resetMethods,
            themeIdHelper, themeIdColorId, columnHelper, columnColorArray,
            skinResponseClass, userGarbSetter, loadEquipSetter, skinResolveMethod,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomThemeSymbols = CustomThemeSymbols(
            themeHelperClassName = obj.optString("themeHelperClassName"),
            themeHelperColorArray = FieldDescriptor.fromJson(obj.getJSONObject("themeHelperColorArray")),
            themeNameClassName = obj.optString("themeNameClassName"),
            themeNameField = FieldDescriptor.fromJson(obj.getJSONObject("themeNameField")),
            builtInThemesClassName = obj.optString("builtInThemesClassName").takeIf { it.isNotBlank() },
            builtInThemesField = obj.optJSONObject("builtInThemesField")?.let(FieldDescriptor::fromJson),
            themeColorsClassName = obj.optString("themeColorsClassName").takeIf { it.isNotBlank() },
            skinListMethod = MethodDescriptor.fromJson(obj.getJSONObject("skinListMethod")),
            themeListClickClassName = obj.optString("themeListClickClassName"),
            skinClassName = obj.optString("skinClassName"),
            themeProcessorClassName = obj.optString("themeProcessorClassName"),
            themeResetMethods = obj.optJSONArray("themeResetMethods").toList { MethodDescriptor.fromJson(it) },
            themeIdHelperClassName = obj.optString("themeIdHelperClassName").takeIf { it.isNotBlank() },
            themeIdHelperColorId = obj.optJSONObject("themeIdHelperColorId")?.let(FieldDescriptor::fromJson),
            columnHelperClassName = obj.optString("columnHelperClassName").takeIf { it.isNotBlank() },
            columnHelperColorArray = obj.optJSONObject("columnHelperColorArray")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
            skinResponseClassName = obj.optString("skinResponseClassName").takeIf { it.isNotBlank() },
            skinResponseUserGarbSetter = obj.optJSONObject("skinResponseUserGarbSetter")?.let(MethodDescriptor::fromJson),
            skinResponseLoadEquipSetter = obj.optJSONObject("skinResponseLoadEquipSetter")?.let(MethodDescriptor::fromJson),
            skinResolveMethod = obj.optJSONObject("skinResolveMethod")?.let(MethodDescriptor::fromJson),
        )
    }
}

data class RestoredCustomThemeSymbols(
    val themeHelperClass: Class<*>,
    val themeHelperColorArray: Field,
    val themeName: Field,
    val allThemes: Field?,
    val themeColorsClass: Class<*>?,
    val skinListMethod: Method,
    val themeListClickClass: Class<*>,
    val skinClass: Class<*>,
    val themeProcessorClass: Class<*>,
    val themeResetMethods: List<Method>,
    val themeIdHelperClass: Class<*>?,
    val themeIdHelperColorId: Field?,
    val columnHelperClass: Class<*>?,
    val columnHelperColorArray: Field?,
    val skinResponseClass: Class<*>?,
    val skinResponseUserGarbSetter: Method?,
    val skinResponseLoadEquipSetter: Method?,
    val skinResolveMethod: Method?,
)

data class MethodDescriptor(
    val declaringClassName: String,
    val name: String,
    val returnTypeName: String,
    val parameterTypeNames: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("declaringClassName", declaringClassName)
        .put("name", name)
        .put("returnTypeName", returnTypeName)
        .put("parameterTypeNames", parameterTypeNames.toJsonArray())

    fun restore(owner: Class<*>): Method? {
        if (owner.name != declaringClassName) return null
        return owner.declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.returnType.name == returnTypeName &&
                method.parameterTypes.map { it.name } == parameterTypeNames
        }?.apply { isAccessible = true }
    }

    companion object {
        fun of(method: Method): MethodDescriptor = MethodDescriptor(
            declaringClassName = method.declaringClass.name,
            name = method.name,
            returnTypeName = method.returnType.name,
            parameterTypeNames = method.parameterTypes.map { it.name },
        )

        fun fromJson(obj: JSONObject): MethodDescriptor = MethodDescriptor(
            declaringClassName = obj.optString("declaringClassName"),
            name = obj.optString("name"),
            returnTypeName = obj.optString("returnTypeName"),
            parameterTypeNames = obj.optJSONArray("parameterTypeNames").toStringList(),
        )
    }
}

data class FieldDescriptor(
    val declaringClassName: String,
    val name: String,
    val typeName: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("declaringClassName", declaringClassName)
        .put("name", name)
        .put("typeName", typeName)

    fun restore(owner: Class<*>): Field? {
        if (owner.name != declaringClassName) return null
        return owner.declaredFields.firstOrNull { field ->
            field.name == name && field.type.name == typeName
        }?.apply { isAccessible = true }
    }

    companion object {
        fun of(field: Field): FieldDescriptor = FieldDescriptor(
            declaringClassName = field.declaringClass.name,
            name = field.name,
            typeName = field.type.name,
        )

        fun fromJson(obj: JSONObject): FieldDescriptor = FieldDescriptor(
            declaringClassName = obj.optString("declaringClassName"),
            name = obj.optString("name"),
            typeName = obj.optString("typeName"),
        )
    }
}

fun BiliHookSymbols.formatStatusLines(): List<String> =
    hookPoints.map { it.toLine() } +
        if (scanErrors.isEmpty()) {
            listOf("Scan Errors: -")
        } else {
            listOf("Scan Errors:") + scanErrors.map { "  - $it" }
        }

internal fun ClassLoader.loadClassOrNull(name: String): Class<*>? =
    runCatching { Class.forName(name, false, this) }.getOrNull()

internal fun MethodDescriptor?.restoreOptional(classLoader: ClassLoader): Method? {
    val descriptor = this ?: return null
    val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return null
    return descriptor.restore(owner)
}

internal fun FieldDescriptor?.restoreOptional(classLoader: ClassLoader): Field? {
    val descriptor = this ?: return null
    val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return null
    return descriptor.restore(owner)
}

internal fun List<MethodDescriptor>.restoreAll(classLoader: ClassLoader): List<Method>? {
    val methods = mapNotNull { it.restoreOptional(classLoader) }
    return methods.takeIf { it.size == size }
}

internal fun List<MethodDescriptor>.restoreAvailable(classLoader: ClassLoader): List<Method> =
    mapNotNull { it.restoreOptional(classLoader) }

internal fun Method.hasParameterTypes(vararg names: String): Boolean =
    parameterTypes.map { it.name } == names.toList()

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        optString(i).takeIf { it.isNotBlank() }?.let(out::add)
    }
    return out
}

private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out.add(mapper(it)) }
    }
    return out
}

private fun Iterable<String>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach(array::put) }

private fun <T> Iterable<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(mapper(it)) } }

internal fun Context.packageVersionCodeLong(): Long {
    val info = packageManager.getPackageInfo(packageName, 0)
    @Suppress("DEPRECATION")
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
}
