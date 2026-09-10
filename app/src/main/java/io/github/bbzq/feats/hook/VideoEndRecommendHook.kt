package io.github.bbzq.feats.hook

import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArrayList

class VideoEndRecommendHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val cachedNormalRelateCards = CopyOnWriteArrayList<Any>()

    override fun startHook() {
        if (env.processName != env.packageName) return

        var installed = 0
        installed += installEndPagePureVideoHook()
        installed += installDisableCMTransformerHook()
        installed += installDisableRelatedTransformerHook()
        installed += installHalfScreenWidgetHook()
        installed += installFullScreenWidgetHook()

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

    private fun hookWidgetViews(classNames: List<String>, shouldHide: () -> Boolean): Int {
        var count = 0
        for (className in classNames) {
            val clazz = classLoader.findClassOrNull(className) ?: continue
            for (method in clazz.declaredMethods) {
                if (Modifier.isStatic(method.modifiers)) continue
                val methodName = method.name
                val isRootViewGetter = View::class.java.isAssignableFrom(method.returnType) &&
                    (methodName == "onCreateContentView" || methodName == "getContentView" || methodName == "getView" || methodName == "getRoot")

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
        // a(RelateCard, boolean): 广告卡片判定 -> 永远返回 false，让推荐引擎按纯普通视频处理
        val isCmMethod = transformerClass.declaredMethods.firstOrNull {
            it.name == "a" && it.parameterCount == 2 && it.returnType == Boolean::class.javaPrimitiveType
        }
        if (isCmMethod != null) {
            env.hookBefore(isCmMethod) { param ->
                param.result = false
            }
            count++
        }
        // b(RelateCard, int, ScreenModeType): 创建 CM 组件 -> 返回 null
        val createCmMethodB = transformerClass.declaredMethods.firstOrNull {
            it.name == "b" && it.parameterCount == 3
        }
        if (createCmMethodB != null) {
            env.hookBefore(createCmMethodB) { param ->
                param.result = null
            }
            count++
        }
        // c(EndPageCMModel, ...): 创建 CM 组件 -> 返回 null
        val createCmMethodC = transformerClass.declaredMethods.firstOrNull {
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
        // b(RelateCard, int, ScreenModeType): 将服务端推荐卡片转换为端上 UI 组件 -> 去除全部结束推荐时返回 null
        val createRelatedMethod = transformerClass.declaredMethods.firstOrNull {
            it.name == "b" && it.parameterCount == 3
        }
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
}
