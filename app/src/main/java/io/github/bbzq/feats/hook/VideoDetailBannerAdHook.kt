package io.github.bbzq.feats.hook

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Space
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredVideoDetailBannerAdSymbols
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

class VideoDetailBannerAdHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val videoDetailProxies = IdentityHashMap<Any, Any>()
    private val underPlayerProxies = IdentityHashMap<Any, Any>()
    private val relateProxies = IdentityHashMap<Any, Any>()
    private val merchandiseProxies = IdentityHashMap<Any, Any>()
    private val pausedPageProxies = IdentityHashMap<Any, Any>()
    private val adPanelProxies = IdentityHashMap<Any, Any>()
    private var blockedCount = 0

    override fun startHook() {
        if (env.processName != env.packageName) return
        val enabled = ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)
        if (!enabled) {
            log("startHook: VideoDetailBannerAd disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val symbols = env.symbols?.videoDetailBannerAd?.restore(classLoader)
        if (symbols == null) {
            log("startHook: VideoDetailBannerAd skipped because symbols are unavailable")
            return
        }

        var installed = 0
        if (installGAdVideoDetailProxy(symbols)) installed++
        installed += installRelateGameComponentBlock(symbols)
        if (installed == 0) {
            log("startHook: VideoDetailBannerAd no hook point found")
        }
    }

    private fun installGAdVideoDetailProxy(symbols: RestoredVideoDetailBannerAdSymbols): Boolean {
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
                val original = param.result ?: return@runCatching
                if (!videoDetailType.isInstance(original)) return@runCatching
                param.result = videoDetailProxy(
                    original = original,
                    videoDetailType = videoDetailType,
                    underPlayerType = symbols.underPlayerType,
                    relateType = symbols.relateType,
                    merchandiseType = symbols.merchandiseType,
                    pausedPageType = symbols.pausedPageType,
                    adPanelType = symbols.adPanelType,
                    requestPausedPage = symbols.requestPausedPage,
                    getPausedPagePanel = symbols.getPausedPagePanel,
                    getBrandPausedPagePanel = symbols.getBrandPausedPagePanel,
                )
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

    private fun installRelateGameComponentBlock(symbols: RestoredVideoDetailBannerAdSymbols): Int {
        val relateGameComponentType = symbols.relateGameComponentType ?: return 0
        val simpleViewEntryConstructor = symbols.simpleViewEntryConstructor ?: return 0
        val createViewEntry = symbols.createViewEntry ?: return 0
        val bindToView = symbols.bindToView ?: return 0
        val unit = symbols.kotlinUnit ?: return 0

        env.hookBefore(createViewEntry) { param ->
            runCatching {
                if (!relateGameComponentType.isInstance(param.thisObject)) return@runCatching
                val context = param.args.getOrNull(0) as? Context ?: return@runCatching
                val emptyEntry = createEmptyViewEntry(simpleViewEntryConstructor, context) ?: return@runCatching
                logBlocked("getRelateGameView")
                param.result = emptyEntry
            }.onFailure {
                log("VideoDetailBannerAd relate createViewEntry failed", it)
            }
        }
        env.hookBefore(bindToView) { param ->
            runCatching {
                if (!relateGameComponentType.isInstance(param.thisObject)) return@runCatching
                param.result = unit
            }.onFailure {
                log("VideoDetailBannerAd relate bindToView failed", it)
            }
        }
        log(
            "startHook: VideoDetailBannerAd relate game ${relateGameComponentType.name} " +
                "at ${createViewEntry.declaringClass.name}.createViewEntry/bindToView",
        )
        return 2
    }

    private fun videoDetailProxy(
        original: Any,
        videoDetailType: Class<*>,
        underPlayerType: Class<*>?,
        relateType: Class<*>?,
        merchandiseType: Class<*>?,
        pausedPageType: Class<*>?,
        adPanelType: Class<*>?,
        requestPausedPage: Method?,
        getPausedPagePanel: Method?,
        getBrandPausedPagePanel: Method?,
    ): Any = synchronized(videoDetailProxies) {
        videoDetailProxies.getOrPut(original) {
            Proxy.newProxyInstance(
                original.javaClass.classLoader ?: classLoader,
                collectProxyInterfaces(original, videoDetailType),
                InvocationHandler { proxy, method, args ->
                    runCatching {
                        when {
                            method.isObjectMethod("toString", 0) ->
                                "BBZQVideoDetailProxy(${original.javaClass.name})"
                            method.isObjectMethod("hashCode", 0) ->
                                System.identityHashCode(proxy)
                            method.isObjectMethod("equals", 1) ->
                                proxy === args?.firstOrNull()
                            method.name == "getUnderPlayer" && method.parameterCount == 0 && underPlayerType != null -> {
                                val underPlayer = invokeOriginal(original, method, args) ?: return@runCatching null
                                underPlayerProxy(underPlayer, underPlayerType)
                            }
                            method.name == "getRelate" && method.parameterCount == 0 && relateType != null -> {
                                val relate = invokeOriginal(original, method, args) ?: return@runCatching null
                                if (relateType.isInstance(relate)) relateProxy(relate, relateType) else relate
                            }
                            method.name == "getMerchandise" &&
                                method.parameterCount == 0 &&
                                merchandiseType != null -> {
                                val merchandise = invokeOriginal(original, method, args) ?: return@runCatching null
                                if (merchandiseType.isInstance(merchandise)) {
                                    merchandiseProxy(merchandise, merchandiseType)
                                } else {
                                    merchandise
                                }
                            }
                            method.name == "getPausedPage" &&
                                method.parameterCount == 0 &&
                                pausedPageType != null &&
                                requestPausedPage != null -> {
                                val pausedPage = invokeOriginal(original, method, args) ?: return@runCatching null
                                if (pausedPageType.isInstance(pausedPage)) {
                                    pausedPageProxy(pausedPage, pausedPageType, requestPausedPage)
                                } else {
                                    pausedPage
                                }
                            }
                            method.name == "getPanel" &&
                                method.parameterCount == 0 &&
                                adPanelType != null &&
                                (getPausedPagePanel != null || getBrandPausedPagePanel != null) -> {
                                val panel = invokeOriginal(original, method, args) ?: return@runCatching null
                                if (adPanelType.isInstance(panel)) {
                                    adPanelProxy(panel, adPanelType, getPausedPagePanel, getBrandPausedPagePanel)
                                } else {
                                    panel
                                }
                            }
                            else ->
                                invokeOriginal(original, method, args)
                        }
                    }.getOrElse {
                        log("VideoDetailBannerAd videoDetail proxy failed at ${method.declaringClass.name}.${method.name}", it)
                        invokeOriginal(original, method, args)
                    }
                },
            )
        }
    }

    private fun underPlayerProxy(original: Any, underPlayerType: Class<*>): Any =
        synchronized(underPlayerProxies) {
            underPlayerProxies.getOrPut(original) {
                Proxy.newProxyInstance(
                    original.javaClass.classLoader ?: classLoader,
                    collectProxyInterfaces(original, underPlayerType),
                    InvocationHandler { proxy, method, args ->
                        runCatching {
                            when {
                                method.isObjectMethod("toString", 0) ->
                                    "BBZQUnderPlayerProxy(${original.javaClass.name})"
                                method.isObjectMethod("hashCode", 0) ->
                                    System.identityHashCode(proxy)
                                method.isObjectMethod("equals", 1) ->
                                    proxy === args?.firstOrNull()
                                method.name == "getUpperAdView" -> {
                                    logBlocked(method.name)
                                    null
                                }
                                method.name in BLOCKED_METHODS -> {
                                    logBlocked(method.name)
                                    val result = invokeOriginal(original, method, args) ?: return@runCatching null
                                    createAdCallbackProxy(result)
                                }
                                else ->
                                    invokeOriginal(original, method, args)
                            }
                        }.getOrElse {
                            log("VideoDetailBannerAd underPlayer proxy failed at ${method.declaringClass.name}.${method.name}", it)
                            invokeOriginal(original, method, args)
                        }
                    },
                )
            }
        }

    private fun pausedPageProxy(
        original: Any,
        pausedPageType: Class<*>,
        requestPausedPage: Method,
    ): Any = synchronized(pausedPageProxies) {
        pausedPageProxies.getOrPut(original) {
            Proxy.newProxyInstance(
                original.javaClass.classLoader ?: classLoader,
                collectProxyInterfaces(original, pausedPageType),
                InvocationHandler { proxy, method, args ->
                    runCatching {
                        when {
                            method.isObjectMethod("toString", 0) ->
                                "BBZQPausedPageProxy(${original.javaClass.name})"
                            method.isObjectMethod("hashCode", 0) ->
                                System.identityHashCode(proxy)
                            method.isObjectMethod("equals", 1) ->
                                proxy === args?.firstOrNull()
                            method.name == "requestPausedPage" || method.hasSameSignatureAs(requestPausedPage) -> {
                                logBlocked(method.name)
                                null
                            }
                            method.name == "getCountDownView" -> {
                                logBlocked(method.name)
                                val context = args?.getOrNull(0) as? Context
                                if (context != null) {
                                    Space(context).apply {
                                        visibility = View.GONE
                                        layoutParams = ViewGroup.LayoutParams(0, 0)
                                    }
                                } else {
                                    null
                                }
                            }
                            else ->
                                invokeOriginal(original, method, args)
                        }
                    }.getOrElse {
                        log("VideoDetailBannerAd paused page proxy failed at ${method.declaringClass.name}.${method.name}", it)
                        invokeOriginal(original, method, args)
                    }
                },
            )
        }
    }

    private fun adPanelProxy(
        original: Any,
        adPanelType: Class<*>,
        getPausedPagePanel: Method?,
        getBrandPausedPagePanel: Method?,
    ): Any = synchronized(adPanelProxies) {
        adPanelProxies.getOrPut(original) {
            Proxy.newProxyInstance(
                original.javaClass.classLoader ?: classLoader,
                collectProxyInterfaces(original, adPanelType),
                InvocationHandler { proxy, method, args ->
                    runCatching {
                        when {
                            method.isObjectMethod("toString", 0) ->
                                "BBZQAdPanelProxy(${original.javaClass.name})"
                            method.isObjectMethod("hashCode", 0) ->
                                System.identityHashCode(proxy)
                            method.isObjectMethod("equals", 1) ->
                                proxy === args?.firstOrNull()
                            method.hasSameSignatureAs(getPausedPagePanel) ||
                                method.hasSameSignatureAs(getBrandPausedPagePanel) -> {
                                logBlocked(method.name)
                                val result = invokeOriginal(original, method, args) ?: return@runCatching null
                                createAdCallbackProxy(result)
                            }
                            else ->
                                invokeOriginal(original, method, args)
                        }
                    }.getOrElse {
                        log("VideoDetailBannerAd panel proxy failed at ${method.declaringClass.name}.${method.name}", it)
                        invokeOriginal(original, method, args)
                    }
                },
            )
        }
    }

    private fun relateProxy(original: Any, relateType: Class<*>): Any =
        synchronized(relateProxies) {
            relateProxies.getOrPut(original) {
                Proxy.newProxyInstance(
                    original.javaClass.classLoader ?: classLoader,
                    collectProxyInterfaces(original, relateType),
                    InvocationHandler { proxy, method, args ->
                        runCatching {
                            when {
                                method.isObjectMethod("toString", 0) ->
                                    "BBZQRelateProxy(${original.javaClass.name})"
                                method.isObjectMethod("hashCode", 0) ->
                                    System.identityHashCode(proxy)
                                method.isObjectMethod("equals", 1) ->
                                    proxy === args?.firstOrNull()
                                method.name == "getAdRelateView" -> {
                                    logBlocked(method.name)
                                    null
                                }
                                else ->
                                    invokeOriginal(original, method, args)
                            }
                        }.getOrElse {
                            log("VideoDetailBannerAd relate proxy failed at ${method.declaringClass.name}.${method.name}", it)
                            invokeOriginal(original, method, args)
                        }
                    },
                )
            }
        }

    private fun merchandiseProxy(original: Any, merchandiseType: Class<*>): Any =
        synchronized(merchandiseProxies) {
            merchandiseProxies.getOrPut(original) {
                Proxy.newProxyInstance(
                    original.javaClass.classLoader ?: classLoader,
                    collectProxyInterfaces(original, merchandiseType),
                    InvocationHandler { proxy, method, args ->
                        runCatching {
                            when {
                                method.isObjectMethod("toString", 0) ->
                                    "BBZQMerchandiseProxy(${original.javaClass.name})"
                                method.isObjectMethod("hashCode", 0) ->
                                    System.identityHashCode(proxy)
                                method.isObjectMethod("equals", 1) ->
                                    proxy === args?.firstOrNull()
                                method.name == "getAdMerchandiseView" -> {
                                    logBlocked(method.name)
                                    null
                                }
                                else ->
                                    invokeOriginal(original, method, args)
                            }
                        }.getOrElse {
                            log("VideoDetailBannerAd merchandise proxy failed at ${method.declaringClass.name}.${method.name}", it)
                            invokeOriginal(original, method, args)
                        }
                    },
                )
            }
        }

    private fun invokeOriginal(target: Any, method: Method, args: Array<Any?>?): Any? =
        try {
            if (args == null) method.invoke(target) else method.invoke(target, *args)
        } catch (throwable: InvocationTargetException) {
            throw throwable.targetException ?: throwable
        }

    private fun Method.isObjectMethod(name: String, parameterCount: Int): Boolean =
        declaringClass == Any::class.java && this.name == name && this.parameterCount == parameterCount

    private fun Method.hasSameSignatureAs(other: Method?): Boolean =
        other != null &&
            name == other.name &&
            returnType == other.returnType &&
            parameterTypes.contentEquals(other.parameterTypes)

    private fun collectProxyInterfaces(original: Any, primaryType: Class<*>): Array<Class<*>> =
        buildSet {
            add(primaryType)
            original.javaClass.interfaces.forEach(::add)
            original.javaClass.takeIf { it.isInterface }?.let(::add)
        }.toTypedArray()

    private fun createEmptyViewEntry(entryConstructor: Constructor<*>, context: Context): Any? {
        val view = Space(context).apply {
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        }
        return runCatching {
            entryConstructor.newInstance(view)
        }.getOrNull()
    }

    private fun createAdCallbackProxy(originalCallback: Any): Any {
        val callbackClass = originalCallback.javaClass

        if (callbackClass.name.startsWith("kotlinx.coroutines.") ||
            callbackClass.name.startsWith("kotlin.coroutines.")
        ) {
            return originalCallback
        }

        val interfaces = buildSet {
            var currentClass: Class<*>? = callbackClass
            while (currentClass != null) {
                currentClass.interfaces.forEach { add(it) }
                currentClass = currentClass.superclass
            }
        }.toTypedArray()

        if (interfaces.isEmpty()) return originalCallback

        if (interfaces.any { it.name.startsWith("kotlinx.coroutines.") || it.name.startsWith("kotlin.coroutines.") }) {
            return originalCallback
        }

        val falseStateFlow = runCatching {
            val stateFlowKt = callbackClass.classLoader?.loadClass("kotlinx.coroutines.flow.StateFlowKt")
                ?: Class.forName("kotlinx.coroutines.flow.StateFlowKt")
            val method = stateFlowKt.getDeclaredMethod("MutableStateFlow", Any::class.java)
            method.invoke(null, java.lang.Boolean.FALSE)
        }.getOrNull()

        return Proxy.newProxyInstance(
            callbackClass.classLoader ?: classLoader,
            interfaces,
            InvocationHandler { proxy, method, args ->
                when {
                    method.isObjectMethod("toString", 0) ->
                        "BBZQAdCallbackProxy(${originalCallback.javaClass.name})"
                    method.isObjectMethod("hashCode", 0) ->
                        System.identityHashCode(proxy)
                    method.isObjectMethod("equals", 1) ->
                        proxy === args?.firstOrNull()
                    method.name == "isBlankView" && method.parameterCount == 0 -> true
                    method.name == "defaultContainerVisible" && method.parameterCount == 0 -> false
                    method.name == "getViewHeight" && method.parameterCount == 0 -> 0
                    method.name == "isSupportAnimIn" && method.parameterCount == 0 -> false
                    method.name == "getVisibleFlow" && method.parameterCount == 0 && falseStateFlow != null -> {
                        falseStateFlow
                    }
                    (method.name == "getRootView" || method.name == "getAdView" || method.name == "getAdRoot") && method.parameterCount == 0 -> {
                        val realView = invokeOriginal(originalCallback, method, args) as? View
                        realView?.apply {
                            visibility = View.GONE
                            layoutParams = ViewGroup.LayoutParams(0, 0)
                            setPadding(0, 0, 0, 0)
                        }
                        realView
                    }
                    else -> invokeOriginal(originalCallback, method, args)
                }
            },
        )
    }

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

