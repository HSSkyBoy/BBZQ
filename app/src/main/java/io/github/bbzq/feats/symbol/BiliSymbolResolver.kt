package io.github.bbzq.feats.symbol

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Bundle
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import io.github.bbzq.BuildConfig
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.symbol.dexkit.DexKitBridgeProvider
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import kotlin.coroutines.Continuation

object BiliSymbolResolver {
    const val CACHE_PREFS_NAME = "bbzq_symbol_cache"
    private const val PREFS_NAME = CACHE_PREFS_NAME
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_SYMBOLS = "symbols"

    private const val HP_ACCOUNT_ACCESS_KEY = "AccessKeyHook.BiliAccounts"
    private const val HP_SETTINGS_FRAGMENT = "SettingHook.PreferenceFragments"
    private const val HP_BLOCK_UPDATE = "BlockUpdateHook.UpdateCheck"
    private const val HP_MINE_VIP = "MineProfileHook.VipEntrance"
    private const val HP_SPLASH_AD = "SplashAdHook.JsonParsers"
    private const val HP_SHARE = "ShareHook.InstallPoints"
    private const val HP_SHARE_LEGACY = "ShareHook.LegacyShareClickResult"
    private const val HP_SHARE_CHANNELS = "ShareHook.ShareChannels"
    private const val HP_SHARE_CLICK_RESULT = "ShareHook.ShareClickResult"
    private const val HP_SHARE_BASE_INFO = "ShareHook.ShareBaseInfo"
    private const val HP_SHARE_CONTENT = "ShareHook.ShareContent"
    private const val HP_SHARE_BILI_CONTENT = "ShareHook.ShareBiliContent"
    private const val HP_SHARE_COPY_CONTENT = "ShareHook.CopyContent"
    private const val HP_SHARE_COPY_UTILITY = "ShareHook.CopyUtility"
    private const val HP_REWARD_AD = "RewardAdHook.InstallPoints"
    private const val HP_REWARD_AD_ACTIVITY = "RewardAdHook.Activity"
    private const val HP_REWARD_AD_HEADER = "RewardAdHook.HeaderTimer"
    private const val HP_REWARD_AD_COUNTDOWN = "RewardAdHook.CountDownText"
    private const val HP_REWARD_AD_JUMP_CLOCK = "RewardAdHook.JumpClock"
    private const val HP_REWARD_AD_ACTIVITY_SWEEPER = "RewardAdHook.ActivitySweeper"
    private const val HP_REWARD_AD_MINI_GAME = "RewardAdHook.MiniGameRewardedVideoAd"
    private const val HP_TRY_FREE_QUALITY = "TryFreeQualityHook.GeneratedMessages"
    private const val HP_TEENAGERS_MODE = "TeenagersModeHook.DialogActivity"
    private const val HP_DOWNLOAD_THREAD_LISTENER = "DownloadThreadHook.Listener"
    private const val HP_DOWNLOAD_THREAD_REPORT = "DownloadThreadHook.ReportMethod"
    private const val HP_STORY_PLAYER_AD = "StoryPlayerAdHook.InstallPoints"
    private const val HP_STORY_FULLSCREEN = "StoryFullscreenHook.StoryVideoActivity"
    private const val HP_STORY_FULLSCREEN_ON_CREATE = "StoryFullscreenHook.OnCreate"
    private const val HP_STORY_FULLSCREEN_FOCUS = "StoryFullscreenHook.OnWindowFocusChanged"
    private const val HP_STORY_FULLSCREEN_ON_RESUME = "StoryFullscreenHook.OnResume"
    private const val HP_STORY_DANMAKU = "StoryDanmakuHook.InstallPoints"
    private const val HP_STORY_DANMAKU_COMMENT_SHOW = "StoryDanmakuHook.CommentShow"
    private const val HP_STORY_DANMAKU_COMMENT_HIDE = "StoryDanmakuHook.CommentHide"
    private const val HP_STORY_DANMAKU_INTRO_COMMENT_SHOW = "StoryDanmakuHook.IntroCommentShow"
    private const val HP_STORY_DANMAKU_INTRO_COMMENT_DISMISS = "StoryDanmakuHook.IntroCommentDismiss"
    private const val HP_STORY_DANMAKU_SET_OPACITY = "StoryDanmakuHook.SetDanmakuOpacity"
    private const val HP_STORY_DANMAKU_UPDATE_CANVAS = "StoryDanmakuHook.UpdateCanvas"
    private const val HP_STORY_COMPONENT_ALPHA = "StoryComponentAlphaHook.InstallPoints"
    private const val HP_STORY_COMPONENT_ALPHA_INFO = "StoryComponentAlphaHook.InfoModule"
    private const val HP_STORY_COMPONENT_ALPHA_RIGHT = "StoryComponentAlphaHook.RightModule"
    private const val HP_STORY_COMPONENT_ALPHA_BOTTOM = "StoryComponentAlphaHook.BottomModule"
    private const val HP_STORY_COMPONENT_ALPHA_SEEKBAR = "StoryComponentAlphaHook.SeekBar"
    private const val HP_STORY_COMPONENT_ALPHA_TOP = "StoryComponentAlphaHook.TopControls"
    private const val HP_VIDEO_DETAIL_BANNER_AD = "VideoDetailBannerAdHook.InstallPoints"
    private const val HP_VIDEO_DETAIL_BANNER_PROXY = "VideoDetailBannerAdHook.Proxy"
    private const val HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_REQUEST = "VideoDetailBannerAdHook.PausedPageRequest"
    private const val HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_PANEL = "VideoDetailBannerAdHook.PausedPagePanel"
    private const val HP_VIDEO_DETAIL_BANNER_RELATE_GAME = "VideoDetailBannerAdHook.RelateGame"
    private const val HP_VIDEO_DETAIL_RELATE_FEED = "VideoDetailRelateFilterHook.InstallPoints"
    private const val HP_HOME_TOP_BAR = "HomeTopBarPurifyHook.InstallPoints"
    private const val HP_HOME_TOP_BAR_GAME = "HomeTopBarPurifyHook.GameMenu"
    private const val HP_HOME_TOP_BAR_VIEW_CREATED = "HomeTopBarPurifyHook.OnViewCreated"
    private const val HP_HOME_TOP_BAR_DEFAULT_WORD = "HomeTopBarPurifyHook.DefaultSearchWord"
    private const val HP_BOTTOM_BAR = "BottomBarHook.InstallPoints"
    private const val HP_BOTTOM_BAR_TAB_HOST = "BottomBarHook.TabHost"
    private const val HP_BOTTOM_BAR_VIEW_CREATED = "BottomBarHook.BaseOnViewCreated"
    private const val HP_HOME_RECOMMEND_FEED = "HomeRecommendFeed.Pegasus"
    private const val HP_HOME_RECOMMEND_TABS = "HomeRecommendTabs.BuildTabs"
    private const val HP_HOME_COMPONENT_HIDE = "HomeComponentHideHook.Components"
    private const val HP_HOME_COMPONENT_HIDE_FRAGMENT = "HomeComponentHideHook.FragmentLifecycle"
    private const val HP_HOME_COMPONENT_HIDE_CATALOG = "HomeComponentHideHook.ComponentCatalog"
    private const val HP_VIDEO_COMMENT = "VideoCommentHook.InstallPoints"
    private const val HP_VIDEO_COMMENT_DISABLE = "VideoCommentHook.DisableComment"
    private const val HP_VIDEO_COMMENT_QUICK_REPLY = "VideoCommentHook.QuickReply"
    private const val HP_VIDEO_COMMENT_WIDGETS = "VideoCommentHook.Widgets"
    private const val HP_VIDEO_COMMENT_SEARCH = "VideoCommentHook.SearchUrls"
    private const val HP_VIDEO_COMMENT_EMPTY_PAGE = "VideoCommentHook.EmptyPage"
    private const val HP_VIDEO_COMMENT_MAIN_LIST = "VideoCommentHook.MainList"
    private const val HP_SKIP_VIDEO_AD = "SkipVideoAdHook.InstallPoints"
    private const val HP_SKIP_VIDEO_AD_PLAY_VIEW = "SkipVideoAdHook.PlayView"
    private const val HP_SKIP_VIDEO_AD_PLAYER_CORE = "SkipVideoAdHook.PlayerCore"
    private const val HP_SKIP_VIDEO_AD_CARD = "SkipVideoAdHook.CardPlayer"
    private const val HP_SKIP_VIDEO_AD_STORY = "SkipVideoAdHook.StoryPlayer"
    private const val HP_SKIP_VIDEO_AD_PROGRESS = "SkipVideoAdProgressHook.InstallPoints"
    private const val HP_SKIP_VIDEO_AD_PROGRESS_DRAW = "SkipVideoAdProgressHook.ProgressDraw"
    private const val HP_SKIP_VIDEO_AD_PROGRESS_STORY = "SkipVideoAdProgressHook.StorySeekBar"
    private const val HP_SKIP_VIDEO_AD_PROGRESS_INLINE = "SkipVideoAdProgressHook.InlineProgress"
    private const val HP_SKIP_VIDEO_AD_AUTO_LIKE = "SkipVideoAdAutoLike.InstallPoints"
    private const val HP_SKIP_VIDEO_AD_AUTO_LIKE_DETAIL = "SkipVideoAdAutoLike.Detail"
    private const val HP_SKIP_VIDEO_AD_AUTO_LIKE_STORY = "SkipVideoAdAutoLike.Story"
    private const val HP_SKIP_VIDEO_AD_AUTO_LIKE_GEMINI = "SkipVideoAdAutoLike.Gemini"
    private const val HP_STORY_PLAYER_AD_FEED = "StoryPlayerAdHook.Feed"
    private const val HP_STORY_PLAYER_AD_PAGER = "StoryPlayerAdHook.Pager"
    private const val HP_STORY_PLAYER_AD_RERANK = "StoryPlayerAdHook.Rerank"
    private const val HP_CHRONOS_PROMOTION = "ChronosPromotionHook.InstallPoints"
    private const val HP_CHRONOS_RPC_RECEIVE = "ChronosPromotionHook.ReceiveRpc"
    private const val HP_CHRONOS_LOCAL_VIEW_PROGRESS = "ChronosPromotionHook.LocalViewProgress"
    private const val HP_CHRONOS_LOCAL_DM_VIEW = "ChronosPromotionHook.LocalDmView"
    private const val HP_CHRONOS_MESSAGE_SENDER = "ChronosPromotionHook.MessageSender"
    private const val HP_CHRONOS_COMMAND_DM_LIST = "ChronosPromotionHook.CommandDmList"
    private const val HP_CHRONOS_REMOTE_VIDEO_DETAIL_STATE = "ChronosPromotionHook.RemoteVideoDetailState"
    private const val HP_CHRONOS_REMOTE_VIEW_PROGRESS = "ChronosPromotionHook.RemoteViewProgress"
    private const val HP_CHRONOS_REMOTE_COMMAND_DANMAKU = "ChronosPromotionHook.RemoteCommandDanmaku"
    private const val HP_CHRONOS_REMOTE_ADD_DANMAKU = "ChronosPromotionHook.RemoteAddDanmaku"
    private const val HP_CHRONOS_REMOTE_AD_FLOAT_EXPOSURE = "ChronosPromotionHook.RemoteAdFloatExposure"
    private const val HP_CHRONOS_AD_DANMAKU_FEED = "ChronosPromotionHook.AdDanmakuFeed"
    private const val HP_CHRONOS_INTERACT_LAYER_VIEW_PROGRESS = "ChronosPromotionHook.InteractLayerViewProgress"
    private const val HP_CHRONOS_GEMINI_OPERATION_RENDER = "ChronosPromotionHook.GeminiOperationRender"
    private const val HP_CHRONOS_GEMINI_OPERATION_UPDATE = "ChronosPromotionHook.GeminiOperationUpdate"
    private const val HP_FULL_NUMBER_FORMAT = "FullNumberFormatHook.NumberFormat"
    private const val HP_TRIPLE_SPEED = "TripleSpeedHook.ExperimentReader"
    private const val HP_CUSTOM_THEME = "CustomThemeHook.ThemeStore"
    private const val HP_CUSTOM_SKIN = "CustomSkinHook.GarbResolver"
    private const val HP_VIDEO_QUALITY = "VideoQualityHook.QualityStrategy"
    private const val PLAY_SPEED_EXPERIMENT_PREF_KEY = "sp_play_speed_experiment"
    private const val HIGH_FRAME_RATE_SPEED_RESET_LOG = "reset 3x speed because target quality"
    private const val PLAY_SPEED_UTILS_CLASS = "com.bilibili.playerbizcommonv2.utils.D"

    @Volatile
    private var memorySymbols: BiliHookSymbols? = null

    fun resolve(
        hostContext: Context,
        classLoader: ClassLoader,
        log: (String, Throwable?) -> Unit,
    ): BiliHookSymbols {
        val appContext = hostContext.applicationContext ?: hostContext
        val fingerprint = buildFingerprint(appContext)
        memorySymbols?.takeIf { it.isUsableWith(fingerprint) }?.let {
            log("BiliSymbolResolver cache hit: memory fp=$fingerprint", null)
            it.formatStatusLines().forEach { line -> log(line, null) }
            return it
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val diskSymbols = BiliHookSymbols.fromJson(prefs.getString(KEY_SYMBOLS, null))
        if (diskSymbols?.isUsableWith(fingerprint) == true) {
            memorySymbols = diskSymbols
            log("BiliSymbolResolver cache hit: disk fp=$fingerprint", null)
            diskSymbols.formatStatusLines().forEach { line -> log(line, null) }
            return diskSymbols
        }

        log("BiliSymbolResolver scan begin fp=$fingerprint", null)
        val scanned = scan(
            hostContext = appContext,
            classLoader = classLoader,
            fingerprint = fingerprint,
            log = log,
        )
        writeCache(prefs, fingerprint, scanned, log)
        memorySymbols = scanned
        log("BiliSymbolResolver scan done fp=$fingerprint", null)
        scanned.formatStatusLines().forEach { line -> log(line, null) }
        publishStatus(prefs, scanned, log)
        return scanned
    }

    fun forceRefresh(
        hostContext: Context,
        classLoader: ClassLoader,
        log: (String, Throwable?) -> Unit,
    ): BiliHookSymbols {
        val appContext = hostContext.applicationContext ?: hostContext
        val fingerprint = buildFingerprint(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        memorySymbols = null

        log("BiliSymbolResolver force scan begin fp=$fingerprint", null)
        val scanned = scan(
            hostContext = appContext,
            classLoader = classLoader,
            fingerprint = fingerprint,
            log = log,
        )
        writeCache(prefs, fingerprint, scanned, log)
        memorySymbols = scanned
        log("BiliSymbolResolver force scan done fp=$fingerprint", null)
        scanned.formatStatusLines().forEach { line -> log(line, null) }
        publishStatus(prefs, scanned, log)
        return scanned
    }

    private fun scan(
        hostContext: Context,
        classLoader: ClassLoader,
        fingerprint: String,
        log: (String, Throwable?) -> Unit,
    ): BiliHookSymbols {
        val sourcePaths = sourcePaths(hostContext)
        val scanErrors = ArrayList<String>()
        var bridge: DexKitBridge? = null

        fun recordError(detail: String) {
            val sanitized = detail.replace('\n', ' ').take(420)
            scanErrors += sanitized
            log("BiliSymbolResolver scan issue: $sanitized", null)
        }

        fun bridge(): DexKitBridge? {
            bridge?.let { return it }
            val opened = DexKitBridgeProvider.openFirstAvailable(
                sourcePaths = sourcePaths,
                recordError = ::recordError,
                log = { log(it, null) },
            ) ?: return null
            bridge = opened.bridge
            return opened.bridge
        }

        val hookPoints = ArrayList<HookPointStatus>()
        val splashAd = scanHookPoint(HP_SPLASH_AD, hookPoints, scanErrors, log) {
            scanSplashAd(classLoader)
        }
        val share = scanHookPoint(HP_SHARE, hookPoints, scanErrors, log) {
            scanShare(classLoader, ::bridge)
        }
        val rewardAd = scanHookPoint(HP_REWARD_AD, hookPoints, scanErrors, log) {
            scanRewardAd(classLoader)
        }
        val tryFreeQuality = scanHookPoint(HP_TRY_FREE_QUALITY, hookPoints, scanErrors, log) {
            scanTryFreeQuality(classLoader, ::bridge)
        }
        val teenagersMode = scanHookPoint(HP_TEENAGERS_MODE, hookPoints, scanErrors, log) {
            scanTeenagersMode(classLoader)
        }
        val account = scanHookPoint(HP_ACCOUNT_ACCESS_KEY, hookPoints, scanErrors, log) {
            scanAccount(classLoader, ::bridge)
        }
        val settings = scanHookPoint(HP_SETTINGS_FRAGMENT, hookPoints, scanErrors, log) {
            scanSettings(classLoader, ::bridge)
        }
        val blockUpdate = scanHookPoint(HP_BLOCK_UPDATE, hookPoints, scanErrors, log) {
            scanBlockUpdate(classLoader, ::bridge)
        }
        val mineProfile = scanHookPoint(HP_MINE_VIP, hookPoints, scanErrors, log) {
            scanMineProfile(classLoader, ::bridge)
        }
        val downloadThreadListeners = scanHookPoint(HP_DOWNLOAD_THREAD_LISTENER, hookPoints, scanErrors, log) {
            scanDownloadThreadListeners(classLoader, ::bridge)
        }.orEmpty()
        val downloadThreadReport = scanOptionalHookPoint(HP_DOWNLOAD_THREAD_REPORT, hookPoints, scanErrors, log) {
            scanDownloadThreadReportMethod(classLoader, ::bridge)
        }
        val downloadThread = if (downloadThreadListeners.isNotEmpty() || downloadThreadReport != null) {
            DownloadThreadSymbols(
                listeners = downloadThreadListeners,
                reportMethod = downloadThreadReport,
                evidence = "listeners=${downloadThreadListeners.size},report=${downloadThreadReport != null}",
            )
        } else {
            null
        }
        val storyPlayerAd = scanHookPoint(HP_STORY_PLAYER_AD, hookPoints, scanErrors, log) {
            scanStoryPlayerAd(classLoader)
        }
        val storyFullscreen = scanHookPoint(HP_STORY_FULLSCREEN, hookPoints, scanErrors, log) {
            scanStoryFullscreen(classLoader)
        }
        val storyDanmaku = scanHookPoint(HP_STORY_DANMAKU, hookPoints, scanErrors, log) {
            scanStoryDanmaku(classLoader)
        }
        val storyComponentAlpha = scanHookPoint(HP_STORY_COMPONENT_ALPHA, hookPoints, scanErrors, log) {
            scanStoryComponentAlpha(classLoader)
        }
        val videoDetailBannerAd = scanHookPoint(HP_VIDEO_DETAIL_BANNER_AD, hookPoints, scanErrors, log) {
            scanVideoDetailBannerAd(classLoader, ::bridge)
        }
        val videoDetailRelateFeed = scanHookPoint(HP_VIDEO_DETAIL_RELATE_FEED, hookPoints, scanErrors, log) {
            scanVideoDetailRelateFeed(classLoader)
        }
        val homeTopBar = scanHookPoint(HP_HOME_TOP_BAR, hookPoints, scanErrors, log) {
            scanHomeTopBar(classLoader)
        }
        val bottomBar = scanHookPoint(HP_BOTTOM_BAR, hookPoints, scanErrors, log) {
            scanBottomBar(classLoader, ::bridge)
        }
        val homeRecommendFeed = scanHookPoint(HP_HOME_RECOMMEND_FEED, hookPoints, scanErrors, log) {
            scanHomeRecommendFeed(classLoader)
        }
        val homeRecommendTabs = scanHookPoint(HP_HOME_RECOMMEND_TABS, hookPoints, scanErrors, log) {
            scanHomeRecommendTabs(classLoader)
        }
        val homeComponentHide = scanHookPoint(HP_HOME_COMPONENT_HIDE, hookPoints, scanErrors, log) {
            scanHomeComponentHide(classLoader)
        }
        val videoComment = scanHookPoint(HP_VIDEO_COMMENT, hookPoints, scanErrors, log) {
            scanVideoComment(classLoader, ::bridge)
        }
        val skipVideoAd = scanHookPoint(HP_SKIP_VIDEO_AD, hookPoints, scanErrors, log) {
            scanSkipVideoAd(classLoader, ::bridge)
        }
        val skipVideoAdProgress = scanHookPoint(HP_SKIP_VIDEO_AD_PROGRESS, hookPoints, scanErrors, log) {
            scanSkipVideoAdProgress(classLoader)
        }
        val skipVideoAdAutoLike = scanHookPoint(HP_SKIP_VIDEO_AD_AUTO_LIKE, hookPoints, scanErrors, log) {
            scanSkipVideoAdAutoLike(classLoader)
        }
        val chronosPromotion = scanHookPoint(HP_CHRONOS_PROMOTION, hookPoints, scanErrors, log) {
            scanChronosPromotion(classLoader, ::bridge)
        }
        val fullNumberFormat = scanHookPoint(HP_FULL_NUMBER_FORMAT, hookPoints, scanErrors, log) {
            scanFullNumberFormat(classLoader)
        }
        val tripleSpeed = scanHookPoint(HP_TRIPLE_SPEED, hookPoints, scanErrors, log) {
            scanTripleSpeed(classLoader, ::bridge)
        }
        val customTheme = scanOptionalHookPoint(HP_CUSTOM_THEME, hookPoints, scanErrors, log) {
            scanCustomTheme(classLoader, ::bridge)
        }
        val customSkin = scanOptionalHookPoint(HP_CUSTOM_SKIN, hookPoints, scanErrors, log) {
            scanCustomSkin(classLoader, ::bridge)
        }
        val videoQuality = scanOptionalHookPoint(HP_VIDEO_QUALITY, hookPoints, scanErrors, log) {
            scanVideoQuality(classLoader, ::bridge)
        }

        runCatching { bridge?.close() }
            .onFailure { recordError("DexKitBridge close failed: ${it.scanMessage()}") }

        return BiliHookSymbols(
            fingerprint = fingerprint,
            hookPoints = hookPoints,
            scanErrors = scanErrors.distinct(),
            splashAd = splashAd,
            share = share,
            rewardAd = rewardAd,
            tryFreeQuality = tryFreeQuality,
            teenagersMode = teenagersMode,
            account = account,
            settings = settings,
            blockUpdate = blockUpdate,
            mineProfile = mineProfile,
            downloadThread = downloadThread,
            storyPlayerAd = storyPlayerAd,
            storyFullscreen = storyFullscreen,
            storyDanmaku = storyDanmaku,
            storyComponentAlpha = storyComponentAlpha,
            videoDetailBannerAd = videoDetailBannerAd,
            videoDetailRelateFeed = videoDetailRelateFeed,
            homeTopBar = homeTopBar,
            bottomBar = bottomBar,
            homeRecommendFeed = homeRecommendFeed,
            homeRecommendTabs = homeRecommendTabs,
            homeComponentHide = homeComponentHide,
            videoComment = videoComment,
            skipVideoAd = skipVideoAd,
            skipVideoAdProgress = skipVideoAdProgress,
            skipVideoAdAutoLike = skipVideoAdAutoLike,
            chronosPromotion = chronosPromotion,
            fullNumberFormat = fullNumberFormat,
            tripleSpeed = tripleSpeed,
            customTheme = customTheme,
            customSkin = customSkin,
            videoQuality = videoQuality,
        )
    }

    private inline fun <T : Any> scanHookPoint(
        id: String,
        hookPoints: MutableList<HookPointStatus>,
        scanErrors: MutableList<String>,
        log: (String, Throwable?) -> Unit,
        block: () -> SymbolScanResult<T>,
    ): T? {
        return try {
            when (val result = block()) {
                is SymbolScanResult.Found -> {
                    hookPoints += HookPointStatus.found(id, result.target, result.evidence)
                    hookPoints += result.hookPoints
                    result.value
                }
                is SymbolScanResult.Missing -> {
                    hookPoints += HookPointStatus.missing(id, result.reason)
                    hookPoints += result.hookPoints
                    null
                }
            }
        } catch (t: Throwable) {
            val reason = t.scanMessage()
            scanErrors += "$id :: $reason"
            hookPoints += HookPointStatus.error(id, reason)
            log("BiliSymbolResolver $id scan failed", t)
            null
        }
    }

    private inline fun <T : Any> scanOptionalHookPoint(
        id: String,
        hookPoints: MutableList<HookPointStatus>,
        scanErrors: MutableList<String>,
        log: (String, Throwable?) -> Unit,
        block: () -> SymbolScanResult<T>,
    ): T? {
        return try {
            when (val result = block()) {
                is SymbolScanResult.Found -> {
                    hookPoints += HookPointStatus.found(id, result.target, result.evidence)
                    hookPoints += result.hookPoints
                    result.value
                }
                is SymbolScanResult.Missing -> {
                    hookPoints += HookPointStatus.optional(id, result.reason)
                    hookPoints += result.hookPoints
                    null
                }
            }
        } catch (t: Throwable) {
            val reason = t.scanMessage()
            scanErrors += "$id :: $reason"
            hookPoints += HookPointStatus.error(id, reason)
            log("BiliSymbolResolver $id scan failed", t)
            null
        }
    }

    private fun scanSplashAd(classLoader: ClassLoader): SymbolScanResult<SplashAdSymbols> {
        val gsonParsers = classLoader.loadClassOrNull(SPLASH_GSON_CLASS)
            ?.allMethods()
            ?.filter { it.name == "fromJson" && !Modifier.isAbstract(it.modifiers) && it.returnType != Void.TYPE }
            ?.toList()
            .orEmpty()
        val fastJsonParsers = SPLASH_FAST_JSON_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
            ?.declaredMethods
            ?.filter {
                Modifier.isStatic(it.modifiers) &&
                    it.name in SPLASH_FAST_JSON_PARSE_METHODS &&
                    it.returnType != Void.TYPE
            }
            .orEmpty()
        val kotlinxParsers = SPLASH_KOTLINX_JSON_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
            ?.allMethods()
            ?.filter { it.name == "decodeFromString" && it.parameterCount >= 2 && it.returnType != Void.TYPE }
            ?.toList()
            .orEmpty()
        val total = gsonParsers.size + fastJsonParsers.size + kotlinxParsers.size
        if (total == 0) return SymbolScanResult.Missing("json parser hook methods not found")
        val symbols = SplashAdSymbols(
            parserMethods = (gsonParsers + fastJsonParsers + kotlinxParsers)
                .distinctBy(Method::toGenericString)
                .map(MethodDescriptor::of),
            evidence = "gson=${gsonParsers.size},fastjson=${fastJsonParsers.size},kotlinx=${kotlinxParsers.size}",
        )
        return SymbolScanResult.Found(symbols, "JsonParsers", symbols.evidence)
    }

    private fun scanShare(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<ShareSymbols> {
        val legacyClass = classLoader.loadClassOrNull(SHARE_LEGACY_RESULT)
        val legacyGetLink = legacyClass?.findNoArgStringMethod("getLink")
        val legacyGetContent = legacyClass?.findNoArgStringMethod("getContent")
        val legacyGetShareMode = legacyClass?.findNoArgMethod("getShareMode")
        val legacySetLink = legacyClass?.findStringSetter("setLink")
        val legacySetContent = legacyClass?.findStringSetter("setContent")
        val legacySetShareMode = legacyClass?.findIntegerSetter("setShareMode")
        val legacyCount = listOfNotNull(
            legacyGetLink,
            legacyGetContent,
            legacyGetShareMode,
            legacySetLink,
            legacySetContent,
            legacySetShareMode,
        ).size

        val shareChannelsClass = classLoader.loadClassOrNull(SHARE_CHANNELS)
        val shareChannelsGetCopyLink = shareChannelsClass?.findNoArgStringMethod("getCopyLink")
        val shareChannelsGetJumpLink = shareChannelsClass?.findNoArgStringMethod("getJumpLink")
        val shareChannelsGetText = shareChannelsClass?.findNoArgStringMethod("getText")
        val shareChannelsSetCopyLink = shareChannelsClass?.findStringSetter("setCopyLink")
        val shareChannelsSetJumpLink = shareChannelsClass?.findStringSetter("setJumpLink")
        val shareChannelsSetText = shareChannelsClass?.findStringSetter("setText")
        val shareChannelItemClass = classLoader.loadClassOrNull(SHARE_CHANNEL_ITEM)
        val shareChannelItemGetJumpLink = shareChannelItemClass?.findNoArgStringMethod("getJumpLink")
        val shareChannelItemSetJumpLink = shareChannelItemClass?.findStringSetter("setJumpLink")
        val shareChannelsCount = listOfNotNull(
            shareChannelsGetCopyLink,
            shareChannelsGetJumpLink,
            shareChannelsGetText,
            shareChannelsSetCopyLink,
            shareChannelsSetJumpLink,
            shareChannelsSetText,
            shareChannelItemGetJumpLink,
            shareChannelItemSetJumpLink,
        ).size

        val shareClickResultScan = findShareClickResultClass(classLoader, bridge)
        val shareClickResultClass = shareClickResultScan.type
        val shareClickResultCount = shareClickResultClass?.declaredConstructors
            ?.count { it.isShareClickResultConstructor() }
            ?: 0

        val shareBaseInfoScan = findShareBaseInfoClass(classLoader, bridge)
        val shareBaseInfoClass = shareBaseInfoScan.type
        val shareBaseInfoCount = shareBaseInfoClass?.declaredConstructors
            ?.count { it.isShareBaseInfoConstructor() }
            ?: 0

        val shareContentClass = SHARE_CONTENT_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        val shareContentCopyMethods = shareContentClass?.copyLikeMethods().orEmpty()
        val shareContentGetLink = shareContentClass?.findNoArgStringMethod("getLink")
        val shareContentGetContent = shareContentClass?.findNoArgStringMethod("getContent")
        val shareContentGetMode = shareContentClass?.findNoArgMethod("getMode")
        val shareContentCount = shareContentClass.installWeight(
            shareContentCopyMethods,
            shareContentGetLink,
            shareContentGetContent,
            shareContentGetMode,
        )

        val shareBiliContentClass = SHARE_BILI_CONTENT_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        val shareBiliContentCopyMethods = shareBiliContentClass?.copyLikeMethods().orEmpty()
        val shareBiliContentGetDescription = shareBiliContentClass?.findNoArgStringMethod("getDescription")
        val shareBiliContentGetContentUrl = shareBiliContentClass?.findNoArgStringMethod("getContentUrl")
        val shareBiliContentCount = shareBiliContentClass.installWeight(
            shareBiliContentCopyMethods,
            shareBiliContentGetDescription,
            shareBiliContentGetContentUrl,
        )

        val copyContentClass = SHARE_COPY_CONTENT_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        val copyContentGetters = listOfNotNull(
            copyContentClass?.findNoArgStringMethod("c"),
            copyContentClass?.findNoArgStringMethod("mo127c"),
        )
        val copyContentCount = copyContentClass.installWeight(copyContentGetters)

        val copyUtilityMethods = SHARE_COPY_UTILITY_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
            ?.allMethods()
            ?.filter {
                Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                    it.returnType == Void.TYPE
            }
            ?.toList()
            .orEmpty()
        val copyUtilityCount = copyUtilityMethods.size
        val hookPoints = listOf(
            optionalChildHookPoint(HP_SHARE_LEGACY, legacyCount > 0, "legacy share result hooks not found", "methods=$legacyCount"),
            optionalChildHookPoint(
                HP_SHARE_CHANNELS,
                shareChannelsCount > 0,
                "share channels link/text hooks not found",
                "methods=$shareChannelsCount",
            ),
            optionalChildHookPoint(
                HP_SHARE_CLICK_RESULT,
                shareClickResultCount > 0,
                shareClickResultScan.missingReason("share click result class not found"),
                "constructors=$shareClickResultCount",
            ),
            optionalChildHookPoint(
                HP_SHARE_BASE_INFO,
                shareBaseInfoCount > 0,
                shareBaseInfoScan.missingReason("share base info class not found"),
                "constructors=$shareBaseInfoCount",
            ),
            optionalChildHookPoint(HP_SHARE_CONTENT, shareContentCount > 0, "share content hooks not found", "methods=$shareContentCount"),
            optionalChildHookPoint(HP_SHARE_BILI_CONTENT, shareBiliContentCount > 0, "share bili content hooks not found", "methods=$shareBiliContentCount"),
            optionalChildHookPoint(HP_SHARE_COPY_CONTENT, copyContentCount > 0, "copy content hooks not found", "methods=$copyContentCount"),
            optionalChildHookPoint(HP_SHARE_COPY_UTILITY, copyUtilityCount > 0, "copy utility hook not found", "methods=$copyUtilityCount"),
        )
        val total = legacyCount + shareChannelsCount + shareClickResultCount + shareBaseInfoCount +
            shareContentCount + shareBiliContentCount + copyContentCount + copyUtilityCount + 1
        val symbols = ShareSymbols(
            legacyGetLink = legacyGetLink?.let(MethodDescriptor::of),
            legacyGetContent = legacyGetContent?.let(MethodDescriptor::of),
            legacyGetShareMode = legacyGetShareMode?.let(MethodDescriptor::of),
            legacySetLink = legacySetLink?.let(MethodDescriptor::of),
            legacySetContent = legacySetContent?.let(MethodDescriptor::of),
            legacySetShareMode = legacySetShareMode?.let(MethodDescriptor::of),
            shareChannelsGetCopyLink = shareChannelsGetCopyLink?.let(MethodDescriptor::of),
            shareChannelsGetJumpLink = shareChannelsGetJumpLink?.let(MethodDescriptor::of),
            shareChannelsGetText = shareChannelsGetText?.let(MethodDescriptor::of),
            shareChannelsSetCopyLink = shareChannelsSetCopyLink?.let(MethodDescriptor::of),
            shareChannelsSetJumpLink = shareChannelsSetJumpLink?.let(MethodDescriptor::of),
            shareChannelsSetText = shareChannelsSetText?.let(MethodDescriptor::of),
            shareChannelItemGetJumpLink = shareChannelItemGetJumpLink?.let(MethodDescriptor::of),
            shareChannelItemSetJumpLink = shareChannelItemSetJumpLink?.let(MethodDescriptor::of),
            shareClickResultClassName = shareClickResultClass?.name,
            shareBaseInfoClassName = shareBaseInfoClass?.name,
            shareContentClassName = shareContentClass?.name,
            shareContentCopyMethods = shareContentCopyMethods.map(MethodDescriptor::of),
            shareContentGetLink = shareContentGetLink?.let(MethodDescriptor::of),
            shareContentGetContent = shareContentGetContent?.let(MethodDescriptor::of),
            shareContentGetMode = shareContentGetMode?.let(MethodDescriptor::of),
            shareBiliContentClassName = shareBiliContentClass?.name,
            shareBiliContentCopyMethods = shareBiliContentCopyMethods.map(MethodDescriptor::of),
            shareBiliContentGetDescription = shareBiliContentGetDescription?.let(MethodDescriptor::of),
            shareBiliContentGetContentUrl = shareBiliContentGetContentUrl?.let(MethodDescriptor::of),
            copyContentClassName = copyContentClass?.name,
            copyContentGetters = copyContentGetters.map(MethodDescriptor::of),
            copyUtilityMethods = copyUtilityMethods.map(MethodDescriptor::of),
            evidence = "methods=$total",
        )
        return SymbolScanResult.Found(symbols, "ClipboardFallback", symbols.evidence, hookPoints)
    }

    private fun scanRewardAd(classLoader: ClassLoader): SymbolScanResult<RewardAdSymbols> {
        val activityClass = REWARD_ACTIVITY_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        val activityOnCreate = activityClass?.findMethod("onCreate", Void.TYPE, Bundle::class.java)
        val activityOnResume = activityClass?.findMethod("onResume", Void.TYPE)
        val activityOnStop = activityClass?.findMethod("onStop", Void.TYPE)
        val activityCount = listOfNotNull(activityOnCreate, activityOnResume, activityOnStop).size

        val headerClass = classLoader.loadClassOrNull(REWARD_HEADER_VIEW)
        val headerSetTotalTime = headerClass?.findRewardTimerSetter("setTotalTime", Int::class.javaPrimitiveType!!)
        val headerSetElapsedTime = headerClass?.findRewardTimerSetter("setElapsedTime", Long::class.javaPrimitiveType!!)
        val headerStartTimer = headerClass?.findMethod("startTimer", Void.TYPE)
        val headerCount = listOfNotNull(headerSetTotalTime, headerSetElapsedTime, headerStartTimer).size

        val countDownClass = classLoader.loadClassOrNull(REWARD_COUNT_DOWN_TEXT_VIEW)
        val countDownSetTotalTime = countDownClass?.findRewardTimerSetter("setTotalTime", Int::class.javaPrimitiveType!!)
        val countDownSetElapsedTime = countDownClass?.findRewardTimerSetter("setElapsedTime", Long::class.javaPrimitiveType!!)
        val countDownCount = listOfNotNull(countDownSetTotalTime, countDownSetElapsedTime).size

        val miniGameRewardClass = classLoader.loadClassOrNull(REWARD_MINI_GAME_ABILITY)
        val miniGameRewardShow = miniGameRewardClass?.declaredMethods?.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 5 &&
                method.parameterTypes[1] == String::class.java &&
                method.parameterTypes[2] == String::class.java &&
                method.parameterTypes[3] == String::class.java &&
                method.returnType != Void.TYPE
        }?.apply { isAccessible = true }
        val miniGameRewardEmitEvent = miniGameRewardClass?.declaredMethods?.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(
                    arrayOf(String::class.java, String::class.java, JSONObject::class.java),
                )
        }?.apply { isAccessible = true }
        val miniGameRewardCallback = miniGameRewardShow?.parameterTypes?.getOrNull(4)
            ?.declaredMethods
            ?.singleOrNull { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(String::class.java, JSONObject::class.java))
            }
            ?.apply { isAccessible = true }
        val miniGameRewardReady =
            miniGameRewardShow != null && miniGameRewardEmitEvent != null && miniGameRewardCallback != null
        val miniGameRewardCount = listOfNotNull(
            miniGameRewardShow,
            miniGameRewardEmitEvent,
            miniGameRewardCallback,
        ).size

        val jumpClockField = REWARD_JUMP_CLOCK_CLASSES.firstNotNullOfOrNull { className ->
            classLoader.loadClassOrNull(className)
                ?.declaredFields
                ?.singleOrNull { field ->
                    Modifier.isStatic(field.modifiers) &&
                        (field.type == java.lang.Long::class.java || field.type == Long::class.javaPrimitiveType)
                }
                ?.apply { isAccessible = true }
        }
        val hookPoints = listOf(
            childHookPoint(HP_REWARD_AD_ACTIVITY, activityCount > 0, "reward activity hooks not found", "methods=$activityCount"),
            childHookPoint(HP_REWARD_AD_HEADER, headerCount > 0, "reward header timer hooks not found", "methods=$headerCount"),
            childHookPoint(HP_REWARD_AD_COUNTDOWN, countDownCount > 0, "countdown text hooks not found", "methods=$countDownCount"),
            optionalChildHookPoint(HP_REWARD_AD_JUMP_CLOCK, jumpClockField != null, "jump clock field not found", "field=${jumpClockField?.name}"),
            HookPointStatus.found(HP_REWARD_AD_ACTIVITY_SWEEPER, "android.app.Activity.onResume", "sdk=true"),
            optionalChildHookPoint(
                HP_REWARD_AD_MINI_GAME,
                miniGameRewardReady,
                "mini game rewarded video hooks not found",
                "methods=$miniGameRewardCount",
            ),
        )
        val total = activityCount + headerCount + countDownCount + (if (miniGameRewardReady) 1 else 0) + 1
        if (total == 1) return SymbolScanResult.Missing("reward ad target hook points not found")
        val symbols = RewardAdSymbols(
            activityOnCreate = activityOnCreate?.let(MethodDescriptor::of),
            activityOnResume = activityOnResume?.let(MethodDescriptor::of),
            activityOnStop = activityOnStop?.let(MethodDescriptor::of),
            headerSetTotalTime = headerSetTotalTime?.let(MethodDescriptor::of),
            headerSetElapsedTime = headerSetElapsedTime?.let(MethodDescriptor::of),
            headerStartTimer = headerStartTimer?.let(MethodDescriptor::of),
            countDownSetTotalTime = countDownSetTotalTime?.let(MethodDescriptor::of),
            countDownSetElapsedTime = countDownSetElapsedTime?.let(MethodDescriptor::of),
            jumpClockField = jumpClockField?.let(FieldDescriptor::of),
            miniGameRewardShow = if (miniGameRewardReady) miniGameRewardShow?.let(MethodDescriptor::of) else null,
            miniGameRewardEmitEvent = if (miniGameRewardReady) {
                miniGameRewardEmitEvent?.let(MethodDescriptor::of)
            } else {
                null
            },
            miniGameRewardCallback = if (miniGameRewardReady) {
                miniGameRewardCallback?.let(MethodDescriptor::of)
            } else {
                null
            },
            evidence = "activity=$activityCount,header=$headerCount,countDown=$countDownCount," +
                "jumpClock=${jumpClockField != null},miniGame=$miniGameRewardReady",
        )
        return SymbolScanResult.Found(symbols, "RewardAd", symbols.evidence, hookPoints)
    }

