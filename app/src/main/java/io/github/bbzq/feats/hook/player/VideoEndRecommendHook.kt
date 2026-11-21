package io.github.bbzq.feats.hook.player

import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.MethodHookParam
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookBeforeAllConstructors
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class VideoEndRecommendHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val cachedNormalRelateCards = CopyOnWriteArrayList<Any>()

    override fun startHook() {
        if (env.processName != env.packageName) return

        var installed = 0
        installed += installEndPagePureVideoHook()
        installed += installDisableCMTransformerHook()
        installed += installDisableRelatedTransformerHook()
        installed += installEndPageAdBridgeGuard()
        installed += installHalfScreenWidgetHook()
        installed += installFullScreenWidgetHook()
        installed += installTheseus2UgcEndPageHook()
        installed += installTheseus2OgvEndPageHook()

        if (installed > 0) {
            isInstalled = true
            log("startHook: VideoEndRecommendHook installed=$installed")
        } else {
            log("startHook: VideoEndRecommendHook no hook point found")
        }
    }

    // =========================================================================
    // 1. 移花接木确保结束推荐为纯净正常视频（消灭广告，消灭空槽）
    // =========================================================================

    private fun installEndPagePureVideoHook(): Int {
        val viewMossClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.v1.ViewMoss") ?: return 0
        var count = 0

        // 1. 缓存当前视频详情接口 (executeView) 中的纯正 UGC 推荐视频 (RelateCard.hasAv == true)
        val executeViewMethod = viewMossClass.declaredMethods.firstOrNull {
            it.name == "executeView" && it.parameterCount == 1
        }
        if (executeViewMethod != null) {
            env.hookAfter(executeViewMethod) { param ->
                runCatching {
                    val reply = param.result ?: return@runCatching
                    val relatesList = reply.callMethod("getRelatesList") as? List<*> ?: return@runCatching
                    val pureVideos = relatesList.filterNotNull().filter { card ->
                        val hasAv = card.callMethod("hasAv") == true
                        val hasCm = card.callMethod("hasCm") == true
                        val hasGame = card.callMethod("hasGame") == true
                        val hasSpecial = card.callMethod("hasSpecial") == true
                        hasAv && !hasCm && !hasGame && !hasSpecial
                    }
                    if (pureVideos.isNotEmpty()) {
                        cachedNormalRelateCards.clear()
                        cachedNormalRelateCards.addAll(pureVideos.take(10))
                        log("VideoEndRecommend: cached ${cachedNormalRelateCards.size} pure relate video cards from executeView")
                    }
                }
            }
            count++
        }

        // 2. 拦截并纯净化结束页推荐卡片：如果 Card 0 为广告/推广，移花接木替换为真正的正常视频卡片
        val executeViewEndPageMethod = viewMossClass.declaredMethods.firstOrNull {
            it.name == "executeViewEndPage" && it.parameterCount == 1
        }
        if (executeViewEndPageMethod != null) {
            env.hookAfter(executeViewEndPageMethod) { param ->
                runCatching {
                    val reply = param.result ?: return@runCatching
                    val cardList = reply.callMethod("getRelatesList") as? List<*> ?: return@runCatching

                    fun isAdOrPromotion(card: Any?): Boolean {
                        if (card == null) return true
                        val relate = card.callMethod("getRelate") ?: return true
                        val hasAv = relate.callMethod("hasAv") == true
                        val hasBangumi = relate.callMethod("hasBangumi") == true
                        val hasCm = relate.callMethod("hasCm") == true
                        val hasGame = relate.callMethod("hasGame") == true
                        val hasSpecial = relate.callMethod("hasSpecial") == true
                        return (!hasAv && !hasBangumi) || hasCm || hasGame || hasSpecial
                    }

                    val firstCard = cardList.firstOrNull()
                    if (firstCard != null && !isAdOrPromotion(firstCard)) {
                        return@runCatching
                    }

                    val fallbackFromEndPage = cardList.firstOrNull { !isAdOrPromotion(it) }
                    val replyBuilder = reply.callMethod("toBuilder") ?: return@runCatching
                    replyBuilder.callMethod("clearRelates")

                    if (fallbackFromEndPage != null) {
                        val cardBuilder = fallbackFromEndPage.callMethod("toBuilder")
                        cardBuilder?.callMethod("setCardIndex", 0)
                        val newFirstCard = cardBuilder?.callMethod("build") ?: fallbackFromEndPage
                        replyBuilder.callMethod("addRelates", newFirstCard)
                        var nextIdx = 1
                        cardList.filter { it !== fallbackFromEndPage && !isAdOrPromotion(it) }.forEach { c ->
                            if (c != null) {
                                val cb = c.callMethod("toBuilder")
                                cb?.callMethod("setCardIndex", nextIdx++)
                                replyBuilder.callMethod("addRelates", cb?.callMethod("build") ?: c)
                            }
                        }
                        param.result = replyBuilder.callMethod("build")
                        log("VideoEndRecommend: replaced ad Card 0 with fallback normal video from end page")
                        return@runCatching
                    }

                    val cachedPureRelate = cachedNormalRelateCards.firstOrNull()
                    if (cachedPureRelate != null) {
                        val endPageCardClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.v1.ViewEndPageCard")
                        val newBuilderMethod = endPageCardClass?.getMethod("newBuilder")
                        val cardBuilder = newBuilderMethod?.invoke(null)
                        if (cardBuilder != null) {
                            cardBuilder.callMethod("setCardIndex", 0)
                            cardBuilder.callMethod("setRelate", cachedPureRelate)
                            val createdCard = cardBuilder.callMethod("build")
                            if (createdCard != null) {
                                replyBuilder.callMethod("addRelates", createdCard)
                                param.result = replyBuilder.callMethod("build")
                                log("VideoEndRecommend: transplanted pure relate video card into end page!")
                            }
                        }
                    }
                }.onFailure {
                    log("VideoEndRecommend: purify end page failed", it)
                }
            }
            count++
        }
        return count
    }


    // =========================================================================
    // 3. 靶向隐藏半屏推荐 Widget（整层彻底消除，同步隐藏外层卡槽，杜绝无内容空槽）
    // =========================================================================

    private fun installHalfScreenWidgetHook(): Int {
        val halfWidgetClasses = listOf(
            "com.bilibili.ship.theseus.ugc.endpage.UGCHalfScreenEndPageWidget",
            "com.bilibili.ship.theseus.ugc.endpage.widget.UGCHalfScreenEndPageWidget",
            "com.bilibili.ship.theseus.ogv.endpage.OgvPlayerEndPageHalfFunctionWidget",
            "com.bilibili.app.gemini.ugc.feature.interactivevideo.InteractEndPageThumbWidget",
            "com.bilibili.tgwt.player.widget.TogetherWatchEndPageFunctionWidget",
        )
        return hookWidgetViews(halfWidgetClasses) {
            ModuleSettings.isDisableAllEndPage(prefs) || ModuleSettings.isDisableHalfEndPage(prefs)
        }
    }

    // =========================================================================
    // 4. 靶向隐藏全屏推荐 Widget（去除全部推荐时生效，支持横屏/竖屏/番剧全屏）
    // =========================================================================

    private fun installFullScreenWidgetHook(): Int {
        val fullWidgetClasses = listOf(
            "com.bilibili.ship.theseus.ugc.endpage.UGCHorizontalFullScreenEndPageWidget",
            "com.bilibili.ship.theseus.ugc.endpage.UGCVerticalFullScreenEndPageWidget",
            "com.bilibili.ship.theseus.ugc.endpage.UGCEndPageComposeWidget",
            "com.bilibili.ship.theseus.ogv.endpage.OgvPlayerEndPageFullScreenFunctionWidget",
            "com.bilibili.ship.theseus.ogv.endpage.OgvPlayerEndPageVerticalFullScreenFunctionWidget",
            "com.bilibili.app.gemini.ugc.feature.endpage.GeminiEndPageLandscapeRelativeWidget",
            "com.bilibili.app.gemini.ugc.feature.endpage.GeminiEndPageThumbRelativeWidget",
            "com.bilibili.app.gemini.ugc.feature.endpage.GeminiEndPageThumbRelativeNewWidget",
            "com.bilibili.app.gemini.ugc.feature.interactivevideo.InteractEndPageLandscapeWidget",
        )
        return hookWidgetViews(fullWidgetClasses) {
            ModuleSettings.isDisableAllEndPage(prefs)
        }
    }

    // =========================================================================
    // 4b. Bilibili 9.20+ (theseus2 Compose) UGC / OGV 结束推荐拦截
    // =========================================================================

    private fun installTheseus2UgcEndPageHook(): Int {
        var count = 0
        val unit = resolveKotlinUnit()

        env.symbols?.videoEndRecommend?.restore(classLoader)?.shouldShowEndPageMethod?.let { decisionMethod ->
            if (env.hookBefore(decisionMethod) { param ->
                    if (ModuleSettings.isDisableAllEndPage(prefs)) {
                        param.result = false
                    }
                }
            ) {
                count++
            }
        }

        if (unit != null) {
            classLoader.findClassOrNull(THESEUS2_UGC_SHOW_SURFACE_LAMBDA_CLASS)?.declaredMethods?.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.name == "invokeSuspend" &&
                    it.parameterTypes.contentEquals(arrayOf(Any::class.java))
            }?.let { invokeSuspend ->
                if (env.hookBefore(invokeSuspend) { param ->
                        if (ModuleSettings.isDisableAllEndPage(prefs)) {
                            param.result = unit
                        }
                    }
                ) {
                    count++
                }
            }
        }

        val widgetJobClass = classLoader.findClassOrNull(THESEUS2_UGC_WIDGET_JOB_LAMBDA_CLASS)
        val composeLambdaMethods = widgetJobClass?.declaredMethods?.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 7 &&
                method.parameterTypes[4].name == COMPOSE_MODIFIER_CLASS &&
                method.parameterTypes[5].name == COMPOSE_COMPOSER_CLASS &&
                method.parameterTypes[6] == Int::class.javaPrimitiveType
        }.orEmpty()

        val ugcServiceClass = composeLambdaMethods.firstOrNull()?.parameterTypes?.getOrNull(0)
        val composeWidgetClass = composeLambdaMethods.firstOrNull()?.parameterTypes?.getOrNull(1)
        val playerModeReader = ugcServiceClass?.let(::resolveScreenStatePlayerModeReader)

        if (unit != null && composeLambdaMethods.isNotEmpty()) {
            composeLambdaMethods.forEach { method ->
                if (env.hookBefore(method) { param ->
                        if (ModuleSettings.isDisableAllEndPage(prefs)) {
                            param.result = unit
                            return@hookBefore
                        }
                        if (!ModuleSettings.isDisableHalfEndPage(prefs)) return@hookBefore
                        val service = param.args.getOrNull(0) ?: return@hookBefore
                        if (playerModeReader?.invoke(service) == "HalfScreen") {
                            param.result = unit
                        }
                    }
                ) {
                    count++
                }
            }
        }

        if (composeWidgetClass != null) {
            val holderField = ugcServiceClass?.let { serviceType ->
                composeWidgetClass.declaredFields.firstOrNull { field ->
                    !Modifier.isStatic(field.modifiers) &&
                        runCatching { field.type.declaredFields.any { it.type == serviceType } }.getOrDefault(false)
                }?.apply { isAccessible = true }
            }
            val holderServiceField = if (holderField != null) {
                holderField.type.declaredFields.firstOrNull {
                    !Modifier.isStatic(it.modifiers) && it.type == ugcServiceClass
                }?.apply { isAccessible = true }
            } else {
                null
            }
            composeWidgetClass.declaredMethods.filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == Void.TYPE &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0].name == COMPOSE_MODIFIER_CLASS &&
                    method.parameterTypes[1].name == COMPOSE_COMPOSER_CLASS &&
                    method.parameterTypes[2] == Int::class.javaPrimitiveType
            }.forEach { renderMethod ->
                if (env.hookBefore(renderMethod) { param ->
                        if (ModuleSettings.isDisableAllEndPage(prefs)) {
                            param.result = null
                            return@hookBefore
                        }
                        if (!ModuleSettings.isDisableHalfEndPage(prefs)) return@hookBefore
                        val widget = param.thisObject ?: return@hookBefore
                        val service = if (holderField != null && holderServiceField != null) {
                            runCatching { holderField.get(widget)?.let(holderServiceField::get) }.getOrNull()
                        } else {
                            null
                        }
                        if (service != null && playerModeReader?.invoke(service) == "HalfScreen") {
                            param.result = null
                        }
                    }
                ) {
                    count++
                }
            }
        }

        if (count > 0) {
            log("VideoEndRecommend: installTheseus2UgcEndPageHook hooked $count points")
        }
        return count
    }

    private fun installTheseus2OgvEndPageHook(): Int {
        val serviceClass = classLoader.findClassOrNull(THESEUS2_OGV_END_PAGE_SERVICE_CLASS) ?: return 0
        val layoutClass = classLoader.findClassOrNull(THESEUS2_OGV_END_PAGE_LAYOUT_CLASS) ?: return 0
        val unit = resolveKotlinUnit()
        var count = 0

        serviceClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount in 1..2 &&
                method.parameterTypes[0] == layoutClass
        }.forEach { method ->
            val isVoid = method.returnType == Void.TYPE
            if (!isVoid && unit == null) return@forEach
            if (env.hookBefore(method) { param ->
                    val layoutName = (param.args.firstOrNull() as? Enum<*>)?.name ?: return@hookBefore
                    val shouldBlock = when (layoutName) {
                        "Half" -> ModuleSettings.isDisableAllEndPage(prefs) || ModuleSettings.isDisableHalfEndPage(prefs)
                        else -> ModuleSettings.isDisableAllEndPage(prefs)
                    }
                    if (shouldBlock) {
                        param.result = if (isVoid) null else unit
                    }
                }
            ) {
                count++
            }
        }

        if (count > 0) {
            log("VideoEndRecommend: installTheseus2OgvEndPageHook hooked $count points")
        }
        return count
    }

    private fun resolveKotlinUnit(): Any? = runCatching {
        classLoader.findClassOrNull("kotlin.Unit")?.getField("INSTANCE")?.get(null)
    }.getOrNull()

    private fun resolveScreenStatePlayerModeReader(ownerClass: Class<*>): ((Any) -> String?)? {
        val playerModeClass = classLoader.findClassOrNull(THESEUS2_PLAYER_MODE_CLASS) ?: return null
        val repoField = ownerClass.declaredFields.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name.startsWith("com.bilibili.ship.theseus2.united.page.screenstate.")
        }?.apply { isAccessible = true } ?: return null
        val stateGetter = repoField.type.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                runCatching { method.returnType.declaredFields.any { it.type == playerModeClass } }.getOrDefault(false)
        }?.apply { isAccessible = true } ?: return null
        val modeField = stateGetter.returnType.declaredFields.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.type == playerModeClass
        }?.apply { isAccessible = true } ?: return null
        return { owner ->
            runCatching {
                val repo = repoField.get(owner) ?: return@runCatching null
                val state = stateGetter.invoke(repo) ?: return@runCatching null
                (modeField.get(state) as? Enum<*>)?.name
            }.getOrNull()
        }
    }

    private fun hookWidgetViews(classNames: List<String>, shouldHide: () -> Boolean): Int {
        var count = 0
        for (className in classNames) {
            val clazz = classLoader.findClassOrNull(className) ?: continue
            for (method in clazz.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                val methodName = method.name
                val isRootViewGetter = View::class.java.isAssignableFrom(method.returnType) &&
                    methodName in ROOT_VIEW_GETTERS

                if (isRootViewGetter) {
                    env.hookAfter(method) { param ->
                        runCatching {
                            if (!shouldHide()) return@runCatching
                            val originalView = param.result as? View ?: return@runCatching
                            hideWidgetAndSlotContainer(originalView)
                        }
                    }
                    count++
                } else if (methodName == "onWidgetShow" || methodName == "show") {
                    env.hookAfter(method) { param ->
                        runCatching {
                            if (!shouldHide()) return@runCatching
                            val widget = param.thisObject ?: return@runCatching
                            val contentViewMethod = widget.javaClass.methods.firstOrNull {
                                (it.name == "getContentView" || it.name == "getView" || it.name == "onCreateContentView") &&
                                    it.parameterTypes.isEmpty() &&
                                    View::class.java.isAssignableFrom(it.returnType)
                            }
                            val view = (contentViewMethod?.invoke(widget) as? View)
                                ?: widget.javaClass.allFields().firstOrNull { View::class.java.isAssignableFrom(it.type) }?.let { field ->
                                    runCatching {
                                        field.isAccessible = true
                                        field.get(widget) as? View
                                    }.getOrNull()
                                }
                            if (view != null) {
                                hideWidgetAndSlotContainer(view)
                            }
                        }
                    }
                    count++
                }
            }
        }
        return count
    }

    private fun hideWidgetAndSlotContainer(view: View) {
        view.visibility = View.GONE
        view.alpha = 0f
        val parent = view.parent as? android.view.ViewGroup ?: return
        val parentCls = parent.javaClass.name
        val isDedicatedContainer = parent.childCount <= 1 ||
            parentCls.contains("Slot", ignoreCase = true) ||
            parentCls.contains("Container", ignoreCase = true) ||
            parentCls.contains("EndPage", ignoreCase = true)

        if (isDedicatedContainer) {
            parent.visibility = View.GONE
            parent.alpha = 0f
        }
    }

    private fun installDisableCMTransformerHook(): Int {
        val transformerClass = classLoader.findClassOrNull(
            "com.bilibili.ship.theseus.ugc.endpage.relatedrecommand.cm.UGCEndPageCMRelatedComponentsTransformer"
        ) ?: return 0
        var count = 0
        // (RelateCard, boolean): 广告卡片判定 -> 永远返回 false，让推荐引擎按纯普通视频处理
        // 混淆名随版本互换(9.15 为 a,9.17 为 b),按签名匹配
        val isCmMethod = transformerClass.declaredMethods.firstOrNull {
            it.isRelateCardMethod() && it.parameterCount == 2 && it.returnType == Boolean::class.javaPrimitiveType
        }
        if (isCmMethod != null) {
            env.hookBefore(isCmMethod) { param ->
                param.result = false
            }
            count++
        }
        // (RelateCard, int, ScreenModeType): 创建 CM 组件 -> 返回 null
        val createCmMethodB = transformerClass.findRelateCardComponentMethod()
        if (createCmMethodB != null) {
            env.hookBefore(createCmMethodB) { param ->
                param.result = null
            }
            count++
        }
        // c(EndPageCMModel, ...): 创建 CM 组件 -> 返回 null
        val createCmMethodC = transformerClass.declaredMethods.firstOrNull {
            it.parameterCount >= 3 && it.parameterTypes[0].name.endsWith(".EndPageCMModel")
        } ?: transformerClass.declaredMethods.firstOrNull {
            it.name == "c" && it.parameterCount >= 3
        }
        if (createCmMethodC != null) {
            env.hookBefore(createCmMethodC) { param ->
                param.result = null
            }
            count++
        }
        log("VideoEndRecommend: installDisableCMTransformerHook hooked $count methods")
        return count
    }

    // =========================================================================
    // 5. 靶向攔截普通推薦卡片渲染轉換器（去除全部結束推薦時生效，與 CM Transformer 形成雙保險）
    // =========================================================================

    private fun installDisableRelatedTransformerHook(): Int {
        val transformerClass = classLoader.findClassOrNull(
            "com.bilibili.ship.theseus.ugc.endpage.relatedrecommand.UGCEndPageRelatedComponentsTransformer"
        ) ?: return 0
        var count = 0
        // (RelateCard, int, ScreenModeType): 将服务端推荐卡片转换为端上 UI 组件 -> 去除全部结束推荐时返回 null
        val createRelatedMethod = transformerClass.findRelateCardComponentMethod()
        if (createRelatedMethod != null) {
            env.hookBefore(createRelatedMethod) { param ->
                if (ModuleSettings.isDisableAllEndPage(prefs)) {
                    param.result = null
                }
            }
            count++
        }
        log("VideoEndRecommend: installDisableRelatedTransformerHook hooked $count methods")
        return count
    }

    // =========================================================================
    // 6. 結束頁廣告橋接防崩：CM 組件被攔截後，宿主 theseus2 的 LegacyEndPageAdBridge
    //    仍會對 null 的 IVDEndPageAdCard 呼叫 getView()，導致影片播完（橫屏/無下一集）
    //    NPE 閃退，表現為白屏後返回上一級。這裡把 null 的廣告卡替換為空白佔位卡。
    // =========================================================================

    private fun installEndPageAdBridgeGuard(): Int {
        val cardClass = classLoader.findClassOrNull(AD_CARD_CLASS) ?: return 0
        var count = 0
        val guard: (MethodHookParam) -> Unit = guard@{ param ->
            val types = (param.executable as? Method)?.parameterTypes
                ?: (param.executable as? java.lang.reflect.Constructor<*>)?.parameterTypes
                ?: return@guard
            for (i in types.indices) {
                if (i < param.args.size && param.args[i] == null && cardClass.isAssignableFrom(types[i])) {
                    param.args[i] = createBlankAdCard(cardClass)
                }
            }
        }
        for (name in AD_BRIDGE_CLASSES) {
            val bridge = classLoader.findClassOrNull(name) ?: continue
            count += env.hookBeforeAllConstructors(bridge, guard)
            bridge.declaredMethods
                .filter { m -> m.parameterTypes.any { cardClass.isAssignableFrom(it) } }
                .forEach { m ->
                    m.isAccessible = true
                    env.hookBefore(m, guard)
                    count++
                }
        }
        // create 內部匿名類（LegacyEndPageAdBridge$create$1）才是實際 NPE 現場
        for (name in AD_BRIDGE_CLASSES) {
            for (suffix in listOf("\$create\$1", "\$create\$2")) {
                val inner = classLoader.findClassOrNull(name + suffix) ?: continue
                count += env.hookBeforeAllConstructors(inner, guard)
            }
        }
        log("VideoEndRecommend: installEndPageAdBridgeGuard hooked $count points")
        return count
    }

    private fun createBlankAdCard(cardClass: Class<*>): Any = Proxy.newProxyInstance(
        cardClass.classLoader, arrayOf(cardClass),
    ) { proxy, method, args ->
        when {
            method.name == "getView" && View::class.java.isAssignableFrom(method.returnType) -> newBlankView()
            method.declaringClass == Any::class.java -> when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> "BlankEndPageAdCard"
            }
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Float.TYPE -> 0f
                java.lang.Double.TYPE -> 0.0
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Character.TYPE -> '\u0000'
                else -> null
            }
        }
    }

    private fun newBlankView(): View {
        val app = Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication").invoke(null) as android.content.Context
        return View(app).apply {
            visibility = View.GONE
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
        }
    }

    private fun java.lang.reflect.Method.isRelateCardMethod(): Boolean =
        parameterTypes.firstOrNull()?.name?.endsWith(".RelateCard") == true

    private fun Class<*>.findRelateCardComponentMethod(): java.lang.reflect.Method? =
        declaredMethods.firstOrNull {
            it.isRelateCardMethod() && it.parameterCount == 3 &&
                it.parameterTypes[1] == Int::class.javaPrimitiveType && it.returnType != Void.TYPE
        }

    private companion object {
        private const val AD_CARD_CLASS = "com.bilibili.gripper.api.ad.biz.videodetail.endpage.IVDEndPageAdCard"
        private const val COMPOSE_MODIFIER_CLASS = "androidx.compose.ui.Modifier"
        private const val COMPOSE_COMPOSER_CLASS = "androidx.compose.runtime.Composer"
        private const val THESEUS2_PLAYER_MODE_CLASS =
            "com.bilibili.ship.theseus2.united.page.screenstate.PlayerMode"
        private const val THESEUS2_UGC_SHOW_SURFACE_LAMBDA_CLASS =
            "com.bilibili.ship.theseus2.ugc.endpage.UGCEndPageService\$showEndPageSurface\$2"
        private const val THESEUS2_UGC_WIDGET_JOB_LAMBDA_CLASS =
            "com.bilibili.ship.theseus2.ugc.endpage.UGCEndPageService\$showEndPageSurface\$2\$widgetJob\$1"
        private const val THESEUS2_OGV_END_PAGE_SERVICE_CLASS =
            "com.bilibili.ship.theseus2.ogv.endpage.OgvEndPageService"
        private const val THESEUS2_OGV_END_PAGE_LAYOUT_CLASS =
            "com.bilibili.ship.theseus2.ogv.endpage.OgvEndPageLayout"
        private val AD_BRIDGE_CLASSES = listOf("com.bilibili.ship.theseus2.adaption.endpage.LegacyEndPageAdBridge")
        private val ROOT_VIEW_GETTERS = setOf("onCreateContentView", "createContentView", "getContentView", "getView", "getRoot")
    }
}
