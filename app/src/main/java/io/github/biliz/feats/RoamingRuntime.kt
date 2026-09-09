package io.github.biliz.feats

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import io.github.biliz.ConfigPorter
import io.github.biliz.ModuleSettingsBridge
import io.github.biliz.RuntimeEnvironmentInfo
import kotlin.LazyThreadSafetyMode
import io.github.biliz.feats.hook.BottomBarHook
import io.github.biliz.feats.hook.AutoLikeHook
import io.github.biliz.feats.hook.ChronosPromotionHook
import io.github.biliz.feats.hook.CustomThemeHook
import io.github.biliz.feats.hook.DaggerCircularDependencyFixHook
import io.github.biliz.feats.hook.CustomCdnHook
import io.github.biliz.feats.hook.DownloadThreadHook
import io.github.biliz.feats.hook.DynamicPageHook
import io.github.biliz.feats.hook.TeenagersModeHook
import io.github.biliz.feats.hook.TryFreeQualityHook
import io.github.biliz.feats.hook.VideoQualityHook
import io.github.biliz.feats.hook.FakeWifiHook
import io.github.biliz.feats.hook.FreeCopyHook
import io.github.biliz.feats.hook.HomeRecommendAdHook
import io.github.biliz.feats.hook.HomeRecommendPreloadHook
import io.github.biliz.feats.hook.HomeRecommendTabHook
import io.github.biliz.feats.hook.HomeComponentHideHook
import io.github.biliz.feats.hook.HomeTopBarPurifyHook
import io.github.biliz.feats.hook.RewardAdHook
import io.github.biliz.feats.hook.SettingHook
import io.github.biliz.feats.hook.ShareHook
import io.github.biliz.feats.hook.SkipVideoAdHook
import io.github.biliz.feats.hook.SkipVideoAdProgressHook
import io.github.biliz.feats.hook.SplashAdHook
import io.github.biliz.feats.hook.StoryComponentAlphaHook
import io.github.biliz.feats.hook.StoryDanmakuHook
import io.github.biliz.feats.hook.StoryDefaultLaunchHook
import io.github.biliz.feats.hook.StoryFullscreenHook
import io.github.biliz.feats.hook.StoryPlayerAdHook
import io.github.biliz.feats.hook.BlockUpdateHook
import io.github.biliz.feats.hook.VideoCommentHook
import io.github.biliz.feats.hook.VideoDetailBannerAdHook
import io.github.biliz.feats.hook.VideoDetailRelateFilterHook
import io.github.biliz.feats.hook.VideoMentionHook
import io.github.biliz.feats.hook.VideoEndRecommendHook
import io.github.biliz.feats.hook.MediaSessionFixHook
import io.github.biliz.feats.hook.FullNumberFormatHook
import io.github.biliz.feats.hook.MineProfileHook
import io.github.biliz.feats.hook.PlayerUiHook
import io.github.biliz.feats.hook.TripleSpeedHook
import io.github.biliz.feats.hook.LongPressSpeedLockHook
import io.github.biliz.feats.hook.ReadEraHook
import io.github.biliz.feats.hook.BlockActivityMetaStickerHook
import io.github.biliz.feats.hook.WoMicHook
import io.github.biliz.feats.symbol.BiliHookSymbols
import io.github.biliz.feats.symbol.BiliSymbolResolver
import io.github.libxposed.api.XposedInterface

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
            env.log("BILIZ runtime starting for $packageName (WO Mic mode)")
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
            runCatching { woMicHook.startHook() }
                .onFailure { env.log("WoMicHook failed", it) }
            env.log("BILIZ runtime installed WoMic hook(s)")
            return
        }

        if (packageName == READERA_PACKAGE) {
            env.log("BILIZ runtime starting for $packageName (ReadEra mode)")
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
            runCatching { readEraHook.startHook() }
                .onFailure { env.log("ReadEraHook failed", it) }
            env.log("BILIZ runtime installed ReadEra hook(s)")
            return
        }

        val processScope = resolveProcessScope(packageName, processName)

        env.log("BILIZ runtime starting for $packageName/$processName")
        if (processScope == ProcessScope.UNSUPPORTED) {
            env.log("BILIZ runtime skipped for unsupported process $processName")
            return
        }

        ModuleSettingsBridge.attach(env.hostContext, xposed)
        autoSeedDefaultConfig(env)
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
        val symbols = if (processScope != ProcessScope.UNSUPPORTED) {
            BiliSymbolResolver.resolve(
                hostContext = env.hostContext,
                classLoader = classLoader,
                log = log,
            )
        } else {
            null
        }
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
                ::DownloadThreadHook,
                ::CustomCdnHook,
            )

            ProcessScope.MAIN -> listOf(
                ::DaggerCircularDependencyFixHook,
                ::SettingHook,
                ::SplashAdHook,
                ::ShareHook,
                ::FreeCopyHook,
                ::BottomBarHook,
                ::HomeComponentHideHook,
                ::HomeRecommendAdHook,
                ::HomeRecommendTabHook,
                ::HomeRecommendPreloadHook,
                ::DynamicPageHook,
                ::HomeTopBarPurifyHook,
                ::StoryDefaultLaunchHook,
                ::StoryPlayerAdHook,
                ::StoryFullscreenHook,
                ::StoryDanmakuHook,
                ::StoryComponentAlphaHook,
                ::VideoDetailBannerAdHook,
                ::VideoDetailRelateFilterHook,
                ::VideoMentionHook,
                ::VideoEndRecommendHook,
                ::MediaSessionFixHook,
                ::PlayerUiHook,
                ::TripleSpeedHook,
                ::LongPressSpeedLockHook,
                ::TryFreeQualityHook,
                ::VideoQualityHook,
                ::FakeWifiHook,
                ::CustomCdnHook,
                ::ChronosPromotionHook,
                ::SkipVideoAdHook,
                ::SkipVideoAdProgressHook,
                ::RewardAdHook,
                ::AutoLikeHook,
                ::TeenagersModeHook,
                ::BlockUpdateHook,
                ::VideoCommentHook,
                ::FullNumberFormatHook,
                ::MineProfileHook,
                ::CustomThemeHook,
                ::BlockActivityMetaStickerHook,
            )
            ProcessScope.UNSUPPORTED -> emptyList()
        }

        val activeHooks = hooks.map { it(env) }
        activeHooks.forEach { hook ->
            runCatching { hook.startHook() }
                .onFailure { env.log("Hook failed: ${hook.javaClass.simpleName}", it) }
        }

        BiliSymbolResolver.onSymbolsUpdated = { updatedSymbols ->
            env.symbols = updatedSymbols
            activeHooks.filter { !it.isInstalled }.forEach { hook ->
                runCatching {
                    hook.startHook()
                    if (hook.isInstalled) {
                        env.log("Deferred hook installed: ${hook.javaClass.simpleName}")
                    }
                }.onFailure { env.log("Deferred hook failed: ${hook.javaClass.simpleName}", it) }
            }
        }

        if (processScope == ProcessScope.WEB) {
            runCatching { CustomThemeHook(env).insertColorForWebProcess() }
                .onFailure { env.log("CustomTheme web process hook failed", it) }
        }

        env.log("BILIZ runtime installed ${activeHooks.size} hook(s)")
    }

    private fun autoSeedDefaultConfig(env: RoamingEnv) {
        val prefs = env.prefs
        if (!prefs.getBoolean("config_preloaded_v1", false)) {
            runCatching {
                val stream = env.moduleContext?.assets?.open("default_config.zip")
                    ?: RoamingRuntime::class.java.classLoader?.getResourceAsStream("assets/default_config.zip")
                    ?: RoamingRuntime::class.java.classLoader?.getResourceAsStream("default_config.zip")
                val bytes = stream?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val result = ConfigPorter.importFromZip(bytes, prefs)
                    if (result is ConfigPorter.ImportResult.Success) {
                        prefs.edit().putBoolean("config_preloaded_v1", true).apply()
                        env.log("autoSeedDefaultConfig: successfully imported ${result.switchCount} switches, ${result.manualCount} manual settings")
                    }
                }
            }.onFailure {
                env.log("autoSeedDefaultConfig failed", it)
            }
        }
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
    var symbols: BiliHookSymbols? = null
        internal set

    val prefs: SharedPreferences
        get() = ModuleSettingsBridge.instance

    val moduleContext: Context? by lazy(LazyThreadSafetyMode.NONE) {
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

    fun log(message: String, throwable: Throwable? = null) {
        logger(message, throwable)
    }

    companion object
}

private const val MODULE_PACKAGE = "io.github.biliz"

private class ModuleResourceContext(
    base: Context,
    private val moduleResources: Resources,
) : ContextWrapper(base) {
    override fun getPackageName(): String = MODULE_PACKAGE

    override fun getResources(): Resources = moduleResources

    override fun getAssets(): AssetManager = moduleResources.assets
}

abstract class BaseRoamingHook(
    protected val env: RoamingEnv,
) {
    var isInstalled: Boolean = false

    protected val xposed: XposedInterface
        get() = env.xposed

    protected val classLoader: ClassLoader
        get() = env.classLoader

    protected val prefs: SharedPreferences
        get() = env.prefs

    protected fun log(message: String, throwable: Throwable? = null) {
        env.log(message, throwable)
    }

    abstract fun startHook()
}

