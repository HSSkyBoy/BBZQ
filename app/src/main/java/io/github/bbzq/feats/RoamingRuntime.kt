package io.github.bbzq.feats

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.feats.hook.ad.BlockActivityMetaStickerHook
import io.github.bbzq.feats.hook.ad.ChronosPromotionHook
import io.github.bbzq.feats.hook.ad.PgcActivityPopupHook
import io.github.bbzq.feats.hook.ad.RewardAdHook
import io.github.bbzq.feats.hook.ad.SkipVideoAdHook
import io.github.bbzq.feats.hook.ad.SkipVideoAdProgressHook
import io.github.bbzq.feats.hook.ad.SplashAdHook
import io.github.bbzq.feats.hook.ad.VideoDetailBannerAdHook
import io.github.bbzq.feats.hook.comment.FreeCopyHook
import io.github.bbzq.feats.hook.comment.VideoCommentHook
import io.github.bbzq.feats.hook.extra.ReadEraHook
import io.github.bbzq.feats.hook.extra.WoMicHook
import io.github.bbzq.feats.hook.home.BottomBarHook
import io.github.bbzq.feats.hook.home.DynamicPageHook
import io.github.bbzq.feats.hook.home.HomeComponentHideHook
import io.github.bbzq.feats.hook.home.HomeFrameHook
import io.github.bbzq.feats.hook.home.HomeRecommendAdHook
import io.github.bbzq.feats.hook.home.HomeRecommendPreloadHook
import io.github.bbzq.feats.hook.home.HomeRecommendTabHook
import io.github.bbzq.feats.hook.home.HomeTopBarPurifyHook
import io.github.bbzq.feats.hook.home.SearchPurifyHook
import io.github.bbzq.feats.hook.live.LiveReservationHook
import io.github.bbzq.feats.hook.live.LiveRoomBlurMaskHook
import io.github.bbzq.feats.hook.live.LiveRoomVerticalSwipeHook
import io.github.bbzq.feats.hook.network.CustomCdnHook
import io.github.bbzq.feats.hook.network.DownloadThreadHook
import io.github.bbzq.feats.hook.network.FakeWifiHook
import io.github.bbzq.feats.hook.network.P2pBlockHook
import io.github.bbzq.feats.hook.player.AiDeclaredVideoHook
import io.github.bbzq.feats.hook.player.AllowMiniPlayHook
import io.github.bbzq.feats.hook.player.AutoLikeHook
import io.github.bbzq.feats.hook.player.BackgroundPlayHook
import io.github.bbzq.feats.hook.player.LongPressSpeedLockHook
import io.github.bbzq.feats.hook.player.MediaSessionFixHook
import io.github.bbzq.feats.hook.player.PlayArcConfHook
import io.github.bbzq.feats.hook.player.PlayerUiHook
import io.github.bbzq.feats.hook.player.SplitScreenFullscreenHook
import io.github.bbzq.feats.hook.player.TripleSpeedHook
import io.github.bbzq.feats.hook.player.TryFreeQualityHook
import io.github.bbzq.feats.hook.player.VideoDetailRelateFilterHook
import io.github.bbzq.feats.hook.player.VideoDetailShareRedirectHook
import io.github.bbzq.feats.hook.player.VideoEndRecommendHook
import io.github.bbzq.feats.hook.player.VideoMentionHook
import io.github.bbzq.feats.hook.player.VideoQualityHook
import io.github.bbzq.feats.hook.profile.AccessKeyHook
import io.github.bbzq.feats.hook.profile.AutoClaimVipExpHook
import io.github.bbzq.feats.hook.profile.CustomThemeHook
import io.github.bbzq.feats.hook.profile.MineProfileHook
import io.github.bbzq.feats.hook.profile.SpaceFixHook
import io.github.bbzq.feats.hook.sea.SeaBangumiDetailHook
import io.github.bbzq.feats.hook.sea.SeaBangumiPlayHook
import io.github.bbzq.feats.hook.sea.SeaBangumiSearchHook
import io.github.bbzq.feats.hook.sea.SeaBangumiSubtitleHook
import io.github.bbzq.feats.hook.story.StoryComponentAlphaHook
import io.github.bbzq.feats.hook.story.StoryDanmakuHook
import io.github.bbzq.feats.hook.story.StoryDefaultLaunchHook
import io.github.bbzq.feats.hook.story.StoryDetailRedirectHook
import io.github.bbzq.feats.hook.story.StoryFullscreenHook
import io.github.bbzq.feats.hook.story.StoryPlayerAdHook
import io.github.bbzq.feats.hook.system.BlockUpdateHook
import io.github.bbzq.feats.hook.system.ComponentPoolBlockHook
import io.github.bbzq.feats.hook.system.CourseIntroCrashGuardHook
import io.github.bbzq.feats.hook.system.DaggerCircularDependencyFixHook
import io.github.bbzq.feats.hook.system.DialogBlurBackgroundHook
import io.github.bbzq.feats.hook.system.FullNumberFormatHook
import io.github.bbzq.feats.hook.system.PageFontScaleHook
import io.github.bbzq.feats.hook.system.SettingHook
import io.github.bbzq.feats.hook.system.ShareHook
import io.github.bbzq.feats.hook.system.SplashAutoNightHook
import io.github.bbzq.feats.hook.system.TeenagersModeHook
import io.github.bbzq.feats.symbol.BiliHookSymbols
import io.github.bbzq.feats.symbol.BiliSymbolResolver
import io.github.bbzq.prefs.ModuleSettingsBridge
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object RoamingRuntime {
    fun isProcessSupported(packageName: String, processName: String): Boolean =
        resolveProcessScope(packageName, processName) != ProcessScope.UNSUPPORTED

    fun isSymbolResolverProcess(packageName: String, processName: String): Boolean =
        resolveProcessScope(packageName, processName) != ProcessScope.UNSUPPORTED

    fun start(
        xposed: XposedInterface,
        packageName: String,
        processName: String,
        application: Context,
        classLoader: ClassLoader,
        log: (String, Throwable?) -> Unit,
    ) {
        val env = RoamingEnv(
            xposed = xposed,
            packageName = packageName,
            processName = processName,
            hostContext = application.applicationContext ?: application,
            classLoader = classLoader,
            logger = log,
        )

        if (packageName == WO_MIC_PACKAGE) {
            env.log("BBZQ runtime starting for $packageName (WO Mic mode)")
            ModuleSettingsBridge.attach(env.hostContext, xposed)
            runCatching {
                RuntimeEnvironmentInfo.recordRuntimeSnapshot(
                    hostContext = env.hostContext,
                    processName = env.processName,
                    xposed = xposed,
                    prefs = env.prefs,
                )
            }
            val woMicHook = WoMicHook(env)
            runCatching { woMicHook.startOnce() }
                .onFailure { env.log("WoMicHook failed", it) }
            env.log("BBZQ runtime installed WoMic hook(s)")
            return
        }

        if (packageName == READERA_PACKAGE) {
            env.log("BBZQ runtime starting for $packageName (ReadEra mode)")
            ModuleSettingsBridge.attach(env.hostContext, xposed)
            runCatching {
                RuntimeEnvironmentInfo.recordRuntimeSnapshot(
                    hostContext = env.hostContext,
                    processName = env.processName,
                    xposed = xposed,
                    prefs = env.prefs,
                )
            }
            val readEraHook = ReadEraHook(env)
            runCatching { readEraHook.startOnce() }
                .onFailure { env.log("ReadEraHook failed", it) }
            env.log("BBZQ runtime installed ReadEra hook(s)")
            return
        }

        val processScope = resolveProcessScope(packageName, processName)

        env.log("BBZQ runtime starting for $packageName/$processName")
        if (processScope == ProcessScope.UNSUPPORTED) {
            env.log("BBZQ runtime skipped for unsupported process $processName")
            return
        }

        ModuleSettingsBridge.attach(env.hostContext, xposed)
        BbzqLevelGate.init(env.hostContext, classLoader) { env.log(it) }
        if (processScope == ProcessScope.MAIN) {
            runCatching {
                RuntimeEnvironmentInfo.recordRuntimeSnapshot(
                    hostContext = env.hostContext,
                    processName = env.processName,
                    xposed = xposed,
                    prefs = env.prefs,
                )
            }
        }
        val symbols = BiliSymbolResolver.resolve(
            hostContext = env.hostContext,
            classLoader = classLoader,
            notifyUserOnAsyncComplete = processScope == ProcessScope.MAIN,
            log = log,
        )
        env.symbols = symbols
        if (processScope == ProcessScope.MAIN) {
            SymbolScanRefreshRequestHandler.install(
                env = env,
                xposed = xposed,
                classLoader = classLoader,
            )
        }

        val hooks = when (processScope) {
            ProcessScope.WEB -> listOf(
                ::ShareHook,
                ::RewardAdHook,
            )

            ProcessScope.DOWNLOAD -> listOf(
                ::ComponentPoolBlockHook,
                ::DownloadThreadHook,
                ::CustomCdnHook,
                ::SeaBangumiPlayHook,
                ::SeaBangumiDetailHook,
            )

            ProcessScope.MAIN -> listOf(
                ::DaggerCircularDependencyFixHook,
                ::CourseIntroCrashGuardHook,
                ::SettingHook,
                ::SplashAdHook,
                ::SplashAutoNightHook,
                ::ShareHook,
                ::FreeCopyHook,
                ::BottomBarHook,
                ::ComponentPoolBlockHook,
                ::HomeComponentHideHook,
                ::HomeRecommendAdHook,
                ::HomeRecommendTabHook,
                ::HomeRecommendPreloadHook,
                ::DynamicPageHook,
                ::HomeTopBarPurifyHook,
                ::HomeFrameHook,
                ::SearchPurifyHook,
                ::SeaBangumiSearchHook,
                ::SeaBangumiPlayHook,
                ::SeaBangumiDetailHook,
                ::SeaBangumiSubtitleHook,
                ::StoryDefaultLaunchHook,
                ::StoryDetailRedirectHook,
                ::StoryPlayerAdHook,
                ::StoryFullscreenHook,
                ::StoryDanmakuHook,
                ::StoryComponentAlphaHook,
                ::VideoDetailBannerAdHook,
                ::VideoDetailRelateFilterHook,
                ::VideoMentionHook,
                ::PlayerUiHook,
                ::TripleSpeedHook,
                ::LongPressSpeedLockHook,
                ::TryFreeQualityHook,
                ::VideoQualityHook,
                ::FakeWifiHook,
                ::CustomCdnHook,
                ::P2pBlockHook,
                ::ChronosPromotionHook,
                ::PgcActivityPopupHook,
                ::AiDeclaredVideoHook,
                ::SkipVideoAdHook,
                ::SkipVideoAdProgressHook,
                ::RewardAdHook,
                ::AutoLikeHook,
                ::AccessKeyHook,
                ::TeenagersModeHook,
                ::BlockUpdateHook,
                ::VideoCommentHook,
                ::FullNumberFormatHook,
                ::SpaceFixHook,
                ::MineProfileHook,
                ::CustomThemeHook,
                ::BlockActivityMetaStickerHook,
                ::LiveRoomBlurMaskHook,
                ::LiveReservationHook,
                ::BackgroundPlayHook,
                ::SplitScreenFullscreenHook,
                ::LiveRoomVerticalSwipeHook,
                ::MediaSessionFixHook,
                ::VideoEndRecommendHook,
                ::VideoDetailShareRedirectHook,
                ::PageFontScaleHook,
                ::AutoClaimVipExpHook,
                ::DialogBlurBackgroundHook,
                ::AllowMiniPlayHook,
                ::PlayArcConfHook,
            )
            ProcessScope.UNSUPPORTED -> emptyList()
        }

        val activeHooks = hooks.map { it(env) }
        activeHooks.forEach { hook ->
            runCatching { hook.startOnce() }
                .onFailure { env.log("Hook failed: ${hook.javaClass.simpleName}", it) }
        }

        if (processScope == ProcessScope.WEB) {
            val webThemeHook = CustomThemeHook(env)
            runCatching { webThemeHook.insertColorForWebProcess() }
                .onFailure { env.log("CustomTheme web process hook failed", it) }
            runCatching { webThemeHook.insertLoadEquipForWebProcess() }
                .onFailure { env.log("CustomTheme web load equip hook failed", it) }
        }

        env.log("BBZQ runtime installed ${activeHooks.size} hook(s)")
    }

    private fun resolveProcessScope(packageName: String, processName: String): ProcessScope {
        val normalizedProcessName = processName.ifBlank { packageName }
        return when {
            normalizedProcessName == packageName -> ProcessScope.MAIN
            normalizedProcessName.endsWith(":web") -> ProcessScope.WEB
            normalizedProcessName.endsWith(":download") -> ProcessScope.DOWNLOAD
            else -> ProcessScope.UNSUPPORTED
        }
    }

    private enum class ProcessScope {
        MAIN,
        WEB,
        DOWNLOAD,
        UNSUPPORTED,
    }

    private const val WO_MIC_PACKAGE = "com.wo.voice2"
    private const val READERA_PACKAGE = "org.readera"
}

