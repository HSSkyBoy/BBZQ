package io.github.bbzq.feats

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import io.github.bbzq.ModuleSettingsBridge
import kotlin.LazyThreadSafetyMode
import io.github.bbzq.feats.hook.BottomBarHook
import io.github.bbzq.feats.hook.AutoLikeHook
import io.github.bbzq.feats.hook.AccessKeyHook
import io.github.bbzq.feats.hook.ChronosPromotionHook
import io.github.bbzq.feats.hook.CustomThemeHook
import io.github.bbzq.feats.hook.CustomCdnHook
import io.github.bbzq.feats.hook.DownloadThreadHook
import io.github.bbzq.feats.hook.DynamicPageHook
import io.github.bbzq.feats.hook.TeenagersModeHook
import io.github.bbzq.feats.hook.TryFreeQualityHook
import io.github.bbzq.feats.hook.FreeCopyHook
import io.github.bbzq.feats.hook.HomeRecommendAdHook
import io.github.bbzq.feats.hook.HomeRecommendPreloadHook
import io.github.bbzq.feats.hook.HomeRecommendTabHook
import io.github.bbzq.feats.hook.HomeComponentHideHook
import io.github.bbzq.feats.hook.HomeTopBarPurifyHook
import io.github.bbzq.feats.hook.RewardAdHook
import io.github.bbzq.feats.hook.SettingHook
import io.github.bbzq.feats.hook.ShareHook
import io.github.bbzq.feats.hook.SkipVideoAdHook
import io.github.bbzq.feats.hook.SkipVideoAdProgressHook
import io.github.bbzq.feats.hook.SplashAdHook
import io.github.bbzq.feats.hook.StoryComponentAlphaHook
import io.github.bbzq.feats.hook.StoryDanmakuHook
import io.github.bbzq.feats.hook.StoryDefaultLaunchHook
import io.github.bbzq.feats.hook.StoryFullscreenHook
import io.github.bbzq.feats.hook.StoryPlayerAdHook
import io.github.bbzq.feats.hook.BlockUpdateHook
import io.github.bbzq.feats.hook.VideoCommentHook
import io.github.bbzq.feats.hook.VideoDetailBannerAdHook
import io.github.bbzq.feats.hook.FullNumberFormatHook
import io.github.bbzq.feats.hook.MineProfileHook
import io.github.bbzq.feats.hook.PlayerUiHook
import io.github.bbzq.feats.hook.TripleSpeedHook
import io.github.bbzq.feats.hook.LongPressSpeedLockHook
import io.github.bbzq.feats.hook.ReadEraHook
import io.github.bbzq.feats.hook.WoMicHook
import io.github.bbzq.feats.symbol.BiliHookSymbols
import io.github.bbzq.feats.symbol.BiliSymbolResolver
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
            env.log("BBZQ runtime starting for $packageName (WO Mic mode)")
            ModuleSettingsBridge.attach(env.hostContext, xposed)
            val woMicHook = WoMicHook(env)
            runCatching { woMicHook.startHook() }
                .onFailure { env.log("WoMicHook failed", it) }
            env.log("BBZQ runtime installed WoMic hook(s)")
            return
        }

        if (packageName == READERA_PACKAGE) {
            env.log("BBZQ runtime starting for $packageName (ReadEra mode)")
            ModuleSettingsBridge.attach(env.hostContext, xposed)
            val readEraHook = ReadEraHook(env)
            runCatching { readEraHook.startHook() }
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
        if (processScope == ProcessScope.MAIN) {
            HookUpdateChecker.check(env)
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
                ::PlayerUiHook,
                ::TripleSpeedHook,
                ::LongPressSpeedLockHook,
                ::TryFreeQualityHook,
                ::CustomCdnHook,
                ::ChronosPromotionHook,
                ::SkipVideoAdHook,
                ::SkipVideoAdProgressHook,
                ::RewardAdHook,
                ::AutoLikeHook,
                ::AccessKeyHook,
                ::TeenagersModeHook,
                ::BlockUpdateHook,
                ::VideoCommentHook,
                ::FullNumberFormatHook,
                ::MineProfileHook,
                ::CustomThemeHook,
            )
            ProcessScope.UNSUPPORTED -> emptyList()
        }

        hooks.forEach { factory ->
            val hook = factory(env)
            runCatching { hook.startHook() }
                .onFailure { env.log("Hook failed: ${hook.javaClass.simpleName}", it) }
        }

        if (processScope == ProcessScope.WEB) {
            runCatching { CustomThemeHook(env).insertColorForWebProcess() }
                .onFailure { env.log("CustomTheme web process hook failed", it) }
        }

        env.log("BBZQ runtime installed ${hooks.size} hook(s)")
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
    protected val env: RoamingEnv,
) {
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

