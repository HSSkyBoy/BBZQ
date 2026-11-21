package io.github.bbzq.feats.symbol

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONObject

data class SplashAdSymbols(
    val parserMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("parserMethods", parserMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSplashAdSymbols? {
        return RestoredSplashAdSymbols(parserMethods.restoreAll(classLoader) ?: return null)
    }

    companion object {
        fun fromJson(obj: JSONObject): SplashAdSymbols = SplashAdSymbols(
            parserMethods = obj.optJSONArray("parserMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSplashAdSymbols(
    val parserMethods: List<Method>,
)

data class RewardAdSymbols(
    val activityOnCreate: MethodDescriptor? = null,
    val activityOnResume: MethodDescriptor? = null,
    val activityOnStop: MethodDescriptor? = null,
    val headerSetTotalTime: MethodDescriptor? = null,
    val headerSetElapsedTime: MethodDescriptor? = null,
    val headerStartTimer: MethodDescriptor? = null,
    val countDownSetTotalTime: MethodDescriptor? = null,
    val countDownSetElapsedTime: MethodDescriptor? = null,
    val jumpClockField: FieldDescriptor? = null,
    val timerSetTotalMethod: MethodDescriptor? = null,
    val jumpResumeMethod: MethodDescriptor? = null,
    val jumpStartField: FieldDescriptor? = null,
    val miniGameRewardShow: MethodDescriptor? = null,
    val miniGameRewardEmitEvent: MethodDescriptor? = null,
    val miniGameRewardCallback: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("activityOnCreate", activityOnCreate?.toJson())
        .putOpt("activityOnResume", activityOnResume?.toJson())
        .putOpt("activityOnStop", activityOnStop?.toJson())
        .putOpt("headerSetTotalTime", headerSetTotalTime?.toJson())
        .putOpt("headerSetElapsedTime", headerSetElapsedTime?.toJson())
        .putOpt("headerStartTimer", headerStartTimer?.toJson())
        .putOpt("countDownSetTotalTime", countDownSetTotalTime?.toJson())
        .putOpt("countDownSetElapsedTime", countDownSetElapsedTime?.toJson())
        .putOpt("jumpClockField", jumpClockField?.toJson())
        .putOpt("timerSetTotalMethod", timerSetTotalMethod?.toJson())
        .putOpt("jumpResumeMethod", jumpResumeMethod?.toJson())
        .putOpt("jumpStartField", jumpStartField?.toJson())
        .putOpt("miniGameRewardShow", miniGameRewardShow?.toJson())
        .putOpt("miniGameRewardEmitEvent", miniGameRewardEmitEvent?.toJson())
        .putOpt("miniGameRewardCallback", miniGameRewardCallback?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredRewardAdSymbols =
        RestoredRewardAdSymbols(
            activityOnCreate = activityOnCreate.restoreOptional(classLoader),
            activityOnResume = activityOnResume.restoreOptional(classLoader),
            activityOnStop = activityOnStop.restoreOptional(classLoader),
            headerSetTotalTime = headerSetTotalTime.restoreOptional(classLoader),
            headerSetElapsedTime = headerSetElapsedTime.restoreOptional(classLoader),
            headerStartTimer = headerStartTimer.restoreOptional(classLoader),
            countDownSetTotalTime = countDownSetTotalTime.restoreOptional(classLoader),
            countDownSetElapsedTime = countDownSetElapsedTime.restoreOptional(classLoader),
            jumpClockField = jumpClockField.restoreOptional(classLoader),
            timerSetTotalMethod = timerSetTotalMethod.restoreOptional(classLoader),
            jumpResumeMethod = jumpResumeMethod.restoreOptional(classLoader),
            jumpStartField = jumpStartField.restoreOptional(classLoader),
            miniGameRewardShow = miniGameRewardShow.restoreOptional(classLoader),
            miniGameRewardEmitEvent = miniGameRewardEmitEvent.restoreOptional(classLoader),
            miniGameRewardCallback = miniGameRewardCallback.restoreOptional(classLoader),
        )

    companion object {
        fun fromJson(obj: JSONObject): RewardAdSymbols = RewardAdSymbols(
            activityOnCreate = obj.optJSONObject("activityOnCreate")?.let(MethodDescriptor::fromJson),
            activityOnResume = obj.optJSONObject("activityOnResume")?.let(MethodDescriptor::fromJson),
            activityOnStop = obj.optJSONObject("activityOnStop")?.let(MethodDescriptor::fromJson),
            headerSetTotalTime = obj.optJSONObject("headerSetTotalTime")?.let(MethodDescriptor::fromJson),
            headerSetElapsedTime = obj.optJSONObject("headerSetElapsedTime")?.let(MethodDescriptor::fromJson),
            headerStartTimer = obj.optJSONObject("headerStartTimer")?.let(MethodDescriptor::fromJson),
            countDownSetTotalTime = obj.optJSONObject("countDownSetTotalTime")?.let(MethodDescriptor::fromJson),
            countDownSetElapsedTime = obj.optJSONObject("countDownSetElapsedTime")?.let(MethodDescriptor::fromJson),
            jumpClockField = obj.optJSONObject("jumpClockField")?.let(FieldDescriptor::fromJson),
            timerSetTotalMethod = obj.optJSONObject("timerSetTotalMethod")?.let(MethodDescriptor::fromJson),
            jumpResumeMethod = obj.optJSONObject("jumpResumeMethod")?.let(MethodDescriptor::fromJson),
            jumpStartField = obj.optJSONObject("jumpStartField")?.let(FieldDescriptor::fromJson),
            miniGameRewardShow = obj.optJSONObject("miniGameRewardShow")?.let(MethodDescriptor::fromJson),
            miniGameRewardEmitEvent = obj.optJSONObject("miniGameRewardEmitEvent")?.let(MethodDescriptor::fromJson),
            miniGameRewardCallback = obj.optJSONObject("miniGameRewardCallback")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredRewardAdSymbols(
    val activityOnCreate: Method?,
    val activityOnResume: Method?,
    val activityOnStop: Method?,
    val headerSetTotalTime: Method?,
    val headerSetElapsedTime: Method?,
    val headerStartTimer: Method?,
    val countDownSetTotalTime: Method?,
    val countDownSetElapsedTime: Method?,
    val jumpClockField: Field?,
    val timerSetTotalMethod: Method?,
    val jumpResumeMethod: Method?,
    val jumpStartField: Field?,
    val miniGameRewardShow: Method?,
    val miniGameRewardEmitEvent: Method?,
    val miniGameRewardCallback: Method?,
)

data class VideoDetailBannerAdSymbols(
    val getVideoDetail: MethodDescriptor?,
    val videoDetailTypeName: String?,
    val underPlayerTypeName: String?,
    val relateTypeName: String?,
    val merchandiseTypeName: String?,
    val pausedPageTypeName: String?,
    val adPanelTypeName: String?,
    val requestPausedPage: MethodDescriptor?,
    val getPausedPagePanel: MethodDescriptor?,
    val getBrandPausedPagePanel: MethodDescriptor?,
    val relateGameComponentTypeName: String?,
    val simpleViewEntryConstructor: ConstructorDescriptor?,
    val createViewEntry: MethodDescriptor?,
    val bindToView: MethodDescriptor?,
    val kotlinUnitField: FieldDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("getVideoDetail", getVideoDetail?.toJson())
        .putOpt("videoDetailTypeName", videoDetailTypeName)
        .putOpt("underPlayerTypeName", underPlayerTypeName)
        .putOpt("relateTypeName", relateTypeName)
        .putOpt("merchandiseTypeName", merchandiseTypeName)
        .putOpt("pausedPageTypeName", pausedPageTypeName)
        .putOpt("adPanelTypeName", adPanelTypeName)
        .putOpt("requestPausedPage", requestPausedPage?.toJson())
        .putOpt("getPausedPagePanel", getPausedPagePanel?.toJson())
        .putOpt("getBrandPausedPagePanel", getBrandPausedPagePanel?.toJson())
        .putOpt("relateGameComponentTypeName", relateGameComponentTypeName)
        .putOpt("simpleViewEntryConstructor", simpleViewEntryConstructor?.toJson())
        .putOpt("createViewEntry", createViewEntry?.toJson())
        .putOpt("bindToView", bindToView?.toJson())
        .putOpt("kotlinUnitField", kotlinUnitField?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoDetailBannerAdSymbols? {
        val getVideoDetailMethod = getVideoDetail?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val videoDetailType = videoDetailTypeName?.let(classLoader::loadClassOrNull)
        val underPlayerType = underPlayerTypeName?.let(classLoader::loadClassOrNull)
        val relateType = relateTypeName?.let(classLoader::loadClassOrNull)
        val merchandiseType = merchandiseTypeName?.let(classLoader::loadClassOrNull)
        val pausedPageType = pausedPageTypeName?.let(classLoader::loadClassOrNull)
        val adPanelType = adPanelTypeName?.let(classLoader::loadClassOrNull)
        val requestPausedPageMethod = requestPausedPage?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val getPausedPagePanelMethod = getPausedPagePanel?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val getBrandPausedPagePanelMethod = getBrandPausedPagePanel?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val relateGameComponentType = relateGameComponentTypeName?.let(classLoader::loadClassOrNull)
        if (getVideoDetail != null && (getVideoDetailMethod == null || videoDetailType == null)) {
            return null
        }
        if (underPlayerTypeName != null && underPlayerType == null) {
            return null
        }
        if (requestPausedPage != null && (pausedPageType == null || requestPausedPageMethod == null)) {
            return null
        }
        if ((getPausedPagePanel != null || getBrandPausedPagePanel != null) && adPanelType == null) {
            return null
        }
        if (getPausedPagePanel != null && getPausedPagePanelMethod == null) {
            return null
        }
        if (getBrandPausedPagePanel != null && getBrandPausedPagePanelMethod == null) {
            return null
        }

        val constructor = simpleViewEntryConstructor?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val create = createViewEntry?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val bind = bindToView?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val unit = kotlinUnitField?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)?.get(null)
        }
        if (createViewEntry != null && (relateGameComponentType == null || constructor == null || create == null || bind == null || unit == null)) {
            return null
        }

        return RestoredVideoDetailBannerAdSymbols(
            getVideoDetail = getVideoDetailMethod,
            videoDetailType = videoDetailType,
            underPlayerType = underPlayerType,
            relateType = relateType,
            merchandiseType = merchandiseType,
            pausedPageType = pausedPageType,
            adPanelType = adPanelType,
            requestPausedPage = requestPausedPageMethod,
            getPausedPagePanel = getPausedPagePanelMethod,
            getBrandPausedPagePanel = getBrandPausedPagePanelMethod,
            relateGameComponentType = relateGameComponentType,
            simpleViewEntryConstructor = constructor,
            createViewEntry = create,
            bindToView = bind,
            kotlinUnit = unit,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoDetailBannerAdSymbols = VideoDetailBannerAdSymbols(
            getVideoDetail = obj.optJSONObject("getVideoDetail")?.let(MethodDescriptor::fromJson),
            videoDetailTypeName = obj.optString("videoDetailTypeName").takeIf { it.isNotBlank() },
            underPlayerTypeName = obj.optString("underPlayerTypeName").takeIf { it.isNotBlank() },
            relateTypeName = obj.optString("relateTypeName").takeIf { it.isNotBlank() },
            merchandiseTypeName = obj.optString("merchandiseTypeName").takeIf { it.isNotBlank() },
            pausedPageTypeName = obj.optString("pausedPageTypeName").takeIf { it.isNotBlank() },
            adPanelTypeName = obj.optString("adPanelTypeName").takeIf { it.isNotBlank() },
            requestPausedPage = obj.optJSONObject("requestPausedPage")?.let(MethodDescriptor::fromJson),
            getPausedPagePanel = obj.optJSONObject("getPausedPagePanel")?.let(MethodDescriptor::fromJson),
            getBrandPausedPagePanel = obj.optJSONObject("getBrandPausedPagePanel")?.let(MethodDescriptor::fromJson),
            relateGameComponentTypeName = obj.optString("relateGameComponentTypeName").takeIf { it.isNotBlank() },
            simpleViewEntryConstructor = obj.optJSONObject("simpleViewEntryConstructor")?.let(ConstructorDescriptor::fromJson),
            createViewEntry = obj.optJSONObject("createViewEntry")?.let(MethodDescriptor::fromJson),
            bindToView = obj.optJSONObject("bindToView")?.let(MethodDescriptor::fromJson),
            kotlinUnitField = obj.optJSONObject("kotlinUnitField")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredVideoDetailBannerAdSymbols(
    val getVideoDetail: Method?,
    val videoDetailType: Class<*>?,
    val underPlayerType: Class<*>?,
    val relateType: Class<*>?,
    val merchandiseType: Class<*>?,
    val pausedPageType: Class<*>?,
    val adPanelType: Class<*>?,
    val requestPausedPage: Method?,
    val getPausedPagePanel: Method?,
    val getBrandPausedPagePanel: Method?,
    val relateGameComponentType: Class<*>?,
    val simpleViewEntryConstructor: Constructor<*>?,
    val createViewEntry: Method?,
    val bindToView: Method?,
    val kotlinUnit: Any?,
)

data class SkipVideoAdSymbols(
    val playViewMethods: List<MethodDescriptor>,
    val playerCoreCurrentPositionMethods: List<MethodDescriptor>,
    val playerCoreStateMethods: List<MethodDescriptor>,
    val playerCoreSeekMethods: List<MethodDescriptor>,
    val cardCurrentPositionMethods: List<MethodDescriptor>,
    val cardStateMethods: List<MethodDescriptor>,
    val cardSeekMethods: List<MethodDescriptor>,
    val storyCurrentPositionMethods: List<MethodDescriptor>,
    val storyStateMethods: List<MethodDescriptor>,
    val storySeekMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("playViewMethods", playViewMethods.toJsonArray { it.toJson() })
        .put("playerCoreCurrentPositionMethods", playerCoreCurrentPositionMethods.toJsonArray { it.toJson() })
        .put("playerCoreStateMethods", playerCoreStateMethods.toJsonArray { it.toJson() })
        .put("playerCoreSeekMethods", playerCoreSeekMethods.toJsonArray { it.toJson() })
        .put("cardCurrentPositionMethods", cardCurrentPositionMethods.toJsonArray { it.toJson() })
        .put("cardStateMethods", cardStateMethods.toJsonArray { it.toJson() })
        .put("cardSeekMethods", cardSeekMethods.toJsonArray { it.toJson() })
        .put("storyCurrentPositionMethods", storyCurrentPositionMethods.toJsonArray { it.toJson() })
        .put("storyStateMethods", storyStateMethods.toJsonArray { it.toJson() })
        .put("storySeekMethods", storySeekMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSkipVideoAdSymbols? {
        return RestoredSkipVideoAdSymbols(
            playViewMethods = playViewMethods.restoreAll(classLoader) ?: return null,
            playerCoreCurrentPositionMethods = playerCoreCurrentPositionMethods.restoreAll(classLoader) ?: return null,
            playerCoreStateMethods = playerCoreStateMethods.restoreAll(classLoader) ?: return null,
            playerCoreSeekMethods = playerCoreSeekMethods.restoreAll(classLoader) ?: return null,
            cardCurrentPositionMethods = cardCurrentPositionMethods.restoreAll(classLoader) ?: return null,
            cardStateMethods = cardStateMethods.restoreAll(classLoader) ?: return null,
            cardSeekMethods = cardSeekMethods.restoreAll(classLoader) ?: return null,
            storyCurrentPositionMethods = storyCurrentPositionMethods.restoreAll(classLoader) ?: return null,
            storyStateMethods = storyStateMethods.restoreAll(classLoader) ?: return null,
            storySeekMethods = storySeekMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): SkipVideoAdSymbols = SkipVideoAdSymbols(
            playViewMethods = obj.optJSONArray("playViewMethods").toList { MethodDescriptor.fromJson(it) },
            playerCoreCurrentPositionMethods = obj.optJSONArray("playerCoreCurrentPositionMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            playerCoreStateMethods = obj.optJSONArray("playerCoreStateMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            playerCoreSeekMethods = obj.optJSONArray("playerCoreSeekMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            cardCurrentPositionMethods = obj.optJSONArray("cardCurrentPositionMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            cardStateMethods = obj.optJSONArray("cardStateMethods").toList { MethodDescriptor.fromJson(it) },
            cardSeekMethods = obj.optJSONArray("cardSeekMethods").toList { MethodDescriptor.fromJson(it) },
            storyCurrentPositionMethods = obj.optJSONArray("storyCurrentPositionMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            storyStateMethods = obj.optJSONArray("storyStateMethods").toList { MethodDescriptor.fromJson(it) },
            storySeekMethods = obj.optJSONArray("storySeekMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSkipVideoAdSymbols(
    val playViewMethods: List<Method>,
    val playerCoreCurrentPositionMethods: List<Method>,
    val playerCoreStateMethods: List<Method>,
    val playerCoreSeekMethods: List<Method>,
    val cardCurrentPositionMethods: List<Method>,
    val cardStateMethods: List<Method>,
    val cardSeekMethods: List<Method>,
    val storyCurrentPositionMethods: List<Method>,
    val storyStateMethods: List<Method>,
    val storySeekMethods: List<Method>,
)

data class SkipVideoAdProgressSymbols(
    val progressOnDraw: MethodDescriptor?,
    val storyOnStartMethods: List<MethodDescriptor>,
    val inlineUpdateMethods: List<MethodDescriptor>,
    val panelWidgetKtClassName: String?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("progressOnDraw", progressOnDraw?.toJson())
        .put("storyOnStartMethods", storyOnStartMethods.toJsonArray { it.toJson() })
        .put("inlineUpdateMethods", inlineUpdateMethods.toJsonArray { it.toJson() })
        .putOpt("panelWidgetKtClassName", panelWidgetKtClassName)
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSkipVideoAdProgressSymbols? {
        return RestoredSkipVideoAdProgressSymbols(
            progressOnDraw = progressOnDraw.restoreOptional(classLoader),
            storyOnStartMethods = storyOnStartMethods.restoreAll(classLoader) ?: return null,
            inlineUpdateMethods = inlineUpdateMethods.restoreAll(classLoader) ?: return null,
            panelWidgetKtClass = panelWidgetKtClassName?.let(classLoader::loadClassOrNull),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): SkipVideoAdProgressSymbols = SkipVideoAdProgressSymbols(
            progressOnDraw = obj.optJSONObject("progressOnDraw")?.let(MethodDescriptor::fromJson),
            storyOnStartMethods = obj.optJSONArray("storyOnStartMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            inlineUpdateMethods = obj.optJSONArray("inlineUpdateMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            panelWidgetKtClassName = obj.optString("panelWidgetKtClassName").takeIf { it.isNotBlank() },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSkipVideoAdProgressSymbols(
    val progressOnDraw: Method?,
    val storyOnStartMethods: List<Method>,
    val inlineUpdateMethods: List<Method>,
    val panelWidgetKtClass: Class<*>?,
)

data class SkipVideoAdAutoLikeSymbols(
    val detailLikeInflateMethod: MethodDescriptor?,
    val detailLikeStateOwnerClassName: String?,
    val storyWidgetClassNames: List<String>,
    val storyActionOwnerClassName: String?,
    val storyBindMethods: List<MethodDescriptor>,
    val geminiLikeWidgetClassName: String?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("detailLikeInflateMethod", detailLikeInflateMethod?.toJson())
        .putOpt("detailLikeStateOwnerClassName", detailLikeStateOwnerClassName)
        .put("storyWidgetClassNames", storyWidgetClassNames.toJsonArray())
        .putOpt("storyActionOwnerClassName", storyActionOwnerClassName)
        .put("storyBindMethods", storyBindMethods.toJsonArray { it.toJson() })
        .putOpt("geminiLikeWidgetClassName", geminiLikeWidgetClassName)
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredSkipVideoAdAutoLikeSymbols? {
        return RestoredSkipVideoAdAutoLikeSymbols(
            detailLikeInflateMethod = detailLikeInflateMethod.restoreOptional(classLoader),
            detailLikeStateOwnerClass = detailLikeStateOwnerClassName?.let(classLoader::loadClassOrNull),
            storyWidgetClasses = storyWidgetClassNames.mapNotNull(classLoader::loadClassOrNull)
                .takeIf { it.size == storyWidgetClassNames.size } ?: return null,
            storyActionOwnerClass = storyActionOwnerClassName?.let(classLoader::loadClassOrNull),
            storyBindMethods = storyBindMethods.restoreAll(classLoader) ?: return null,
            geminiLikeWidgetClass = geminiLikeWidgetClassName?.let(classLoader::loadClassOrNull),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): SkipVideoAdAutoLikeSymbols = SkipVideoAdAutoLikeSymbols(
            detailLikeInflateMethod = obj.optJSONObject("detailLikeInflateMethod")?.let(MethodDescriptor::fromJson),
            detailLikeStateOwnerClassName = obj.optString("detailLikeStateOwnerClassName").takeIf { it.isNotBlank() },
            storyWidgetClassNames = obj.optJSONArray("storyWidgetClassNames").toStringList(),
            storyActionOwnerClassName = obj.optString("storyActionOwnerClassName").takeIf { it.isNotBlank() },
            storyBindMethods = obj.optJSONArray("storyBindMethods").toList { MethodDescriptor.fromJson(it) },
            geminiLikeWidgetClassName = obj.optString("geminiLikeWidgetClassName").takeIf { it.isNotBlank() },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredSkipVideoAdAutoLikeSymbols(
    val detailLikeInflateMethod: Method?,
    val detailLikeStateOwnerClass: Class<*>?,
    val storyWidgetClasses: List<Class<*>>,
    val storyActionOwnerClass: Class<*>?,
    val storyBindMethods: List<Method>,
    val geminiLikeWidgetClass: Class<*>?,
)

data class ChronosPromotionSymbols(
    val classSymbols: List<NamedClassSymbol>,
    val methodGroups: List<NamedMethodGroup>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("classSymbols", classSymbols.toJsonArray { it.toJson() })
        .put("methodGroups", methodGroups.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredChronosPromotionSymbols? {
        val classes = classSymbols.mapNotNull { symbol ->
            classLoader.loadClassOrNull(symbol.className)?.let { symbol.id to it }
        }.toMap()
        if (classes.size != classSymbols.size) return null
        val methods = methodGroups.mapNotNull { group ->
            val restored = group.methods.restoreAll(classLoader) ?: return@mapNotNull null
            group.id to restored
        }.toMap()
        if (methods.size != methodGroups.size) return null
        return RestoredChronosPromotionSymbols(classes, methods)
    }

    companion object {
        fun fromJson(obj: JSONObject): ChronosPromotionSymbols = ChronosPromotionSymbols(
            classSymbols = obj.optJSONArray("classSymbols").toList { NamedClassSymbol.fromJson(it) },
            methodGroups = obj.optJSONArray("methodGroups").toList { NamedMethodGroup.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredChronosPromotionSymbols(
    val classes: Map<String, Class<*>>,
    val methods: Map<String, List<Method>>,
) {
    fun clazz(id: String): Class<*>? = classes[id]
    fun methods(id: String): List<Method> = methods[id].orEmpty()
    fun firstMethod(id: String): Method? = methods(id).firstOrNull()
}