class RoamingEnv(
    val xposed: XposedInterface,
    val packageName: String,
    val processName: String,
    val hostContext: Context,
    val classLoader: ClassLoader,
    private val logger: (String, Throwable?) -> Unit,
) {
    private data class InstalledHookKey(
        val stage: HookStage,
        val executable: Executable,
        val callbackIdentity: Int,
    )

    private val installedHooks = ConcurrentHashMap.newKeySet<InstalledHookKey>()

    @Volatile
    var symbols: BiliHookSymbols? = null
        internal set

    val prefs: SharedPreferences
        get() = ModuleSettingsBridge.instance

    val moduleContext: Context? by lazy {
        runCatching {
            hostContext.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        }
            .getOrElse { packageContextError ->
                val resources = runCatching {
                    hostContext.packageManager.getResourcesForApplication(xposed.moduleApplicationInfo)
                }.onFailure { resourceError ->
                    logger(
                        "Failed to create module resource context for $MODULE_PACKAGE",
                        resourceError.also { it.addSuppressed(packageContextError) },
                    )
                }.getOrNull() ?: return@lazy null
                ModuleResourceContext(hostContext, resources)
            }
    }

    internal fun markHookInstalled(stage: HookStage, executable: Executable, callback: Any): Boolean =
        installedHooks.add(InstalledHookKey(stage, executable, System.identityHashCode(callback)))

    internal fun unmarkHookInstalled(stage: HookStage, executable: Executable, callback: Any) {
        installedHooks.remove(InstalledHookKey(stage, executable, System.identityHashCode(callback)))
    }

    fun log(message: String, throwable: Throwable? = null) {
        logger(message, throwable)
    }

    companion object
}

private const val MODULE_PACKAGE = "io.github.bbzq"

private class ModuleResourceContext(
    base: Context,
    private val moduleResources: Resources,
) : ContextWrapper(base) {
    override fun getPackageName(): String = MODULE_PACKAGE

    override fun getResources(): Resources = moduleResources

    override fun getAssets(): AssetManager = moduleResources.assets
}

abstract class BaseRoamingHook(
    internal val env: RoamingEnv,
) {
    private val started = AtomicBoolean(false)

    internal val xposed: XposedInterface
        get() = env.xposed

    internal val classLoader: ClassLoader
        get() = env.classLoader

    internal val prefs: SharedPreferences
        get() = env.prefs

    internal fun log(message: String, throwable: Throwable? = null) {
        env.log(message, throwable)
    }

    @Volatile
    var isInstalled: Boolean = false

    internal fun startOnce() {
        if (started.compareAndSet(false, true)) {
            startHook()
        }
    }

    abstract fun startHook()
}
