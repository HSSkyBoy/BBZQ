package io.github.bbzq.feats.symbol

import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONObject

data class TryFreeQualitySymbols(
    val playViewMethods: List<MethodDescriptor>,
    val getIsNeedTrialMethods: List<MethodDescriptor>,
    val setIsNeedTrialMethods: List<MethodDescriptor>,
    val getVipFreeMethods: List<MethodDescriptor>,
    val getNeedVipMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("playViewMethods", playViewMethods.toJsonArray { it.toJson() })
        .put("getIsNeedTrialMethods", getIsNeedTrialMethods.toJsonArray { it.toJson() })
        .put("setIsNeedTrialMethods", setIsNeedTrialMethods.toJsonArray { it.toJson() })
        .put("getVipFreeMethods", getVipFreeMethods.toJsonArray { it.toJson() })
        .put("getNeedVipMethods", getNeedVipMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredTryFreeQualitySymbols? {
        return RestoredTryFreeQualitySymbols(
            playViewMethods = playViewMethods.restoreAll(classLoader) ?: return null,
            getIsNeedTrialMethods = getIsNeedTrialMethods.restoreAll(classLoader) ?: return null,
            setIsNeedTrialMethods = setIsNeedTrialMethods.restoreAll(classLoader) ?: return null,
            getVipFreeMethods = getVipFreeMethods.restoreAll(classLoader) ?: return null,
            getNeedVipMethods = getNeedVipMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): TryFreeQualitySymbols = TryFreeQualitySymbols(
            playViewMethods = obj.optJSONArray("playViewMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            getIsNeedTrialMethods = obj.optJSONArray("getIsNeedTrialMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            setIsNeedTrialMethods = obj.optJSONArray("setIsNeedTrialMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            getVipFreeMethods = obj.optJSONArray("getVipFreeMethods").toList { MethodDescriptor.fromJson(it) },
            getNeedVipMethods = obj.optJSONArray("getNeedVipMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredTryFreeQualitySymbols(
    val playViewMethods: List<Method>,
    val getIsNeedTrialMethods: List<Method>,
    val setIsNeedTrialMethods: List<Method>,
    val getVipFreeMethods: List<Method>,
    val getNeedVipMethods: List<Method>,
)

data class VideoDetailRelateFeedSymbols(
    val responseGetItems: List<RelateResponseGetItemsSymbols>,
    val detailRelateServiceMethod: MethodDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("responseGetItems", responseGetItems.toJsonArray { it.toJson() })
        .putOpt("detailRelateServiceMethod", detailRelateServiceMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoDetailRelateFeedSymbols {
        val responses = responseGetItems.mapNotNull { it.restore(classLoader) }
        val detailServiceMethod = detailRelateServiceMethod?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        return RestoredVideoDetailRelateFeedSymbols(
            responseGetItems = responses,
            detailRelateServiceMethod = detailServiceMethod,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoDetailRelateFeedSymbols = VideoDetailRelateFeedSymbols(
            responseGetItems = obj.optJSONArray("responseGetItems").toList {
                RelateResponseGetItemsSymbols.fromJson(it)
            },
            detailRelateServiceMethod = obj.optJSONObject("detailRelateServiceMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RelateResponseGetItemsSymbols(
    val getItems: MethodDescriptor,
    val itemsField: FieldDescriptor?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("getItems", getItems.toJson())
        .putOpt("itemsField", itemsField?.toJson())

    fun restore(classLoader: ClassLoader): RestoredRelateResponseGetItemsSymbols? {
        val owner = classLoader.loadClassOrNull(getItems.declaringClassName) ?: return null
        return RestoredRelateResponseGetItemsSymbols(
            getItems = getItems.restore(owner) ?: return null,
            itemsField = itemsField.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): RelateResponseGetItemsSymbols = RelateResponseGetItemsSymbols(
            getItems = MethodDescriptor.fromJson(obj.getJSONObject("getItems")),
            itemsField = obj.optJSONObject("itemsField")?.let(FieldDescriptor::fromJson),
        )
    }
}

data class RestoredVideoDetailRelateFeedSymbols(
    val responseGetItems: List<RestoredRelateResponseGetItemsSymbols>,
    val detailRelateServiceMethod: Method?,
)

data class RestoredRelateResponseGetItemsSymbols(
    val getItems: Method,
    val itemsField: Field?,
)

data class TripleSpeedSymbols(
    // 9.17 起 playerbizcommonv2 与 playerbizcommon3 各有一个长按倍速实验读取点
    val experimentReaderMethods: List<MethodDescriptor>,
    val qualitySpeedResetMethod: MethodDescriptor? = null,
    val highFrameRateSpeedGuardMethod: MethodDescriptor? = null,
    val longPressSpeedMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("experimentReaderMethods", experimentReaderMethods.toJsonArray { it.toJson() })
        .putOpt("longPressSpeedMethod", longPressSpeedMethod?.toJson())
        .putOpt("qualitySpeedResetMethod", qualitySpeedResetMethod?.toJson())
        .putOpt("highFrameRateSpeedGuardMethod", highFrameRateSpeedGuardMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredTripleSpeedSymbols? {
        val methods = experimentReaderMethods.restoreAvailable(classLoader).takeIf { it.isNotEmpty() } ?: return null
        return RestoredTripleSpeedSymbols(
            experimentReaderMethods = methods,
            qualitySpeedResetMethod = qualitySpeedResetMethod.restoreOptional(classLoader),
            highFrameRateSpeedGuardMethod = highFrameRateSpeedGuardMethod.restoreOptional(classLoader),
            longPressSpeedMethod = longPressSpeedMethod.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): TripleSpeedSymbols = TripleSpeedSymbols(
            experimentReaderMethods = obj.optJSONArray("experimentReaderMethods").toList { MethodDescriptor.fromJson(it) },
            qualitySpeedResetMethod = obj.optJSONObject("qualitySpeedResetMethod")?.let(MethodDescriptor::fromJson),
            highFrameRateSpeedGuardMethod = obj.optJSONObject("highFrameRateSpeedGuardMethod")
                ?.let(MethodDescriptor::fromJson),
            longPressSpeedMethod = obj.optJSONObject("longPressSpeedMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredTripleSpeedSymbols(
    val experimentReaderMethods: List<Method>,
    val qualitySpeedResetMethod: Method?,
    val highFrameRateSpeedGuardMethod: Method?,
    val longPressSpeedMethod: Method?,
)

data class VideoQualitySymbols(
    val playerPreloadGetMethods: List<MethodDescriptor> = emptyList(),
    val playerSettingHelperGetDefaultQnMethod: MethodDescriptor? = null,
    val rangeQualityUpdateMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("playerPreloadGetMethods", playerPreloadGetMethods.toJsonArray { it.toJson() })
        .putOpt("playerSettingHelperGetDefaultQnMethod", playerSettingHelperGetDefaultQnMethod?.toJson())
        .putOpt("rangeQualityUpdateMethod", rangeQualityUpdateMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoQualitySymbols =
        RestoredVideoQualitySymbols(
            playerPreloadGetMethods = playerPreloadGetMethods.restoreAvailable(classLoader),
            playerSettingHelperGetDefaultQnMethod = playerSettingHelperGetDefaultQnMethod?.restoreOptional(classLoader),
            rangeQualityUpdateMethod = rangeQualityUpdateMethod?.restoreOptional(classLoader),
        )

    companion object {
        fun fromJson(obj: JSONObject): VideoQualitySymbols = VideoQualitySymbols(
            playerPreloadGetMethods = obj.optJSONArray("playerPreloadGetMethods").toList { MethodDescriptor.fromJson(it) },
            playerSettingHelperGetDefaultQnMethod = obj.optJSONObject("playerSettingHelperGetDefaultQnMethod")?.let(MethodDescriptor::fromJson),
            rangeQualityUpdateMethod = obj.optJSONObject("rangeQualityUpdateMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredVideoQualitySymbols(
    val playerPreloadGetMethods: List<Method>,
    val playerSettingHelperGetDefaultQnMethod: Method?,
    val rangeQualityUpdateMethod: Method? = null,
)

data class VideoEndRecommendSymbols(
    val shouldShowEndPageMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("shouldShowEndPageMethod", shouldShowEndPageMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoEndRecommendSymbols =
        RestoredVideoEndRecommendSymbols(
            shouldShowEndPageMethod = shouldShowEndPageMethod?.restoreOptional(classLoader),
        )

    companion object {
        fun fromJson(obj: JSONObject): VideoEndRecommendSymbols = VideoEndRecommendSymbols(
            shouldShowEndPageMethod = obj.optJSONObject("shouldShowEndPageMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredVideoEndRecommendSymbols(
    val shouldShowEndPageMethod: Method?,
)
