package io.github.bbzq.feats.symbol

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.bbzq.BuildConfig
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.symbol.dexkit.DexKitBridgeProvider
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge

object BiliSymbolResolver {
    private const val HP_ACCOUNT_ACCESS_KEY = "AccessKeyHook.BiliAccounts"

    private const val HP_SETTINGS_FRAGMENT = "SettingHook.PreferenceFragments"

    private const val HP_BLOCK_UPDATE = "BlockUpdateHook.UpdateCheck"

    private const val HP_MINE_VIP = "MineProfileHook.VipEntrance"

    private const val HP_SPLASH_AD = "SplashAdHook.JsonParsers"

    private const val HP_SHARE = "ShareHook.InstallPoints"

    private const val HP_REWARD_AD = "RewardAdHook.InstallPoints"

    private const val HP_TRY_FREE_QUALITY = "TryFreeQualityHook.GeneratedMessages"

    private const val HP_TEENAGERS_MODE = "TeenagersModeHook.DialogActivity"

    private const val HP_DOWNLOAD_THREAD_LISTENER = "DownloadThreadHook.Listener"

    private const val HP_STORY_PLAYER_AD = "StoryPlayerAdHook.InstallPoints"

    private const val HP_STORY_FULLSCREEN = "StoryFullscreenHook.StoryVideoActivity"

    private const val HP_STORY_DANMAKU = "StoryDanmakuHook.InstallPoints"

    private const val HP_STORY_COMPONENT_ALPHA = "StoryComponentAlphaHook.InstallPoints"

    private const val HP_VIDEO_DETAIL_BANNER_AD = "VideoDetailBannerAdHook.InstallPoints"

    private const val HP_VIDEO_DETAIL_RELATE_FEED = "VideoDetailRelateFilterHook.InstallPoints"

    private const val HP_HOME_TOP_BAR = "HomeTopBarPurifyHook.InstallPoints"

    private const val HP_HOME_FRAME = "HomeFrameHook.TabData"

    private const val HP_BOTTOM_BAR = "BottomBarHook.InstallPoints"

    private const val HP_HOME_RECOMMEND_FEED = "HomeRecommendFeed.Pegasus"

    private const val HP_HOME_RECOMMEND_TABS = "HomeRecommendTabs.BuildTabs"

    private const val HP_HOME_COMPONENT_HIDE = "HomeComponentHideHook.Components"

    private const val HP_VIDEO_COMMENT = "VideoCommentHook.InstallPoints"

    private const val HP_SKIP_VIDEO_AD = "SkipVideoAdHook.InstallPoints"

    private const val HP_SKIP_VIDEO_AD_PROGRESS = "SkipVideoAdProgressHook.InstallPoints"

    private const val HP_SKIP_VIDEO_AD_AUTO_LIKE = "SkipVideoAdAutoLike.InstallPoints"

    private const val HP_CHRONOS_PROMOTION = "ChronosPromotionHook.InstallPoints"

    private const val HP_FULL_NUMBER_FORMAT = "FullNumberFormatHook.NumberFormat"

    private const val HP_TRIPLE_SPEED = "TripleSpeedHook.ExperimentReader"

    private const val HP_CUSTOM_THEME = "CustomThemeHook.ThemeStore"

    private const val HP_CUSTOM_SKIN = "CustomSkinHook.GarbResolver"

    private const val HP_VIDEO_QUALITY = "VideoQualityHook.QualityStrategy"

    private const val HP_VIDEO_END_RECOMMEND = "VideoEndRecommendHook.Theseus2Decision"

    const val CACHE_PREFS_NAME = "bbzq_symbol_cache"

    private const val PREFS_NAME = CACHE_PREFS_NAME

    private const val KEY_FINGERPRINT = "fingerprint"

    private const val KEY_SYMBOLS = "symbols"

    @Volatile
    private var memorySymbols: BiliHookSymbols? = null

    private val asyncScanRunning = AtomicBoolean(false)

