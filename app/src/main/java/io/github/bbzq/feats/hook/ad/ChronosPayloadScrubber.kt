package io.github.bbzq.feats.hook.ad

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.isAssignableFromBoxed
import io.github.bbzq.feats.setObjectField
import java.lang.reflect.Array as JavaArray
import java.util.ArrayList
import java.util.LinkedHashMap

internal fun ChronosPromotionHook.stripViewProgressDetail(detail: Any): Int {
    var removed = 0
    removed += clearAttentionCard(detail)
    removed += filterVideoGuide(detail)
    removed += scrubOriginData(detail)
    return removed
}

private fun ChronosPromotionHook.clearAttentionCard(detail: Any): Int {
    val dmResource = detail.callMethod("getDmResource") ?: return 0
    val attentionCard = dmResource.callMethod("getAttentionCard") ?: return 0
    val showTimes = attentionCard.callMethod("getShowTimeList") as? Collection<*> ?: return 0
    if (showTimes.isEmpty()) return 0
    val removed = showTimes.size
    if (dmResource.setObjectField("attentionCard", null)) {
        return removed
    }
    (showTimes as? MutableCollection<*>)?.clear()
    return removed
}

private fun ChronosPromotionHook.filterVideoGuide(detail: Any): Int {
    val videoGuide = detail.callMethod("getVideoGuide") ?: return 0
    var removed = 0

    val rightMaterial = videoGuide.callMethod("getRightMaterial")
    if (rightMaterial != null && materialBlockReason(rightMaterial) != null) {
        if (videoGuide.setObjectField("rightMaterial", null)) removed++
    }

    val materialList = videoGuide.callMethod("getMaterialList") as? List<*>
    if (!materialList.isNullOrEmpty()) {
        val kept = materialList.filterNot { item -> item != null && materialBlockReason(item) != null }
        val removedMaterials = materialList.size - kept.size
        if (removedMaterials > 0 && videoGuide.setObjectField("materialList", ArrayList(kept))) {
            removed += removedMaterials
        }
    }

    val videoPoint = videoGuide.callMethod("getVideoPoint") ?: return removed
    val pointMaterial = videoPoint.callMethod("getPointMaterial")
    if (pointMaterial != null && pointMaterialBlockReason(pointMaterial) != null) {
        if (videoPoint.setObjectField("pointMaterial", null)) removed++
    }

    val videoPointList = videoPoint.callMethod("getVideoPointList") as? List<*> ?: return removed
    if (videoPointList.isEmpty()) return removed
    val keptPoints = videoPointList.filterNot { item -> item != null && videoPointBlockReason(item) != null }
    val removedPoints = videoPointList.size - keptPoints.size
    if (removedPoints > 0 && videoPoint.setObjectField("videoPointList", ArrayList(keptPoints))) {
        removed += removedPoints
    }
    return removed
}

private fun ChronosPromotionHook.scrubOriginData(detail: Any): Int =
    runCatching {
        val origin = detail.callMethod("getOriginData") ?: return@runCatching 0
        scrubOriginReply(origin)
    }.onFailure { throwable ->
        val count = ++originScrubErrorCount
        if (count <= 3) {
            log("ChronosPromotion origin scrub failed: ${throwable.message}", throwable)
        }
    }.getOrDefault(0)

