package io.github.bbzq.feats.symbol

import android.content.Context
import org.json.JSONObject

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
    val videoDetailRelateFeed: VideoDetailRelateFeedSymbols? = null,
    val homeTopBar: HomeTopBarSymbols? = null,
    val homeFrame: HomeFrameSymbols? = null,
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
    val videoQuality: VideoQualitySymbols? = null,
    val videoEndRecommend: VideoEndRecommendSymbols? = null,
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
        .putOpt("videoDetailRelateFeed", videoDetailRelateFeed?.toJson())
        .putOpt("homeTopBar", homeTopBar?.toJson())
        .putOpt("homeFrame", homeFrame?.toJson())
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
        .putOpt("videoQuality", videoQuality?.toJson())
        .putOpt("videoEndRecommend", videoEndRecommend?.toJson())

    companion object {
        const val CACHE_SCHEMA_VERSION = 41

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
                    videoDetailRelateFeed = obj.optJSONObject("videoDetailRelateFeed")
                        ?.let(VideoDetailRelateFeedSymbols::fromJson),
                    homeTopBar = obj.optJSONObject("homeTopBar")?.let(HomeTopBarSymbols::fromJson),
                    homeFrame = obj.optJSONObject("homeFrame")?.let(HomeFrameSymbols::fromJson),
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
                    videoQuality = obj.optJSONObject("videoQuality")?.let(VideoQualitySymbols::fromJson),
                    videoEndRecommend = obj.optJSONObject("videoEndRecommend")?.let(VideoEndRecommendSymbols::fromJson),
                )
            }.getOrNull()
        }
    }
}

object DexKitRuleVersions {
    const val CURRENT = 69
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

fun BiliHookSymbols.formatStatusLines(): List<String> =
    hookPoints.map { it.toLine() } +
        if (scanErrors.isEmpty()) {
            listOf("Scan Errors: -")
        } else {
            listOf("Scan Errors:") + scanErrors.map { "  - $it" }
        }

internal fun Context.packageVersionCodeLong(): Long {
    val info = packageManager.getPackageInfo(packageName, 0)
    return info.longVersionCode
}
