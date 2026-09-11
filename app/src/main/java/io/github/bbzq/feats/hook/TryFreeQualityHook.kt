package io.github.bbzq.feats.hook

import android.app.Application
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostAccountResolver
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.getObjectField
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Method

class TryFreeQualityHook(env: io.github.bbzq.feats.RoamingEnv) : BaseRoamingHook(env) {
    private var trialQualityEnabled = false
    private var highestBitrateEnabled = false
    private val highestBitrate = HighestBitrateProcessor { message, throwable ->
        log("HighestBitrate $message", throwable)
    }
    private val hookedHandlerClasses = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Class<*>, Boolean>())

    private fun isTrialEnabled() = ModuleSettings.isUnlockVideoFeaturesEnabled(prefs)
    private fun isHighestBitrateEnabled() = ModuleSettings.isUnlockHighestBitrateEnabled(prefs)

    override fun startHook() {
        if (env.processName != env.packageName) return
        trialQualityEnabled = isTrialEnabled()
        highestBitrateEnabled = isHighestBitrateEnabled()
        val videoDownloadEnabled = ModuleSettings.isVideoDownloadEnabled(prefs)

        if (!trialQualityEnabled && !highestBitrateEnabled && !videoDownloadEnabled) {
            log("startHook: PlayView quality pipeline and video download disabled")
            return
        }
        
        if (highestBitrateEnabled) {
            highestBitrate.avoidHdrDolby = ModuleSettings.isAvoidHdrDolbyEnabled(prefs)
        }
        env.hostContext?.let { context ->
            VideoStatsOverlayController.getOrCreate(context)
        }

        // Hook TextView to append Download Link to description when enabled
        if (videoDownloadEnabled) {
            runCatching {
                val setTextMethod = android.widget.TextView::class.java.getMethod("setText", CharSequence::class.java, android.widget.TextView.BufferType::class.java)
                env.hookBefore(setTextMethod) { param ->
                    val tv = param.thisObject as? android.widget.TextView ?: return@hookBefore
                    if (tv.id == android.view.View.NO_ID) return@hookBefore

                    val ctx = tv.context ?: return@hookBefore
                    var activity: android.app.Activity? = null
                    var current = ctx
                    while (current is android.content.ContextWrapper) {
                        if (current is android.app.Activity) {
                            activity = current
                            break
                        }
                        current = current.baseContext
                    }
                    if (activity == null || !isVideoDetailActivity(activity)) return@hookBefore

                    val resName = runCatching { tv.resources.getResourceEntryName(tv.id) }.getOrNull()?.lowercase() ?: return@hookBefore
                    if (!isTargetDescResName(resName)) return@hookBefore

                    val text = param.args[0] as? CharSequence ?: return@hookBefore
                    val textStr = text.toString()
                    if (textStr.isNotBlank() && !textStr.contains("下载视频")) {
                        tv.isFocusable = true
                        tv.isClickable = true
                        tv.isEnabled = true
                        tv.setTextIsSelectable(true)
                        tv.movementMethod = android.text.method.LinkMovementMethod.getInstance()

                        val spannable = android.text.SpannableStringBuilder(text)
                        spannable.append("\n\n")
                        
                        // Stats Span
                        val statsText = "视频数据"
                        val statsSpan = android.text.SpannableString(statsText)
                        statsSpan.setSpan(object : android.text.style.ClickableSpan() {
                            override fun onClick(widget: android.view.View) {
                                val targetActivity = findActivity(widget.context) ?: activity
                                VideoStatsOverlayController.getOrCreate(targetActivity).showStats(targetActivity)
                            }
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                super.updateDrawState(ds)
                                ds.color = android.graphics.Color.parseColor("#FB7299")
                                ds.isUnderlineText = false
                            }
                        }, 0, statsSpan.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        
                        // Download Span
                        val dlText = "下载视频"
                        val dlSpan = android.text.SpannableString(dlText)
                        dlSpan.setSpan(object : android.text.style.ClickableSpan() {
                            override fun onClick(widget: android.view.View) {
                                val targetActivity = findActivity(widget.context) ?: activity
                                VideoStatsOverlayController.getOrCreate(targetActivity).showDownload(targetActivity)
                            }
                            override fun updateDrawState(ds: android.text.TextPaint) {
                                super.updateDrawState(ds)
                                ds.color = android.graphics.Color.parseColor("#FB7299")
                                ds.isUnderlineText = false
                            }
                        }, 0, dlSpan.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                        spannable.append(" ")
                        spannable.append(statsSpan)
                        spannable.append(" | ")
                        spannable.append(dlSpan)
                        spannable.append(" ")
                        param.args[0] = spannable
                    }
                }
            }.onFailure { log("Failed to hook TextView.setText for download link", it) }
        }

        if (!trialQualityEnabled && !highestBitrateEnabled) {
            log("startHook: PlayView quality pipeline disabled (video download hook active)")
            return
        }
        
        val symbols = env.symbols?.tryFreeQuality?.restore(classLoader) ?: run {
            log("startHook: TryFreeQuality skipped because symbols are unavailable")
            return
        }

        var requestHooks = 0
        var responseHooks = 0
        var uiHooks = 0

        if (trialQualityEnabled) requestHooks += hookRequestMethods(symbols)
        responseHooks += hookResponseMethods(symbols)
        if (trialQualityEnabled && ModuleSettings.isUnlockVideoFeaturesUiEnabled(prefs)) {
            uiHooks += hookUiMethods(symbols)
        }

        log("startHook: TryFreeQuality, request=$requestHooks,response=$responseHooks,ui=$uiHooks")
    }

    private fun hookRequestMethods(symbols: io.github.bbzq.feats.symbol.RestoredTryFreeQualitySymbols): Int {
        var count = 0
        symbols.getIsNeedTrialMethods.forEach { method ->
            count += hookSafely(method, "request/getIsNeedTrial") {
                env.hookBefore(method) { param ->
                    param.result = true
                }
            }
        }
        symbols.setIsNeedTrialMethods.forEach { method ->
            count += hookSafely(method, "request/setIsNeedTrial") {
                env.hookBefore(method) { param ->
                    if (param.args.isNotEmpty()) {
                        param.args[0] = true
                    }
                }
            }
        }
        return count
    }

    private fun hookResponseMethods(symbols: io.github.bbzq.feats.symbol.RestoredTryFreeQualitySymbols): Int {
        var count = 0
        symbols.playViewMethods.forEach { method ->
            count += hookSafely(method, "response/playView") {
                env.hookBefore(method) { param ->
                    runCatching {
                        preparePlayViewRequest(param.args.getOrNull(0))
                        val handler = param.args.getOrNull(1) ?: return@runCatching
                        hookHandlerClass(handler.javaClass)
                    }.onFailure {
                        log("TryFreeQuality response before hook failed at ${method.declaringClass.name}.${method.name}", it)
                    }
                }
                env.hookAfter(method) { param ->
                    runCatching {
                        processPlayViewResponse(param.result)
                    }.onFailure {
                        log("TryFreeQuality response after hook failed at ${method.declaringClass.name}.${method.name}", it)
                    }
                }
            }
        }
        return count
    }

    private fun hookHandlerClass(handlerClass: Class<*>): Boolean {
        if (!hookedHandlerClasses.add(handlerClass)) return true
        var hooked = false
        handlerClass.allMethods().filter { it.name == "onNext" && it.parameterCount == 1 }.forEach { onNextMethod ->
            env.hookBefore(onNextMethod) { p ->
                runCatching {
                    processPlayViewResponse(p.args.firstOrNull())
                }.onFailure {
                    log("TryFreeQuality handler onNext hook failed", it)
                }
            }
            hooked = true
        }
        return hooked
    }

    private fun hookUiMethods(symbols: io.github.bbzq.feats.symbol.RestoredTryFreeQualitySymbols): Int {
        var count = 0
        symbols.getVipFreeMethods.forEach { method ->
            count += hookSafely(method, "ui/getVipFree") {
                env.hookAfter(method) { param ->
                    if (!isTrialEnabled() || !ModuleSettings.isUnlockVideoFeaturesUiEnabled(prefs)) return@hookAfter
                    val needVip = (param.thisObject?.getObjectField("needVip_") as? Boolean) ?: return@hookAfter
                    param.result = needVip
                }
            }
        }
        symbols.getNeedVipMethods.forEach { method ->
            count += hookSafely(method, "ui/getNeedVip") {
                env.hookBefore(method) { param ->
                    if (!isTrialEnabled() || !ModuleSettings.isUnlockVideoFeaturesUiEnabled(prefs)) return@hookBefore
                    param.result = false
                }
            }
        }
        return count
    }

    private fun hookSafely(method: Method, group: String, register: () -> Unit): Int {
        return runCatching {
            register()
            1
        }.getOrElse {
            log("TryFreeQuality failed to hook $group at ${method.declaringClass.name}.${method.name}", it)
            0
        }
    }

    private fun processPlayViewResponse(target: Any?) {
        if (target == null) return
        val trial = isTrialEnabled()
        val highest = isHighestBitrateEnabled()
        if (!trial && !highest) return

        runCatching {
            val videoInfo = target.callMethod("getVideoInfo") ?: target.callMethod("getVodInfo")
            val bvid = (videoInfo?.callMethod("getBvid") as? String)?.takeIf { it.isNotBlank() }
                ?: (target.callMethod("getBvid") as? String)?.takeIf { it.isNotBlank() }
            if (bvid != null && (bvid.startsWith("BV1") || bvid.startsWith("bv1"))) {
                VideoStatsOverlayController.currentBvid = bvid
            }

            if (trial) {
                clearTrialMarkers(target)
                clearStreamVipMarkers(target.callMethod("getVideoInfo"))
                clearStreamVipMarkers(target.callMethod("getVodInfo"))
                clearStreamVipMarkers(target.callMethod("getViewInfo"))
            }
            if (highest) {
                highestBitrate.avoidHdrDolby = ModuleSettings.isAvoidHdrDolbyEnabled(prefs)
            }
            val stats = if (highest) {
                highestBitrate.preferHighestBitrate(target)
            } else {
                highestBitrate.readStats(target)
            }
            if (stats != null) {
                VideoStatsOverlayController.instance?.update(stats)
            }
            CustomCdnProcessor.rewriteResponse(target, prefs, ::log)
        }.onFailure {
            log("PlayView quality response processing failed at ${target.javaClass.name}", it)
        }
    }

    private fun clearTrialMarkers(target: Any) {
        if (target.callMethod("hasQnTrialInfo") as? Boolean == true) {
            target.callMethod("clearQnTrialInfo")
        }
        if (target.callMethod("hasHighDefinitionTrialInfo") as? Boolean == true) {
            target.callMethod("clearHighDefinitionTrialInfo")
        }
        val viewInfo = target.callMethod("getViewInfo") ?: return
        if (viewInfo.callMethod("hasHighDefinitionTrialInfo") as? Boolean == true) {
            viewInfo.callMethod("clearHighDefinitionTrialInfo")
        }
    }

    private fun clearStreamVipMarkers(container: Any?) {
        if (container == null) return
        runCatching {
            val streamList = container.callMethod("getStreamListList")
            val streams = streamList as? Iterable<*> ?: return@runCatching
            streams.forEach { stream ->
                val streamItem = stream ?: return@forEach
                clearStreamInfo(streamItem.callMethod("getStreamInfo"))
                clearStreamInfo(streamItem)
                clearStreamInfo(streamItem.callMethod("getDashVideo"))
            }
        }.onFailure {
            log("TryFreeQuality stream cleanup failed at ${container.javaClass.name}", it)
        }
    }

    private fun clearStreamInfo(target: Any?) {
        if (target == null) return
        runCatching {
            target.callMethod("setNeedVip", false)
            target.callMethod("setVipFree", true)
        }.onFailure {
            log("TryFreeQuality streamInfo cleanup failed at ${target.javaClass.name}", it)
        }
    }

    private fun preparePlayViewRequest(request: Any?) {
        if (request == null) return
        val trial = isTrialEnabled()
        val highest = isHighestBitrateEnabled()
        if (!trial && !highest) return

        runCatching {
            val bvid = (request.callMethod("getBvid") as? String)?.takeIf { it.isNotBlank() }
            val aid = (request.callMethod("getAid") as? Number)?.toLong()?.takeIf { it > 0 }
            val cid = (request.callMethod("getCid") as? Number)?.toLong()?.takeIf { it > 0 }
            if (bvid != null && (bvid.startsWith("BV1") || bvid.startsWith("bv1"))) {
                VideoStatsOverlayController.currentBvid = bvid
            } else if (aid != null) {
                VideoStatsOverlayController.currentBvid = SkipVideoAdState.bvidFromAid(aid)
            }
            if (cid != null) VideoStatsOverlayController.currentCid = cid

            if (trial) {
                request.callMethod("setIsNeedTrial", true)
                request.callMethod("setIsNeedViewInfo", true)
                request.callMethod("getVod")?.let { vod ->
                    vod.callMethod("setIsNeedTrial", true)
                    vod.callMethod("setIsNeedViewInfo", true)
                }
                request.callMethod("getViewInfo")?.let { viewInfo ->
                    viewInfo.callMethod("setIsNeedViewInfo", true)
                }
            }
            if (highest) {
                highestBitrate.avoidHdrDolby = ModuleSettings.isAvoidHdrDolbyEnabled(prefs)
                highestBitrate.prepareRequest(request)
            }
        }.onFailure {
            log("TryFreeQuality request prep failed at ${request.javaClass.name}", it)
        }
    }

    private fun resolveWatermarkIdentity(): UserWatermarkIdentity {
        val snapshot = HostAccountResolver.resolve(env.hostContext, classLoader)
        return UserWatermarkIdentity(
            uid = snapshot.uid,
            userName = snapshot.userName,
        )
    }

    private fun findActivity(context: android.content.Context?): android.app.Activity? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun isVideoDetailActivity(activity: android.app.Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("VideoDetail", ignoreCase = true) ||
            name.contains("UnitedBizDetailsActivity", ignoreCase = true)
    }

    private fun isTargetDescResName(resName: String): Boolean {
        if (resName in ALLOWED_DESC_RES_NAMES) return true
        if (BLOCKED_DESC_KEYWORDS.any { resName.contains(it) }) return false
        return resName == "desc" || resName == "tv_desc" || resName == "video_desc" ||
            resName.endsWith("_desc") || resName.endsWith("_description") || resName.startsWith("desc_")
    }

    private companion object {
        private val ALLOWED_DESC_RES_NAMES = setOf(
            "desc",
            "tv_desc",
            "expandable_desc",
            "video_desc",
            "tv_description",
            "intro_desc",
            "ugc_desc",
            "archive_desc",
            "detail_desc",
            "desc_text",
            "desc_content",
            "video_detail_desc",
        )
        private val BLOCKED_DESC_KEYWORDS = listOf(
            "vote",
            "poll",
            "dialog",
            "reply",
            "comment",
            "goods",
            "mall",
            "shop",
            "item",
            "badge",
            "honor",
            "award",
            "card",
            "banner",
            "author",
            "user",
            "header",
            "footer",
            "notice",
            "guide",
            "toast",
        )
    }
}