internal fun ChronosPromotionHook.scrubOriginReply(origin: Any): Int {
    var removed = 0

    val videoGuide = origin.callMethod("getVideoGuide")
    if (videoGuide != null) {
        removed += filterGeneratedList(
            owner = videoGuide,
            listGetter = "getMaterialList",
            clearMethod = "clearMaterial",
            addAllMethod = "addAllMaterial",
        ) { item -> item != null && materialBlockReason(item) != null }

        val rightMaterial = videoGuide.callMethod("getRightMaterial")
        if (rightMaterial != null && materialBlockReason(rightMaterial) != null && videoGuide.invokeNoArgMethod("clearRightMaterial")) {
            removed++
        }

        val pointMaterial = videoGuide.callMethod("getPointMaterial")
        if (pointMaterial != null && pointMaterialBlockReason(pointMaterial) != null && videoGuide.invokeNoArgMethod("clearPointMaterial")) {
            removed++
        }

        removed += filterGeneratedList(
            owner = videoGuide,
            listGetter = "getPointsList",
            clearMethod = "clearPoints",
            addAllMethod = "addAllPoints",
        ) { item -> item != null && videoPointBlockReason(item) != null }

        val videoPoint = videoGuide.callMethod("getVideoPoint")
        if (videoPoint != null) {
            val pointMaterialInGuide = videoPoint.callMethod("getPointMaterial")
            if (
                pointMaterialInGuide != null &&
                pointMaterialBlockReason(pointMaterialInGuide) != null &&
                videoPoint.invokeNoArgMethod("clearPointMaterial")
            ) {
                removed++
            }
            removed += filterGeneratedList(
                owner = videoPoint,
                listGetter = "getPointsList",
                clearMethod = "clearPoints",
                addAllMethod = "addAllPoints",
            ) { item -> item != null && videoPointBlockReason(item) != null }
        }

        removed += filterGeneratedList(
            owner = videoGuide,
            listGetter = "getCommandDmsList",
            clearMethod = "clearCommandDms",
            addAllMethod = "addAllCommandDms",
        ) { item -> item != null && commandDanmakuBlockReason(item) != null }

        removed += filterGeneratedList(
            owner = videoGuide,
            listGetter = "getOperationCardList",
            clearMethod = "clearOperationCard",
            addAllMethod = "addAllOperationCard",
        ) { item -> item != null && operationCardBlockReason(item) != null }

        removed += filterGeneratedList(
            owner = videoGuide,
            listGetter = "getOperationCardNewList",
            clearMethod = "clearOperationCardNew",
            addAllMethod = "addAllOperationCardNew",
        ) { item -> item != null && operationCardBlockReason(item) != null }

        removed += filterGeneratedList(
            owner = videoGuide,
            listGetter = "getCardsSecondList",
            clearMethod = "clearCardsSecond",
            addAllMethod = "addAllCardsSecond",
        ) { item -> item != null && operationCardBlockReason(item) != null }

        val contractCard = videoGuide.callMethod("getContractCard")
        if (contractCard != null && contractCardBlockReason(contractCard) != null && videoGuide.invokeNoArgMethod("clearContractCard")) {
            removed++
        }
    }

    val dm = origin.callMethod("getDm")
    if (dm != null) {
        val attention = dm.callMethod("getAttention")
        val attentionTimes = attention?.callMethod("getShowTimeList") as? Collection<*>
        if (!attentionTimes.isNullOrEmpty() && dm.invokeNoArgMethod("clearAttention")) {
            removed += attentionTimes.size
        }

        removed += filterGeneratedList(
            owner = dm,
            listGetter = "getCommandDmsList",
            clearMethod = "clearCommandDms",
            addAllMethod = "addAllCommandDms",
        ) { item -> item != null && commandDanmakuBlockReason(item) != null }

        removed += filterGeneratedList(
            owner = dm,
            listGetter = "getCardsList",
            clearMethod = "clearCards",
            addAllMethod = "addAllCards",
        ) { item -> item != null && operationCardBlockReason(item) != null }
    }

    val rootPointMaterial = origin.callMethod("getPointMaterial")
    if (rootPointMaterial != null && pointMaterialBlockReason(rootPointMaterial) != null && origin.invokeNoArgMethod("clearPointMaterial")) {
        removed++
    }
    val buzzwordPeriods = origin.callMethod("getBuzzwordPeriodsList") as? Collection<*>
    if (!buzzwordPeriods.isNullOrEmpty() && origin.invokeNoArgMethod("clearBuzzwordPeriods")) {
        removed += buzzwordPeriods.size
    }
    removed += filterGeneratedList(
        owner = origin,
        listGetter = "getPointsList",
        clearMethod = "clearPoints",
        addAllMethod = "addAllPoints",
    ) { item -> item != null && videoPointBlockReason(item) != null }

    return removed
}

