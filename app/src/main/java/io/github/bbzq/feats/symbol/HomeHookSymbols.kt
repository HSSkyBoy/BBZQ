package io.github.bbzq.feats.symbol

import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONObject

data class HomeTopBarSymbols(
    val gameMenuMethod: MethodDescriptor?,
    val baseOnViewCreated: MethodDescriptor?,
    val defaultWordMethods: List<MethodDescriptor>,
    val evidence: String,
    val composeGameMenuMethod: MethodDescriptor? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("gameMenuMethod", gameMenuMethod?.toJson())
        .putOpt("composeGameMenuMethod", composeGameMenuMethod?.toJson())
        .putOpt("baseOnViewCreated", baseOnViewCreated?.toJson())
        .put("defaultWordMethods", defaultWordMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeTopBarSymbols? {
        val gameMenu = gameMenuMethod?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val onViewCreated = baseOnViewCreated?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val defaultWord = defaultWordMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (defaultWord.size != defaultWordMethods.size) return null
        return RestoredHomeTopBarSymbols(gameMenu, onViewCreated, defaultWord, composeGameMenuMethod.restoreOptional(classLoader))
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeTopBarSymbols = HomeTopBarSymbols(
            gameMenuMethod = obj.optJSONObject("gameMenuMethod")?.let(MethodDescriptor::fromJson),
            composeGameMenuMethod = obj.optJSONObject("composeGameMenuMethod")?.let(MethodDescriptor::fromJson),
            baseOnViewCreated = obj.optJSONObject("baseOnViewCreated")?.let(MethodDescriptor::fromJson),
            defaultWordMethods = obj.optJSONArray("defaultWordMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredHomeTopBarSymbols(
    val gameMenuMethod: Method?,
    val baseOnViewCreated: Method?,
    val defaultWordMethods: List<Method>,
    val composeGameMenuMethod: Method? = null,
)

data class BottomBarSymbols(
    val tabHostSetTabsMethods: List<MethodDescriptor>,
    val tabHostGetTabsMethods: List<MethodDescriptor>,
    val baseOnViewCreatedMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tabHostSetTabsMethods", tabHostSetTabsMethods.toJsonArray { it.toJson() })
        .put("tabHostGetTabsMethods", tabHostGetTabsMethods.toJsonArray { it.toJson() })
        .put("baseOnViewCreatedMethods", baseOnViewCreatedMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredBottomBarSymbols? {
        val setTabs = tabHostSetTabsMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        val getTabs = tabHostGetTabsMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        val onViewCreated = baseOnViewCreatedMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (
            setTabs.size != tabHostSetTabsMethods.size ||
            getTabs.size != tabHostGetTabsMethods.size ||
            onViewCreated.size != baseOnViewCreatedMethods.size
        ) return null
        return RestoredBottomBarSymbols(setTabs, getTabs, onViewCreated)
    }

    companion object {
        fun fromJson(obj: JSONObject): BottomBarSymbols = BottomBarSymbols(
            tabHostSetTabsMethods = obj.optJSONArray("tabHostSetTabsMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            tabHostGetTabsMethods = obj.optJSONArray("tabHostGetTabsMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            baseOnViewCreatedMethods = obj.optJSONArray("baseOnViewCreatedMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredBottomBarSymbols(
    val tabHostSetTabsMethods: List<Method>,
    val tabHostGetTabsMethods: List<Method>,
    val baseOnViewCreatedMethods: List<Method>,
)

data class HomeRecommendTabSymbols(
    val buildTabsMethod: MethodDescriptor,
    val idField: FieldDescriptor,
    val titleField: FieldDescriptor,
    val uriField: FieldDescriptor,
    val reporterIdField: FieldDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("buildTabsMethod", buildTabsMethod.toJson())
        .put("idField", idField.toJson())
        .put("titleField", titleField.toJson())
        .put("uriField", uriField.toJson())
        .putOpt("reporterIdField", reporterIdField?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeRecommendTabSymbols? {
        val methodOwner = classLoader.loadClassOrNull(buildTabsMethod.declaringClassName) ?: return null
        val idOwner = classLoader.loadClassOrNull(idField.declaringClassName) ?: return null
        val titleOwner = classLoader.loadClassOrNull(titleField.declaringClassName) ?: return null
        val uriOwner = classLoader.loadClassOrNull(uriField.declaringClassName) ?: return null
        return RestoredHomeRecommendTabSymbols(
            buildTabsMethod = buildTabsMethod.restore(methodOwner) ?: return null,
            idField = idField.restore(idOwner) ?: return null,
            titleField = titleField.restore(titleOwner) ?: return null,
            uriField = uriField.restore(uriOwner) ?: return null,
            reporterIdField = reporterIdField.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeRecommendTabSymbols = HomeRecommendTabSymbols(
            buildTabsMethod = MethodDescriptor.fromJson(obj.getJSONObject("buildTabsMethod")),
            idField = FieldDescriptor.fromJson(obj.getJSONObject("idField")),
            titleField = FieldDescriptor.fromJson(obj.getJSONObject("titleField")),
            uriField = FieldDescriptor.fromJson(obj.getJSONObject("uriField")),
            reporterIdField = obj.optJSONObject("reporterIdField")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredHomeRecommendTabSymbols(
    val buildTabsMethod: Method,
    val idField: Field,
    val titleField: Field,
    val uriField: Field,
    val reporterIdField: Field?,
)

data class HomeRecommendFeedSymbols(
    val responseGetItems: List<PegasusResponseGetItemsSymbols>,
    val getHolderType: MethodDescriptor,
    val getBizType: MethodDescriptor?,
    val getHolderStyle: MethodDescriptor?,
    val isSmallCard: MethodDescriptor?,
    val getAdInfo: MethodDescriptor?,
    val getCardType: MethodDescriptor?,
    val getCardGoto: MethodDescriptor?,
    val getGoTo: MethodDescriptor?,
    val getUri: MethodDescriptor?,
    val adInfoClassName: String?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("responseGetItems", responseGetItems.toJsonArray { it.toJson() })
        .put("getHolderType", getHolderType.toJson())
        .putOpt("getBizType", getBizType?.toJson())
        .putOpt("getHolderStyle", getHolderStyle?.toJson())
        .putOpt("isSmallCard", isSmallCard?.toJson())
        .putOpt("getAdInfo", getAdInfo?.toJson())
        .putOpt("getCardType", getCardType?.toJson())
        .putOpt("getCardGoto", getCardGoto?.toJson())
        .putOpt("getGoTo", getGoTo?.toJson())
        .putOpt("getUri", getUri?.toJson())
        .putOpt("adInfoClassName", adInfoClassName)
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeRecommendFeedSymbols? {
        val responses = responseGetItems.mapNotNull { it.restore(classLoader) }
        if (responses.size != responseGetItems.size) return null
        val holderOwner = classLoader.loadClassOrNull(getHolderType.declaringClassName) ?: return null
        return RestoredHomeRecommendFeedSymbols(
            responseGetItems = responses,
            getHolderType = getHolderType.restore(holderOwner) ?: return null,
            getBizType = getBizType.restoreOptional(classLoader),
            getHolderStyle = getHolderStyle.restoreOptional(classLoader),
            isSmallCard = isSmallCard.restoreOptional(classLoader),
            getAdInfo = getAdInfo.restoreOptional(classLoader),
            getCardType = getCardType.restoreOptional(classLoader),
            getCardGoto = getCardGoto.restoreOptional(classLoader),
            getGoTo = getGoTo.restoreOptional(classLoader),
            getUri = getUri.restoreOptional(classLoader),
            adInfoClass = adInfoClassName?.let(classLoader::loadClassOrNull),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeRecommendFeedSymbols = HomeRecommendFeedSymbols(
            responseGetItems = obj.optJSONArray("responseGetItems").toList {
                PegasusResponseGetItemsSymbols.fromJson(it)
            },
            getHolderType = MethodDescriptor.fromJson(obj.getJSONObject("getHolderType")),
            getBizType = obj.optJSONObject("getBizType")?.let(MethodDescriptor::fromJson),
            getHolderStyle = obj.optJSONObject("getHolderStyle")?.let(MethodDescriptor::fromJson),
            isSmallCard = obj.optJSONObject("isSmallCard")?.let(MethodDescriptor::fromJson),
            getAdInfo = obj.optJSONObject("getAdInfo")?.let(MethodDescriptor::fromJson),
            getCardType = obj.optJSONObject("getCardType")?.let(MethodDescriptor::fromJson),
            getCardGoto = obj.optJSONObject("getCardGoto")?.let(MethodDescriptor::fromJson),
            getGoTo = obj.optJSONObject("getGoTo")?.let(MethodDescriptor::fromJson),
            getUri = obj.optJSONObject("getUri")?.let(MethodDescriptor::fromJson),
            adInfoClassName = obj.optString("adInfoClassName").takeIf { it.isNotBlank() },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class PegasusResponseGetItemsSymbols(
    val getItems: MethodDescriptor,
    val itemsField: FieldDescriptor?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("getItems", getItems.toJson())
        .putOpt("itemsField", itemsField?.toJson())

    fun restore(classLoader: ClassLoader): RestoredPegasusResponseGetItemsSymbols? {
        val owner = classLoader.loadClassOrNull(getItems.declaringClassName) ?: return null
        return RestoredPegasusResponseGetItemsSymbols(
            getItems = getItems.restore(owner) ?: return null,
            itemsField = itemsField.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): PegasusResponseGetItemsSymbols = PegasusResponseGetItemsSymbols(
            getItems = MethodDescriptor.fromJson(obj.getJSONObject("getItems")),
            itemsField = obj.optJSONObject("itemsField")?.let(FieldDescriptor::fromJson),
        )
    }
}

data class RestoredHomeRecommendFeedSymbols(
    val responseGetItems: List<RestoredPegasusResponseGetItemsSymbols>,
    val getHolderType: Method,
    val getBizType: Method?,
    val getHolderStyle: Method?,
    val isSmallCard: Method?,
    val getAdInfo: Method?,
    val getCardType: Method?,
    val getCardGoto: Method?,
    val getGoTo: Method?,
    val getUri: Method?,
    val adInfoClass: Class<*>?,
)

data class RestoredPegasusResponseGetItemsSymbols(
    val getItems: Method,
    val itemsField: Field?,
)

data class HomeComponentHideSymbols(
    val fragmentLifecycleMethods: List<MethodDescriptor>,
    val baseHomeFragmentMethods: List<MethodDescriptor>,
    val componentCatalogMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fragmentLifecycleMethods", fragmentLifecycleMethods.toJsonArray { it.toJson() })
        .put("baseHomeFragmentMethods", baseHomeFragmentMethods.toJsonArray { it.toJson() })
        .putOpt("componentCatalogMethod", componentCatalogMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredHomeComponentHideSymbols? {
        val fragmentMethods = fragmentLifecycleMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (fragmentMethods.size != fragmentLifecycleMethods.size) return null
        val methods = baseHomeFragmentMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (methods.size != baseHomeFragmentMethods.size) return null
        return RestoredHomeComponentHideSymbols(
            fragmentLifecycleMethods = fragmentMethods,
            baseHomeFragmentMethods = methods,
            componentCatalogMethod = componentCatalogMethod?.restoreOptional(classLoader),
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeComponentHideSymbols = HomeComponentHideSymbols(
            fragmentLifecycleMethods = obj.optJSONArray("fragmentLifecycleMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            baseHomeFragmentMethods = obj.optJSONArray("baseHomeFragmentMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            componentCatalogMethod = obj.optJSONObject("componentCatalogMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredHomeComponentHideSymbols(
    val fragmentLifecycleMethods: List<Method>,
    val baseHomeFragmentMethods: List<Method>,
    val componentCatalogMethod: Method?,
)