    /**
     * Kotlin may append a module name to public Java method names after a module split.
     * Keep accepting the stable name while requiring the original timer signature.
     */
    private fun Class<*>.findRewardTimerSetter(name: String, parameterType: Class<*>): Method? =
        allMethods().firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(parameterType)) &&
                (method.name == name || method.name.startsWith("$name\$"))
        }?.apply { isAccessible = true }

    private fun scanTryFreeQuality(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<TryFreeQualitySymbols> {
        val playViewClassNames = buildSet {
            addAll(PLAYER_MOSS_CANDIDATES)
            addAll(findClassNamesBySimpleName(bridge, "PlayerMoss"))
            addAll(findClassNamesBySimpleName(bridge, "KPlayerMoss"))
            addAll(findClassNamesBySimpleName(bridge, "PlayURLMoss"))
        }
        val playViewMethods = playViewClassNames
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.allMethods().asSequence().filter { method ->
                    method.isConcreteInstanceHookMethod() &&
                        method.name in PLAY_VIEW_METHOD_NAMES &&
                        method.parameterCount >= 1 &&
                        method.parameterTypes.firstOrNull()?.isPlayViewRequestType() == true
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val getIsNeedTrial = ArrayList<Method>()
        val setIsNeedTrial = ArrayList<Method>()
        TRY_FREE_NEED_TRIAL_CLASSES.forEach { className ->
            val type = classLoader.loadClassOrNull(className) ?: return@forEach
            type.findMethod("getIsNeedTrial", Boolean::class.javaPrimitiveType!!)?.let(getIsNeedTrial::add)
            type.findMethod("setIsNeedTrial", Void.TYPE, Boolean::class.javaPrimitiveType!!)?.let(setIsNeedTrial::add)
        }
        val getVipFree = ArrayList<Method>()
        val getNeedVip = ArrayList<Method>()
        TRY_FREE_STREAM_INFO_CLASSES.forEach { className ->
            val type = classLoader.loadClassOrNull(className) ?: return@forEach
            type.findMethod("getVipFree", Boolean::class.javaPrimitiveType!!)?.let(getVipFree::add)
            type.findMethod("getNeedVip", Boolean::class.javaPrimitiveType!!)?.let(getNeedVip::add)
        }
        val needTrialCount = getIsNeedTrial.size + setIsNeedTrial.size
        val streamInfoCount = getVipFree.size + getNeedVip.size
        val playViewCount = playViewMethods.size
        val total = needTrialCount + streamInfoCount + playViewCount
        if (total == 0) return SymbolScanResult.Missing("try-free quality hook points not found")
        val symbols = TryFreeQualitySymbols(
            playViewMethods = playViewMethods.map(MethodDescriptor::of),
            getIsNeedTrialMethods = getIsNeedTrial.map(MethodDescriptor::of),
            setIsNeedTrialMethods = setIsNeedTrial.map(MethodDescriptor::of),
            getVipFreeMethods = getVipFree.map(MethodDescriptor::of),
            getNeedVipMethods = getNeedVip.map(MethodDescriptor::of),
            evidence = "needTrial=$needTrialCount,streamInfo=$streamInfoCount,playView=$playViewCount",
        )
        return SymbolScanResult.Found(symbols, "GeneratedMessages/PlayView", symbols.evidence)
    }

    private fun scanTeenagersMode(classLoader: ClassLoader): SymbolScanResult<TeenagersModeSymbols> {
        val methods = TEENAGERS_MODE_ACTIVITIES.mapNotNull { className ->
            classLoader.loadClassOrNull(className)?.let { type ->
                type.allMethods()
                    .firstOrNull {
                        it.name == "onCreate" &&
                            it.returnType == Void.TYPE &&
                            it.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
                    }
            }
        }
        if (methods.isEmpty()) return SymbolScanResult.Missing("teenagers mode dialog activity not found")
        val symbols = TeenagersModeSymbols(
            onCreateMethods = methods.map(MethodDescriptor::of),
            evidence = "activities=${methods.size}",
        )
        return SymbolScanResult.Found(symbols, methods.joinToString("|") { "${it.declaringClass.name}.${it.name}" }, symbols.evidence)
    }

    private fun scanAccount(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<AccountSymbols> {
        val candidates = (
            ACCOUNT_CLASS_NAMES.asSequence() +
                findClassNamesBySimpleName(bridge, "BiliAccounts").asSequence()
            )
            .distinct()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .toList()

        for (type in candidates) {
            val getMethod = type.declaredMethods
                .filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.returnType == type &&
                        (
                            method.parameterCount == 0 ||
                                method.hasParameterTypes("android.content.Context")
                            )
                }
                .sortedWith(compareBy<Method> { if (it.name == "get") 0 else 1 }.thenBy { it.parameterCount })
                .firstOrNull()
                ?.apply { isAccessible = true }
                ?: continue
            val accessKeyMethod = type.declaredMethods
                .filter { method ->
                    !Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 0 &&
                        method.returnType == String::class.java
                }
                .sortedWith(
                    compareBy<Method> {
                        when (it.name) {
                            "getAccessKey" -> 0
                            "accessKey" -> 1
                            else -> 2
                        }
                    }.thenBy { it.name },
                )
                .firstOrNull { method -> method.name.contains("access", ignoreCase = true) || method.name in SHORT_ACCESS_METHODS }
                ?.apply { isAccessible = true }
                ?: continue
            val isLoginMethod = type.declaredMethods.firstOrNull { method ->
                method.name == "isLogin" && !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 && method.returnType == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true } ?: continue
            val midMethod = type.declaredMethods.firstOrNull { method ->
                method.name == "mid" && !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 && method.returnType == Long::class.javaPrimitiveType
            }?.apply { isAccessible = true }

            val symbols = AccountSymbols(
                accountClassName = type.name,
                getMethod = MethodDescriptor.of(getMethod),
                accessKeyMethod = MethodDescriptor.of(accessKeyMethod),
                isLoginMethod = MethodDescriptor.of(isLoginMethod),
                midMethod = midMethod?.let(MethodDescriptor::of),
                evidence = if (type.name in ACCOUNT_CLASS_NAMES) "stableClass" else "dexkitSimpleName",
            )
            return SymbolScanResult.Found(symbols, "${type.name}.${accessKeyMethod.name}", symbols.evidence)
        }
        return SymbolScanResult.Missing("account class or getAccessKey method not verified")
    }

    private fun scanSettings(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<SettingsSymbols> {
        val fragmentNames = (
            SETTINGS_FRAGMENT_NAMES.asSequence() +
                findClassNamesBySimpleName(bridge, "BiliPreferencesFragment").asSequence() +
                findClassNamesBySimpleName(bridge, "WideBiliPreferencesFragment").asSequence()
            ).distinct()

        val methods = fragmentNames
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .mapNotNull { type ->
                type.declaredMethods.firstOrNull { method ->
                    method.name == "onCreatePreferences" && method.parameterCount == 2
                }?.apply { isAccessible = true }
            }
            .distinctBy { it.declaringClass.name + "#" + it.name + it.parameterTypes.joinToString { type -> type.name } }
            .toList()

        val preferenceClass = PREFERENCE_CLASS_NAMES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
            ?: return SymbolScanResult.Missing("preference class not found")
        if (methods.isEmpty()) {
            return SymbolScanResult.Missing("onCreatePreferences fragment method not found")
        }
        val symbols = SettingsSymbols(
            fragmentMethods = methods.map(MethodDescriptor::of),
            preferenceClassName = preferenceClass.name,
            evidence = "fragments=${methods.size},preference=${preferenceClass.name}",
        )
        return SymbolScanResult.Found(symbols, methods.joinToString("|") { it.declaringClass.name }, symbols.evidence)
    }

    private fun scanVideoQuality(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<VideoQualitySymbols> {
        val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
        
        // 1. AutoSupremumQuality constructor
        val autoSupremumQualityClass = runCatching {
            currentBridge.findClass(
                FindClass.create().matcher(ClassMatcher.create().usingStrings("AutoSupremumQuality(loginHalfScreen=")),
            ).mapNotNull { classLoader.loadClassOrNull(it.name) }.firstOrNull()
        }.getOrNull()

        val autoSupremumCtor = autoSupremumQualityClass?.declaredConstructors?.firstOrNull { ctor ->
            ctor.parameterTypes.size == 6 && ctor.parameterTypes.all { it == Int::class.javaPrimitiveType }
        }?.apply { isAccessible = true }

        // 2. QualityStrategyProvider selectQuality method
        val buildStrategyMethod = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings("Quality Strategy share:")),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }.firstOrNull()
        }.getOrNull()

        val providerClass = buildStrategyMethod?.declaringClass
        val selectQualityMethod = providerClass?.declaredMethods?.firstOrNull { method ->
            method.parameterCount == 3 &&
                autoSupremumQualityClass != null &&
                method.parameterTypes[0] == autoSupremumQualityClass &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[2] == Boolean::class.javaPrimitiveType
        }?.apply { isAccessible = true }

        // 3. PlayerPreloadHolder (PreloadData(type=)
        val preloadMethods = runCatching {
            val preloadClass = currentBridge.findClass(
                FindClass.create().matcher(ClassMatcher.create().usingStrings("PreloadData(type=")),
            ).mapNotNull { classLoader.loadClassOrNull(it.name) }.firstOrNull()
            val outerHolderClass = preloadClass?.declaringClass
            outerHolderClass?.declaredMethods?.filter {
                Modifier.isPublic(it.modifiers) && it.parameterCount == 1
            }?.onEach { it.isAccessible = true }.orEmpty()
        }.getOrElse { emptyList() }

        // 4. PlayerQualityService (player.unite_login_qn)
        val qualityServiceMethods = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings("player.unite_login_qn")),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .onEach { it.isAccessible = true }
        }.getOrElse { emptyList() }

        // 5. PlayerSettingHelper (quality settings:)
        val defaultQnMethod = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings("quality settings:")),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .firstOrNull { it.returnType == Int::class.javaPrimitiveType || it.returnType == Int::class.javaObjectType }
                ?.apply { isAccessible = true }
        }.getOrNull()

        val symbols = VideoQualitySymbols(
            autoSupremumQualityConstructor = autoSupremumCtor?.let(ConstructorDescriptor::of),
            qualityStrategySelectMethod = selectQualityMethod?.let(MethodDescriptor::of),
            playerPreloadGetMethods = preloadMethods.map(MethodDescriptor::of),
            playerQualityServiceMethods = qualityServiceMethods.map(MethodDescriptor::of),
            playerSettingHelperGetDefaultQnMethod = defaultQnMethod?.let(MethodDescriptor::of),
            evidence = "autoSupremum=${autoSupremumCtor != null},strategy=${selectQualityMethod != null},preload=${preloadMethods.size},service=${qualityServiceMethods.size},setting=${defaultQnMethod != null}",
        )

        return if (autoSupremumCtor != null || selectQualityMethod != null || preloadMethods.isNotEmpty() || qualityServiceMethods.isNotEmpty()) {
            SymbolScanResult.Found(symbols, autoSupremumQualityClass?.name ?: "VideoQuality", symbols.evidence)
        } else {
            SymbolScanResult.Missing("video quality hook points not found")
        }
    }

    private fun scanCustomSkin(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<CustomSkinSymbols> {
        val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
        val resolverMethod = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings("shouldApplyForceOpGarb =")),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .firstOrNull { method ->
                    method.parameterCount == 1 &&
                        method.returnType != Void.TYPE &&
                        method.returnType.name.contains("GarbDetail")
                }
        }.getOrNull() ?: return SymbolScanResult.Missing("garb resolver method not found")
        resolverMethod.isAccessible = true

        // 皮肤响应模型:含 setUserGarb / setLoadEquip setter,
        // load_equip(下拉动画)与 user_equip 一起在解析 /x/resource/show/skin 时注入
        val skinResponseClass = runCatching {
            currentBridge.findClass(
                FindClass.create().matcher(ClassMatcher.create().usingStrings("user_equip")),
            ).mapNotNull { classLoader.loadClassOrNull(it.name) }
                .firstOrNull { type ->
                    type.declaredMethods.any { it.name == "setUserGarb" && it.parameterCount == 1 }
                }
        }.getOrNull()
        val skinResponseUserGarbSetter = skinResponseClass?.declaredMethods?.firstOrNull {
            it.name == "setUserGarb" && it.parameterCount == 1
        }?.apply { isAccessible = true }
        val skinResponseLoadEquipSetter = skinResponseClass?.declaredMethods?.firstOrNull {
            it.name == "setLoadEquip" && it.parameterCount == 1
        }?.apply { isAccessible = true }

        // 皮肤解析入口方法(宽松匹配,与 scanCustomTheme 的规则一致)
        val skinResolveMethod = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings("shouldApplyForceOpGarb =")),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .firstOrNull { it.parameterCount == 1 && it.returnType != Void.TYPE }
                ?.apply { isAccessible = true }
        }.getOrNull()

        // 进度条图标(play_icon)的三个 URL getter:类名限定 PlayerIcon,
        // 命中 protobuf PlayerIcon 与番剧 JSON 模型,排除 Description 等无关类
        val videoPlayerIconGetters = runCatching {
            listOf("getDragLeftPng", "getDragRightPng", "getMiddlePng").flatMap { methodName ->
                currentBridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create().name(methodName)),
                ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                    .filter { method ->
                        method.parameterCount == 0 &&
                            !Modifier.isAbstract(method.modifiers) &&
                            !method.declaringClass.isInterface &&
                            method.declaringClass.name.contains("PlayerIcon")
                    }
            }.distinctBy { "${it.declaringClass.name}.${it.name}" }
                .map { it.apply { isAccessible = true } }
        }.getOrDefault(emptyList())

        // blkv 工厂(返回 SharedPrefX,非标准 SharedPreferences):反射调用写入下拉动画配置
        val blkvPrefsFactory = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings(".blkv")),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .firstOrNull { method ->
                    method.parameterCount == 4 &&
                        Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                        method.parameterTypes[1] == String::class.java &&
                        method.parameterTypes[2] == Boolean::class.javaPrimitiveType &&
                        method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                        method.returnType.name.contains("Shared")
                }
                ?.apply { isAccessible = true }
        }.getOrNull()

        // SharedPrefX 实现类的 get(String,Object):所有 blkv 读取的出口,
        // hook 后读 garb_load_equip_conf 强制返回自制配置,摆脱广播/缓存竞态
        val blkvGetMethods = runCatching {
            currentBridge.findClass(
                FindClass.create().matcher(ClassMatcher.create().addInterface("com.bilibili.lib.blkv.SharedPrefX")),
            ).mapNotNull { runCatching { classLoader.loadClassOrNull(it.name) }.getOrNull() }
                .flatMap { clazz ->
                    clazz.declaredMethods.asSequence()
                        .filter { method ->
                            method.name == "get" && method.parameterCount == 2 &&
                                method.parameterTypes[0] == String::class.java &&
                                !method.parameterTypes[1].isPrimitive
                        }
                        .map { it.apply { isAccessible = true } }
                        .toList()
                }.distinctBy { "${it.declaringClass.name}.${it.name}" }
        }.getOrDefault(emptyList())

        val symbols = CustomSkinSymbols(
            resolverMethod = MethodDescriptor.of(resolverMethod),
            skinResponseClassName = skinResponseClass?.name,
            skinResponseUserGarbSetter = skinResponseUserGarbSetter?.let(MethodDescriptor::of),
            skinResponseLoadEquipSetter = skinResponseLoadEquipSetter?.let(MethodDescriptor::of),
            skinResolveMethod = skinResolveMethod?.let(MethodDescriptor::of),
            videoPlayerIconGetters = videoPlayerIconGetters.map(MethodDescriptor::of),
            blkvPrefsFactory = blkvPrefsFactory?.let(MethodDescriptor::of),
            blkvGetMethods = blkvGetMethods.map(MethodDescriptor::of),
            evidence = "resolver=${resolverMethod.declaringClass.name}.${resolverMethod.name}" +
                ",userGarb=${skinResponseUserGarbSetter != null}" +
                ",loadEquip=${skinResponseLoadEquipSetter != null}" +
                ",videoPlayerIcon=${videoPlayerIconGetters.size}" +
                ",blkvFactory=${blkvPrefsFactory != null}" +
                ",blkvGets=${blkvGetMethods.size}",
        )
        return SymbolScanResult.Found(symbols, resolverMethod.declaringClass.name, symbols.evidence)
    }

    private fun scanCustomTheme(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<CustomThemeSymbols> {
        val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
        val themeStoreActivity = classLoader.loadClassOrNull(THEME_STORE_ACTIVITY)
            ?: return SymbolScanResult.Missing("ThemeStoreActivity not found")
        val skinListClass = classLoader.loadClassOrNull(BILI_SKIN_LIST)
            ?: return SymbolScanResult.Missing("BiliSkinList not found")
        val skinClass = classLoader.loadClassOrNull(BILI_SKIN)
            ?: return SymbolScanResult.Missing("BiliSkin not found")

        val colorArrayMethod = currentBridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().usingStrings("theme_entries_last_key")),
        ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .firstOrNull {
                it.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                    it.returnType == Int::class.javaPrimitiveType
            }
            ?: return SymbolScanResult.Missing("theme color array method not found")
        val themeHelperClass = colorArrayMethod.declaringClass
        val colorArrayField = themeHelperClass.declaredFields.firstOrNull {
            it.name == colorArrayMethod.name && it.type == SparseArray::class.java && Modifier.isStatic(it.modifiers)
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("theme color SparseArray not found")

        val themeColorsClass = currentBridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings("GarbThemeColors(garb=")),
        ).mapNotNull { classLoader.loadClassOrNull(it.name) }
            .firstOrNull { type -> type.declaredConstructors.any { Modifier.isPrivate(it.modifiers) } }

        val skinListMethod = themeStoreActivity.declaredMethods.firstOrNull {
            it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == skinListClass &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("theme skin list method not found")

        val themeProcessorClass = findClassNamesByNameContains(bridge, listOf("tv.danmaku.bili.ui.theme"))
            .mapNotNull(classLoader::loadClassOrNull)
            .firstOrNull { type -> type.declaredFields.count { it.type == skinListClass } > 1 }
            ?: return SymbolScanResult.Missing("theme processor class not found")
        val themeResetMethods = themeProcessorClass.declaredMethods.filter {
            it.parameterCount == 0 && it.modifiers == 0
        }.onEach { it.isAccessible = true }
        if (themeResetMethods.isEmpty()) {
            return SymbolScanResult.Missing("theme reset methods not found")
        }

        val themeListClickClass = themeStoreActivity.declaredClasses.firstOrNull {
            it.interfaces.contains(View.OnClickListener::class.java)
        } ?: return SymbolScanResult.Missing("theme list click listener not found")

        val themeNameClass = findThemeNameClass(classLoader, currentBridge)
            ?: return SymbolScanResult.Missing("theme name map class not found")
        val themeNameField = themeNameClass.declaredFields.firstOrNull {
            it.type == Map::class.java && Modifier.isStatic(it.modifiers)
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("theme name map field not found")

        val builtInThemeCandidate = findClassNamesByNameContains(bridge, listOf("theme", "garb"))
            .mapNotNull(classLoader::loadClassOrNull)
            .firstNotNullOfOrNull { type ->
                type.declaredFields.firstOrNull {
                    it.type == Map::class.java && Modifier.isStatic(it.modifiers)
                }?.apply { isAccessible = true }?.let { type to it }
            }

        // Mirror BiliRoamingX's response replacement at Bilibili's parsed model boundary.
        // This narrows the hook to skin data instead of intercepting every OkHttp response.
        val skinResponseClass = runCatching {
            currentBridge.findClass(
                FindClass.create().matcher(ClassMatcher.create().usingStrings("user_equip")),
            ).mapNotNull { classLoader.loadClassOrNull(it.name) }
                .firstOrNull { type ->
                    type.declaredMethods.any { it.name == "setUserGarb" && it.parameterCount == 1 }
                }
        }.getOrNull()
        val skinResponseUserGarbSetter = skinResponseClass?.declaredMethods?.firstOrNull {
            it.name == "setUserGarb" && it.parameterCount == 1
        }?.apply { isAccessible = true }
        val skinResponseLoadEquipSetter = skinResponseClass?.declaredMethods?.firstOrNull {
            it.name == "setLoadEquip" && it.parameterCount == 1
        }?.apply { isAccessible = true }
        val skinResolveMethod = runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings("shouldApplyForceOpGarb =")),
            ).mapNotNull { it.getMethodInstance(classLoader) }
                .firstOrNull { it.parameterCount == 1 && it.returnType != Void.TYPE }
                ?.apply { isAccessible = true }
        }.getOrNull()

        val symbols = CustomThemeSymbols(
            themeHelperClassName = themeHelperClass.name,
            themeHelperColorArray = FieldDescriptor.of(colorArrayField),
            themeNameClassName = themeNameClass.name,
            themeNameField = FieldDescriptor.of(themeNameField),
            builtInThemesClassName = builtInThemeCandidate?.first?.name,
            builtInThemesField = builtInThemeCandidate?.second?.let(FieldDescriptor::of),
            themeColorsClassName = themeColorsClass?.name,
            skinListMethod = MethodDescriptor.of(skinListMethod),
            themeListClickClassName = themeListClickClass.name,
            skinClassName = skinClass.name,
            themeProcessorClassName = themeProcessorClass.name,
            themeResetMethods = themeResetMethods.map(MethodDescriptor::of),
            evidence = "helper=${themeHelperClass.name},processor=${themeProcessorClass.name},reset=${themeResetMethods.size},builtIn=${builtInThemeCandidate != null}",
            skinResponseClassName = skinResponseClass?.name,
            skinResponseUserGarbSetter = skinResponseUserGarbSetter?.let(MethodDescriptor::of),
            skinResponseLoadEquipSetter = skinResponseLoadEquipSetter?.let(MethodDescriptor::of),
            skinResolveMethod = skinResolveMethod?.let(MethodDescriptor::of),
        )
        return SymbolScanResult.Found(symbols, themeStoreActivity.name, symbols.evidence)
    }

    private fun findThemeNameClass(classLoader: ClassLoader, bridge: DexKitBridge): Class<*>? {
        val candidates = bridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings(".garb.GARB_CHANGE")),
        ).map { it.name }.toList() + bridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings("white")),
        ).map { it.name }.toList()
        return candidates.asSequence().distinct().mapNotNull(classLoader::loadClassOrNull)
            .firstOrNull { type ->
                type.declaredFields.any { Modifier.isStatic(it.modifiers) && it.type == Map::class.java }
            }
    }

    private fun scanBlockUpdate(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<BlockUpdateSymbols> {
        val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
        val searchErrors = ArrayList<String>()
        val signatureMethodData = runCatching {
            currentBridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .returnType(BILI_UPGRADE_INFO)
                            .paramTypes(Context::class.java),
                    ),
            )
        }.getOrElse { throwable ->
            searchErrors += "signature Context->BiliUpgradeInfo: ${throwable.scanMessage()}"
            emptyList()
        }
        val stringMethodData = BLOCK_UPDATE_STRING_RULES.asSequence()
            .mapNotNull { strings ->
                runCatching {
                    currentBridge.findMethod(
                        FindMethod.create()
                            .matcher(
                                strings.fold(
                                    MethodMatcher.create()
                                        .paramTypes(Context::class.java),
                                ) { matcher, string -> matcher.usingStrings(string) },
                            ),
                    )
                }.getOrElse { throwable ->
                    searchErrors += strings.joinToString("+") + ": " + throwable.scanMessage()
                    null
                }
            }
            .flatMap { it.asSequence() }
            .distinctBy { it.descriptor }
            .toList()
        if (signatureMethodData.isEmpty() && stringMethodData.isEmpty() && searchErrors.isNotEmpty()) {
            return SymbolScanResult.Missing("update check method search failed: ${searchErrors.joinToString("; ")}")
        }
        val restoreErrors = ArrayList<String>()
        val stringCandidates = stringMethodData
            .mapNotNull { data ->
                runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                    restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                    null
                }
            }
            .filter { it.isBlockUpdateCheckCandidate() }
            .distinctBy(Method::toGenericString)
            .toList()
        val stringCandidateKeys = stringCandidates.map(Method::toGenericString).toSet()
        val candidates = (signatureMethodData.asSequence() + stringMethodData.asSequence())
            .distinctBy { it.descriptor }
            .mapNotNull { data ->
                runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                    restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                    null
                }
            }
            .filter { it.isBlockUpdateCheckCandidate() }
            .distinctBy(Method::toGenericString)
            .toList()
        val supplierCandidates = candidates
            .mapNotNull { method ->
                method.findBlockUpdateSupplierInterface()?.let { supplierInterface ->
                    method to supplierInterface
                }
            }
        val leafSupplierCandidates = supplierCandidates
            .filterNot { (method, supplierInterface) ->
                method.declaringClass.hasBlockUpdateDelegateField(supplierInterface)
            }
        val method = when {
            stringCandidates.size == 1 -> stringCandidates.single()
            leafSupplierCandidates.size == 1 -> leafSupplierCandidates.single().first
            supplierCandidates.size == 1 -> supplierCandidates.single().first
            candidates.size == 1 -> candidates.single()
            candidates.isEmpty() -> {
                val restoreHint = restoreErrors.take(2).joinToString("; ").takeIf { it.isNotBlank() }
                return SymbolScanResult.Missing(
                    buildString {
                        append("update check method not found")
                        append(": signature=${signatureMethodData.size},string=${stringMethodData.size}")
                        append(",restored=0")
                        if (restoreHint != null) append(",restore=$restoreHint")
                    },
                )
            }
            else -> return SymbolScanResult.Missing(
                "update check method ambiguous: " +
                    "candidates=${candidates.size},supplier=${supplierCandidates.size},leaf=${leafSupplierCandidates.size}," +
                    candidates.joinToString(limit = 4) { it.declaringClass.name },
            )
        }
        val symbols = BlockUpdateSymbols(
            checkMethod = MethodDescriptor.of(method),
            evidence = "${method.declaringClass.name}.${method.name}," +
                "signature=${signatureMethodData.size},string=${stringMethodData.size}," +
                "stringHit=${method.toGenericString() in stringCandidateKeys}",
        )
        return SymbolScanResult.Found(symbols, symbols.evidence, symbols.evidence)
    }

    private fun Method.isBlockUpdateCheckCandidate(): Boolean {
        if (parameterTypes.contentEquals(arrayOf(Context::class.java)).not()) return false
        if (returnType.name != BILI_UPGRADE_INFO) return false
        if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) return false
        if (declaringClass.isInterface || Modifier.isAbstract(declaringClass.modifiers)) return false
        return true
    }

    private fun Method.findBlockUpdateSupplierInterface(): Class<*>? =
        declaringClass.interfaces.firstOrNull { candidate ->
            candidate.allMethods().any { method ->
                method.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                    method.returnType.name == BILI_UPGRADE_INFO
            }
        }

    private fun Class<*>.hasBlockUpdateDelegateField(supplierInterface: Class<*>): Boolean =
        allFields().any { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type != this &&
                supplierInterface.isAssignableFrom(field.type)
        }

    private fun scanTripleSpeed(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<TripleSpeedSymbols> {
        val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
        val methodData = runCatching {
            currentBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings(PLAY_SPEED_EXPERIMENT_PREF_KEY)),
            )
        }.getOrElse { throwable ->
            return SymbolScanResult.Missing("play speed experiment search failed: ${throwable.scanMessage()}")
        }
        val qualityResetData = runCatching {
            currentBridge.findMethod(
                FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings(HIGH_FRAME_RATE_SPEED_RESET_LOG)),
            )
        }.getOrElse { throwable ->
            return SymbolScanResult.Missing("high-frame-rate speed reset search failed: ${throwable.scanMessage()}")
        }
        val restoreErrors = ArrayList<String>()
        // The reader lives in a synthetic kotlin Function0 whose zero-arg invoke() maps the
        // "sp_play_speed_experiment" preference int onto LongPressSpeedExperiment. The other match
        // is the A/B writer (invokeSuspend, 1 parameter), which we exclude by shape.
        val candidates = methodData
            .mapNotNull { data ->
                runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                    restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                    null
                }
            }
            .filter { it.name == "invoke" && it.parameterCount == 0 && it.returnType == Any::class.java }
            .distinctBy(Method::toGenericString)
            .toList()
        val reader = when {
            candidates.size == 1 -> candidates.single()
            candidates.isEmpty() -> return SymbolScanResult.Missing(
                "play speed experiment reader not found: strings=${methodData.size}" +
                    (restoreErrors.take(2).joinToString("; ").takeIf { it.isNotBlank() }?.let { ",restore=$it" } ?: ""),
            )
            else -> return SymbolScanResult.Missing(
                "play speed experiment reader ambiguous: candidates=${candidates.size}," +
                    candidates.joinToString(limit = 4) { it.declaringClass.name },
            )
        }
        val qualityResetCandidates = qualityResetData
            .mapNotNull { data ->
                runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                    restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                    null
                }
            }
            .filter {
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                    it.returnType == Void.TYPE
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val qualityReset = when {
            qualityResetCandidates.size == 1 -> qualityResetCandidates.single()
            qualityResetCandidates.isEmpty() -> null
            else -> null
        }
        val highFrameRateSpeedGuard = classLoader.loadClassOrNull(PLAY_SPEED_UTILS_CLASS)
            ?.declaredMethods
            ?.firstOrNull {
                Modifier.isStatic(it.modifiers) &&
                    it.name == "c" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }
            ?.apply { isAccessible = true }
        val symbols = TripleSpeedSymbols(
            experimentReaderMethod = MethodDescriptor.of(reader),
            qualitySpeedResetMethod = qualityReset?.let(MethodDescriptor::of),
            highFrameRateSpeedGuardMethod = highFrameRateSpeedGuard?.let(MethodDescriptor::of),
            evidence = "${reader.declaringClass.name}.${reader.name},strings=${methodData.size}," +
                "qualityReset=${qualityReset?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}," +
                "qualityStrings=${qualityResetData.size}," +
                "highFrameGuard=${highFrameRateSpeedGuard?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}",
        )
        return SymbolScanResult.Found(symbols, symbols.evidence, symbols.evidence)
    }

    private fun scanMineProfile(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<MineProfileSymbols> {
        val fragmentClass = (
            MINE_FRAGMENT_CLASS_NAMES.asSequence() +
                findClassNamesBySimpleName(bridge, "HomeUserCenterFragment").asSequence()
            )
            .distinct()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .firstOrNull { type ->
                type.declaredMethods.any { method -> method.name == "onResume" && method.parameterCount == 0 }
            }
            ?: return SymbolScanResult.Missing("HomeUserCenterFragment not verified")

        val vipViewClass = (
            MINE_VIP_VIEW_CLASS_NAMES.asSequence() +
                findClassNamesBySimpleName(bridge, "MineVipEntranceView").asSequence() +
                findClassNamesBySimpleName(bridge, "VipEntranceView").asSequence()
            )
            .distinct()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .firstOrNull()

        val onResume = fragmentClass.declaredMethods.firstOrNull { method ->
            method.name == "onResume" && method.parameterCount == 0
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("onResume not found")

        if (vipViewClass != null) {
            val vipField = fragmentClass.declaredFields.firstOrNull { field ->
                vipViewClass.isAssignableFrom(field.type)
            }?.apply { isAccessible = true }
                ?: return SymbolScanResult.Missing("vip view field not found")

            val symbols = MineProfileSymbols(
                fragmentClassName = fragmentClass.name,
                vipViewClassName = vipViewClass.name,
                vipField = FieldDescriptor.of(vipField),
                onResume = MethodDescriptor.of(onResume),
                evidence = "fragment=${fragmentClass.name},vip=${vipViewClass.name},field=${vipField.name}",
            )
            return SymbolScanResult.Found(symbols, "${fragmentClass.name}.${onResume.name}", symbols.evidence)
        }

        val managerClass = MINE_VIP_MANAGER_CLASS_NAMES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
            ?: return SymbolScanResult.Missing("mine vip view or manager class not found")
        val viewBindingClass = classLoader.loadClassOrNull("androidx.viewbinding.ViewBinding")
            ?: return SymbolScanResult.Missing("ViewBinding class not found")
        val managerField = fragmentClass.declaredFields.firstOrNull { field ->
            managerClass.isAssignableFrom(field.type)
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("mine vip manager field not found")
        val bindingField = managerClass.declaredFields.firstOrNull { field ->
            viewBindingClass.isAssignableFrom(field.type)
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("mine vip manager binding field not found")
        val rootField = bindingField.type.declaredFields.firstOrNull { field ->
            View::class.java.isAssignableFrom(field.type)
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("mine vip binding root view field not found")

        val symbols = MineProfileSymbols(
            fragmentClassName = fragmentClass.name,
            vipViewClassName = managerClass.name,
            vipField = FieldDescriptor.of(managerField),
            managerBindingField = FieldDescriptor.of(bindingField),
            bindingRootField = FieldDescriptor.of(rootField),
            onResume = MethodDescriptor.of(onResume),
            evidence = "fragment=${fragmentClass.name},manager=${managerClass.name},field=${managerField.name},root=${rootField.name}",
        )
        return SymbolScanResult.Found(symbols, "${fragmentClass.name}.${onResume.name}", symbols.evidence)
    }

    private fun scanDownloadThreadListeners(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<List<DownloadThreadListenerSymbols>> {
        val classNames = findClassNamesByNameContains(
            bridge = bridge,
            terms = DOWNLOAD_THREAD_LISTENER_CLASS_TERMS,
        )
        val listeners = classNames
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .mapNotNull { type -> type.toDownloadThreadListenerSymbols() }
            .distinctBy { it.className }
            .toList()

        if (listeners.isEmpty()) {
            return SymbolScanResult.Missing("download thread listener class not verified")
        }
        return SymbolScanResult.Found(
            listeners,
            listeners.joinToString("|") { it.className },
            "dexkitClassName+TextViewCtor+onClick+TextViewField",
        )
    }

    private fun scanDownloadThreadReportMethod(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<MethodDescriptor> {
        val classNames = findClassNamesByNameContains(
            bridge = bridge,
            terms = DOWNLOAD_THREAD_REPORT_CLASS_TERMS,
        )
        val methods = classNames
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .flatMap { type -> type.allMethods() }
            .filter { method ->
                method.name == "reportDownloadThread" &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0].name == "android.content.Context" &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    !method.returnType.isPrimitive &&
                    CharSequence::class.java.isAssignableFrom(method.returnType)
            }
            .distinctBy { it.declaringClass.name + "#" + it.name + it.parameterTypes.joinToString { type -> type.name } }
            .toList()

        if (methods.size != 1) {
            return SymbolScanResult.Missing("reportDownloadThread method candidates=${methods.size}")
        }
        return SymbolScanResult.Found(
            MethodDescriptor.of(methods.single()),
            methods.single().toGenericString(),
            "dexkitClassName+exactSignature",
        )
    }

    private fun Class<*>.toDownloadThreadListenerSymbols(): DownloadThreadListenerSymbols? {
        if (isInterface || Modifier.isAbstract(modifiers)) return null
        val constructor = declaredConstructors.firstOrNull { ctor ->
            ctor.parameterTypes.any { TextView::class.java.isAssignableFrom(it) }
        }?.apply { isAccessible = true } ?: return null

        val onClick = allMethods().firstOrNull { method ->
            method.name == "onClick" &&
                method.parameterCount == 1 &&
                View::class.java.isAssignableFrom(method.parameterTypes[0])
        } ?: return null

        val textViewField = allFields().firstOrNull { field ->
            TextView::class.java.isAssignableFrom(field.type)
        } ?: return null

        return DownloadThreadListenerSymbols(
            className = name,
            constructor = ConstructorDescriptor.of(constructor),
            onClick = MethodDescriptor.of(onClick),
            textViewField = FieldDescriptor.of(textViewField),
        )
    }

    private fun Class<*>.isKotlinContinuationTypeName(): Boolean =
        name == "kotlin.coroutines.Continuation" ||
            name == "kotlin.coroutines.jvm.internal.ContinuationImpl"

    private fun childHookPoint(id: String, found: Boolean, missing: String, evidence: String = "-"): HookPointStatus =
        if (found) {
            HookPointStatus.found(id, id.substringAfterLast('.'), evidence)
        } else {
            HookPointStatus.missing(id, "fail closed: $missing")
        }

    private fun optionalChildHookPoint(id: String, found: Boolean, missing: String, evidence: String = "-"): HookPointStatus =
        if (found) {
            HookPointStatus.found(id, id.substringAfterLast('.'), evidence)
        } else {
            HookPointStatus.optional(id, missing)
        }

    private fun skipVideoAdControllerHookPoint(
        id: String,
        scan: ControllerClassScan,
        ready: Boolean,
        missing: String,
        evidence: String,
    ): HookPointStatus {
        if (ready) {
            val scanEvidence = scan.error?.let { "$evidence,scanError=${it.take(180)}" } ?: evidence
            return HookPointStatus.found(id, id.substringAfterLast('.'), scanEvidence)
        }
        return scan.error?.let { HookPointStatus.error(id, "fail closed: ${it.take(240)}") }
            ?: HookPointStatus.optional(id, missing)
    }

    private fun storySkipVideoAdHookPoint(
        ready: Boolean,
        classFound: Boolean,
        scanError: String?,
        evidence: String,
    ): HookPointStatus {
        if (ready) return HookPointStatus.found(HP_SKIP_VIDEO_AD_STORY, "StoryPlayer", evidence)
        scanError?.let { return HookPointStatus.error(HP_SKIP_VIDEO_AD_STORY, "fail closed: ${it.take(240)}") }
        val missing = if (classFound) {
            "story player position/seek methods not found"
        } else {
            "story pager player class not found"
        }
        return HookPointStatus.optional(HP_SKIP_VIDEO_AD_STORY, missing)
    }

    private fun Class<*>.findMethod(name: String, returnType: Class<*>, vararg parameterTypes: Class<*>): Method? =
        allMethods().firstOrNull {
            it.name == name &&
                it.returnType == returnType &&
                it.parameterTypes.contentEquals(parameterTypes)
        }

    private fun Class<*>.findNoArgMethod(name: String): Method? =
        allMethods().firstOrNull { it.name == name && it.parameterCount == 0 }

    private fun Class<*>.findNoArgStringMethod(name: String): Method? =
        findMethod(name, String::class.java)

    private fun Class<*>.findStringSetter(name: String): Method? =
        findMethod(name, Void.TYPE, String::class.java)

    private fun Class<*>.findIntegerSetter(name: String): Method? =
        findMethod(name, Void.TYPE, java.lang.Integer::class.java)

    private fun Class<*>?.copyLikeMethods(): List<Method> {
        val type = this ?: return emptyList()
        return type.allMethods()
            .filter {
                !Modifier.isStatic(it.modifiers) &&
                    it.returnType == type &&
                    it.parameterCount >= 2
            }
            .toList()
    }

    private fun findShareClickResultClass(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): ClassStringScan {
        return findClassByString(classLoader, bridge, SHARE_CLICK_RESULT_DESCRIPTOR) { type ->
            type.isShareClickResultType()
        }
    }

    private fun findShareBaseInfoClass(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): ClassStringScan {
        return findClassByString(classLoader, bridge, SHARE_BASE_INFO_TO_STRING) { type ->
            type.isShareBaseInfoType()
        }
    }

    private fun findClassByString(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
        string: String,
        validator: (Class<*>) -> Boolean,
    ): ClassStringScan {
        val currentBridge = bridge() ?: return ClassStringScan(null, error = "DexKitBridge unavailable")
        val names = runCatching {
            currentBridge.findClass(
                FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings(string)),
            ).map { it.name }.toList()
        }.getOrElse { throwable ->
            return ClassStringScan(null, error = "DexKit string search failed: ${throwable.scanMessage()}")
        }
        val candidates = names
            .asSequence()
            .flatMap { name -> sequenceOf(name, name.substringBefore('$')).distinct() }
            .mapNotNull { name -> classLoader.loadClassOrNull(name) }
            .distinctBy { it.name }
            .toList()
        val validated = candidates.filter(validator)
        return when (validated.size) {
            1 -> ClassStringScan(type = validated.single(), candidates = candidates.size)
            0 -> ClassStringScan(null, error = "validated class not found", candidates = candidates.size)
            else -> ClassStringScan(
                null,
                error = "ambiguous classes=${validated.joinToString { it.name }.take(180)}",
                candidates = candidates.size,
            )
        }
    }

    private fun Class<*>.isShareClickResultType(): Boolean =
        declaredConstructors.any { it.isShareClickResultConstructor() }

    private fun java.lang.reflect.Constructor<*>.isShareClickResultConstructor(): Boolean {
        val params = parameterTypes
        if (params.size != 13) return false
        return params[0] == Int::class.javaPrimitiveType &&
            params[1] == java.lang.Integer::class.java &&
            params[2] == String::class.java &&
            params[3] == String::class.java &&
            params[4] == String::class.java &&
            params[5] == String::class.java &&
            params[6] == String::class.java &&
            params[7] == java.lang.Integer::class.java &&
            params[8] == String::class.java &&
            params[9] == String::class.java &&
            params[10] == String::class.java &&
            params[11] == String::class.java &&
            params[12] == java.lang.Boolean::class.java
    }

    private fun Class<*>.isShareBaseInfoType(): Boolean =
        declaredConstructors.any { it.isShareBaseInfoConstructor() }

    private fun java.lang.reflect.Constructor<*>.isShareBaseInfoConstructor(): Boolean =
        parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java, String::class.java))

    private fun Class<*>?.installWeight(vararg methods: Method?): Int =
        if (this == null) 0 else declaredConstructors.size + methods.count { it != null }

    private fun Class<*>?.installWeight(methods: List<Method>, vararg moreMethods: Method?): Int =
        if (this == null) 0 else declaredConstructors.size + methods.size + moreMethods.count { it != null }

    private fun scanStoryPlayerAd(
        classLoader: ClassLoader,
    ): SymbolScanResult<StoryPlayerAdSymbols> {
        val storyFeedResponse = classLoader.loadClassOrNull(STORY_FEED_RESPONSE)
        val feedGetItems = storyFeedResponse?.declaredMethods?.firstOrNull {
            it.name == "getItems" &&
                it.parameterCount == 0 &&
                List::class.java.isAssignableFrom(it.returnType) &&
                !Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers)
        }?.apply { isAccessible = true }

        val storyPagerPlayer = classLoader.loadClassOrNull(STORY_PAGER_PLAYER)
        val pagerMethods = storyPagerPlayer?.declaredMethods
            ?.filter(::isStoryPagerListMethod)
            ?.distinctBy(Method::toGenericString)
            .orEmpty()

        val rerankTask = classLoader.loadClassOrNull(STORY_AD_RERANK_TASK)
        val rerankInvoke = rerankTask?.declaredMethods?.firstOrNull {
            it.name == "invokeSuspend" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == Any::class.java &&
                it.returnType == Any::class.java &&
                !Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers)
        }?.apply { isAccessible = true }
        val unitField = classLoader.loadClassOrNull(KOTLIN_UNIT)
            ?.getDeclaredField("INSTANCE")
            ?.apply { isAccessible = true }

        if (feedGetItems == null && pagerMethods.isEmpty() && (rerankInvoke == null || unitField == null)) {
            return SymbolScanResult.Missing("story player ad hook points not found")
        }

        val symbols = StoryPlayerAdSymbols(
            feedGetItems = feedGetItems?.let(MethodDescriptor::of),
            pagerListMethods = pagerMethods.map(MethodDescriptor::of),
            rerankInvokeSuspend = rerankInvoke?.let(MethodDescriptor::of),
            kotlinUnitField = if (rerankInvoke != null) unitField?.let(FieldDescriptor::of) else null,
            evidence = "feed=${feedGetItems != null},pager=${pagerMethods.size},rerank=${rerankInvoke != null && unitField != null}",
        )
        val hookPoints = listOf(
            childHookPoint(HP_STORY_PLAYER_AD_FEED, feedGetItems != null, "feed getItems not found", "method=${feedGetItems?.name}"),
            childHookPoint(HP_STORY_PLAYER_AD_PAGER, pagerMethods.isNotEmpty(), "pager list method not found", "methods=${pagerMethods.size}"),
            childHookPoint(
                HP_STORY_PLAYER_AD_RERANK,
                rerankInvoke != null && unitField != null,
                "rerank invoke/unit field not found",
                "invoke=${rerankInvoke != null},unit=${unitField != null}",
            ),
        )
        return SymbolScanResult.Found(symbols, "StoryPlayerAd", symbols.evidence, hookPoints)
    }

    private fun scanStoryFullscreen(
        classLoader: ClassLoader,
    ): SymbolScanResult<StoryFullscreenSymbols> {
        val storyActivity = classLoader.loadClassOrNull(STORY_VIDEO_ACTIVITY)
            ?: return SymbolScanResult.Missing("story video activity class not found")
        val onCreate = storyActivity.findMethod("onCreate", Void.TYPE, Bundle::class.java)
            ?.apply { isAccessible = true }
        val onWindowFocusChanged = storyActivity.findMethod(
            "onWindowFocusChanged",
            Void.TYPE,
            Boolean::class.javaPrimitiveType!!,
        )?.takeIf { it.declaringClass.name == STORY_VIDEO_ACTIVITY }
            ?.apply { isAccessible = true }
        val onResume = storyActivity.findMethod("onResume", Void.TYPE)
            ?.takeIf { it.declaringClass.name == STORY_VIDEO_ACTIVITY }
            ?.apply { isAccessible = true }

        if (onCreate == null || onWindowFocusChanged == null) {
            return SymbolScanResult.Missing(
                "story fullscreen lifecycle methods not found: " +
                    "onCreate=${onCreate != null},focus=${onWindowFocusChanged != null}",
            )
        }

        val symbols = StoryFullscreenSymbols(
            onCreate = MethodDescriptor.of(onCreate),
            onWindowFocusChanged = MethodDescriptor.of(onWindowFocusChanged),
            onResume = onResume?.let(MethodDescriptor::of),
            evidence = "onCreate=${onCreate.declaringClass.name},focus=${onWindowFocusChanged.declaringClass.name},onResume=${onResume != null}",
        )
        val hookPoints = listOf(
            childHookPoint(HP_STORY_FULLSCREEN_ON_CREATE, true, "onCreate not found", "method=${onCreate.name}"),
            childHookPoint(HP_STORY_FULLSCREEN_FOCUS, true, "onWindowFocusChanged not found", "method=${onWindowFocusChanged.name}"),
            optionalChildHookPoint(HP_STORY_FULLSCREEN_ON_RESUME, onResume != null, "onResume not declared by StoryVideoActivity", "method=${onResume?.name}"),
        )
        return SymbolScanResult.Found(
            symbols,
            STORY_VIDEO_ACTIVITY,
            symbols.evidence,
            hookPoints,
        )
    }

    private fun scanStoryDanmaku(
        classLoader: ClassLoader,
    ): SymbolScanResult<StoryDanmakuSymbols> {
        val storyDetail = classLoader.loadClassOrNull(STORY_DETAIL)
            ?: return SymbolScanResult.Missing("story detail class not found")
        val storyPagerPlayer = classLoader.loadClassOrNull(STORY_PAGER_PLAYER)
            ?: return SymbolScanResult.Missing("story pager player class not found")
        val interactLayerService = classLoader.loadClassOrNull(INTERACT_LAYER_SERVICE)
            ?: return SymbolScanResult.Missing("interact layer service class not found")
        val introCommentService = classLoader.loadClassOrNull(STORY_INTRO_COMMENT_SERVICE)

        var matchedSignature: Array<Class<*>>? = null
        var matchedVerticalContainer: Class<*>? = null
        var matchedLandscapeContainer: Class<*>? = null

        for (name in STORY_COMMENT_OUTER_CANDIDATES) {
            val prefix = "com.bilibili.video.story.action.$name"
            val containerInterface = classLoader.loadClassOrNull("$prefix\$b") ?: continue
            val sig = containerInterface.allMethods().firstOrNull { method ->
                method.name == "a" &&
                    method.returnType == Void.TYPE &&
                    method.parameterCount == 8 &&
                    method.parameterTypes[0] == storyDetail &&
                    method.parameterTypes[2] == Long::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Long::class.javaPrimitiveType &&
                    method.parameterTypes[4] == String::class.java
            }?.parameterTypes ?: continue

            matchedSignature = sig
            matchedVerticalContainer = classLoader.loadClassOrNull("$prefix\$VerticalContainerV2")
                ?: classLoader.loadClassOrNull("$prefix\$f")
            matchedLandscapeContainer = classLoader.loadClassOrNull("$prefix\$d")
            break
        }

        val showSignature = matchedSignature ?: return SymbolScanResult.Missing("story comment show signature not found")
        val verticalContainer = matchedVerticalContainer
            ?: STORY_COMMENT_VERTICAL_CONTAINER_CLASSES.firstNotNullOfOrNull(classLoader::loadClassOrNull)
            ?: return SymbolScanResult.Missing("story vertical comment container class not found")
        val landscapeContainer = matchedLandscapeContainer
            ?: STORY_COMMENT_LANDSCAPE_CONTAINER_CLASSES.firstNotNullOfOrNull(classLoader::loadClassOrNull)

        val showMethods = buildList {
            verticalContainer.findMethod("a", Void.TYPE, *showSignature)
                ?.apply { isAccessible = true }
                ?.let(::add)
            landscapeContainer?.findMethod("a", Void.TYPE, *showSignature)
                ?.apply { isAccessible = true }
                ?.let(::add)
        }
        val hideMethods = buildList {
            verticalContainer.findMethod("c", Void.TYPE)
                ?.apply { isAccessible = true }
                ?.let(::add)
            landscapeContainer?.findMethod("c", Void.TYPE)
                ?.apply { isAccessible = true }
                ?.let(::add)
        }
        val introCommentShow = introCommentService
            ?.allMethods()
            ?.singleOrNull { method ->
                method.name == "b" &&
                    method.returnType == Void.TYPE &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0].name.startsWith(STORY_TAB_PACKAGE_PREFIX)
            }
            ?.apply { isAccessible = true }
        val introCommentDismiss = introCommentService?.findMethod("a", Void.TYPE)
            ?.apply { isAccessible = true }
        val setDanmakuOpacity = interactLayerService.findMethod(
            "setDanmakuOpacity",
            Void.TYPE,
            Float::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
        )?.apply { isAccessible = true }
        val updateCanvasCandidates = storyPagerPlayer.allMethods().filter { method ->
            method.returnType == Void.TYPE &&
                method.declaringClass.name == STORY_PAGER_PLAYER &&
                method.parameterTypes.map { it.name } == listOf("int", "int", "int")
        }.toList()
        val updateCanvas = updateCanvasCandidates.singleOrNull()?.apply { isAccessible = true }

        if (showMethods.isEmpty() || hideMethods.isEmpty() || setDanmakuOpacity == null || updateCanvas == null) {
            return SymbolScanResult.Missing(
                "story danmaku hook points not found: " +
                    "show=${showMethods.size},hide=${hideMethods.size}," +
                    "setOpacity=${setDanmakuOpacity != null},updateCanvas=${updateCanvas != null}",
            )
        }

        val symbols = StoryDanmakuSymbols(
            commentShowMethods = showMethods.map(MethodDescriptor::of),
            commentHideMethods = hideMethods.map(MethodDescriptor::of),
            introCommentShowMethod = introCommentShow?.let(MethodDescriptor::of),
            introCommentDismissMethod = introCommentDismiss?.let(MethodDescriptor::of),
            setDanmakuOpacity = MethodDescriptor.of(setDanmakuOpacity),
            updateCanvas = MethodDescriptor.of(updateCanvas),
            evidence = "show=${showMethods.size},hide=${hideMethods.size}," +
                "introShow=${introCommentShow != null},introDismiss=${introCommentDismiss != null}," +
                "setOpacity=true,updateCanvas=true",
        )
        val hookPoints = listOf(
            childHookPoint(HP_STORY_DANMAKU_COMMENT_SHOW, showMethods.isNotEmpty(), "comment show method not found", "methods=${showMethods.size}"),
            childHookPoint(HP_STORY_DANMAKU_COMMENT_HIDE, hideMethods.isNotEmpty(), "comment hide method not found", "methods=${hideMethods.size}"),
            optionalChildHookPoint(
                HP_STORY_DANMAKU_INTRO_COMMENT_SHOW,
                introCommentShow != null,
                "intro comment show method not found",
                "method=${introCommentShow?.name}",
            ),
            optionalChildHookPoint(
                HP_STORY_DANMAKU_INTRO_COMMENT_DISMISS,
                introCommentDismiss != null,
                "intro comment dismiss method not found",
                "method=${introCommentDismiss?.name}",
            ),
            childHookPoint(HP_STORY_DANMAKU_SET_OPACITY, true, "setDanmakuOpacity method not found", "method=${setDanmakuOpacity.name}"),
            childHookPoint(
                HP_STORY_DANMAKU_UPDATE_CANVAS,
                true,
                "story updateCanvas method not unique: candidates=${updateCanvasCandidates.size}",
                "method=${updateCanvas.name}",
            ),
        )
        return SymbolScanResult.Found(
            symbols,
            "StoryDanmaku",
            symbols.evidence,
            hookPoints,
        )
    }

    private fun scanStoryComponentAlpha(
        classLoader: ClassLoader,
    ): SymbolScanResult<StoryComponentAlphaSymbols> {
        val infoModule = classLoader.loadClassOrNull(STORY_INFO_MODULE)
            ?: return SymbolScanResult.Missing("story info module class not found")
        val rightModule = classLoader.loadClassOrNull(STORY_RIGHT_MODULE)
            ?: return SymbolScanResult.Missing("story right module class not found")
        val bottomModule = classLoader.loadClassOrNull(STORY_BOTTOM_MODULE)
            ?: return SymbolScanResult.Missing("story bottom module class not found")
        val storySeekBar = classLoader.loadClassOrNull(STORY_SEEK_BAR_CLASS)
            ?: return SymbolScanResult.Missing("story seek bar class not found")
        val storyFragment = classLoader.loadClassOrNull(STORY_VIDEO_FRAGMENT)
            ?: return SymbolScanResult.Missing("story video fragment class not found")

        val infoConstructors = infoModule.storyModuleConstructors()
        val rightConstructors = rightModule.storyModuleConstructors()
        val bottomConstructors = bottomModule.storyModuleConstructors()
        val seekBarConstructors = storySeekBar.storyViewConstructors()
        val onCreateView = storyFragment.findMethod(
            "onCreateView",
            View::class.java,
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java,
        )?.takeIf { it.declaringClass.name == STORY_VIDEO_FRAGMENT }
            ?.apply { isAccessible = true }

        if (
            infoConstructors.isEmpty() ||
            rightConstructors.isEmpty() ||
            bottomConstructors.isEmpty() ||
            seekBarConstructors.isEmpty() ||
            onCreateView == null
        ) {
            return SymbolScanResult.Missing(
                "story component alpha hook points not found: " +
                    "info=${infoConstructors.size},right=${rightConstructors.size},bottom=${bottomConstructors.size}," +
                    "seekBar=${seekBarConstructors.size},top=${onCreateView != null}",
            )
        }

        val symbols = StoryComponentAlphaSymbols(
            infoConstructors = infoConstructors.map(ConstructorDescriptor::of),
            rightConstructors = rightConstructors.map(ConstructorDescriptor::of),
            bottomConstructors = bottomConstructors.map(ConstructorDescriptor::of),
            seekBarConstructors = seekBarConstructors.map(ConstructorDescriptor::of),
            fragmentOnCreateView = MethodDescriptor.of(onCreateView),
            evidence = "info=${infoConstructors.size},right=${rightConstructors.size},bottom=${bottomConstructors.size}," +
                "seekBar=${seekBarConstructors.size},top=${onCreateView.name}",
        )
        val hookPoints = listOf(
            childHookPoint(
                HP_STORY_COMPONENT_ALPHA_INFO,
                infoConstructors.isNotEmpty(),
                "story info constructors not found",
                "constructors=${infoConstructors.size}",
            ),
            childHookPoint(
                HP_STORY_COMPONENT_ALPHA_RIGHT,
                rightConstructors.isNotEmpty(),
                "story right constructors not found",
                "constructors=${rightConstructors.size}",
            ),
            childHookPoint(
                HP_STORY_COMPONENT_ALPHA_BOTTOM,
                bottomConstructors.isNotEmpty(),
                "story bottom constructors not found",
                "constructors=${bottomConstructors.size}",
            ),
            childHookPoint(
                HP_STORY_COMPONENT_ALPHA_SEEKBAR,
                seekBarConstructors.isNotEmpty(),
                "story seek bar constructors not found",
                "constructors=${seekBarConstructors.size}",
            ),
            childHookPoint(
                HP_STORY_COMPONENT_ALPHA_TOP,
                true,
                "story fragment onCreateView not found",
                "method=${onCreateView.name}",
            ),
        )
        return SymbolScanResult.Found(
            symbols,
            "StoryComponentAlpha",
            symbols.evidence,
            hookPoints,
        )
    }

    private fun isStoryPagerListMethod(method: Method): Boolean =
        method.returnType == Void.TYPE &&
            method.declaringClass.name == STORY_PAGER_PLAYER &&
            !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers) &&
            !method.isSynthetic &&
            !method.isBridge &&
            method.parameterCount == 1 &&
            List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
            isStoryDetailListParameter(method)

    private fun isStoryDetailListParameter(method: Method): Boolean {
        val parameterType = method.genericParameterTypes.firstOrNull()?.typeName ?: return true
        return parameterType == List::class.java.name || parameterType.contains(STORY_DETAIL)
    }

    private fun Class<*>.storyModuleConstructors() =
        declaredConstructors
            .filter { constructor ->
                constructor.parameterTypes.let { params ->
                    params.size in 1..2 &&
                        params[0] == Context::class.java &&
                        (params.size == 1 || params[1] == AttributeSet::class.java)
                }
            }
            .onEach { it.isAccessible = true }

    private fun Class<*>.storyViewConstructors() =
        declaredConstructors
            .filter { constructor ->
                constructor.parameterTypes.let { params ->
                    params.size in 1..3 &&
                        params[0] == Context::class.java &&
                        (params.size == 1 || params[1] == AttributeSet::class.java) &&
                        (params.size < 3 || params[2] == Int::class.javaPrimitiveType)
                }
            }
            .onEach { it.isAccessible = true }

    private fun scanVideoDetailBannerAd(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<VideoDetailBannerAdSymbols> {
        val bizKt = classLoader.loadClassOrNull(G_AD_BIZ_KT)
        val videoDetailType = classLoader.loadClassOrNull(G_AD_VIDEO_DETAIL)
        val underPlayerType = classLoader.loadClassOrNull(I_AD_UNDER_PLAYER)
        val relateType = classLoader.loadClassOrNull(I_AD_VIDEO_RELATE)
        val merchandiseType = classLoader.loadClassOrNull(I_AD_MERCHANDISE)
        val pausedPageType = classLoader.loadClassOrNull(I_VD_PAUSED_PAGE)
        val adPanelType = classLoader.loadClassOrNull(I_AD_PANEL)
        val getVideoDetail = if (bizKt != null && videoDetailType != null) {
            bizKt.allMethods().firstOrNull {
                it.name == "getGAdVideoDetail" &&
                    it.parameterCount == 0 &&
                    Modifier.isStatic(it.modifiers) &&
                    videoDetailType.isAssignableFrom(it.returnType)
            }
        } else {
            null
        }
        val requestPausedPage = pausedPageType?.allMethods()?.firstOrNull { it.isPausedPageRequestMethod() }
        val getPausedPagePanel = adPanelType?.allMethods()?.firstOrNull {
            it.name == "getPausedPagePanel" &&
                it.parameterCount == 1
        }
        val getBrandPausedPagePanel = adPanelType?.allMethods()?.firstOrNull {
            it.name == "getBrandPausedPagePanel" &&
                it.parameterCount == 2
        }

        val baseComponent = findGeminiBindingComponentClass(classLoader)
        val viewBindingClass = classLoader.loadClassOrNull(ANDROIDX_VIEW_BINDING)
        val relateGameComponent = if (baseComponent != null && viewBindingClass != null) {
            findRelateGameComponentClass(classLoader, bridge, baseComponent, viewBindingClass)
        } else {
            RelateGameComponentScan(
                type = null,
                evidence = "base=${baseComponent != null},viewBinding=${viewBindingClass != null}",
            )
        }
        val simpleViewEntry = classLoader.loadClassOrNull(GEMINI_SIMPLE_VIEW_ENTRY)
        val simpleViewEntryConstructor = simpleViewEntry?.declaredConstructors?.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(View::class.java))
        }?.apply { isAccessible = true }
        val createViewEntry = baseComponent?.allMethods()?.firstOrNull {
            it.name == "createViewEntry" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == Context::class.java &&
                it.parameterTypes[1] == ViewGroup::class.java
        }
        val bindToView = baseComponent?.allMethods()?.firstOrNull {
            it.name == "bindToView" &&
                it.parameterCount == 2 &&
                it.parameterTypes[1].name == "kotlin.coroutines.Continuation"
        }
        val unitField = runCatching {
            classLoader.loadClassOrNull(KOTLIN_UNIT)
                ?.getDeclaredField("INSTANCE")
                ?.apply { isAccessible = true }
        }.getOrNull()

        val hasProxyHook = getVideoDetail != null && videoDetailType != null && underPlayerType != null
        val hasPausedPageRequestHook = getVideoDetail != null &&
            videoDetailType != null &&
            pausedPageType != null &&
            requestPausedPage != null
        val hasPausedPagePanelHook = getVideoDetail != null &&
            videoDetailType != null &&
            adPanelType != null &&
            getPausedPagePanel != null &&
            getBrandPausedPagePanel != null
        val hasRelateGameHook = simpleViewEntryConstructor != null &&
            createViewEntry != null &&
            bindToView != null &&
            unitField != null &&
            relateGameComponent.type != null
        if (!hasProxyHook && !hasPausedPageRequestHook && !hasPausedPagePanelHook && !hasRelateGameHook) {
            return SymbolScanResult.Missing("video detail banner hook points not found")
        }

        val symbols = VideoDetailBannerAdSymbols(
            getVideoDetail = getVideoDetail?.let(MethodDescriptor::of),
            videoDetailTypeName = if (hasProxyHook || hasPausedPageRequestHook || hasPausedPagePanelHook) {
                videoDetailType?.name
            } else {
                null
            },
            underPlayerTypeName = if (hasProxyHook) underPlayerType?.name else null,
            relateTypeName = relateType?.name,
            merchandiseTypeName = merchandiseType?.name,
            pausedPageTypeName = if (hasPausedPageRequestHook) pausedPageType?.name else null,
            adPanelTypeName = if (hasPausedPagePanelHook) adPanelType?.name else null,
            requestPausedPage = if (hasPausedPageRequestHook) requestPausedPage?.let(MethodDescriptor::of) else null,
            getPausedPagePanel = if (hasPausedPagePanelHook) getPausedPagePanel?.let(MethodDescriptor::of) else null,
            getBrandPausedPagePanel = if (hasPausedPagePanelHook) {
                getBrandPausedPagePanel?.let(MethodDescriptor::of)
            } else {
                null
            },
            relateGameComponentTypeName = if (hasRelateGameHook) relateGameComponent.type?.name else null,
            simpleViewEntryConstructor = if (hasRelateGameHook) {
                simpleViewEntryConstructor?.let(ConstructorDescriptor::of)
            } else {
                null
            },
            createViewEntry = if (hasRelateGameHook) createViewEntry?.let(MethodDescriptor::of) else null,
            bindToView = if (hasRelateGameHook) bindToView?.let(MethodDescriptor::of) else null,
            kotlinUnitField = if (hasRelateGameHook) unitField?.let(FieldDescriptor::of) else null,
            evidence = "proxy=$hasProxyHook,pausedRequest=$hasPausedPageRequestHook," +
                "pausedPanel=$hasPausedPagePanelHook,relateGame=$hasRelateGameHook," +
                "component=${relateGameComponent.type?.name ?: "-"}",
        )
        val hookPoints = listOf(
            childHookPoint(
                HP_VIDEO_DETAIL_BANNER_PROXY,
                hasProxyHook,
                "proxy hook dependencies not found",
                "method=${getVideoDetail != null},detail=${videoDetailType != null},underPlayer=${underPlayerType != null}",
            ),
            childHookPoint(
                HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_REQUEST,
                hasPausedPageRequestHook,
                "paused page request hook dependencies not found",
                "method=${getVideoDetail != null},detail=${videoDetailType != null},pausedPage=${pausedPageType != null},request=${requestPausedPage != null}",
            ),
            childHookPoint(
                HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_PANEL,
                hasPausedPagePanelHook,
                "paused page panel hook dependencies not found",
                "method=${getVideoDetail != null},detail=${videoDetailType != null},panel=${adPanelType != null},normal=${getPausedPagePanel != null},brand=${getBrandPausedPagePanel != null}",
            ),
            childHookPoint(
                HP_VIDEO_DETAIL_BANNER_RELATE_GAME,
                hasRelateGameHook,
                "relate game view hook dependencies not found",
                "component=${relateGameComponent.type?.name ?: "-"},ctor=${simpleViewEntryConstructor != null},create=${createViewEntry != null},bind=${bindToView != null},unit=${unitField != null},scan=${relateGameComponent.evidence}",
            ),
        )
        return SymbolScanResult.Found(symbols, "VideoDetailBannerAd", symbols.evidence, hookPoints)
    }

    private fun Method.isPausedPageRequestMethod(): Boolean =
        name == "requestPausedPage" &&
            parameterCount > 0 &&
            parameterTypes.last().isKotlinContinuationTypeName() &&
            returnType == Any::class.java

    private fun findGeminiBindingComponentClass(classLoader: ClassLoader): Class<*>? {
        for (className in GEMINI_BINDING_COMPONENT_CLASSES) {
            val clazz = classLoader.loadClassOrNull(className) ?: continue
            if (Modifier.isAbstract(clazz.modifiers) &&
                clazz.allMethods().any { it.name == "createViewEntry" && it.parameterCount == 2 } &&
                clazz.allMethods().any { it.name == "bindToView" && it.parameterCount == 2 }
            ) {
                return clazz
            }
        }
        return GEMINI_BINDING_COMPONENT_CLASSES.firstNotNullOfOrNull(classLoader::loadClassOrNull)
    }

    private fun findRelateGameComponentClass(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
        baseComponent: Class<*>,
        viewBindingClass: Class<*>,
    ): RelateGameComponentScan {
        val searched = findClassNamesByNameContains(bridge, listOf(RELATE_GAME_COMPONENT_PACKAGE))
            .filter { it.startsWith("$RELATE_GAME_COMPONENT_PACKAGE.") }
        val names = (searched + RELATE_GAME_COMPONENT_CANDIDATE_NAMES).distinct()
        if (names.isEmpty()) {
            return RelateGameComponentScan(null, "candidates=0")
        }

        val restoreErrors = ArrayList<String>()
        val candidates = names.mapNotNull { name ->
            runCatching { Class.forName(name, false, classLoader) }
                .onFailure { restoreErrors += "$name:${it.scanMessage()}" }
                .getOrNull()
        }.filter { type ->
            type.isRelateGameComponentCandidate(baseComponent, viewBindingClass)
        }.distinctBy { it.name }

        val errorSuffix = restoreErrors.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = ",restoreErrors=", limit = 2) { it.take(120) }
            .orEmpty()
        return when (candidates.size) {
            1 -> RelateGameComponentScan(
                type = candidates.single(),
                evidence = "candidates=${names.size},matched=1$errorSuffix",
            )
            0 -> RelateGameComponentScan(
                type = null,
                evidence = "candidates=${names.size},matched=0$errorSuffix",
            )
            else -> RelateGameComponentScan(
                type = null,
                evidence = "candidates=${names.size},matched=${candidates.size},ambiguous=${candidates.joinToString(limit = 4) { it.name }}$errorSuffix",
            )
        }
    }

    private fun Class<*>.isRelateGameComponentCandidate(
        baseComponent: Class<*>,
        viewBindingClass: Class<*>,
    ): Boolean {
        if (!baseComponent.isAssignableFrom(this)) return false
        if (Modifier.isAbstract(modifiers) || isInterface) return false
        val hasCreateBinding = declaredMethods.any { method ->
            method.parameterCount == 3 &&
                method.parameterTypes[0] == Context::class.java &&
                method.parameterTypes[1] == LayoutInflater::class.java &&
                method.parameterTypes[2] == ViewGroup::class.java &&
                viewBindingClass.isAssignableFrom(method.returnType)
        }
        if (!hasCreateBinding) return false
        return declaredMethods.any { method ->
            method.parameterCount == 2 &&
                viewBindingClass.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1].isKotlinContinuationTypeName()
        }
    }

    private fun scanHomeTopBar(
        classLoader: ClassLoader,
    ): SymbolScanResult<HomeTopBarSymbols> {
        val menuItemClass = classLoader.loadClassOrNull(HOME_MENU_ITEM_CLASS)
        val gameMenuMethod = menuItemClass?.declaredMethods?.firstOrNull {
            it.name == "b" &&
                it.parameterTypes.contentEquals(arrayOf(Menu::class.java, MenuInflater::class.java))
        }?.apply { isAccessible = true }

        val baseFragmentClass = HOME_BASE_MAIN_FRAME_FRAGMENTS.firstNotNullOfOrNull(classLoader::loadClassOrNull)
        val baseOnViewCreated = baseFragmentClass?.declaredMethods?.firstOrNull {
            it.name == "onViewCreated" &&
                it.parameterTypes.contentEquals(arrayOf(View::class.java, Bundle::class.java))
        }?.apply { isAccessible = true }

        val mainFragmentClass = classLoader.loadClassOrNull(HOME_MAIN_FRAGMENT)
        val defaultWordClass = classLoader.loadClassOrNull(HOME_DEFAULT_SEARCH_WORD_CLASS)
        val defaultWordMethods = if (mainFragmentClass != null && defaultWordClass != null) {
            mainFragmentClass.allMethods()
                .filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == defaultWordClass &&
                        !Modifier.isStatic(method.modifiers) &&
                        !Modifier.isAbstract(method.modifiers)
                }
                .distinctBy(Method::toGenericString)
                .toList()
        } else {
            emptyList()
        }

        if (gameMenuMethod == null && baseOnViewCreated == null && defaultWordMethods.isEmpty()) {
            return SymbolScanResult.Missing("home top bar hook points not found")
        }
        val symbols = HomeTopBarSymbols(
            gameMenuMethod = gameMenuMethod?.let(MethodDescriptor::of),
            baseOnViewCreated = baseOnViewCreated?.let(MethodDescriptor::of),
            defaultWordMethods = defaultWordMethods.map(MethodDescriptor::of),
            evidence = "game=${gameMenuMethod != null},onViewCreated=${baseOnViewCreated != null},defaultWord=${defaultWordMethods.size}",
        )
        val hookPoints = listOf(
            childHookPoint(HP_HOME_TOP_BAR_GAME, gameMenuMethod != null, "game menu method not found"),
            childHookPoint(HP_HOME_TOP_BAR_VIEW_CREATED, baseOnViewCreated != null, "base onViewCreated method not found"),
            childHookPoint(
                HP_HOME_TOP_BAR_DEFAULT_WORD,
                defaultWordMethods.isNotEmpty(),
                "default search word method not found",
                "methods=${defaultWordMethods.size}",
            ),
        )
        return SymbolScanResult.Found(symbols, "HomeTopBarPurify", symbols.evidence, hookPoints)
    }

    private fun scanBottomBar(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<BottomBarSymbols> {
        val tabHostClasses = buildSet {
            addAll(BOTTOM_TAB_HOST_CLASSES.mapNotNull(classLoader::loadClassOrNull))
            addAll(findClassNamesBySimpleName(bridge, "TabHost").mapNotNull(classLoader::loadClassOrNull))
        }
        val tabHostSetTabsMethods = tabHostClasses
            .asSequence()
            .flatMap { it.allMethods() }
            .filter { method ->
                method.name == "setTabs" &&
                    method.parameterCount == 1 &&
                    List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.returnType == Void.TYPE
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val tabHostGetTabsMethods = tabHostClasses
            .asSequence()
            .flatMap { it.allMethods() }
            .filter { method ->
                method.name == "getTabs" &&
                    method.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(method.returnType)
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val baseOnViewCreatedMethods = HOME_BASE_MAIN_FRAME_FRAGMENTS
            .asSequence()
            .mapNotNull(classLoader::loadClassOrNull)
            .flatMap { it.allMethods() }
            .filter { method ->
                method.name == "onViewCreated" &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == View::class.java &&
                    method.parameterTypes[1] == Bundle::class.java &&
                    method.returnType == Void.TYPE
            }
            .distinctBy(Method::toGenericString)
            .toList()

        if (tabHostSetTabsMethods.isEmpty() || tabHostGetTabsMethods.isEmpty()) {
            return SymbolScanResult.Missing("bottom bar hook points not found")
        }
        val symbols = BottomBarSymbols(
            tabHostSetTabsMethods = tabHostSetTabsMethods.map(MethodDescriptor::of),
            tabHostGetTabsMethods = tabHostGetTabsMethods.map(MethodDescriptor::of),
            baseOnViewCreatedMethods = baseOnViewCreatedMethods.map(MethodDescriptor::of),
            evidence = "setTabs=${tabHostSetTabsMethods.size},getTabs=${tabHostGetTabsMethods.size},onViewCreated=${baseOnViewCreatedMethods.size}",
        )
        val hookPoints = listOf(
            childHookPoint(
                HP_BOTTOM_BAR_TAB_HOST,
                tabHostSetTabsMethods.isNotEmpty() && tabHostGetTabsMethods.isNotEmpty(),
                "TabHost getTabs/setTabs methods not found",
                "setTabs=${tabHostSetTabsMethods.size},getTabs=${tabHostGetTabsMethods.size}",
            ),
            optionalChildHookPoint(
                HP_BOTTOM_BAR_VIEW_CREATED,
                baseOnViewCreatedMethods.isNotEmpty(),
                "BaseMainFrameFragment.onViewCreated not found",
                "methods=${baseOnViewCreatedMethods.size}",
            ),
        )
        return SymbolScanResult.Found(symbols, "BottomBar", symbols.evidence, hookPoints)
    }

    private fun scanHomeRecommendFeed(
        classLoader: ClassLoader,
    ): SymbolScanResult<HomeRecommendFeedSymbols> {
        val holderDataClass = classLoader.loadClassOrNull(PEGASUS_HOLDER_DATA)
            ?: return SymbolScanResult.Missing("PegasusHolderData class not found")
        val responseClasses = PEGASUS_RESPONSE_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
        if (responseClasses.isEmpty()) return SymbolScanResult.Missing("PegasusResponse class not found")

        val getHolderType = holderDataClass.allMethods().firstOrNull {
            it.name == "getHolderType" &&
                it.parameterCount == 0 &&
                it.returnType == String::class.java &&
                !Modifier.isStatic(it.modifiers)
        } ?: return SymbolScanResult.Missing("getHolderType not found")

        val responseGetItems = responseClasses.mapNotNull { responseClass ->
            val getItems = responseClass.allMethods().firstOrNull {
                it.name == "getItems" &&
                    it.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(it.returnType) &&
                    !Modifier.isStatic(it.modifiers) &&
                    !Modifier.isAbstract(it.modifiers)
            } ?: return@mapNotNull null
            val itemsField = responseClass.allFields()
                .filter { List::class.java.isAssignableFrom(it.type) }
                .singleOrNull()
            PegasusResponseGetItemsSymbols(MethodDescriptor.of(getItems), itemsField?.let(FieldDescriptor::of))
        }.distinctBy { it.getItems.declaringClassName + "#" + it.getItems.name }
        if (responseGetItems.isEmpty()) return SymbolScanResult.Missing("PegasusResponse.getItems not found")

        val baseDataClass = BASE_PEGASUS_DATA_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        val holderStyleClass = classLoader.loadClassOrNull(PEGASUS_HOLDER_STYLE)
        val adInfoClass = classLoader.loadClassOrNull(AD_INFO)
        val symbols = HomeRecommendFeedSymbols(
            responseGetItems = responseGetItems,
            getHolderType = MethodDescriptor.of(getHolderType),
            getBizType = holderDataClass.noArgMethod("getBizType")?.let(MethodDescriptor::of),
            getHolderStyle = holderDataClass.noArgMethod("getHolderStyle")?.let(MethodDescriptor::of),
            isSmallCard = holderStyleClass?.allMethods()?.firstOrNull {
                it.name == "isSmallCard" &&
                    it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            }?.let(MethodDescriptor::of),
            getAdInfo = baseDataClass?.noArgMethod("getAdInfo")?.let(MethodDescriptor::of),
            getCardType = baseDataClass?.noArgMethod("getCardType")?.let(MethodDescriptor::of),
            getCardGoto = baseDataClass?.noArgMethod("getCardGoto")?.let(MethodDescriptor::of),
            getGoTo = baseDataClass?.noArgMethod("getGoTo")?.let(MethodDescriptor::of),
            getUri = baseDataClass?.noArgMethod("getUri")?.let(MethodDescriptor::of),
            adInfoClassName = adInfoClass?.name,
            evidence = "responses=${responseGetItems.size},holder=${holderDataClass.name},base=${baseDataClass?.name}",
        )
        return SymbolScanResult.Found(symbols, responseGetItems.joinToString("|") { it.getItems.declaringClassName }, symbols.evidence)
    }

    private fun scanVideoDetailRelateFeed(
        classLoader: ClassLoader,
    ): SymbolScanResult<VideoDetailRelateFeedSymbols> {
        val responseClasses = VIDEO_DETAIL_RELATE_RESPONSE_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
        val responseGetItems = responseClasses.mapNotNull { responseClass ->
            val getItems = responseClass.allMethods().firstOrNull {
                (it.name == "getCardsList" || it.name == "getRelatesList") &&
                    it.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(it.returnType) &&
                    !Modifier.isStatic(it.modifiers) &&
                    !Modifier.isAbstract(it.modifiers)
            } ?: return@mapNotNull null
            val itemsField = responseClass.allFields()
                .filter { List::class.java.isAssignableFrom(it.type) }
                .firstOrNull {
                    it.name.startsWith("cards", ignoreCase = true) ||
                        it.name.startsWith("relates", ignoreCase = true) ||
                        it.name.startsWith("items", ignoreCase = true)
                } ?: responseClass.allFields().filter { List::class.java.isAssignableFrom(it.type) }.singleOrNull()
            RelateResponseGetItemsSymbols(MethodDescriptor.of(getItems), itemsField?.let(FieldDescriptor::of))
        }.distinctBy { it.getItems.declaringClassName + "#" + it.getItems.name }

        val detailRelateServiceClass = classLoader.loadClassOrNull(DETAIL_RELATE_SERVICE_CLASS)
        val detailRelateServiceMethod = detailRelateServiceClass?.allMethods()?.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                !Modifier.isAbstract(method.modifiers) &&
                method.parameterCount == 1 &&
                (method.name == "d" || method.parameterTypes[0].name.endsWith("D0") || method.returnType.name.contains("RunningUIComponent"))
        }

        if (responseGetItems.isEmpty() && detailRelateServiceMethod == null) {
            return SymbolScanResult.Missing("VideoDetailRelate response and service methods not found")
        }

        val symbols = VideoDetailRelateFeedSymbols(
            responseGetItems = responseGetItems,
            detailRelateServiceMethod = detailRelateServiceMethod?.let(MethodDescriptor::of),
            evidence = "responses=${responseGetItems.size},service=${detailRelateServiceMethod != null}",
        )
        val target = buildList {
            addAll(responseGetItems.map { it.getItems.declaringClassName })
            detailRelateServiceMethod?.let { add(it.declaringClass.name) }
        }.joinToString("|")
        return SymbolScanResult.Found(symbols, target, symbols.evidence)
    }

    private fun scanHomeRecommendTabs(
        classLoader: ClassLoader,
    ): SymbolScanResult<HomeRecommendTabSymbols> {
        val homeFragmentClasses = HOME_FRAGMENT_V2_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .toList()
        if (homeFragmentClasses.isEmpty()) {
            return SymbolScanResult.Missing("HomeFragmentV2 class not found")
        }

        val buildTabMethods = homeFragmentClasses
            .asSequence()
            .flatMap { it.declaredMethods.asSequence() }
            .filter { it.isHomeRecommendTabBuildMethod() }
            .distinctBy(Method::toGenericString)
            .toList()
        val buildTabsMethod = buildTabMethods.singleOrNull()
            ?: return SymbolScanResult.Missing("home recommend tab build method candidates=${buildTabMethods.size}")

        val tabResourceClassName = buildTabsMethod.listParameterGenericTypeName(0)
        val tabResourceClass = tabResourceClassName?.let(classLoader::loadClassOrNull)
            ?: HOME_TAB_RESOURCE_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
            ?: return SymbolScanResult.Missing("home recommend tab resource class not found")
        val stringFields = tabResourceClass.declaredFields
            .filter { field ->
                field.type == String::class.java &&
                    !Modifier.isStatic(field.modifiers)
            }
            .onEach { it.isAccessible = true }
        if (stringFields.size < 3) {
            return SymbolScanResult.Missing("home recommend tab string fields not found")
        }

        val symbols = HomeRecommendTabSymbols(
            buildTabsMethod = MethodDescriptor.of(buildTabsMethod),
            idField = FieldDescriptor.of(stringFields[0]),
            titleField = FieldDescriptor.of(stringFields[1]),
            uriField = FieldDescriptor.of(stringFields[2]),
            reporterIdField = stringFields.getOrNull(3)?.let(FieldDescriptor::of),
            evidence = "method=${buildTabsMethod.name},resource=${tabResourceClass.name},strings=${stringFields.size}",
        )
        return SymbolScanResult.Found(
            symbols,
            "${buildTabsMethod.declaringClass.name}.${buildTabsMethod.name}",
            symbols.evidence,
        )
    }

    private fun Method.isHomeRecommendTabBuildMethod(): Boolean {
        if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) return false
        if (parameterCount != 1) return false
        if (!List::class.java.isAssignableFrom(returnType)) return false
        if (!List::class.java.isAssignableFrom(parameterTypes[0])) return false
        val returnTypeName = genericReturnType.typeName
        val parameterTypeName = genericParameterTypes.firstOrNull()?.typeName.orEmpty()
        return "BasePrimaryMultiPageFragment" in returnTypeName &&
            "main2.resource." in parameterTypeName
    }

    private fun Method.listParameterGenericTypeName(index: Int): String? {
        val typeName = genericParameterTypes.getOrNull(index)?.typeName ?: return null
        if ('<' !in typeName || '>' !in typeName) return null
        return typeName
            .substringAfter('<')
            .substringBefore('>')
            .substringBefore(',')
            .trim()
            .removePrefix("? extends ")
            .removePrefix("? super ")
            .takeIf { it.isNotBlank() }
    }

    private fun scanHomeComponentHide(
        classLoader: ClassLoader,
    ): SymbolScanResult<HomeComponentHideSymbols> {
        val fragmentClass = classLoader.loadClassOrNull(ANDROIDX_FRAGMENT_CLASS)
        val fragmentLifecycleMethods = listOfNotNull(
            fragmentClass?.findMethod("onViewCreated", Void.TYPE, View::class.java, Bundle::class.java),
            fragmentClass?.findMethod("onHiddenChanged", Void.TYPE, Boolean::class.javaPrimitiveType!!),
        ).distinctBy(Method::toGenericString)
        val baseClasses = BASE_HOME_FRAGMENT_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .toList()
        val methods = baseClasses
            .asSequence()
            .flatMap { type ->
                HOME_COMPONENT_LIFECYCLE_METHODS.asSequence().flatMap { methodName ->
                    type.allMethods().asSequence().filter { it.name == methodName }
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val componentCatalog = baseClasses
            .asSequence()
            .flatMap { it.declaredMethods.asSequence() }
            .filter {
                it.parameterCount == 0 &&
                    List::class.java.isAssignableFrom(it.returnType) &&
                    !Modifier.isStatic(it.modifiers) &&
                    !Modifier.isAbstract(it.modifiers)
            }
            .distinctBy(Method::toGenericString)
            .singleOrNull()
        val fragmentLifecycleHookPoint = childHookPoint(
            HP_HOME_COMPONENT_HIDE_FRAGMENT,
            fragmentLifecycleMethods.isNotEmpty(),
            "androidx fragment lifecycle methods not found",
            "methods=${fragmentLifecycleMethods.size}",
        )
        if (fragmentLifecycleMethods.isEmpty()) {
            return SymbolScanResult.Missing(
                "androidx fragment lifecycle methods not found",
                hookPoints = listOf(fragmentLifecycleHookPoint),
            )
        }
        val symbols = HomeComponentHideSymbols(
            fragmentLifecycleMethods = fragmentLifecycleMethods.map(MethodDescriptor::of),
            baseHomeFragmentMethods = methods.map(MethodDescriptor::of),
            componentCatalogMethod = componentCatalog?.let(MethodDescriptor::of),
            evidence = "fragment=${fragmentLifecycleMethods.size},baseLifecycle=${methods.size},catalog=${componentCatalog != null}",
        )
        val hookPoints = listOf(
            fragmentLifecycleHookPoint,
            childHookPoint(
                HP_HOME_COMPONENT_HIDE_CATALOG,
                componentCatalog != null,
                "home component catalog list method not found",
                "method=${componentCatalog?.name}",
            ),
        )
        return SymbolScanResult.Found(
            symbols,
            (fragmentLifecycleMethods + methods).joinToString("|") { "${it.declaringClass.name}.${it.name}" },
            symbols.evidence,
            hookPoints,
        )
    }

    private fun scanFullNumberFormat(
        classLoader: ClassLoader,
    ): SymbolScanResult<FullNumberFormatSymbols> {
        val formatterClasses = NUMBER_FORMAT_CLASS_NAMES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .toList()
        val methods = formatterClasses
            .asSequence()
            .flatMap { type -> type.allMethods() }
            .filter { method -> method.isFullNumberFormatterMethod() }
            .distinctBy(Method::toGenericString)
            .toList()
        if (methods.isEmpty()) return SymbolScanResult.Missing("number formatter methods not found")

        val symbols = FullNumberFormatSymbols(
            formatterMethods = methods.map(MethodDescriptor::of),
            evidence = "classes=${formatterClasses.size},methods=${methods.size}",
        )
        return SymbolScanResult.Found(
            symbols,
            methods.joinToString("|") { "${it.declaringClass.name}.${it.name}" },
            symbols.evidence,
        )
    }

    private fun Method.isFullNumberFormatterMethod(): Boolean {
        if (!Modifier.isStatic(modifiers)) return false
        if (returnType != String::class.java) return false
        if (name !in FULL_NUMBER_FORMAT_METHOD_NAMES) return false
        val params = parameterTypes
        if (params.size !in 1..2) return false
        val first = params[0]
        if (first != Long::class.javaPrimitiveType && first != Int::class.javaPrimitiveType) return false
        if (params.size == 2 && params[1] != String::class.java) return false
        return true
    }

    private fun scanVideoComment(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<VideoCommentSymbols> {
        val disableCommentConstructors = THESEUS_TAB_PAGER_SERVICE
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { it.declaredConstructors.asSequence() }
            .onEach { it.isAccessible = true }
            .map(ConstructorDescriptor::of)
            .toList()

        val commentViewModelClasses = COMMENT_VIEW_MODEL_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
            .toList()
        val quickReplyActionScan = findQuickReplyActionClasses(classLoader, bridge)
        val quickReplyActionClasses = quickReplyActionScan.classes

        val quickReplyViewModelMethods = commentViewModelClasses
            .asSequence()
            .flatMap { type ->
                (sequenceOf(type) + type.declaredClasses.asSequence()).flatMap { it.declaredMethods.asSequence() }
            }
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount == 1 &&
                    !method.name.contains("lambda", ignoreCase = true) &&
                    method.parameterTypes.firstOrNull()?.isCommentActionType(quickReplyActionClasses) == true
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val quickReplyDispatcherMethods = commentViewModelClasses
            .asSequence()
            .flatMap { type ->
                type.declaredClasses.asSequence().flatMap { it.declaredMethods.asSequence() }
            }
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount == 1 &&
                    !method.name.contains("lambda", ignoreCase = true) &&
                    method.parameterTypes.firstOrNull()?.isCommentActionBaseType(quickReplyActionClasses) == true
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val quickReplyActionBaseMethods = (
            COMMENT_ACTION_BASE_CLASSES.asSequence().mapNotNull { classLoader.loadClassOrNull(it) } +
                quickReplyActionClasses.asSequence()
            )
            .distinctBy { it.name }
            .flatMap { type ->
                type.allMethods().filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0].isInterface &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                        !Modifier.isStatic(method.modifiers)
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val quickReplyActionMethods = (quickReplyViewModelMethods + quickReplyDispatcherMethods + quickReplyActionBaseMethods)
            .distinctBy(Method::toGenericString)

        val quickReplyDialogMethods = QUICK_REPLY_DIALOG_COLLECTOR_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.declaredMethods.asSequence().filter { method ->
                    method.parameterCount == 2 &&
                        !method.name.contains("lambda", ignoreCase = true) &&
                        method.parameterTypes.firstOrNull()?.isQuickReplyDialogIntentType() == true &&
                        method.parameterTypes.getOrNull(1)?.let { Continuation::class.java.isAssignableFrom(it) } == true
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val voteWidgetMethods = (CMT_VOTE_WIDGET_CLASSES + CMT_MOUNT_WIDGET_CLASSES + COMMENT_VOTE_VIEW_CLASSES)
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.declaredMethods.asSequence().filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterCount >= 1 &&
                        !method.name.contains("lambda", ignoreCase = true) &&
                        method.name in VOTE_WIDGET_METHOD_NAMES
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val followWidgetMethods = COMMENT_FOLLOW_WIDGET_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.declaredMethods.asSequence().filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterCount >= 1 &&
                        !method.name.contains("lambda", ignoreCase = true) &&
                        method.name in FOLLOW_WIDGET_METHOD_NAMES
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val headerDecorativeMethods = COMMENT_HEADER_DECORATIVE_VIEW_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.declaredMethods.asSequence().filter { method ->
                    method.returnType == Void.TYPE &&
                        method.parameterCount >= 1 &&
                        !method.name.contains("lambda", ignoreCase = true) &&
                        method.name in HEADER_DECORATIVE_METHOD_NAMES &&
                        List::class.java.isAssignableFrom(method.parameterTypes[0])
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()

        val searchUrlsMethod = COMMENT_CONTENT_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .mapNotNull { it.allMethods().firstOrNull { method -> method.name == "internalGetUrls" && method.parameterCount == 0 } }
            .firstOrNull()

        val emptyPageHooks = scanVideoCommentEmptyPageHooks(classLoader)
        val mainListOnNextMethods = COMMENT_MAIN_LIST_OBSERVERS
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .mapNotNull { it.allMethods().firstOrNull { method -> method.name == "onNext" && method.parameterCount == 1 } }
            .distinctBy(Method::toGenericString)
            .toList()

        val total = disableCommentConstructors.size + quickReplyActionMethods.size + quickReplyDialogMethods.size +
            voteWidgetMethods.size + followWidgetMethods.size + headerDecorativeMethods.size +
            (if (searchUrlsMethod != null) 1 else 0) + emptyPageHooks.size + mainListOnNextMethods.size
        if (total == 0) return SymbolScanResult.Missing("video comment hook points not found")

        val symbols = VideoCommentSymbols(
            disableCommentConstructors = disableCommentConstructors,
            quickReplyViewModelMethods = quickReplyActionMethods.map(MethodDescriptor::of),
            quickReplyDialogMethods = quickReplyDialogMethods.map(MethodDescriptor::of),
            voteWidgetMethods = voteWidgetMethods.map(MethodDescriptor::of),
            followWidgetMethods = followWidgetMethods.map(MethodDescriptor::of),
            headerDecorativeMethods = headerDecorativeMethods.map(MethodDescriptor::of),
            searchUrlsMethod = searchUrlsMethod?.let(MethodDescriptor::of),
            emptyPageHooks = emptyPageHooks,
            mainListOnNextMethods = mainListOnNextMethods.map(MethodDescriptor::of),
            evidence = "constructors=${disableCommentConstructors.size},quick=${quickReplyActionMethods.size + quickReplyDialogMethods.size},quickScan=${quickReplyActionScan.evidence},widgets=${voteWidgetMethods.size + followWidgetMethods.size + headerDecorativeMethods.size},empty=${emptyPageHooks.size},main=${mainListOnNextMethods.size}",
        )
        val quickReplyCount = quickReplyActionMethods.size + quickReplyDialogMethods.size
        val widgetCount = voteWidgetMethods.size + followWidgetMethods.size + headerDecorativeMethods.size
        val hookPoints = listOf(
            childHookPoint(
                HP_VIDEO_COMMENT_DISABLE,
                disableCommentConstructors.isNotEmpty(),
                "disable comment constructor hook not found",
                "constructors=${disableCommentConstructors.size}",
            ),
            childHookPoint(
                HP_VIDEO_COMMENT_QUICK_REPLY,
                quickReplyCount > 0,
                "quick reply hook methods not found",
                "methods=$quickReplyCount,${quickReplyActionScan.evidence}",
            ),
            childHookPoint(HP_VIDEO_COMMENT_WIDGETS, widgetCount > 0, "comment widget hook methods not found", "methods=$widgetCount"),
            childHookPoint(HP_VIDEO_COMMENT_SEARCH, searchUrlsMethod != null, "comment search url method not found"),
            childHookPoint(HP_VIDEO_COMMENT_EMPTY_PAGE, emptyPageHooks.isNotEmpty(), "empty page hook points not found", "hooks=${emptyPageHooks.size}"),
            childHookPoint(HP_VIDEO_COMMENT_MAIN_LIST, mainListOnNextMethods.isNotEmpty(), "main list onNext hook methods not found", "methods=${mainListOnNextMethods.size}"),
        )
        return SymbolScanResult.Found(symbols, "VideoComment", symbols.evidence, hookPoints)
    }

    private fun scanVideoCommentEmptyPageHooks(classLoader: ClassLoader): List<VideoCommentEmptyPageSymbols> {
        val subjectControl = COMMENT_SUBJECT_CONTROL_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
            .mapNotNull { type ->
                val emptyPageClass = classLoader.loadClassOrNull(type.name.replace("SubjectControl", "EmptyPage"))
                    ?: return@mapNotNull null
                val defaultInstance = emptyPageClass.declaredFields.firstOrNull { it.name == "DEFAULT_INSTANCE" }
                    ?.apply { isAccessible = true } ?: return@mapNotNull null
                val method = type.allMethods().firstOrNull { it.name == "getEmptyPage" && it.parameterCount == 0 }
                    ?: return@mapNotNull null
                VideoCommentEmptyPageSymbols(MethodDescriptor.of(method), FieldDescriptor.of(defaultInstance))
            }
        val subjectDesc = COMMENT_SUBJECT_DESC_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
            .mapNotNull { type ->
                val emptyPageClass = classLoader.loadClassOrNull(type.name.replace("SubjectDescriptionReply", "EmptyPage"))
                    ?: return@mapNotNull null
                val defaultInstance = emptyPageClass.declaredFields.firstOrNull { it.name == "DEFAULT_INSTANCE" }
                    ?.apply { isAccessible = true } ?: return@mapNotNull null
                val method = type.allMethods().firstOrNull { it.name == "getEmptyPage" && it.parameterCount == 0 }
                    ?: return@mapNotNull null
                VideoCommentEmptyPageSymbols(MethodDescriptor.of(method), FieldDescriptor.of(defaultInstance))
            }
        return (subjectControl + subjectDesc).distinctBy { it.getEmptyPage.declaringClassName }
    }

    private fun scanSkipVideoAd(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<SkipVideoAdSymbols> {
        val playViewMethods = PLAYER_MOSS_CANDIDATES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.allMethods().asSequence().filter { method ->
                    method.isConcreteInstanceHookMethod() &&
                        method.name in PLAY_VIEW_METHOD_NAMES &&
                        method.parameterCount >= 1 &&
                        method.parameterTypes.firstOrNull()?.isPlayViewRequestType() == true
                }
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val playerCoreScan = findConcreteImplementors(
            classLoader = classLoader,
            bridge = bridge,
            interfaceName = PLAYER_CORE_SERVICE_INTERFACE,
        )
        val cardScan = findConcreteImplementors(
            classLoader = classLoader,
            bridge = bridge,
            interfaceName = CARD_PLAYER_CONTEXT_INTERFACE,
        )
        val playerCoreClasses = playerCoreScan.classes
        val cardClasses = cardScan.classes
        val playerCoreCurrent = playerCoreClasses.flatMap { it.currentPositionMethods() }.distinctBy(Method::toGenericString)
        val playerCoreState = playerCoreClasses.flatMap { it.stateMethods(STATE_METHOD_NAMES) }.distinctBy(Method::toGenericString)
        val playerCoreSeek = playerCoreClasses.flatMap { it.seekMethods() }.distinctBy(Method::toGenericString)
        val cardCurrent = cardClasses.flatMap { it.currentPositionMethods() }.distinctBy(Method::toGenericString)
        val cardState = cardClasses.flatMap { it.stateMethods(CARD_STATE_METHOD_NAMES) }.distinctBy(Method::toGenericString)
        val cardSeek = cardClasses.flatMap { it.seekMethods() }.distinctBy(Method::toGenericString)
        var storyScanError: String? = null
        val storyPagerPlayer = classLoader.loadClassOrNull(STORY_PAGER_PLAYER)
        val storyMethods = storyPagerPlayer?.let { type ->
            runCatching {
                Triple(
                    type.currentPositionMethods().distinctBy(Method::toGenericString),
                    type.stateMethods(STATE_METHOD_NAMES).distinctBy(Method::toGenericString),
                    type.seekMethods().distinctBy(Method::toGenericString),
                )
            }.onFailure { throwable ->
                storyScanError = throwable.scanMessage()
            }.getOrNull()
        } ?: Triple(emptyList(), emptyList(), emptyList())
        val storyCurrent = storyMethods.first
        val storyState = storyMethods.second
        val storySeek = storyMethods.third
        val playerCoreReady = playerCoreCurrent.isNotEmpty() && playerCoreState.isNotEmpty() && playerCoreSeek.isNotEmpty()
        val cardReady = cardCurrent.isNotEmpty() && cardState.isNotEmpty() && cardSeek.isNotEmpty()
        val storyReady = storyCurrent.isNotEmpty() && storySeek.isNotEmpty()
        val controllerReady = playerCoreReady || cardReady || storyReady
        val hookPoints = listOf(
            childHookPoint(HP_SKIP_VIDEO_AD_PLAY_VIEW, playViewMethods.isNotEmpty(), "play view hook methods not found", "methods=${playViewMethods.size}"),
            skipVideoAdControllerHookPoint(
                id = HP_SKIP_VIDEO_AD_PLAYER_CORE,
                scan = playerCoreScan,
                ready = playerCoreReady,
                missing = "player core position/state/seek methods not found",
                evidence = "classes=${playerCoreClasses.size},current=${playerCoreCurrent.size},state=${playerCoreState.size},seek=${playerCoreSeek.size}",
            ),
            skipVideoAdControllerHookPoint(
                id = HP_SKIP_VIDEO_AD_CARD,
                scan = cardScan,
                ready = cardReady,
                missing = "card player position/state/seek methods not found",
                evidence = "classes=${cardClasses.size},current=${cardCurrent.size},state=${cardState.size},seek=${cardSeek.size}",
            ),
            storySkipVideoAdHookPoint(
                ready = storyReady,
                classFound = storyPagerPlayer != null,
                scanError = storyScanError,
                evidence = "class=${storyPagerPlayer != null},current=${storyCurrent.size},state=${storyState.size},seek=${storySeek.size}",
            ),
        )
        val missingReason = when {
            playViewMethods.isEmpty() -> "skip video ad play view hook points not found"
            !controllerReady -> "skip video ad controller hook points not found"
            else -> null
        }
        if (missingReason != null) {
            return SymbolScanResult.Missing(missingReason, hookPoints)
        }
        val symbols = SkipVideoAdSymbols(
            playViewMethods = playViewMethods.map(MethodDescriptor::of),
            playerCoreCurrentPositionMethods = playerCoreCurrent.map(MethodDescriptor::of),
            playerCoreStateMethods = playerCoreState.map(MethodDescriptor::of),
            playerCoreSeekMethods = playerCoreSeek.map(MethodDescriptor::of),
            cardCurrentPositionMethods = cardCurrent.map(MethodDescriptor::of),
            cardStateMethods = cardState.map(MethodDescriptor::of),
            cardSeekMethods = cardSeek.map(MethodDescriptor::of),
            storyCurrentPositionMethods = storyCurrent.map(MethodDescriptor::of),
            storyStateMethods = storyState.map(MethodDescriptor::of),
            storySeekMethods = storySeek.map(MethodDescriptor::of),
            evidence = "play=${playViewMethods.size},core=${playerCoreCurrent.size}/${playerCoreState.size}/${playerCoreSeek.size},card=${cardCurrent.size}/${cardState.size}/${cardSeek.size},story=${storyCurrent.size}/${storyState.size}/${storySeek.size}",
        )
        return SymbolScanResult.Found(symbols, "SkipVideoAd", symbols.evidence, hookPoints)
    }

    private fun scanSkipVideoAdProgress(
        classLoader: ClassLoader,
    ): SymbolScanResult<SkipVideoAdProgressSymbols> {
        val progressOnDraw = classLoader.loadClassOrNull(PROGRESS_BAR_CLASS)
            ?.allMethods()
            ?.firstOrNull { it.name == "onDraw" && it.parameterTypes.contentEquals(arrayOf(Canvas::class.java)) }
        val storyOnStart = classLoader.loadClassOrNull(STORY_SEEK_BAR_CLASS)
            ?.allMethods()
            ?.filter {
                it.name == "onStart" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes.firstOrNull()?.isNumericType() == true
            }
            ?.distinctBy(Method::toGenericString)
            ?.toList()
            .orEmpty()
        val inlineUpdate = INLINE_PROGRESS_CLASSES
            .asSequence()
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .flatMap { type ->
                type.allMethods().asSequence().filter { it.name == "updateProgress" && it.parameterCount == 0 }
            }
            .distinctBy(Method::toGenericString)
            .toList()
        val panelWidget = classLoader.loadClassOrNull(PANEL_WIDGET_KT_CLASS)
        if (progressOnDraw == null && storyOnStart.isEmpty() && inlineUpdate.isEmpty()) {
            return SymbolScanResult.Missing("skip video ad progress hook points not found")
        }
        val symbols = SkipVideoAdProgressSymbols(
            progressOnDraw = progressOnDraw?.let(MethodDescriptor::of),
            storyOnStartMethods = storyOnStart.map(MethodDescriptor::of),
            inlineUpdateMethods = inlineUpdate.map(MethodDescriptor::of),
            panelWidgetKtClassName = panelWidget?.name,
            evidence = "draw=${progressOnDraw != null},story=${storyOnStart.size},inline=${inlineUpdate.size},panel=${panelWidget != null}",
        )
        val hookPoints = listOf(
            childHookPoint(HP_SKIP_VIDEO_AD_PROGRESS_DRAW, progressOnDraw != null, "progress onDraw hook not found"),
            childHookPoint(HP_SKIP_VIDEO_AD_PROGRESS_STORY, storyOnStart.isNotEmpty(), "story seek bar onStart hook not found", "methods=${storyOnStart.size}"),
            childHookPoint(HP_SKIP_VIDEO_AD_PROGRESS_INLINE, inlineUpdate.isNotEmpty(), "inline progress update hook not found", "methods=${inlineUpdate.size}"),
        )
        return SymbolScanResult.Found(symbols, "SkipVideoAdProgress", symbols.evidence, hookPoints)
    }

    private fun scanSkipVideoAdAutoLike(
        classLoader: ClassLoader,
    ): SymbolScanResult<SkipVideoAdAutoLikeSymbols> {
        val detailLikeInflate = classLoader.loadClassOrNull(DETAIL_LIKE_COMPONENT)
            ?.allMethods()
            ?.firstOrNull { it.isDetailLikeInflateMethod() }
        val detailLikeStateOwner = classLoader.loadClassOrNull(DETAIL_LIKE_STATE_OWNER)
        val storyActionOwner = classLoader.loadClassOrNull(STORY_ACTION_OWNER)
        val storyWidgetClasses = listOf(STORY_LIKE_WIDGET, STORY_LANDSCAPE_LIKE_WIDGET)
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .distinctBy { it.name }
        val storyBindMethods = storyWidgetClasses
            .flatMap { type -> type.allMethods().filter { it.isStoryBindMethod() } }
            .distinctBy(Method::toGenericString)
        val geminiLikeWidget = classLoader.loadClassOrNull(GEMINI_PLAYER_LIKE_WIDGET)
        if (detailLikeInflate == null && storyWidgetClasses.isEmpty() && geminiLikeWidget == null) {
            return SymbolScanResult.Missing("skip video ad auto-like hook points not found")
        }
        val symbols = SkipVideoAdAutoLikeSymbols(
            detailLikeInflateMethod = detailLikeInflate?.let(MethodDescriptor::of),
            detailLikeStateOwnerClassName = detailLikeStateOwner?.name,
            storyWidgetClassNames = storyWidgetClasses.map { it.name },
            storyActionOwnerClassName = storyActionOwner?.name,
            storyBindMethods = storyBindMethods.map(MethodDescriptor::of),
            geminiLikeWidgetClassName = geminiLikeWidget?.name,
            evidence = "detail=${detailLikeInflate != null},detailOwner=${detailLikeStateOwner != null},storyClasses=${storyWidgetClasses.size},storyOwner=${storyActionOwner != null},storyBind=${storyBindMethods.size},gemini=${geminiLikeWidget != null}",
        )
        val hookPoints = listOf(
            childHookPoint(
                HP_SKIP_VIDEO_AD_AUTO_LIKE_DETAIL,
                detailLikeInflate != null && detailLikeStateOwner != null,
                "detail like inflate/state owner not found",
                "inflate=${detailLikeInflate != null},owner=${detailLikeStateOwner != null}",
            ),
            childHookPoint(
                HP_SKIP_VIDEO_AD_AUTO_LIKE_STORY,
                storyWidgetClasses.isNotEmpty() && storyBindMethods.isNotEmpty(),
                "story like widget/bind hook not found",
                "widgets=${storyWidgetClasses.size},owner=${storyActionOwner != null},bind=${storyBindMethods.size}",
            ),
            childHookPoint(HP_SKIP_VIDEO_AD_AUTO_LIKE_GEMINI, geminiLikeWidget != null, "gemini like widget class not found"),
        )
        return SymbolScanResult.Found(symbols, "SkipVideoAdAutoLike", symbols.evidence, hookPoints)
    }

    private fun scanChronosPromotion(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): SymbolScanResult<ChronosPromotionSymbols> {
        val classes = ArrayList<NamedClassSymbol>()
        val methodGroups = ArrayList<NamedMethodGroup>()

        fun addClass(id: String, className: String): Class<*>? {
            val type = classLoader.loadClassOrNull(className) ?: return null
            classes += NamedClassSymbol(id, type.name)
            return type
        }

        fun addMethods(id: String, methods: List<Method>) {
            if (methods.isNotEmpty()) {
                methodGroups += NamedMethodGroup(id, methods.distinctBy(Method::toGenericString).map(MethodDescriptor::of))
            }
        }

        fun addMethods(id: String, methods: Sequence<Method>) {
            addMethods(id, methods.toList())
        }

        val rpcHandler = addClass(CHRONOS_ID_RPC_HANDLER, CHRONOS_RPC_HANDLER)
        val remoteHandler = addClass(CHRONOS_ID_REMOTE_HANDLER, REMOTE_SERVICE_HANDLER)
        val function2Type = addClass(CHRONOS_ID_FUNCTION2, KOTLIN_FUNCTION2)

        val updateDetailStateRequest = addClass(CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST, UPDATE_VIDEO_DETAIL_STATE_REQUEST)
        val openUrlRequest = addClass(CHRONOS_ID_OPEN_URL_REQUEST, OPEN_URL_SCHEME_REQUEST)
        val adDanmakuEventRequest = addClass(CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST, AD_DANMAKU_EVENT_REQUEST)
        val notifyCommercialEventRequest = addClass(CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST, NOTIFY_COMMERCIAL_EVENT_REQUEST)
        val getViewProgressRequest = addClass(CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST, GET_VIEW_PROGRESS_REQUEST)
        val getDmViewRequest = addClass(CHRONOS_ID_GET_DM_VIEW_REQUEST, GET_DM_VIEW_REQUEST)
        val viewProgressReply = addClass(CHRONOS_ID_VIEW_PROGRESS_REPLY, VIEW_PROGRESS_REPLY)
        val uniteViewProgressReply = addClass(CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY, UNITE_VIEW_PROGRESS_REPLY)
        val dmViewReply = addClass(CHRONOS_ID_DM_VIEW_REPLY, DM_VIEW_REPLY)
        val addCustomRequest = addClass(CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST, ADD_CUSTOM_DANMAKU_REQUEST)
        val dmViewChangeRequest = addClass(CHRONOS_ID_DM_VIEW_CHANGE_REQUEST, DM_VIEW_CHANGE_REQUEST)
        val commandDanmakuSentRequest = addClass(CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST, COMMAND_DANMAKU_SENT_REQUEST)
        val commandDmListRequest = addClass(CHRONOS_ID_COMMAND_DM_LIST_REQUEST, COMMAND_DM_LIST_REQUEST)
        val commandDmListResponse = addClass(CHRONOS_ID_COMMAND_DM_LIST_RESPONSE, COMMAND_DM_LIST_RESPONSE)
        val videoDetailStateChangeRequest = addClass(CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST, VIDEO_DETAIL_STATE_CHANGE_REQUEST)
        val viewProgressDetail = addClass(CHRONOS_ID_VIEW_PROGRESS_DETAIL, VIEW_PROGRESS_DETAIL)
        val senderScan = findChronosMessageSenderType(classLoader, remoteHandler, function2Type, bridge)
        val senderType = senderScan.type?.also {
            classes += NamedClassSymbol(CHRONOS_ID_MESSAGE_SENDER, it.name)
        }

        if (rpcHandler != null) {
            val invokeMethods = rpcHandler.allMethods()
                .filter { it.name == "invoke" && it.parameterCount == 6 }
                .distinctBy(Method::toGenericString)
                .toList()
            if (
                (updateDetailStateRequest != null && hasDetailStateGetterMethods(updateDetailStateRequest, DETAIL_STATE_GETTERS)) ||
                openUrlRequest != null ||
                adDanmakuEventRequest != null ||
                notifyCommercialEventRequest != null
            ) {
                addMethods(CHRONOS_METHOD_RPC_RECEIVE, invokeMethods)
            }
            if (getViewProgressRequest != null && function2Type != null && (viewProgressReply != null || uniteViewProgressReply != null)) {
                addMethods(CHRONOS_METHOD_LOCAL_VIEW_PROGRESS, invokeMethods)
            }
            if (getDmViewRequest != null && dmViewReply != null && function2Type != null) {
                addMethods(CHRONOS_METHOD_LOCAL_DM_VIEW, invokeMethods)
            }
        }

        if (remoteHandler != null) {
            if (videoDetailStateChangeRequest != null &&
                hasDetailStateGetterMethods(videoDetailStateChangeRequest, VIDEO_DETAIL_STATE_CHANGE_GETTERS)
            ) {
                addMethods(
                    CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE,
                    remoteHandler.allMethods().filter {
                        it.name == "onVideoDetailStateChanged" &&
                            it.parameterCount == 1 &&
                            it.parameterTypes[0] == videoDetailStateChangeRequest
                    },
                )
            }
            if (viewProgressDetail != null) {
                addMethods(
                    CHRONOS_METHOD_REMOTE_VIEW_PROGRESS,
                    remoteHandler.allMethods().filter {
                        it.name == "configChronos" &&
                            it.parameterTypes.contentEquals(
                                arrayOf(viewProgressDetail, java.lang.Long.TYPE, java.lang.Long.TYPE),
                            )
                    },
                )
            }
            addMethods(
                CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU,
                remoteHandler.allMethods().filter {
                    it.name == "setCommandDanmakus" &&
                        it.parameterCount == 1 &&
                        List::class.java.isAssignableFrom(it.parameterTypes[0])
                },
            )
            addMethods(
                CHRONOS_METHOD_REMOTE_ADD_DANMAKU,
                remoteHandler.allMethods().filter {
                    it.name == "addDanmaku" &&
                        it.parameterCount == 4 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == java.lang.Integer.TYPE &&
                        Map::class.java.isAssignableFrom(it.parameterTypes[3])
                },
            )
            addMethods(
                CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE,
                remoteHandler.allMethods().filter {
                    it.name == "adDanmakuExposureRequest" &&
                        it.parameterTypes.contentEquals(arrayOf(List::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE))
                },
            )
        }

        if (senderType != null) {
            if (addCustomRequest != null || commandDanmakuSentRequest != null || (dmViewChangeRequest != null && dmViewReply != null)) {
                addMethods(
                    CHRONOS_METHOD_MESSAGE_SEND,
                    senderType.allMethods().filter {
                        it.isChronosMessageSendMethod()
                    },
                )
            }
            if (commandDmListRequest != null && commandDmListResponse != null && function2Type != null) {
                addMethods(
                    CHRONOS_METHOD_COMMAND_DM_LIST,
                    senderType.allMethods().filter {
                        it.isChronosCommandDmListMethod(function2Type)
                    },
                )
            }
        }

        val adDanmakuDelegate = addClass(CHRONOS_ID_AD_DANMAKU_DELEGATE, AD_DANMAKU_DELEGATE)
        adDanmakuDelegate?.let { delegate ->
            addMethods(
                CHRONOS_METHOD_AD_DANMAKU_FEED,
                delegate.allMethods().filter {
                    it.name == "feedData2Chronos" &&
                        it.parameterTypes.contentEquals(arrayOf(List::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE))
                },
            )
        }
        val interactLayerService = addClass(CHRONOS_ID_INTERACT_LAYER_SERVICE, INTERACT_LAYER_SERVICE)
        interactLayerService?.let { service ->
            if (viewProgressDetail != null) {
                addMethods(
                    CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS,
                    service.allMethods().filter {
                        it.name == "getViewProgressDetail" &&
                            it.parameterCount == 0 &&
                            viewProgressDetail.isAssignableFrom(it.returnType)
                    },
                )
            }
        }
        val geminiOperationWidget = addClass(CHRONOS_ID_GEMINI_OPERATION_WIDGET, GEMINI_OPERATION_WIDGET)
        geminiOperationWidget?.let { widget ->
            addMethods(
                CHRONOS_METHOD_GEMINI_OPERATION_RENDER,
                widget.allMethods().filter {
                    it.name in GEMINI_OPERATION_RENDER_METHOD_NAMES &&
                        it.parameterCount == 0 &&
                        it.returnType == java.lang.Void.TYPE
                },
            )
        }
        val geminiOperationObserver = addClass(CHRONOS_ID_GEMINI_OPERATION_OBSERVER, GEMINI_OPERATION_OBSERVER)
        geminiOperationObserver?.let { observer ->
            if (viewProgressDetail != null) {
                addMethods(
                    CHRONOS_METHOD_GEMINI_OPERATION_UPDATE,
                    observer.allMethods().filter {
                        it.name == "onUpdate" &&
                            it.parameterCount == 1 &&
                            viewProgressDetail.isAssignableFrom(it.parameterTypes[0])
                    },
                )
            }
        }

        val totalMethods = methodGroups.sumOf { it.methods.size }
        if (totalMethods == 0) {
            return SymbolScanResult.Missing("chronos promotion hook points not found")
        }
        val childHookPoints = buildChronosPromotionHookPointStatus(
            methodGroups = methodGroups,
            rpcHandler = rpcHandler,
            updateDetailStateRequest = updateDetailStateRequest,
            openUrlRequest = openUrlRequest,
            adDanmakuEventRequest = adDanmakuEventRequest,
            notifyCommercialEventRequest = notifyCommercialEventRequest,
            senderType = senderType,
            addCustomRequest = addCustomRequest,
            dmViewChangeRequest = dmViewChangeRequest,
            dmViewReply = dmViewReply,
            commandDanmakuSentRequest = commandDanmakuSentRequest,
            commandDmListRequest = commandDmListRequest,
            commandDmListResponse = commandDmListResponse,
            function2Type = function2Type,
            remoteHandler = remoteHandler,
            getViewProgressRequest = getViewProgressRequest,
            getDmViewRequest = getDmViewRequest,
            viewProgressReply = viewProgressReply,
            uniteViewProgressReply = uniteViewProgressReply,
            videoDetailStateChangeRequest = videoDetailStateChangeRequest,
            viewProgressDetail = viewProgressDetail,
            adDanmakuDelegate = adDanmakuDelegate,
            interactLayerService = interactLayerService,
            geminiOperationWidget = geminiOperationWidget,
            geminiOperationObserver = geminiOperationObserver,
            senderScan = senderScan,
        )
        val symbols = ChronosPromotionSymbols(
            classSymbols = classes.distinctBy { it.id },
            methodGroups = methodGroups.distinctBy { it.id },
            evidence = "classes=${classes.distinctBy { it.id }.size},methodGroups=${methodGroups.size},methods=$totalMethods,sender=${senderScan.evidence}",
        )
        return SymbolScanResult.Found(symbols, "ChronosPromotion", symbols.evidence, childHookPoints)
    }

    private fun buildChronosPromotionHookPointStatus(
        methodGroups: List<NamedMethodGroup>,
        rpcHandler: Class<*>?,
        updateDetailStateRequest: Class<*>?,
        openUrlRequest: Class<*>?,
        adDanmakuEventRequest: Class<*>?,
        notifyCommercialEventRequest: Class<*>?,
        senderType: Class<*>?,
        addCustomRequest: Class<*>?,
        dmViewChangeRequest: Class<*>?,
        dmViewReply: Class<*>?,
        commandDanmakuSentRequest: Class<*>?,
        commandDmListRequest: Class<*>?,
        commandDmListResponse: Class<*>?,
        function2Type: Class<*>?,
        remoteHandler: Class<*>?,
        getViewProgressRequest: Class<*>?,
        getDmViewRequest: Class<*>?,
        viewProgressReply: Class<*>?,
        uniteViewProgressReply: Class<*>?,
        videoDetailStateChangeRequest: Class<*>?,
        viewProgressDetail: Class<*>?,
        adDanmakuDelegate: Class<*>?,
        interactLayerService: Class<*>?,
        geminiOperationWidget: Class<*>?,
        geminiOperationObserver: Class<*>?,
        senderScan: ChronosSenderScan,
    ): List<HookPointStatus> {
        fun methodCount(id: String): Int =
            methodGroups.firstOrNull { it.id == id }?.methods?.size ?: 0

        fun status(id: String, methodId: String, missing: List<String>, evidence: String): HookPointStatus {
            val methods = methodCount(methodId)
            return when {
                missing.isNotEmpty() -> HookPointStatus(
                    id = id,
                    state = HookPointState.MISSING,
                    missing = "fail closed: ${missing.joinToString()}",
                    evidence = evidence,
                )
                methods == 0 -> HookPointStatus(
                    id = id,
                    state = HookPointState.MISSING,
                    missing = "fail closed: method group $methodId not found",
                    evidence = evidence,
                )
                else -> HookPointStatus.found(id, "ChronosPromotion.$methodId", "methods=$methods,$evidence")
            }
        }

        val receiveMissing = buildList {
            if (rpcHandler == null) add(CHRONOS_ID_RPC_HANDLER)
            if (updateDetailStateRequest == null) add(CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST)
            if (openUrlRequest == null) add(CHRONOS_ID_OPEN_URL_REQUEST)
            if (adDanmakuEventRequest == null) add(CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST)
            if (notifyCommercialEventRequest == null) add(CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST)
        }
        val senderMissing = buildList {
            if (senderType == null) add(senderScan.missing ?: CHRONOS_ID_MESSAGE_SENDER)
            if (addCustomRequest == null) add(CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST)
            if (commandDanmakuSentRequest == null) add(CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST)
            if (dmViewChangeRequest == null) add(CHRONOS_ID_DM_VIEW_CHANGE_REQUEST)
            if (dmViewReply == null) add(CHRONOS_ID_DM_VIEW_REPLY)
        }
        val commandDmListMissing = buildList {
            if (senderType == null) add(senderScan.missing ?: CHRONOS_ID_MESSAGE_SENDER)
            if (commandDmListRequest == null) add(CHRONOS_ID_COMMAND_DM_LIST_REQUEST)
            if (commandDmListResponse == null) add(CHRONOS_ID_COMMAND_DM_LIST_RESPONSE)
            if (function2Type == null) add(CHRONOS_ID_FUNCTION2)
        }
        val localViewProgressMissing = buildList {
            if (rpcHandler == null) add(CHRONOS_ID_RPC_HANDLER)
            if (getViewProgressRequest == null) add(CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST)
            if (function2Type == null) add(CHRONOS_ID_FUNCTION2)
            if (viewProgressReply == null && uniteViewProgressReply == null) add("$CHRONOS_ID_VIEW_PROGRESS_REPLY|$CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY")
        }
        val localDmViewMissing = buildList {
            if (rpcHandler == null) add(CHRONOS_ID_RPC_HANDLER)
            if (getDmViewRequest == null) add(CHRONOS_ID_GET_DM_VIEW_REQUEST)
            if (dmViewReply == null) add(CHRONOS_ID_DM_VIEW_REPLY)
            if (function2Type == null) add(CHRONOS_ID_FUNCTION2)
        }
        val remoteVideoDetailMissing = buildList {
            if (remoteHandler == null) add(CHRONOS_ID_REMOTE_HANDLER)
            if (videoDetailStateChangeRequest == null) add(CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST)
        }
        val remoteViewProgressMissing = buildList {
            if (remoteHandler == null) add(CHRONOS_ID_REMOTE_HANDLER)
            if (viewProgressDetail == null) add(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
        }
        val remoteHandlerMissing = buildList {
            if (remoteHandler == null) add(CHRONOS_ID_REMOTE_HANDLER)
        }
        val adDanmakuMissing = buildList {
            if (adDanmakuDelegate == null) add(CHRONOS_ID_AD_DANMAKU_DELEGATE)
        }
        val interactLayerMissing = buildList {
            if (interactLayerService == null) add(CHRONOS_ID_INTERACT_LAYER_SERVICE)
            if (viewProgressDetail == null) add(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
        }
        val geminiRenderMissing = buildList {
            if (geminiOperationWidget == null) add(CHRONOS_ID_GEMINI_OPERATION_WIDGET)
        }
        val geminiUpdateMissing = buildList {
            if (geminiOperationObserver == null) add(CHRONOS_ID_GEMINI_OPERATION_OBSERVER)
            if (viewProgressDetail == null) add(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
        }
        return listOf(
            status(
                id = HP_CHRONOS_RPC_RECEIVE,
                methodId = CHRONOS_METHOD_RPC_RECEIVE,
                missing = receiveMissing,
                evidence = "requests=${4 - receiveMissing.count { it != CHRONOS_ID_RPC_HANDLER }}/4",
            ),
            status(
                id = HP_CHRONOS_LOCAL_VIEW_PROGRESS,
                methodId = CHRONOS_METHOD_LOCAL_VIEW_PROGRESS,
                missing = localViewProgressMissing,
                evidence = "reply=${viewProgressReply != null},uniteReply=${uniteViewProgressReply != null}",
            ),
            status(
                id = HP_CHRONOS_LOCAL_DM_VIEW,
                methodId = CHRONOS_METHOD_LOCAL_DM_VIEW,
                missing = localDmViewMissing,
                evidence = "request=${getDmViewRequest != null},reply=${dmViewReply != null}",
            ),
            status(
                id = HP_CHRONOS_MESSAGE_SENDER,
                methodId = CHRONOS_METHOD_MESSAGE_SEND,
                missing = senderMissing,
                evidence = "sender=${senderScan.evidence},requests=${4 - senderMissing.count { it != senderScan.missing && it != CHRONOS_ID_MESSAGE_SENDER }}/4",
            ),
            status(
                id = HP_CHRONOS_COMMAND_DM_LIST,
                methodId = CHRONOS_METHOD_COMMAND_DM_LIST,
                missing = commandDmListMissing,
                evidence = "sender=${senderScan.evidence},request=${commandDmListRequest != null},response=${commandDmListResponse != null}",
            ),
            status(
                id = HP_CHRONOS_REMOTE_VIDEO_DETAIL_STATE,
                methodId = CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE,
                missing = remoteVideoDetailMissing,
                evidence = "request=${videoDetailStateChangeRequest != null}",
            ),
            status(
                id = HP_CHRONOS_REMOTE_VIEW_PROGRESS,
                methodId = CHRONOS_METHOD_REMOTE_VIEW_PROGRESS,
                missing = remoteViewProgressMissing,
                evidence = "detail=${viewProgressDetail != null}",
            ),
            status(
                id = HP_CHRONOS_REMOTE_COMMAND_DANMAKU,
                methodId = CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU,
                missing = remoteHandlerMissing,
                evidence = "remoteHandler=${remoteHandler != null}",
            ),
            status(
                id = HP_CHRONOS_REMOTE_ADD_DANMAKU,
                methodId = CHRONOS_METHOD_REMOTE_ADD_DANMAKU,
                missing = remoteHandlerMissing,
                evidence = "remoteHandler=${remoteHandler != null}",
            ),
            status(
                id = HP_CHRONOS_REMOTE_AD_FLOAT_EXPOSURE,
                methodId = CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE,
                missing = remoteHandlerMissing,
                evidence = "remoteHandler=${remoteHandler != null}",
            ),
            status(
                id = HP_CHRONOS_AD_DANMAKU_FEED,
                methodId = CHRONOS_METHOD_AD_DANMAKU_FEED,
                missing = adDanmakuMissing,
                evidence = "delegate=${adDanmakuDelegate != null}",
            ),
            status(
                id = HP_CHRONOS_INTERACT_LAYER_VIEW_PROGRESS,
                methodId = CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS,
                missing = interactLayerMissing,
                evidence = "service=${interactLayerService != null},detail=${viewProgressDetail != null}",
            ),
            status(
                id = HP_CHRONOS_GEMINI_OPERATION_RENDER,
                methodId = CHRONOS_METHOD_GEMINI_OPERATION_RENDER,
                missing = geminiRenderMissing,
                evidence = "widget=${geminiOperationWidget != null}",
            ),
            status(
                id = HP_CHRONOS_GEMINI_OPERATION_UPDATE,
                methodId = CHRONOS_METHOD_GEMINI_OPERATION_UPDATE,
                missing = geminiUpdateMissing,
                evidence = "observer=${geminiOperationObserver != null},detail=${viewProgressDetail != null}",
            ),
        )
    }

    private fun hasDetailStateGetterMethods(requestType: Class<*>, getters: List<String>): Boolean =
        getters.any { getter -> requestType.allMethods().any { it.name == getter && it.parameterCount == 0 } }

    private fun findChronosMessageSenderType(
        classLoader: ClassLoader,
        remoteHandler: Class<*>?,
        function2Type: Class<*>?,
        bridge: () -> DexKitBridge?,
    ): ChronosSenderScan {
        if (remoteHandler == null) {
            return ChronosSenderScan(type = null, evidence = "remoteHandler=false", missing = CHRONOS_ID_MESSAGE_SENDER)
        }
        val fields = runCatching { remoteHandler.allFields().toList() }.getOrElse { throwable ->
            val reason = "fields=${throwable.scanMessage()}"
            return ChronosSenderScan(type = null, evidence = reason, missing = "$CHRONOS_ID_MESSAGE_SENDER($reason)")
        }
        val senderErrors = ArrayList<String>()
        val remoteMethods = runCatching { remoteHandler.allMethods().toList() }.getOrElse { throwable ->
            senderErrors += "remoteMethods=${throwable.scanMessage()}"
            emptyList()
        }
        val fieldTypes = fields.map { it.type }.distinctBy { it.name }

        fun candidateOf(type: Class<*>, source: String): ChronosSenderCandidate? {
            if (type.isPrimitive || type.name == remoteHandler.name || !type.isConcreteHookClass()) return null
            val methods = runCatching { type.allMethods().toList() }.getOrElse { throwable ->
                senderErrors += "${type.name}.methods=${throwable.scanMessage()}"
                return null
            }
            return ChronosSenderCandidate(
                type = type,
                source = source,
                messageMethods = methods.count { it.isChronosMessageSendMethod() },
                commandMethods = function2Type?.let { function2 ->
                    methods.count { it.isChronosCommandDmListMethod(function2) }
                } ?: methods.count { it.isChronosSenderRequestMethod(function2Type, allowAbstract = false) },
            )
        }

        val directCandidates = fieldTypes
            .mapNotNull { type -> candidateOf(type, "field") }

        val senderInterfaces = (fieldTypes.asSequence().flatMap { sequenceOf(it) + it.chronosImplementedInterfaces() } +
            remoteMethods.asSequence().map { it.returnType })
            .filter { type -> type.isInterface && type.isChronosSenderInterface(function2Type) }
            .distinctBy { it.name }
            .toList()

        val implementorErrors = ArrayList<String>()
        val interfaceCandidates = senderInterfaces.flatMap { senderInterface ->
            val scan = findConcreteImplementors(classLoader, bridge, senderInterface.name)
            scan.error?.let { implementorErrors += it }
            scan.classes.mapNotNull { type -> candidateOf(type, "iface:${senderInterface.name.substringAfterLast('.')}") }
        }

        val candidates = (directCandidates + interfaceCandidates)
            .filter { it.messageMethods > 0 || it.commandMethods > 0 }
            .groupBy { it.type.name }
            .map { (_, sameType) ->
                val first = sameType.first()
                first.copy(source = sameType.map { it.source }.distinct().joinToString("+"))
            }
        val completeCandidates = candidates.filter { it.messageMethods > 0 && it.commandMethods > 0 }
        val directCompleteCandidates = completeCandidates.filter { it.source.split('+').contains("field") }
        val commandCandidates = candidates.filter { it.commandMethods > 0 }
        val directCommandCandidates = commandCandidates.filter { it.source.split('+').contains("field") }
        val messageCandidates = candidates.filter { it.messageMethods > 0 }
        val selected = when {
            directCompleteCandidates.size == 1 -> directCompleteCandidates.single()
            completeCandidates.size == 1 -> completeCandidates.single()
            directCommandCandidates.size == 1 -> directCommandCandidates.single()
            commandCandidates.size == 1 -> commandCandidates.single()
            messageCandidates.size == 1 -> messageCandidates.single()
            else -> null
        }
        val evidenceParts = listOf(
            "fields=${fieldTypes.size}",
            "interfaces=${senderInterfaces.size}",
            "candidates=${candidates.size}",
            "complete=${completeCandidates.size}",
            "source=${selected?.source ?: "-"}",
            "methods=${selected?.messageMethods ?: 0}/${selected?.commandMethods ?: 0}",
            "type=${selected?.type?.name ?: "-"}",
        )
        val evidence = (
            evidenceParts +
                senderErrors.take(1).map { "error=$it" } +
                implementorErrors.take(1).map { "scan=$it" }
            )
            .joinToString(",")
            .take(360)
        return ChronosSenderScan(
            type = selected?.type,
            evidence = evidence,
            missing = if (selected == null) "$CHRONOS_ID_MESSAGE_SENDER($evidence)" else null,
        )
    }

    private fun Method.isChronosMessageSendMethod(): Boolean =
        isChronosVoidLikeReturn() &&
            !Modifier.isStatic(modifiers) &&
            !Modifier.isAbstract(modifiers) &&
            parameterCount == 2 &&
            Map::class.java.isAssignableFrom(parameterTypes[1])

    private fun Method.isChronosCommandDmListMethod(function2Type: Class<*>): Boolean =
        isChronosSenderRequestMethod(function2Type, allowAbstract = false)

    private fun Method.isChronosSenderRequestMethod(function2Type: Class<*>?, allowAbstract: Boolean): Boolean =
        isChronosVoidLikeReturn() &&
            !Modifier.isStatic(modifiers) &&
            (allowAbstract || !Modifier.isAbstract(modifiers)) &&
            parameterCount == 5 &&
            Map::class.java.isAssignableFrom(parameterTypes[1]) &&
            Class::class.java.isAssignableFrom(parameterTypes[2]) &&
            (function2Type == null || parameterTypes[3].canAcceptChronosFunction2(function2Type)) &&
            (function2Type == null || parameterTypes[4].canAcceptChronosFunction2(function2Type))

    private fun Class<*>.isChronosSenderInterface(function2Type: Class<*>?): Boolean {
        val methods = allMethods().toList()
        return methods.any { it.isChronosSenderRequestMethod(function2Type, allowAbstract = true) } &&
            methods.any { it.isChronosSenderSyncMethod() }
    }

    private fun Method.isChronosSenderSyncMethod(): Boolean =
        !Modifier.isStatic(modifiers) &&
            parameterCount == 2 &&
            Class::class.java.isAssignableFrom(parameterTypes[0]) &&
            !isChronosVoidLikeReturn()

    private fun Class<*>.chronosImplementedInterfaces(): Sequence<Class<*>> = sequence {
        val seen = HashSet<String>()
        val pending = java.util.ArrayDeque<Class<*>>()
        pending.add(this@chronosImplementedInterfaces)
        while (!pending.isEmpty()) {
            val type = pending.removeFirst()
            type.interfaces.forEach { iface ->
                if (seen.add(iface.name)) {
                    yield(iface)
                    pending.add(iface)
                }
            }
            type.superclass?.let(pending::add)
        }
    }

    private fun Method.isChronosVoidLikeReturn(): Boolean =
        returnType == Void.TYPE || returnType.name == "kotlin.Unit"

    private fun Class<*>.canAcceptChronosFunction2(function2Type: Class<*>): Boolean =
        this == Any::class.java || isAssignableFrom(function2Type)

    private fun MutableList<Method>.addParserMethod(
        classLoader: ClassLoader,
        className: String,
        methodName: String,
        vararg parameterTypeNames: String,
    ) {
        val type = classLoader.loadClassOrNull(className) ?: return
        val parameterTypes = parameterTypeNames.map { resolveParameterType(classLoader, it) }
            .takeIf { it.none { resolved -> resolved == null } }
            ?.filterNotNull()
            ?.toTypedArray()
            ?: return
        val method = type.allMethods().firstOrNull {
            it.name == methodName && it.parameterTypes.contentEquals(parameterTypes)
        } ?: return
        add(method)
    }

    private fun resolveParameterType(classLoader: ClassLoader, typeName: String): Class<*>? = when (typeName) {
        "int" -> Int::class.javaPrimitiveType
        else -> classLoader.loadClassOrNull(typeName)
    }

    private fun Class<*>.noArgMethod(name: String): Method? =
        allMethods().firstOrNull {
            it.name == name &&
                it.parameterCount == 0 &&
                !Modifier.isStatic(it.modifiers)
        }?.apply { isAccessible = true }

    private fun findQuickReplyActionClasses(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
    ): QuickReplyActionClassScan {
        val currentBridge = bridge() ?: return QuickReplyActionClassScan(
            classes = emptyList(),
            candidates = 0,
            error = "DexKitBridge unavailable",
        )
        val stringCandidates = runCatching {
            currentBridge.findClass(
                FindClass.create()
                    .matcher(ClassMatcher.create().usingStrings(QUICK_REPLY_SHOW_PUBLISH_DIALOG_STRING)),
            ).map { it.name }.toList()
        }.getOrElse { throwable ->
            return QuickReplyActionClassScan(
                classes = emptyList(),
                candidates = 0,
                error = throwable.scanMessage(),
            )
        }
        val classes = stringCandidates
            .asSequence()
            .flatMap { name -> sequenceOf(name, name.substringBefore('$')).distinct() }
            .mapNotNull { classLoader.loadClassOrNull(it) }
            .filter { it.hasQuickReplyPublishDialogReference() }
            .flatMap { type ->
                generateSequence(type) { it.superclass?.takeIf { parent -> parent != Any::class.java } }
                    .filter { it.isCommentActionBaseType(emptyList()) || it.hasQuickReplyPublishDialogReference() }
            }
            .distinctBy { it.name }
            .toList()
        return QuickReplyActionClassScan(
            classes = classes,
            candidates = stringCandidates.size,
            error = null,
        )
    }

    private fun Class<*>.hasQuickReplyPublishDialogReference(): Boolean =
        declaredFields.any { field -> field.type.isQuickReplyDialogIntentType() } ||
            declaredClasses.any { child ->
                child.declaredFields.any { field -> field.type.isQuickReplyDialogIntentType() } &&
                    child.declaredFields.isNotEmpty()
            } ||
            allMethods().any { method ->
                method.returnType.isQuickReplyDialogIntentType() ||
                    method.parameterTypes.any { it.isQuickReplyDialogIntentType() }
            }

    private fun Class<*>.isCommentActionType(actionBases: List<Class<*>> = emptyList()): Boolean {
        if (actionBases.any { it.isAssignableFrom(this) }) return true
        val simpleName = simpleName
        val className = name
        return className.contains(".comment3.", ignoreCase = true) &&
            (simpleName.contains("Action", ignoreCase = true) ||
                simpleName.contains("Intent", ignoreCase = true))
    }

    private fun Class<*>.isCommentActionBaseType(actionBases: List<Class<*>> = emptyList()): Boolean {
        if (actionBases.any { it == this || it.isAssignableFrom(this) || this.isAssignableFrom(it) }) return true
        if (name == "Kj.AbstractC8070c" || name == "Wk.AbstractC24337c" || name == "Wk.c") return true
        if (simpleName.contains("Action", ignoreCase = true) && (name.startsWith("Kj.") || name.startsWith("Wk."))) return true
        return allMethods().any { method ->
            method.name == "a" &&
                method.returnType == Void.TYPE &&
                method.parameterCount == 2 &&
                method.parameterTypes[0].isInterface &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
    }

    private fun Class<*>.isQuickReplyDialogIntentType(): Boolean {
        val className = name
        return className.endsWith("PublishDialogIntent", ignoreCase = true) ||
            className.contains("PublishDialogIntent", ignoreCase = true) ||
            (
                className.contains(".comment3.", ignoreCase = true) &&
                    simpleName.contains("DialogIntent", ignoreCase = true) &&
                    declaredFields.any { field ->
                        field.type.isEnum || field.type == Boolean::class.javaPrimitiveType
                    }
            )
    }

    private fun Class<*>.isPlayViewRequestType(): Boolean {
        val methods = allMethods().toList()
        return methods.any { it.name == "getBvid" && it.parameterCount == 0 } &&
            methods.any { method ->
                method.parameterCount == 0 &&
                    method.name in setOf(
                        "getVod",
                        "getAid",
                        "getCid",
                        "getEpId",
                        "getIsNeedViewInfo",
                        "isNeedViewInfo",
                    )
            }
    }

    private fun findConcreteImplementors(
        classLoader: ClassLoader,
        bridge: () -> DexKitBridge?,
        interfaceName: String,
    ): ControllerClassScan {
        val candidates = linkedSetOf<Class<*>>()
        val errors = ArrayList<String>()
        val currentBridge = bridge()
        if (currentBridge != null) {
            runCatching {
                currentBridge.findClass(
                    FindClass.create()
                        .matcher(ClassMatcher.create().addInterface(interfaceName)),
                )
            }.onFailure { throwable ->
                errors += "DexKit implementor search failed for $interfaceName: ${throwable.scanMessage()}"
            }.getOrNull()?.forEach { classData ->
                runCatching {
                    Class.forName(classData.name, false, classLoader)
                }.onSuccess { type ->
                    candidates += type
                }.onFailure { throwable ->
                    errors += "restore ${classData.name} failed: ${throwable.scanMessage()}"
                }
            }
        } else {
            errors += "DexKitBridge unavailable for $interfaceName"
        }

        val interfaceType = classLoader.loadClassOrNull(interfaceName)
        val classes = candidates
            .asSequence()
            .filter { type -> type.isConcreteHookClass() }
            .filter { type -> interfaceType?.isAssignableFrom(type) == true }
            .distinctBy { it.name }
            .toList()
        return ControllerClassScan(
            classes = classes,
            error = errors.takeIf { it.isNotEmpty() }?.joinToString("; ")?.take(360),
        )
    }

    private fun Class<*>.currentPositionMethods(): List<Method> =
        allMethods()
            .filter {
                it.isConcreteInstanceHookMethod() &&
                    it.name == "getCurrentPosition" &&
                    it.parameterCount == 0 &&
                    it.returnType.isNumericType()
            }
            .onEach { it.isAccessible = true }
            .toList()

    private fun Class<*>.stateMethods(stateMethodNames: Set<String>): List<Method> =
        allMethods()
            .filter {
                it.isConcreteInstanceHookMethod() &&
                    it.name in stateMethodNames &&
                    it.parameterCount == 0 &&
                    it.returnType.isNumericType()
            }
            .onEach { it.isAccessible = true }
            .toList()

    private fun Class<*>.seekMethods(): List<Method> =
        allMethods()
            .filter {
                it.isConcreteInstanceHookMethod() &&
                    it.isSeekToMethod()
            }
            .onEach { it.isAccessible = true }
            .toList()

    private fun Method.isConcreteInstanceHookMethod(): Boolean =
        !Modifier.isStatic(modifiers) &&
            !Modifier.isAbstract(modifiers) &&
            !declaringClass.isInterface

    private fun Class<*>.isConcreteHookClass(): Boolean =
        !isInterface && !Modifier.isAbstract(modifiers)

    private fun Method.isSeekToMethod(): Boolean {
        if (name != "seekTo" || parameterCount !in 1..2) return false
        if (!parameterTypes[0].isNumericType()) return false
        return parameterCount == 1 || parameterTypes[1].isBooleanType()
    }

    private fun Class<*>.isNumericType(): Boolean =
        this == Int::class.javaPrimitiveType ||
            this == Int::class.javaObjectType ||
            this == Long::class.javaPrimitiveType ||
            this == Long::class.javaObjectType

    private fun Class<*>.isBooleanType(): Boolean =
        this == Boolean::class.javaPrimitiveType || this == Boolean::class.javaObjectType

    private fun Method.isDetailLikeInflateMethod(): Boolean {
        if (name != "b" || parameterCount != 3) return false
        val params = parameterTypes
        return Context::class.java.isAssignableFrom(params[0]) &&
            LayoutInflater::class.java.isAssignableFrom(params[1]) &&
            ViewGroup::class.java.isAssignableFrom(params[2])
    }

    private fun Method.isStoryBindMethod(): Boolean =
        parameterCount == 1 &&
            returnType == Void.TYPE &&
            parameterTypes[0].isStoryActionOwnerType()

    private fun Class<*>.isStoryActionOwnerType(): Boolean {
        if (name == STORY_ACTION_OWNER) return true
        val methods = allMethods().toList()
        return methods.any {
            it.name == "getData" && it.parameterCount == 0
        } && methods.any {
            it.name == "isActive" &&
                it.parameterCount == 0 &&
                (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.javaObjectType)
        }
    }

    private fun findClassNamesBySimpleName(
        bridge: () -> DexKitBridge?,
        simpleName: String,
    ): List<String> {
        val currentBridge = bridge() ?: return emptyList()
        return try {
            currentBridge.findClass(
                FindClass.create()
                    .searchPackages("com.bilibili", "tv.danmaku")
                    .matcher(ClassMatcher.create().className(simpleName, StringMatchType.Contains)),
            ).map { it.name }.toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun findClassNamesByNameContains(
        bridge: () -> DexKitBridge?,
        terms: List<String>,
    ): List<String> {
        val currentBridge = bridge() ?: return emptyList()
        return terms.flatMap { term ->
            try {
                currentBridge.findClass(
                    FindClass.create()
                        .matcher(ClassMatcher.create().className(term, StringMatchType.Contains)),
                ).map { it.name }.toList()
            } catch (t: Throwable) {
                throw IllegalStateException("DexKit class name search failed term=$term: ${t.scanMessage()}", t)
            }
        }.distinct()
    }

    private fun writeCache(
        prefs: SharedPreferences,
        fingerprint: String,
        symbols: BiliHookSymbols,
        log: (String, Throwable?) -> Unit,
    ) {
        runCatching {
            val committed = prefs.edit()
                .putString(KEY_FINGERPRINT, fingerprint)
                .putString(KEY_SYMBOLS, symbols.toJson().toString())
                .commit()
            if (!committed) {
                log("BiliSymbolResolver cache write failed: commit returned false", null)
            }
        }.onFailure { log("BiliSymbolResolver cache write failed", it) }
    }

    private fun publishStatus(
        prefs: SharedPreferences,
        symbols: BiliHookSymbols,
        log: (String, Throwable?) -> Unit,
    ) {
        runCatching {
            val notFound = symbols.hookPoints.filter { it.state == HookPointState.MISSING || it.state == HookPointState.ERROR }
            val notFoundPreview = notFound.take(3).joinToString { it.id.substringAfterLast('.') }
            val summary = when {
                notFound.isEmpty() && symbols.scanErrors.isEmpty() ->
                    "当前扫描结果：未发现缺失方法"
                symbols.scanErrors.isEmpty() ->
                    "当前扫描结果：${notFound.size} 个 HookPoint 异常${
                        if (notFoundPreview.isBlank()) "" else "：$notFoundPreview"
                    }"
                else ->
                    "当前扫描结果：${notFound.size} 个 HookPoint 异常，${symbols.scanErrors.size} 个扫描错误"
            }
            val report = buildString {
                appendLine("缓存指纹：${symbols.fingerprint}")
                if (notFound.isEmpty()) {
                    appendLine("未发现异常 HookPoint。")
                } else {
                    appendLine("异常的 HookPoint：")
                    notFound.forEach { status ->
                        appendLine("- ${status.id}: ${status.state.name}")
                        appendLine("  missing=${status.missing}")
                        if (status.target != "-") appendLine("  target=${status.target}")
                        if (status.evidence != "-") appendLine("  evidence=${status.evidence}")
                    }
                }
                if (symbols.scanErrors.isNotEmpty()) {
                    appendLine("Scan Errors:")
                    symbols.scanErrors.forEach { appendLine("- $it") }
                }
            }.trim()

            val editor = prefs.edit()
                .putString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_SUMMARY, summary)
                .putString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT, report)
                .putString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_UPDATED_AT, System.currentTimeMillis().toString())
            if (!editor.commit()) {
                log("BiliSymbolResolver publish status failed: commit returned false", null)
            }
        }.onFailure {
            log("BiliSymbolResolver publish status failed", it)
        }
    }

    private fun buildFingerprint(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val sourceDir = info.applicationInfo?.sourceDir.orEmpty()
            val source = File(sourceDir)
            val versionCode = context.packageVersionCodeLong()
            val versionName = info.versionName.orEmpty()
            listOf(
                context.packageName,
                versionCode.toString(),
                versionName,
                source.length().toString(),
                source.lastModified().toString(),
                BuildConfig.VERSION_CODE.toString(),
            ).joinToString("|")
        } catch (_: Throwable) {
            "unknown|${BuildConfig.VERSION_CODE}"
        }
    }

    @Suppress("DEPRECATION")
    private fun sourcePaths(context: Context): List<String> {
        val info = context.applicationInfo
        return buildList {
            info.sourceDir?.takeIf { it.isNotBlank() }?.let(::add)
            info.splitSourceDirs?.filterTo(this) { it.isNotBlank() }
        }.distinct()
    }

    private val ACCOUNT_CLASS_NAMES = listOf(
        "com.bilibili.lib.accounts.BiliAccounts",
        "com.bilibili.app.accounts.BiliAccounts",
        "com.bilibili.p4439app.accounts.BiliAccounts",
    )

    private val SHORT_ACCESS_METHODS = setOf("a", "b")

    private val SETTINGS_FRAGMENT_NAMES = listOf(
        "com.bilibili.p4439app.preferences.BiliPreferencesActivity\$BiliPreferencesFragment",
        "com.bilibili.app.preferences.BiliPreferencesActivity\$BiliPreferencesFragment",
        "com.bilibili.p4439app.preferences.fragment.WideBiliPreferencesFragment",
        "com.bilibili.app.preferences.fragment.WideBiliPreferencesFragment",
        "com.bilibili.p4439app.preferences.fragment.BaseWidePreferenceFragment",
        "com.bilibili.app.preferences.fragment.BaseWidePreferenceFragment",
    )

    private val PREFERENCE_CLASS_NAMES = listOf(
        "com.bilibili.p4439app.preferences.settingWide.CornerPreference",
        "com.bilibili.app.preferences.settingWide.CornerPreference",
        "tv.danmaku.p9138bili.widget.preference.BLPreference",
        "androidx.preference.Preference",
    )

    private const val SPLASH_GSON_CLASS = "com.google.gson.Gson"
    private val SPLASH_FAST_JSON_CLASSES = arrayOf(
        "com.alibaba.fastjson.JSON",
        "com.alibaba.fastjson2.JSON",
    )
    private val SPLASH_FAST_JSON_PARSE_METHODS = setOf("parse", "parseObject")
    private val SPLASH_KOTLINX_JSON_CLASSES = arrayOf(
        "kotlinx.serialization.json.Json",
        "kotlinx.serialization.p7923json.AbstractC137025Json",
    )

    private const val SHARE_LEGACY_RESULT = "com.bilibili.lib.sharewrapper.online.api.ShareClickResult"
    private const val SHARE_CHANNELS = "com.bilibili.lib.sharewrapper.online.api.ShareChannels"
    private const val SHARE_CHANNEL_ITEM = "com.bilibili.lib.sharewrapper.online.api.ShareChannels\$ChannelItem"
    private const val SHARE_CLICK_RESULT_DESCRIPTOR = "kntr.common.share.core.model.ShareClickResult"
    private const val SHARE_BASE_INFO_TO_STRING = "ShareBaseInfo(title="
    private val SHARE_CONTENT_CLASSES = arrayOf(
        "kntr.common.share.domain.v1.ShareContent",
        "p7645kntr.common.share.domain.p7866v1.ShareContent",
    )
    private val SHARE_BILI_CONTENT_CLASSES = arrayOf(
        "kntr.common.share.domain.v1.ShareBiliContent",
        "p7645kntr.common.share.domain.p7866v1.ShareBiliContent",
    )
    private val SHARE_COPY_CONTENT_CLASSES = arrayOf(
        "kntr.common.share.common.handler.copy.b",
        "p7645kntr.common.share.common.handler.p7848copy.C134543b",
    )
    private val SHARE_COPY_UTILITY_CLASSES = arrayOf(
        "kntr.common.share.common.handler.copy.a",
        "p7645kntr.common.share.common.handler.p7848copy.C134542a",
    )

    private val REWARD_ACTIVITY_CLASSES = arrayOf(
        "com.bilibili.ad.reward.activity.BaseRewardAdActivity",
        "com.bilibili.ad.reward.RewardAdActivity",
    )
    private const val REWARD_HEADER_VIEW = "com.bilibili.ad.reward.view.header.RewardAdHeaderView"
    private const val REWARD_COUNT_DOWN_TEXT_VIEW = "com.bilibili.ad.reward.view.header.CountDownTextView"
    private const val REWARD_MINI_GAME_ABILITY =
        "com.bilibili.lib.fasthybrid.game.engine.ability.impl.ad.BWARewardAbility"
    private val REWARD_JUMP_CLOCK_CLASSES = arrayOf("Ke.m", "Pe.k")

    private val TRY_FREE_NEED_TRIAL_CLASSES = arrayOf(
        "com.bapis.bilibili.pgc.gateway.player.v2.SceneControl",
        "com.bapis.bilibili.playershared.VideoVod",
    )
    private val TRY_FREE_STREAM_INFO_CLASSES = arrayOf(
        "com.bapis.bilibili.app.playurl.v1.StreamInfo",
        "com.bapis.bilibili.playershared.StreamInfo",
    )
    private val TEENAGERS_MODE_ACTIVITIES = arrayOf(
        "com.bilibili.app.preferences.TeenagersModeDialogActivity",
        "com.bilibili.p4439app.preferences.TeenagersModeDialogActivity",
        "tv.danmaku.bili.ui.teenagersmode.TeenagersModeDialogActivity",
        "com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity",
        "com.bilibili.teenagersmode.p6010ui.TeenagersModeActivity",
        "com.bilibili.teenagersmode.ui.TeenagersModeActivity",
        "com.bilibili.teenagersmode.osteen.OSTeensParentControlRedirectActivity",
    )

    private val MINE_FRAGMENT_CLASS_NAMES = listOf(
        "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment",
        "tv.danmaku.p9138bili.p9228ui.main2.p9247mine.HomeUserCenterFragment",
        "p4235tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment",
    )

    private val MINE_VIP_VIEW_CLASS_NAMES = listOf(
        "tv.danmaku.bili.ui.main2.mine.widgets.MineVipEntranceView",
        "tv.danmaku.p9138bili.p9228ui.main2.p9247mine.widgets.MineVipEntranceView",
        "tv.danmaku.p9138bili.p9228ui.main2.p9247mine.modularvip.VipEntranceView",
    )

    private val MINE_VIP_MANAGER_CLASS_NAMES = listOf(
        "tv.danmaku.bili.ui.main2.mine.modularvip.MineVipModuleManager",
        "p4235tv.danmaku.bili.ui.main2.mine.modularvip.MineVipModuleManager",
    )

    private val DOWNLOAD_THREAD_LISTENER_CLASS_TERMS = listOf(
        "Download",
        "download",
        "Thread",
        "thread",
        "Listener",
        "listener",
        "Cache",
        "cache",
        "Offline",
        "offline",
    )

    private val DOWNLOAD_THREAD_REPORT_CLASS_TERMS = listOf(
        "Download",
        "download",
        "Thread",
        "thread",
        "Report",
        "report",
        "Cache",
        "cache",
        "Offline",
        "offline",
    )

    private const val STORY_VIDEO_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity"
    private const val THEME_STORE_ACTIVITY = "tv.danmaku.bili.ui.theme.ThemeStoreActivity"
    private const val BILI_SKIN_LIST = "tv.danmaku.bili.ui.theme.api.BiliSkinList"
    private const val BILI_SKIN = "tv.danmaku.bili.ui.theme.api.BiliSkin"
    private const val STORY_VIDEO_FRAGMENT = "com.bilibili.video.story.StoryVideoFragment"
    private const val STORY_PAGER_PLAYER = "com.bilibili.video.story.player.StoryPagerPlayer"
    private const val STORY_FEED_RESPONSE = "com.bilibili.video.story.api.StoryFeedResponse"
    private const val STORY_AD_RERANK_TASK = "com.bilibili.video.story.action.service.StoryAdReRankService\$2"
    private const val STORY_INFO_MODULE = "com.bilibili.video.story.module.StoryInfoModule"
    private const val STORY_RIGHT_MODULE = "com.bilibili.video.story.module.StoryRightModule"
    private const val STORY_BOTTOM_MODULE = "com.bilibili.video.story.module.StoryBottomModule"
    private const val STORY_DETAIL = "com.bilibili.video.story.StoryDetail"
    private val STORY_COMMENT_OUTER_CANDIDATES = listOf("StoryCommentHelper", "C", "B") + ('D'..'Z').map { it.toString() }
    private val STORY_COMMENT_VERTICAL_CONTAINER_CLASSES = (listOf("StoryCommentHelper\$VerticalContainerV2", "C\$f", "B\$f") + ('D'..'Z').map { "$it\$f" }).map { "com.bilibili.video.story.action.$it" }.toTypedArray()
    private val STORY_COMMENT_LANDSCAPE_CONTAINER_CLASSES = (listOf("StoryCommentHelper\$d", "C\$d", "B\$d") + ('D'..'Z').map { "$it\$d" }).map { "com.bilibili.video.story.action.$it" }.toTypedArray()
    private const val STORY_INTRO_COMMENT_SERVICE = "com.bilibili.video.story.action.widget.comment.p"
    private const val STORY_TAB_PACKAGE_PREFIX = "com.bilibili.video.story.tab."
    private const val KOTLIN_UNIT = "kotlin.Unit"
    private const val ANDROIDX_VIEW_BINDING = "androidx.viewbinding.ViewBinding"
    private const val G_AD_BIZ_KT = "com.bilibili.gripper.api.ad.biz.GAdBizKt"
    private const val G_AD_VIDEO_DETAIL = "com.bilibili.gripper.api.ad.biz.GAdVideoDetail"
    private const val I_AD_UNDER_PLAYER = "com.bilibili.gripper.api.ad.biz.videodetail.underplayer.IAdUnderPlayer"
    private const val I_AD_PANEL = "com.bilibili.gripper.api.ad.biz.videodetail.IAdPanel"
    private const val I_VD_PAUSED_PAGE = "com.bilibili.gripper.api.ad.biz.videodetail.pausedpage.IVDPausedPage"
    private const val I_AD_VIDEO_RELATE = "com.bilibili.gripper.api.ad.biz.videodetail.relate.IAdVideoRelate"
    private const val I_AD_MERCHANDISE = "com.bilibili.gripper.api.ad.biz.videodetail.merchandise.IAdMerchandise"
    private const val RELATE_GAME_COMPONENT_PACKAGE =
        "com.bilibili.ship.theseus.united.page.intro.module.relate.game"
    private val RELATE_GAME_COMPONENT_CANDIDATE_NAMES = listOf(
        "com.bilibili.ship.theseus.united.page.intro.module.relate.game.e",
        "com.bilibili.ship.theseus.united.page.intro.module.relate.game.d",
        "com.bilibili.ship.theseus.united.page.intro.module.relate.game.f",
        "com.bilibili.ship.theseus.united.page.intro.module.relate.game.RelateGameComponent",
    )
    private val GEMINI_BINDING_COMPONENT_CLASSES = arrayOf(
        "com.bilibili.app.gemini.ui.l",
        "com.bilibili.app.gemini.ui.m",
        "com.bilibili.app.gemini.ui.k",
        "com.bilibili.app.gemini.ui.n",
        "com.bilibili.app.gemini.ui.o",
        "com.bilibili.app.gemini.ui.d",
        "com.bilibili.app.gemini.ui.e",
    )
    private const val GEMINI_SIMPLE_VIEW_ENTRY = "com.bilibili.app.gemini.ui.UIComponent\$b"
    private const val HOME_MENU_ITEM_CLASS = "com.bilibili.lib.homepage.startdust.menu.a"
    private val HOME_BASE_MAIN_FRAME_FRAGMENTS = setOf(
        "tv.danmaku.bili.ui.main2.basic.BaseMainFrameFragment",
        "tv.danmaku.p9138bili.p9228ui.main2.basic.BaseMainFrameFragment",
        "tv.danmaku.bili.ui.main2.MainFragment",
        "tv.danmaku.p9138bili.p9228ui.main2.MainFragment",
    )
    private const val HOME_MAIN_FRAGMENT = "tv.danmaku.bili.ui.main2.MainFragment"
    private const val HOME_DEFAULT_SEARCH_WORD_CLASS = "com.bilibili.app.comm.list.common.api.b"
    private val BOTTOM_TAB_HOST_CLASSES = setOf(
        "com.bilibili.lib.homepage.widget.TabHost",
        "com.bilibili.p5690lib.p5708homepage.widget.TabHost",
        "tv.danmaku.bili.ui.main2.widget.TabHost",
        "tv.danmaku.p9138bili.p9228ui.main2.widget.TabHost",
    )
    private val PEGASUS_RESPONSE_CLASSES = arrayOf(
        "com.bilibili.pegasus.data.base.PegasusResponse",
        "com.bilibili.pegasus.p5730data.p5731base.PegasusResponse",
        "com.bilibili.pegasus.p5730data.request.PegasusResponseWrapper",
    )
    private const val PEGASUS_HOLDER_DATA = "com.bilibili.pegasus.PegasusHolderData"
    private val BASE_PEGASUS_DATA_CLASSES = arrayOf(
        "com.bilibili.pegasus.data.base.BasePegasusData",
        "com.bilibili.pegasus.p5730data.p5731base.BasePegasusData",
    )
    private const val PEGASUS_HOLDER_STYLE = "com.bilibili.pegasus.HolderStyle"
    private const val AD_INFO = "com.bilibili.adcommon.data.IAdInfo"
    private val HOME_FRAGMENT_V2_CLASSES = arrayOf(
        "tv.danmaku.bili.ui.main2.HomeFragmentV2",
        "tv.danmaku.p9138bili.p9228ui.main2.HomeFragmentV2",
    )
    private val HOME_TAB_RESOURCE_CLASSES = arrayOf(
        "tv.danmaku.bili.ui.main2.resource.z",
        "tv.danmaku.p9138bili.p9228ui.main2.resource.z",
    )
    private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
    private val BASE_HOME_FRAGMENT_CLASSES = arrayOf(
        "tv.danmaku.bili.home.tab.page.BaseHomeFragment",
        "tv.danmaku.p9138bili.p9170home.p9173tab.p9174page.BaseHomeFragment",
    )
    private val HOME_COMPONENT_LIFECYCLE_METHODS = listOf(
        "onAttach",
        "onCreate",
        "onCreateView",
        "onViewCreated",
        "onResume",
    )
    private val NUMBER_FORMAT_CLASS_NAMES = listOf(
        "com.bilibili.base.util.NumberFormat",
        "com.bilibili.p4566base.p4568util.NumberFormat",
        "com.bilibili.n9.util.NumberFormat",
        "com.bilibili.lib.utils.NumberFormat",
    )
    private val FULL_NUMBER_FORMAT_METHOD_NAMES = setOf(
        "format",
        "formatWithComma",
    )
    private val THESEUS_TAB_PAGER_SERVICE = arrayOf(
        "com.bilibili.ship.theseus.united.page.tab.TheseusTabPagerService",
        "com.bilibili.p5797ship.theseus.united.p5850page.p5861tab.TheseusTabPagerService",
    )
    private val COMMENT_VIEW_MODEL_CLASSES = arrayOf(
        "com.bilibili.app.comment3.viewmodel.CommentViewModel",
        "com.bilibili.p4439app.comment3.viewmodel.CommentViewModel",
    )
    private val COMMENT_ACTION_BASE_CLASSES = arrayOf(
        "Kj.AbstractC8070c",
        "Kj.c",
        "Wk.AbstractC24337c",
        "Wk.c",
    )
    private const val QUICK_REPLY_SHOW_PUBLISH_DIALOG_STRING = "ShowPublishDialog(args="
    private val QUICK_REPLY_DIALOG_COLLECTOR_CLASSES = (1..50).flatMap { i ->
        listOf(
            "com.bilibili.app.comment3.ui.CommentContainerImpl\$attachRepository\$$i",
            "com.bilibili.p4439app.comment3.p4518ui.CommentContainerImpl\$attachRepository\$$i",
            "com.bilibili.app.comment3.ui.CommentContainerImpl\$attachRepository\$$i\$C636262",
            "com.bilibili.p4439app.comment3.p4518ui.CommentContainerImpl\$attachRepository\$$i\$C636262",
            "com.bilibili.app.comment3.ui.CommentContainerImpl\$attachRepository\$$i\$2",
            "com.bilibili.p4439app.comment3.p4518ui.CommentContainerImpl\$attachRepository\$$i\$2",
        )
    }.toTypedArray()
    private val CMT_VOTE_WIDGET_CLASSES = arrayOf(
        "com.bilibili.app.comment.ext.widgets.CmtVoteWidget",
        "com.bilibili.p4439app.comment.p4511ext.widgets.CmtVoteWidget",
    )
    private val CMT_MOUNT_WIDGET_CLASSES = arrayOf(
        "com.bilibili.app.comment.ext.widgets.CmtMountWidget",
        "com.bilibili.p4439app.comment.p4511ext.widgets.CmtMountWidget",
    )
    private val COMMENT_VOTE_VIEW_CLASSES = arrayOf(
        "com.bilibili.app.comment3.ui.widget.CommentVoteView",
        "com.bilibili.p4439app.comment3.p4518ui.widget.CommentVoteView",
    )
    private val COMMENT_FOLLOW_WIDGET_CLASSES = arrayOf(
        "com.bilibili.app.comm.comment2.phoenix.view.CommentFollowWidget",
        "com.bilibili.p4439app.p4450comm.comment2.phoenix.p4467view.CommentFollowWidget",
    )
    private val COMMENT_HEADER_DECORATIVE_VIEW_CLASSES = arrayOf(
        "com.bilibili.app.comment3.ui.widget.CommentHeaderDecorativeView",
        "com.bilibili.p4439app.comment3.p4518ui.widget.CommentHeaderDecorativeView",
    )
    private val COMMENT_CONTENT_CLASSES = arrayOf(
        "com.bapis.bilibili.main.community.reply.v1.Content",
        "com.bapis.bilibili.p4311main.community.reply.p4312v1.Content",
    )
    private val COMMENT_SUBJECT_CONTROL_CLASSES = arrayOf(
        "com.bapis.bilibili.main.community.reply.v1.SubjectControl",
        "com.bapis.bilibili.p4311main.community.reply.p4312v1.SubjectControl",
    )
    private val COMMENT_SUBJECT_DESC_CLASSES = arrayOf(
        "com.bapis.bilibili.main.community.reply.v2.SubjectDescriptionReply",
        "com.bapis.bilibili.p4311main.community.reply.p4313v2.SubjectDescriptionReply",
    )
    private val COMMENT_MAIN_LIST_OBSERVERS = arrayOf(
        "com.bapis.bilibili.main.community.reply.v1.ReplyMossKtxKt\$suspendMainList\$\$inlined\$suspendCall\$1",
        "com.bapis.bilibili.main.community.reply.v1.KReplyMoss\$mainList\$\$inlined\$suspendCall\$2",
        "com.bapis.bilibili.p4311main.community.reply.p4312v1.ReplyMossKtxKt\$suspendMainList\$\$inlined\$suspendCall\$1",
        "com.bapis.bilibili.p4311main.community.reply.p4312v1.KReplyMoss\$mainList\$\$inlined\$suspendCall\$2",
    )
    private val VOTE_WIDGET_METHOD_NAMES = setOf("a", "setData", "setVoteData", "bindData", "update", "refresh", "bind", "onBind")
    private val FOLLOW_WIDGET_METHOD_NAMES = setOf("W", "setData", "bindData", "update", "refresh", "bind", "onBind")
    private val HEADER_DECORATIVE_METHOD_NAMES = setOf("a", "setData", "bindData", "update", "refresh", "submitList")
    private const val PLAYER_CORE_SERVICE_INTERFACE = "tv.danmaku.biliplayerv2.service.IPlayerCoreService"
    private const val CARD_PLAYER_CONTEXT_INTERFACE = "tv.danmaku.video.bilicardplayer.ICardPlayerContext"
    private val PLAY_VIEW_METHOD_NAMES = setOf("executePlayView", "executePlayViewUnite", "playViewUnite", "playView")
    private val STATE_METHOD_NAMES = setOf("getState")
    private val CARD_STATE_METHOD_NAMES = setOf("getPlayerState", "getState")
    private val PLAYER_MOSS_CANDIDATES = arrayOf(
        "com.bapis.bilibili.app.playerunite.v1.PlayerMoss",
        "com.bapis.bilibili.app.playerunite.v1.KPlayerMoss",
        "com.bapis.bilibili.p4218app.playerunite.p4240v1.PlayerMoss",
        "com.bapis.bilibili.p4218app.playerunite.p4240v1.KPlayerMoss",
    )
    private const val PROGRESS_BAR_CLASS = "android.widget.ProgressBar"
    private const val PANEL_WIDGET_KT_CLASS = "com.bilibili.inline.panel.PanelWidgetKt"
    private const val STORY_SEEK_BAR_CLASS = "com.bilibili.video.story.view.StorySeekBar"
    private val INLINE_PROGRESS_CLASSES = setOf(
        "com.bilibili.app.comm.list.common.inline.widgetV3.InlineProgressWidgetV3",
        "com.bilibili.p4439app.p4450comm.p4472list.common.inline.widgetV3.InlineProgressWidgetV3",
        "com.bilibili.p4440app.p4451comm.p4473list.common.inline.widgetV3.InlineProgressWidgetV3",
    )
    private const val DETAIL_LIKE_COMPONENT =
        "com.bilibili.ship.theseus.united.page.intro.module.kingposition.KingPositionComponent2\$LikeComponent"
    private const val DETAIL_LIKE_STATE_OWNER =
        "com.bilibili.ship.theseus.united.page.intro.module.kingposition.KingPositionService\$d"
    private const val STORY_ACTION_OWNER = "com.bilibili.video.story.action.InterfaceC24213g"
    private const val STORY_LIKE_WIDGET = "com.bilibili.video.story.action.widget.StoryLikeWidget"
    private const val STORY_LANDSCAPE_LIKE_WIDGET = "com.bilibili.video.story.action.widget.StoryLandscapeLikeWidget"
    private const val GEMINI_PLAYER_LIKE_WIDGET = "com.bilibili.app.gemini.player.widget.like.GeminiPlayerLikeWidget"
    private const val CHRONOS_ID_RPC_HANDLER = "rpcHandler"
    private const val CHRONOS_ID_REMOTE_HANDLER = "remoteHandler"
    private const val CHRONOS_ID_MESSAGE_SENDER = "messageSender"
    private const val CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST = "addCustomDanmakuRequest"
    private const val CHRONOS_ID_DM_VIEW_CHANGE_REQUEST = "dmViewChangeRequest"
    private const val CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST = "commandDanmakuSentRequest"
    private const val CHRONOS_ID_COMMAND_DM_LIST_REQUEST = "commandDmListRequest"
    private const val CHRONOS_ID_COMMAND_DM_LIST_RESPONSE = "commandDmListResponse"
    private const val CHRONOS_ID_AD_DANMAKU_DELEGATE = "adDanmakuDelegate"
    private const val CHRONOS_ID_INTERACT_LAYER_SERVICE = "interactLayerService"
    private const val CHRONOS_ID_GEMINI_OPERATION_WIDGET = "geminiOperationWidget"
    private const val CHRONOS_ID_GEMINI_OPERATION_OBSERVER = "geminiOperationObserver"
    private const val CHRONOS_ID_VIEW_PROGRESS_DETAIL = "viewProgressDetail"
    private const val CHRONOS_ID_VIEW_PROGRESS_REPLY = "viewProgressReply"
    private const val CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY = "uniteViewProgressReply"
    private const val CHRONOS_ID_DM_VIEW_REPLY = "dmViewReply"
    private const val CHRONOS_ID_GET_DM_VIEW_REQUEST = "getDmViewRequest"
    private const val CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST = "getViewProgressRequest"
    private const val CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST = "updateDetailStateRequest"
    private const val CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST = "videoDetailStateChangeRequest"
    private const val CHRONOS_ID_OPEN_URL_REQUEST = "openUrlRequest"
    private const val CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST = "adDanmakuEventRequest"
    private const val CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST = "notifyCommercialEventRequest"
    private const val CHRONOS_ID_FUNCTION2 = "function2"
    private const val CHRONOS_METHOD_RPC_RECEIVE = "rpcReceive"
    private const val CHRONOS_METHOD_LOCAL_VIEW_PROGRESS = "localViewProgress"
    private const val CHRONOS_METHOD_LOCAL_DM_VIEW = "localDmView"
    private const val CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE = "remoteVideoDetailState"
    private const val CHRONOS_METHOD_REMOTE_VIEW_PROGRESS = "remoteViewProgress"
    private const val CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU = "remoteCommandDanmaku"
    private const val CHRONOS_METHOD_REMOTE_ADD_DANMAKU = "remoteAddDanmaku"
    private const val CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE = "remoteAdFloatExposure"
    private const val CHRONOS_METHOD_MESSAGE_SEND = "messageSend"
    private const val CHRONOS_METHOD_COMMAND_DM_LIST = "commandDmList"
    private const val CHRONOS_METHOD_AD_DANMAKU_FEED = "adDanmakuFeed"
    private const val CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS = "interactLayerViewProgress"
    private const val CHRONOS_METHOD_GEMINI_OPERATION_RENDER = "geminiOperationRender"
    private const val CHRONOS_METHOD_GEMINI_OPERATION_UPDATE = "geminiOperationUpdate"
    private const val CHRONOS_RPC_HANDLER =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.a"
    private const val REMOTE_SERVICE_HANDLER =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.remote.RemoteServiceHandler"
    private const val ADD_CUSTOM_DANMAKU_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.AddCustomDanmaku\$Request"
    private const val DM_VIEW_CHANGE_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.DmViewChange\$Request"
    private const val COMMAND_DANMAKU_SENT_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.CommandDanmakuSent\$Request"
    private const val COMMAND_DM_LIST_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.CommandDmListRequest\$Request"
    private const val COMMAND_DM_LIST_RESPONSE =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.CommandDmListRequest\$Response"
    private const val AD_DANMAKU_DELEGATE =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.AdDanmakuDelegate"
    private const val INTERACT_LAYER_SERVICE =
        "tv.danmaku.biliplayerv2.service.interact.biz.InteractLayerService"
    private const val GEMINI_OPERATION_WIDGET =
        "com.bilibili.app.gemini.player.widget.operation.a"
    private val GEMINI_OPERATION_RENDER_METHOD_NAMES = setOf("i", "j", "h", "k")
    private const val GEMINI_OPERATION_OBSERVER =
        "com.bilibili.app.gemini.player.widget.operation.a\$d"
    private const val VIEW_PROGRESS_DETAIL =
        "tv.danmaku.biliplayerv2.service.interact.biz.model.viewprogress.uniteviewprogress.ViewProgressDetail"
    private const val VIEW_PROGRESS_REPLY =
        "com.bapis.bilibili.app.view.v1.ViewProgressReply"
    private const val UNITE_VIEW_PROGRESS_REPLY =
        "com.bapis.bilibili.app.viewunite.v1.ViewProgressReply"
    private const val DM_VIEW_REPLY =
        "com.bapis.bilibili.community.service.dm.v1.DmViewReply"
    private const val GET_DM_VIEW_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.GetDmView\$Request"
    private const val GET_VIEW_PROGRESS_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.GetViewProgress\$Request"
    private const val UPDATE_VIDEO_DETAIL_STATE_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.UpdateVideoDetailState\$Request"
    private const val VIDEO_DETAIL_STATE_CHANGE_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.VideoDetailStateChange\$Request"
    private const val OPEN_URL_SCHEME_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.OpenUrlScheme\$Request"
    private const val AD_DANMAKU_EVENT_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.AdDanmakuEvent\$Request"
    private const val NOTIFY_COMMERCIAL_EVENT_REQUEST =
        "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.NotifyCommercialEvent\$Request"
    private const val KOTLIN_FUNCTION2 = "kotlin.jvm.functions.Function2"
    private const val BILI_UPGRADE_INFO = "tv.danmaku.bili.update.model.BiliUpgradeInfo"
    private val BLOCK_UPDATE_STRING_RULES = listOf(
        listOf("Do sync http request."),
        listOf("Http request result %s, saved to file cache."),
        listOf("Nothing to update, clean caches."),
        listOf("code", "message"),
    )
    private val DETAIL_STATE_GETTERS = listOf(
        "getFollowStates",
        "getReserveState",
        "getClockInState",
        "getVoteState",
    )
    private val VIDEO_DETAIL_STATE_CHANGE_GETTERS = listOf(
        "getFollowStates",
        "getReserveState",
        "getClockInState",
        "getVoteState",
        "getActivityState",
    )
    private val VIDEO_DETAIL_RELATE_RESPONSE_CLASSES = arrayOf(
        "com.bapis.bilibili.app.viewunite.common.Relates",
        "com.bapis.bilibili.app.viewunite.v1.Relates",
        "com.bapis.bilibili.app.viewunite.v1.RelatesFeedReply",
        "com.bapis.bilibili.app.view.v1.RelatesFeedReply",
        "com.bapis.bilibili.app.view.v1.ViewReply",
        "com.bapis.bilibili.app.view.v1.PlayerRelatesReply",
    )
    private const val DETAIL_RELATE_SERVICE_CLASS =
        "com.bilibili.ship.theseus.united.page.intro.module.relate.DetailRelateService"
}

private data class ControllerClassScan(
    val classes: List<Class<*>>,
    val error: String?,
)

private data class RelateGameComponentScan(
    val type: Class<*>?,
    val evidence: String,
)

private data class ChronosSenderScan(
    val type: Class<*>?,
    val evidence: String,
    val missing: String?,
)

private data class ChronosSenderCandidate(
    val type: Class<*>,
    val source: String,
    val messageMethods: Int,
    val commandMethods: Int,
)

private data class QuickReplyActionClassScan(
    val classes: List<Class<*>>,
    val candidates: Int,
    val error: String?,
) {
    val evidence: String =
        "quickClasses=${classes.size},quickCandidates=$candidates" +
            error?.let { ",quickError=${it.take(120)}" }.orEmpty()
}

private data class ClassStringScan(
    val type: Class<*>?,
    val error: String? = null,
    val candidates: Int = 0,
) {
    fun missingReason(defaultReason: String): String =
        error?.let { "$defaultReason: $it" } ?: defaultReason
}

private sealed class SymbolScanResult<out T> {
    data class Found<T : Any>(
        val value: T,
        val target: String,
        val evidence: String,
        val hookPoints: List<HookPointStatus> = emptyList(),
    ) : SymbolScanResult<T>()

    data class Missing(
        val reason: String,
        val hookPoints: List<HookPointStatus> = emptyList(),
    ) : SymbolScanResult<Nothing>()
}
