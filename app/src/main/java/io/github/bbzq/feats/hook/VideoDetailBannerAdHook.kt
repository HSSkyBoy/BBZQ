package io.github.bbzq.feats.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredVideoDetailBannerAdSymbols
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class VideoDetailBannerAdHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val hookedDriverClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedUnderPlayerClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedRelateClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedMerchandiseClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedPausedPageClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedAdPanelClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private var blockedCount = 0

    override fun startHook() {
        if (env.processName != env.packageName) return

        var installed = 0
        installed += installVDPausedPageDirectBlock()

        val symbols = env.symbols?.videoDetailBannerAd?.restore(classLoader)
        if (symbols != null) {
            if (installGAdVideoDetailHooks(symbols)) {
                installed++
            }
        } else {
            log("startHook: VideoDetailBannerAd symbols restore returned null, using direct hooks")
        }

        if (installed > 0) {
            isInstalled = true
            log("startHook: VideoDetailBannerAd installed=$installed, initialEnabled=${ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)}")
        } else {
            log("startHook: VideoDetailBannerAd no hook point found")
        }
    }

    private fun installVDPausedPageDirectBlock(): Int {
        val vdPausedPageClass = classLoader.findClassOrNull("com.bilibili.ad.adview.videodetail.pausedpage.VDPausedPage")
            ?: return 0
        var count = 0
        val requestMethod = vdPausedPageClass.declaredMethods.firstOrNull {
            it.name == "requestPausedPage" && !Modifier.isStatic(it.modifiers)
        }
        if (requestMethod != null) {
            env.hookBefore(requestMethod) { param ->
                if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                logBlocked("VDPausedPage.requestPausedPage")
                param.result = null
            }
            count++
        }

        val countDownMethod = vdPausedPageClass.declaredMethods.firstOrNull {
            it.name == "getCountDownView" && !Modifier.isStatic(it.modifiers)
        }
        if (countDownMethod != null) {
            env.hookAfter(countDownMethod) { param ->
                runCatching {
                    if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@runCatching
                    logBlocked("VDPausedPage.getCountDownView")
                    val v = param.result as? View
                    v?.visibility = View.GONE
                }
            }
            count++
        }
        log("startHook: VideoDetailBannerAd VDPausedPage direct hook installed=$count")
        return count
    }

    private fun installGAdVideoDetailHooks(symbols: RestoredVideoDetailBannerAdSymbols): Boolean {
        val getVideoDetail = symbols.getVideoDetail ?: return false
        val videoDetailType = symbols.videoDetailType ?: return false
        if (
            symbols.underPlayerType == null &&
            symbols.relateType == null &&
            symbols.merchandiseType == null &&
            symbols.pausedPageType == null &&
            symbols.adPanelType == null
        ) {
            return false
        }

        env.hookAfter(getVideoDetail) { param ->
            runCatching {
                if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@runCatching
                val original = param.result ?: return@runCatching
                if (!videoDetailType.isInstance(original)) return@runCatching
                val concreteClass = original.javaClass
                if (hookedDriverClasses.add(concreteClass)) {
                    hookVideoDetailClassMethods(concreteClass, symbols)
                }
            }.onFailure {
                log("VideoDetailBannerAd hook failed at ${getVideoDetail.declaringClass.name}.${getVideoDetail.name}", it)
            }
        }
        log(
            "startHook: VideoDetailBannerAd at ${getVideoDetail.declaringClass.name}.${getVideoDetail.name}, " +
                "underPlayer=${symbols.underPlayerType != null} relate=${symbols.relateType != null} " +
                "merchandise=${symbols.merchandiseType != null} pausedRequest=${symbols.requestPausedPage != null} " +
                "pausedPanel=${symbols.getPausedPagePanel != null || symbols.getBrandPausedPagePanel != null}",
        )
        return true
    }

    private fun hookVideoDetailClassMethods(
        targetClass: Class<*>,
        symbols: RestoredVideoDetailBannerAdSymbols,
    ) {
        val underPlayerType = symbols.underPlayerType
        val relateType = symbols.relateType
        val merchandiseType = symbols.merchandiseType
        val pausedPageType = symbols.pausedPageType
        val adPanelType = symbols.adPanelType
        val requestPausedPage = symbols.requestPausedPage
        val getPausedPagePanel = symbols.getPausedPagePanel
        val getBrandPausedPagePanel = symbols.getBrandPausedPagePanel

        targetClass.allMethods().filter { !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 }.forEach { method ->
            when (method.name) {
                "getUnderPlayer" -> if (underPlayerType != null) {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookAfter
                        val underPlayer = param.result ?: return@hookAfter
                        if (underPlayerType.isInstance(underPlayer)) {
                            hookUnderPlayerClass(underPlayer.javaClass)
                        }
                    }
                }
                "getRelate" -> if (relateType != null) {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookAfter
                        val relate = param.result ?: return@hookAfter
                        if (relateType.isInstance(relate)) {
                            hookRelateClass(relate.javaClass)
                        }
                    }
                }
                "getMerchandise" -> if (merchandiseType != null) {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookAfter
                        val merchandise = param.result ?: return@hookAfter
                        if (merchandiseType.isInstance(merchandise)) {
                            hookMerchandiseClass(merchandise.javaClass)
                        }
                    }
                }
                "getPausedPage" -> if (pausedPageType != null && requestPausedPage != null) {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookAfter
                        val pausedPage = param.result ?: return@hookAfter
                        if (pausedPageType.isInstance(pausedPage)) {
                            hookPausedPageClass(pausedPage.javaClass, requestPausedPage)
                        }
                    }
                }
                "getPanel" -> if (adPanelType != null && (getPausedPagePanel != null || getBrandPausedPagePanel != null)) {
                    env.hookAfter(method) { param ->
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookAfter
                        val panel = param.result ?: return@hookAfter
                        if (adPanelType.isInstance(panel)) {
                            hookAdPanelClass(panel.javaClass, getPausedPagePanel, getBrandPausedPagePanel)
                        }
                    }
                }
                "getEndPage" -> {
                    env.hookBefore(method) { param ->
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                        logBlocked("videoDetail.getEndPage")
                        param.result = null
                    }
                }
            }
        }
        log("VideoDetailBannerAd successfully hooked methods on ${targetClass.name}")
    }

    private fun hookUnderPlayerClass(clazz: Class<*>) {
        if (!hookedUnderPlayerClasses.add(clazz)) return
        clazz.allMethods().filter { !Modifier.isStatic(it.modifiers) && it.name in BLOCKED_METHODS }.forEach { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                logBlocked("underPlayer.${method.name}")
                param.result = null
            }
        }
    }

    private fun hookRelateClass(clazz: Class<*>) {
        if (!hookedRelateClasses.add(clazz)) return
        clazz.allMethods().filter { !Modifier.isStatic(it.modifiers) && it.name == "getAdRelateView" }.forEach { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                logBlocked("relate.getAdRelateView")
                param.result = null
            }
        }
    }

    private fun hookMerchandiseClass(clazz: Class<*>) {
        if (!hookedMerchandiseClasses.add(clazz)) return
        clazz.allMethods().filter { !Modifier.isStatic(it.modifiers) && it.name == "getAdMerchandiseView" }.forEach { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                logBlocked("merchandise.getAdMerchandiseView")
                param.result = null
            }
        }
    }

    private fun hookPausedPageClass(clazz: Class<*>, requestPausedPage: Method) {
        if (!hookedPausedPageClasses.add(clazz)) return
        clazz.allMethods().filter { !Modifier.isStatic(it.modifiers) }.forEach { method ->
            if (method.name == "requestPausedPage" || method.hasSameSignatureAs(requestPausedPage)) {
                env.hookBefore(method) { param ->
                    if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                    logBlocked("pausedPage.${method.name}")
                    param.result = null
                }
            } else if (method.name == "getCountDownView") {
                env.hookAfter(method) { param ->
                    runCatching {
                        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@runCatching
                        logBlocked("pausedPage.getCountDownView")
                        val v = param.result as? View
                        v?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun hookAdPanelClass(clazz: Class<*>, getPausedPagePanel: Method?, getBrandPausedPagePanel: Method?) {
        if (!hookedAdPanelClasses.add(clazz)) return
        clazz.allMethods().filter { !Modifier.isStatic(it.modifiers) }.forEach { method ->
            if (method.hasSameSignatureAs(getPausedPagePanel) ||
                method.hasSameSignatureAs(getBrandPausedPagePanel) ||
                method.name == "getDynamicPausedPagePanel"
            ) {
                env.hookBefore(method) { param ->
                    if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookBefore
                    logBlocked("adPanel.${method.name}")
                    param.result = null
                }
            }
        }
    }

    private fun Method.hasSameSignatureAs(other: Method?): Boolean =
        other != null &&
            name == other.name &&
            returnType == other.returnType &&
            parameterTypes.contentEquals(other.parameterTypes)

    private fun logBlocked(methodName: String) {
        val count = ++blockedCount
        if (count <= 20 || count % 20 == 0) {
            log("VideoDetailBannerAd blocked $methodName count=$count")
        }
    }

    private companion object {
        private val BLOCKED_METHODS = setOf("getUpperAdView", "getUpperHDView", "getUpperNestView")
    }
}