private fun ChronosPromotionHook.filterGeneratedList(
    owner: Any,
    listGetter: String,
    clearMethod: String,
    addAllMethod: String,
    block: (Any?) -> Boolean,
): Int {
    val originalList = owner.callMethod(listGetter) as? List<*> ?: return 0
    if (originalList.isEmpty()) return 0
    val kept = originalList.filterNot(block)
    val removed = originalList.size - kept.size
    if (removed <= 0) return 0
    if (!owner.invokeNoArgMethod(clearMethod)) return 0
    if (kept.isNotEmpty() && !owner.invokeOneArgMethod(addAllMethod, kept)) return removed
    return removed
}

internal fun ChronosPromotionHook.hookLocalViewProgressCallback(clazz: Class<*>, replyTypes: Map<String, Class<*>>) {
    if (!hookedViewProgressClasses.add(clazz)) return
    clazz.allMethods().filter { it.name == "invoke" && it.parameterCount == 2 }.forEach { invokeMethod ->
        env.hookBefore(invokeMethod) { param ->
            runCatching {
                if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) return@hookBefore
                val extra = param.args.getOrNull(1) ?: return@hookBefore
                val scrubbed = scrubLocalViewProgressExtra(extra, replyTypes)
                if (scrubbed.removed > 0) logBlocked("localViewProgress:${scrubbed.removed}")
                if (scrubbed.extra != null) {
                    param.args[1] = scrubbed.extra
                }
            }.onFailure {
                val count = ++localGetViewProgressErrorCount
                if (count <= 3) {
                    log("ChronosPromotion LocalViewProgress invoke hook failed", it)
                }
            }
        }
    }
}

internal fun ChronosPromotionHook.hookLocalDmViewCallback(clazz: Class<*>, replyType: Class<*>) {
    if (!hookedDmViewClasses.add(clazz)) return
    clazz.allMethods().filter { it.name == "invoke" && it.parameterCount == 2 }.forEach { invokeMethod ->
        env.hookBefore(invokeMethod) { param ->
            runCatching {
                if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) return@hookBefore
                val extra = param.args.getOrNull(1) ?: return@hookBefore
                val scrubbed = scrubDmViewExtra(extra, replyType)
                if (scrubbed.removed > 0) logBlocked("localDmView:${scrubbed.removed}")
                if (scrubbed.extra != null) {
                    param.args[1] = scrubbed.extra
                }
            }.onFailure {
                val count = ++localGetDmViewErrorCount
                if (count <= 3) {
                    log("ChronosPromotion LocalDmView invoke hook failed", it)
                }
            }
        }
    }
}

internal fun ChronosPromotionHook.hookCommandDmListCallback(clazz: Class<*>, responseType: Class<*>) {
    if (!hookedCommandDmListClasses.add(clazz)) return
    clazz.allMethods().filter { it.name == "invoke" && it.parameterCount == 2 }.forEach { invokeMethod ->
        env.hookBefore(invokeMethod) { param ->
            runCatching {
                if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) return@hookBefore
                val response = param.args.getOrNull(0) ?: return@hookBefore
                if (responseType.isInstance(response)) {
                    val removed = scrubCommandDmListResponse(response)
                    if (removed > 0) logBlocked("commandDmList:$removed")
                }
            }.onFailure {
                val count = ++commandDmListErrorCount
                if (count <= 3) {
                    log("ChronosPromotion CommandDmList invoke hook failed", it)
                }
            }
        }
    }
}