    fun resolve(
        hostContext: Context,
        classLoader: ClassLoader,
        notifyUserOnAsyncComplete: Boolean = false,
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

        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        if (isMainThread) {
            log("BiliSymbolResolver: cache miss on main thread, performing fast initial scan to prevent ANR/black screen", null)
            val fastSymbols = scan(
                hostContext = appContext,
                classLoader = classLoader,
                fingerprint = fingerprint,
                log = log,
                allowDexKit = false,
            )
            memorySymbols = fastSymbols
            if (asyncScanRunning.compareAndSet(false, true)) {
                kotlin.concurrent.thread(name = "BBZQ-AsyncScan", isDaemon = true) {
                    try {
                        log("BiliSymbolResolver: async full DexKit scan started", null)
                        val fullSymbols = scan(
                            hostContext = appContext,
                            classLoader = classLoader,
                            fingerprint = fingerprint,
                            log = log,
                            allowDexKit = true,
                        )
                        writeCache(prefs, fingerprint, fullSymbols, log)
                        memorySymbols = fullSymbols
                        log("BiliSymbolResolver: async full DexKit scan completed and cached fp=$fingerprint", null)
                        publishStatus(appContext, fullSymbols, log)
                        if (notifyUserOnAsyncComplete) {
                            Handler(Looper.getMainLooper()).post {
                                runCatching {
                                    Toast.makeText(
                                        appContext,
                                        "BBZQ 符號快取已更新，重啟嗶哩嗶哩後完整生效",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }.onFailure { toastError ->
                                    log("BiliSymbolResolver: failed to show symbol cache toast", toastError)
                                }
                            }
                        }
                    } catch (throwable: Throwable) {
                        log("BiliSymbolResolver: async full DexKit scan failed", throwable)
                    } finally {
                        asyncScanRunning.set(false)
                    }
                }
            }
            return fastSymbols
        }

        log("BiliSymbolResolver scan begin fp=$fingerprint", null)
        val scanned = scan(
            hostContext = appContext,
            classLoader = classLoader,
            fingerprint = fingerprint,
            log = log,
            allowDexKit = true,
        )
        writeCache(prefs, fingerprint, scanned, log)
        memorySymbols = scanned
        log("BiliSymbolResolver scan done fp=$fingerprint", null)
        scanned.formatStatusLines().forEach { line -> log(line, null) }
        publishStatus(appContext, scanned, log)
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
            allowDexKit = true,
        )
        writeCache(prefs, fingerprint, scanned, log)
        memorySymbols = scanned
        log("BiliSymbolResolver force scan done fp=$fingerprint", null)
        scanned.formatStatusLines().forEach { line -> log(line, null) }
        publishStatus(appContext, scanned, log)
        return scanned
    }

    private fun scan(
        hostContext: Context,
        classLoader: ClassLoader,
        fingerprint: String,
        log: (String, Throwable?) -> Unit,
        allowDexKit: Boolean = true,
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
            if (!allowDexKit) return null
            bridge?.let { return it }
            val opened = DexKitBridgeProvider.openFirstAvailable(
                sourcePaths = sourcePaths,
                context = hostContext,
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
            scanRewardAd(classLoader, ::bridge)
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
        val downloadThread = if (downloadThreadListeners.isNotEmpty()) {
            DownloadThreadSymbols(
                listeners = downloadThreadListeners,
                evidence = "listeners=${downloadThreadListeners.size}",
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
            scanStoryDanmaku(classLoader, ::bridge)
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
        val homeFrame = scanOptionalHookPoint(HP_HOME_FRAME, hookPoints, scanErrors, log) {
            val symbols = locateHomeFrameSymbols(classLoader)
            if (symbols == null) SymbolScanResult.Missing("new home frame model not verified")
            else SymbolScanResult.Found(symbols, "HomeTabData", symbols.evidence)
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
            scanFullNumberFormat(classLoader, ::bridge)
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
        val videoEndRecommend = scanOptionalHookPoint(HP_VIDEO_END_RECOMMEND, hookPoints, scanErrors, log) {
            scanVideoEndRecommend(classLoader, ::bridge)
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
            homeFrame = homeFrame,
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
            videoEndRecommend = videoEndRecommend,
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

    internal fun publishStatus(
        context: Context,
        symbols: BiliHookSymbols,
        log: (String, Throwable?) -> Unit,
    ) {
        val cachePrefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)
        publishStatusToPrefs(listOf(cachePrefs, settingsPrefs), symbols, log)
    }

    internal fun publishStatus(
        prefs: SharedPreferences,
        symbols: BiliHookSymbols,
        log: (String, Throwable?) -> Unit,
    ) {
        publishStatusToPrefs(listOf(prefs), symbols, log)
    }

    internal fun publishStatusToPrefs(
        prefsList: List<SharedPreferences>,
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

            val updatedAt = System.currentTimeMillis().toString()
            prefsList.forEach { targetPrefs ->
                val editor = targetPrefs.edit()
                    .putString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_SUMMARY, summary)
                    .putString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT, report)
                    .putString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_UPDATED_AT, updatedAt)
                if (!editor.commit()) {
                    log("BiliSymbolResolver publish status failed: commit returned false", null)
                }
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
}