private fun ChronosPromotionHook.scrubLocalViewProgressExtra(extra: Any?, replyTypes: Map<String, Class<*>>): ScrubbedExtra {
    val original = extra as? Map<*, *> ?: return ScrubbedExtra(null, 0)
    var removed = 0
    var changed = false
    val cleaned = LinkedHashMap<Any?, Any?>(original.size)
    original.forEach { (key, value) ->
        val keyString = key as? String
        val replyType = keyString?.let(replyTypes::get)
        val bytes = value as? ByteArray
        if (replyType == null || bytes == null) {
            cleaned[key] = value
            return@forEach
        }

        val scrubbed = scrubReplyBytes(replyType, bytes)
        if (scrubbed.removed > 0) {
            removed += scrubbed.removed
            changed = true
        }
        cleaned[key] = scrubbed.bytes
    }
    return if (changed) ScrubbedExtra(cleaned, removed) else ScrubbedExtra(null, 0)
}

private fun ChronosPromotionHook.scrubReplyBytes(replyType: Class<*>, bytes: ByteArray): ScrubbedBytes =
    runCatching {
        val reply = replyType.callStaticMethod("parseFrom", bytes) ?: return@runCatching ScrubbedBytes(bytes, 0)
        val removed = scrubOriginReply(reply)
        if (removed <= 0) {
            ScrubbedBytes(bytes, 0)
        } else {
            val cleanBytes = reply.callMethod("toByteArray") as? ByteArray ?: bytes
            ScrubbedBytes(cleanBytes, removed)
        }
    }.onFailure { throwable ->
        val count = ++replyByteScrubErrorCount
        if (count <= 3) {
            log("ChronosPromotion reply byte scrub failed: ${throwable.message}", throwable)
        }
    }.getOrDefault(ScrubbedBytes(bytes, 0))

internal fun ChronosPromotionHook.scrubDmViewExtra(extra: Any?, replyType: Class<*>): ScrubbedExtra {
    val original = extra as? Map<*, *> ?: return ScrubbedExtra(null, 0)
    var removed = 0
    var changed = false
    val cleaned = LinkedHashMap<Any?, Any?>(original.size)
    original.forEach { (key, value) ->
        if (key != "reply" || value !is ByteArray) {
            cleaned[key] = value
            return@forEach
        }

        val scrubbed = scrubDmViewReplyBytes(replyType, value)
        if (scrubbed.removed > 0) {
            removed += scrubbed.removed
            changed = true
        }
        cleaned[key] = scrubbed.bytes
    }
    return if (changed) ScrubbedExtra(cleaned, removed) else ScrubbedExtra(null, 0)
}

private fun ChronosPromotionHook.scrubDmViewReplyBytes(replyType: Class<*>, bytes: ByteArray): ScrubbedBytes =
    runCatching {
        val reply = replyType.callStaticMethod("parseFrom", bytes) ?: return@runCatching ScrubbedBytes(bytes, 0)
        val removed = scrubDmViewReply(reply)
        if (removed <= 0) {
            ScrubbedBytes(bytes, 0)
        } else {
            val cleanBytes = reply.callMethod("toByteArray") as? ByteArray ?: bytes
            ScrubbedBytes(cleanBytes, removed)
        }
    }.onFailure { throwable ->
        val count = ++dmViewByteScrubErrorCount
        if (count <= 3) {
            log("ChronosPromotion dm view byte scrub failed: ${throwable.message}", throwable)
        }
    }.getOrDefault(ScrubbedBytes(bytes, 0))

internal fun ChronosPromotionHook.scrubDmViewReply(reply: Any): Int {
    val command = reply.callMethod("getCommand") ?: return 0
    return filterGeneratedList(
        owner = command,
        listGetter = "getCommandDmsList",
        clearMethod = "clearCommandDms",
        addAllMethod = "addAllCommandDms",
    ) { item -> item != null && commandDanmakuBlockReason(item) != null }
}

private fun ChronosPromotionHook.scrubCommandDmListResponse(response: Any): Int =
    runCatching {
        val originalList = response.callMethod("getDanmakuList") as? List<*> ?: return@runCatching 0
        if (originalList.isEmpty()) return@runCatching 0
        val kept = originalList.filterNot { item -> item != null && commandDanmakuBlockReason(item) != null }
        val removed = originalList.size - kept.size
        if (removed <= 0) return@runCatching 0
        if (response.invokeOneArgMethod("setDanmakuList", ArrayList(kept))) {
            removed
        } else {
            0
        }
    }.onFailure { throwable ->
        val count = ++commandDmListErrorCount
        if (count <= 3) {
            log("ChronosPromotion command dm list scrub failed: ${throwable.message}", throwable)
        }
    }.getOrDefault(0)

internal fun ChronosPromotionHook.scrubAddCustomDanmakuRequest(request: Any): ScrubbedCustomDanmakus =
    runCatching {
        val original = request.callMethod("getDms") as? Array<*> ?: return@runCatching ScrubbedCustomDanmakus(0, 0)
        if (original.isEmpty()) return@runCatching ScrubbedCustomDanmakus(0, 0)

        val kept = original.filterNot { item -> item != null && customDanmakuBlockReason(item) != null }
        val removed = original.size - kept.size
        if (removed <= 0) return@runCatching ScrubbedCustomDanmakus(0, original.size)
        if (kept.isEmpty() || replaceCustomDanmakus(request, original, kept)) {
            ScrubbedCustomDanmakus(removed, kept.size)
        } else {
            ScrubbedCustomDanmakus(0, original.size)
        }
    }.onFailure { throwable ->
        val count = ++customDanmakuScrubErrorCount
        if (count <= 3) {
            log("ChronosPromotion AddCustomDanmaku scrub failed: ${throwable.message}", throwable)
        }
    }.getOrDefault(ScrubbedCustomDanmakus(0, 0))

private fun ChronosPromotionHook.replaceCustomDanmakus(request: Any, original: Array<*>, kept: List<Any?>): Boolean {
    val componentType = original.javaClass.componentType ?: return false
    val cleaned = JavaArray.newInstance(componentType, kept.size)
    kept.forEachIndexed { index, item -> JavaArray.set(cleaned, index, item) }
    return request.invokeOneArgMethod("setDms", cleaned) || request.setObjectField("dms", cleaned)
}

internal fun ChronosPromotionHook.scrubGeminiOperationMaterials(widget: Any): Int {
    var removed = 0
    val fields = geminiListFieldsCache.getOrPut(widget.javaClass) {
        widget.javaClass.allFields()
            .filter { field -> MutableList::class.java.isAssignableFrom(field.type) }
            .toList()
    }
    fields.forEach { field ->
        @Suppress("UNCHECKED_CAST")
        val list = runCatching { field.get(widget) as? MutableList<Any?> }.getOrNull() ?: return@forEach
        if (list.isEmpty()) return@forEach
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next() ?: continue
            if (item is CharSequence || item is Number || item is Boolean) continue
            if (materialBlockReason(item) != null) {
                iterator.remove()
                removed++
            }
        }
    }
    return removed
}

private fun Any.invokeNoArgMethod(name: String): Boolean =
    invokeReflectMethod(name)

private fun Any.invokeOneArgMethod(name: String, arg: Any?): Boolean =
    invokeReflectMethod(name, arg)

private fun Any.invokeReflectMethod(name: String, vararg args: Any?): Boolean {
    val method = javaClass.allMethods().firstOrNull { candidate ->
        candidate.name == name &&
            candidate.parameterCount == args.size &&
            candidate.parameterTypes.indices.all { index ->
                candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
            }
    } ?: return false
    return runCatching {
        method.invoke(this, *args)
        true
    }.getOrDefault(false)
}

internal data class ScrubbedExtra(
    val extra: Map<*, *>?,
    val removed: Int,
)

private data class ScrubbedBytes(
    val bytes: ByteArray,
    val removed: Int,
)

internal data class ScrubbedCustomDanmakus(
    val removed: Int,
    val remaining: Int,
)
