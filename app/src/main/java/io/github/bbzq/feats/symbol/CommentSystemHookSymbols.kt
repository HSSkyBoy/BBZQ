package io.github.bbzq.feats.symbol

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONObject

data class ShareSymbols(
    val legacyGetLink: MethodDescriptor? = null,
    val legacyGetContent: MethodDescriptor? = null,
    val legacyGetShareMode: MethodDescriptor? = null,
    val legacySetLink: MethodDescriptor? = null,
    val legacySetContent: MethodDescriptor? = null,
    val legacySetShareMode: MethodDescriptor? = null,
    val shareChannelsGetCopyLink: MethodDescriptor? = null,
    val shareChannelsGetJumpLink: MethodDescriptor? = null,
    val shareChannelsGetText: MethodDescriptor? = null,
    val shareChannelsSetCopyLink: MethodDescriptor? = null,
    val shareChannelsSetJumpLink: MethodDescriptor? = null,
    val shareChannelsSetText: MethodDescriptor? = null,
    val shareChannelItemGetJumpLink: MethodDescriptor? = null,
    val shareChannelItemSetJumpLink: MethodDescriptor? = null,
    val shareBaseInfoClassName: String? = null,
    val shareContentClassName: String? = null,
    val shareContentCopyMethods: List<MethodDescriptor>,
    val shareContentGetLink: MethodDescriptor? = null,
    val shareContentGetContent: MethodDescriptor? = null,
    val shareContentGetMode: MethodDescriptor? = null,
    val shareBiliContentClassName: String? = null,
    val shareBiliContentCopyMethods: List<MethodDescriptor>,
    val shareBiliContentGetDescription: MethodDescriptor? = null,
    val shareBiliContentGetContentUrl: MethodDescriptor? = null,
    val copyContentClassName: String? = null,
    val copyContentGetters: List<MethodDescriptor>,
    val copyUtilityMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("legacyGetLink", legacyGetLink?.toJson())
        .putOpt("legacyGetContent", legacyGetContent?.toJson())
        .putOpt("legacyGetShareMode", legacyGetShareMode?.toJson())
        .putOpt("legacySetLink", legacySetLink?.toJson())
        .putOpt("legacySetContent", legacySetContent?.toJson())
        .putOpt("legacySetShareMode", legacySetShareMode?.toJson())
        .putOpt("shareChannelsGetCopyLink", shareChannelsGetCopyLink?.toJson())
        .putOpt("shareChannelsGetJumpLink", shareChannelsGetJumpLink?.toJson())
        .putOpt("shareChannelsGetText", shareChannelsGetText?.toJson())
        .putOpt("shareChannelsSetCopyLink", shareChannelsSetCopyLink?.toJson())
        .putOpt("shareChannelsSetJumpLink", shareChannelsSetJumpLink?.toJson())
        .putOpt("shareChannelsSetText", shareChannelsSetText?.toJson())
        .putOpt("shareChannelItemGetJumpLink", shareChannelItemGetJumpLink?.toJson())
        .putOpt("shareChannelItemSetJumpLink", shareChannelItemSetJumpLink?.toJson())
        .putOpt("shareBaseInfoClassName", shareBaseInfoClassName)
        .putOpt("shareContentClassName", shareContentClassName)
        .put("shareContentCopyMethods", shareContentCopyMethods.toJsonArray { it.toJson() })
        .putOpt("shareContentGetLink", shareContentGetLink?.toJson())
        .putOpt("shareContentGetContent", shareContentGetContent?.toJson())
        .putOpt("shareContentGetMode", shareContentGetMode?.toJson())
        .putOpt("shareBiliContentClassName", shareBiliContentClassName)
        .put("shareBiliContentCopyMethods", shareBiliContentCopyMethods.toJsonArray { it.toJson() })
        .putOpt("shareBiliContentGetDescription", shareBiliContentGetDescription?.toJson())
        .putOpt("shareBiliContentGetContentUrl", shareBiliContentGetContentUrl?.toJson())
        .putOpt("copyContentClassName", copyContentClassName)
        .put("copyContentGetters", copyContentGetters.toJsonArray { it.toJson() })
        .put("copyUtilityMethods", copyUtilityMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredShareSymbols =
        RestoredShareSymbols(
            legacyGetLink = legacyGetLink.restoreOptional(classLoader),
            legacyGetContent = legacyGetContent.restoreOptional(classLoader),
            legacyGetShareMode = legacyGetShareMode.restoreOptional(classLoader),
            legacySetLink = legacySetLink.restoreOptional(classLoader),
            legacySetContent = legacySetContent.restoreOptional(classLoader),
            legacySetShareMode = legacySetShareMode.restoreOptional(classLoader),
            shareChannelsGetCopyLink = shareChannelsGetCopyLink.restoreOptional(classLoader),
            shareChannelsGetJumpLink = shareChannelsGetJumpLink.restoreOptional(classLoader),
            shareChannelsGetText = shareChannelsGetText.restoreOptional(classLoader),
            shareChannelsSetCopyLink = shareChannelsSetCopyLink.restoreOptional(classLoader),
            shareChannelsSetJumpLink = shareChannelsSetJumpLink.restoreOptional(classLoader),
            shareChannelsSetText = shareChannelsSetText.restoreOptional(classLoader),
            shareChannelItemGetJumpLink = shareChannelItemGetJumpLink.restoreOptional(classLoader),
            shareChannelItemSetJumpLink = shareChannelItemSetJumpLink.restoreOptional(classLoader),
            shareBaseInfoClass = shareBaseInfoClassName?.let(classLoader::loadClassOrNull),
            shareContentClass = shareContentClassName?.let(classLoader::loadClassOrNull),
            shareContentCopyMethods = shareContentCopyMethods.restoreAvailable(classLoader),
            shareContentGetLink = shareContentGetLink.restoreOptional(classLoader),
            shareContentGetContent = shareContentGetContent.restoreOptional(classLoader),
            shareContentGetMode = shareContentGetMode.restoreOptional(classLoader),
            shareBiliContentClass = shareBiliContentClassName?.let(classLoader::loadClassOrNull),
            shareBiliContentCopyMethods = shareBiliContentCopyMethods.restoreAvailable(classLoader),
            shareBiliContentGetDescription = shareBiliContentGetDescription.restoreOptional(classLoader),
            shareBiliContentGetContentUrl = shareBiliContentGetContentUrl.restoreOptional(classLoader),
            copyContentClass = copyContentClassName?.let(classLoader::loadClassOrNull),
            copyContentGetters = copyContentGetters.restoreAvailable(classLoader),
            copyUtilityMethods = copyUtilityMethods.restoreAvailable(classLoader),
        )

    companion object {
        fun fromJson(obj: JSONObject): ShareSymbols = ShareSymbols(
            legacyGetLink = obj.optJSONObject("legacyGetLink")?.let(MethodDescriptor::fromJson),
            legacyGetContent = obj.optJSONObject("legacyGetContent")?.let(MethodDescriptor::fromJson),
            legacyGetShareMode = obj.optJSONObject("legacyGetShareMode")?.let(MethodDescriptor::fromJson),
            legacySetLink = obj.optJSONObject("legacySetLink")?.let(MethodDescriptor::fromJson),
            legacySetContent = obj.optJSONObject("legacySetContent")?.let(MethodDescriptor::fromJson),
            legacySetShareMode = obj.optJSONObject("legacySetShareMode")?.let(MethodDescriptor::fromJson),
            shareChannelsGetCopyLink = obj.optJSONObject("shareChannelsGetCopyLink")?.let(MethodDescriptor::fromJson),
            shareChannelsGetJumpLink = obj.optJSONObject("shareChannelsGetJumpLink")?.let(MethodDescriptor::fromJson),
            shareChannelsGetText = obj.optJSONObject("shareChannelsGetText")?.let(MethodDescriptor::fromJson),
            shareChannelsSetCopyLink = obj.optJSONObject("shareChannelsSetCopyLink")?.let(MethodDescriptor::fromJson),
            shareChannelsSetJumpLink = obj.optJSONObject("shareChannelsSetJumpLink")?.let(MethodDescriptor::fromJson),
            shareChannelsSetText = obj.optJSONObject("shareChannelsSetText")?.let(MethodDescriptor::fromJson),
            shareChannelItemGetJumpLink = obj.optJSONObject("shareChannelItemGetJumpLink")
                ?.let(MethodDescriptor::fromJson),
            shareChannelItemSetJumpLink = obj.optJSONObject("shareChannelItemSetJumpLink")
                ?.let(MethodDescriptor::fromJson),
            shareBaseInfoClassName = obj.optString("shareBaseInfoClassName").takeIf { it.isNotBlank() },
            shareContentClassName = obj.optString("shareContentClassName").takeIf { it.isNotBlank() },
            shareContentCopyMethods = obj.optJSONArray("shareContentCopyMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            shareContentGetLink = obj.optJSONObject("shareContentGetLink")?.let(MethodDescriptor::fromJson),
            shareContentGetContent = obj.optJSONObject("shareContentGetContent")?.let(MethodDescriptor::fromJson),
            shareContentGetMode = obj.optJSONObject("shareContentGetMode")?.let(MethodDescriptor::fromJson),
            shareBiliContentClassName = obj.optString("shareBiliContentClassName").takeIf { it.isNotBlank() },
            shareBiliContentCopyMethods = obj.optJSONArray("shareBiliContentCopyMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            shareBiliContentGetDescription = obj.optJSONObject("shareBiliContentGetDescription")
                ?.let(MethodDescriptor::fromJson),
            shareBiliContentGetContentUrl = obj.optJSONObject("shareBiliContentGetContentUrl")
                ?.let(MethodDescriptor::fromJson),
            copyContentClassName = obj.optString("copyContentClassName").takeIf { it.isNotBlank() },
            copyContentGetters = obj.optJSONArray("copyContentGetters").toList { MethodDescriptor.fromJson(it) },
            copyUtilityMethods = obj.optJSONArray("copyUtilityMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredShareSymbols(
    val legacyGetLink: Method?,
    val legacyGetContent: Method?,
    val legacyGetShareMode: Method?,
    val legacySetLink: Method?,
    val legacySetContent: Method?,
    val legacySetShareMode: Method?,
    val shareChannelsGetCopyLink: Method?,
    val shareChannelsGetJumpLink: Method?,
    val shareChannelsGetText: Method?,
    val shareChannelsSetCopyLink: Method?,
    val shareChannelsSetJumpLink: Method?,
    val shareChannelsSetText: Method?,
    val shareChannelItemGetJumpLink: Method?,
    val shareChannelItemSetJumpLink: Method?,
    val shareBaseInfoClass: Class<*>?,
    val shareContentClass: Class<*>?,
    val shareContentCopyMethods: List<Method>,
    val shareContentGetLink: Method?,
    val shareContentGetContent: Method?,
    val shareContentGetMode: Method?,
    val shareBiliContentClass: Class<*>?,
    val shareBiliContentCopyMethods: List<Method>,
    val shareBiliContentGetDescription: Method?,
    val shareBiliContentGetContentUrl: Method?,
    val copyContentClass: Class<*>?,
    val copyContentGetters: List<Method>,
    val copyUtilityMethods: List<Method>,
)

data class TeenagersModeSymbols(
    val onCreateMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("onCreateMethods", onCreateMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredTeenagersModeSymbols? {
        return RestoredTeenagersModeSymbols(onCreateMethods.restoreAll(classLoader) ?: return null)
    }

    companion object {
        fun fromJson(obj: JSONObject): TeenagersModeSymbols = TeenagersModeSymbols(
            onCreateMethods = obj.optJSONArray("onCreateMethods").toList { MethodDescriptor.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredTeenagersModeSymbols(
    val onCreateMethods: List<Method>,
)

data class SettingsSymbols(
    val fragmentMethods: List<MethodDescriptor>,
    val preferenceClassName: String,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fragmentMethods", fragmentMethods.toJsonArray { it.toJson() })
        .put("preferenceClassName", preferenceClassName)
        .put("evidence", evidence)

    fun restoreFragmentMethods(classLoader: ClassLoader): List<Method> =
        fragmentMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }

    fun restorePreferenceClass(classLoader: ClassLoader): Class<*>? =
        classLoader.loadClassOrNull(preferenceClassName)

    companion object {
        fun fromJson(obj: JSONObject): SettingsSymbols = SettingsSymbols(
            fragmentMethods = obj.optJSONArray("fragmentMethods").toList { MethodDescriptor.fromJson(it) },
            preferenceClassName = obj.optString("preferenceClassName"),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class BlockUpdateSymbols(
    val checkMethod: MethodDescriptor,
    val evidence: String,
    val helperMethods: List<MethodDescriptor> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("checkMethod", checkMethod.toJson())
        .put("helperMethods", helperMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredBlockUpdateSymbols? {
        val owner = classLoader.loadClassOrNull(checkMethod.declaringClassName) ?: return null
        val method = checkMethod.restore(owner) ?: return null
        return RestoredBlockUpdateSymbols(method, helperMethods.restoreAvailable(classLoader))
    }

    companion object {
        fun fromJson(obj: JSONObject): BlockUpdateSymbols = BlockUpdateSymbols(
            checkMethod = MethodDescriptor.fromJson(obj.getJSONObject("checkMethod")),
            evidence = obj.optString("evidence", "-"),
            helperMethods = obj.optJSONArray("helperMethods").toList { MethodDescriptor.fromJson(it) },
        )
    }
}

data class RestoredBlockUpdateSymbols(
    val checkMethod: Method,
    val helperMethods: List<Method> = emptyList(),
)

data class DownloadThreadSymbols(
    val listeners: List<DownloadThreadListenerSymbols>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("listeners", listeners.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredDownloadThreadSymbols =
        RestoredDownloadThreadSymbols(listeners.mapNotNull { it.restore(classLoader) })

    companion object {
        fun fromJson(obj: JSONObject): DownloadThreadSymbols = DownloadThreadSymbols(
            listeners = obj.optJSONArray("listeners").toList { DownloadThreadListenerSymbols.fromJson(it) },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class DownloadThreadListenerSymbols(
    val className: String,
    val constructor: ConstructorDescriptor,
    val onClick: MethodDescriptor,
    val textViewField: FieldDescriptor,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("className", className)
        .put("constructor", constructor.toJson())
        .put("onClick", onClick.toJson())
        .put("textViewField", textViewField.toJson())

    fun restore(classLoader: ClassLoader): RestoredDownloadThreadListenerSymbols? {
        val type = classLoader.loadClassOrNull(className) ?: return null
        val ctor = constructor.restore(type) ?: return null
        val click = onClick.restore(type) ?: return null
        val fieldOwner = classLoader.loadClassOrNull(textViewField.declaringClassName) ?: return null
        val field = textViewField.restore(fieldOwner) ?: return null
        return RestoredDownloadThreadListenerSymbols(type, ctor, click, field)
    }

    companion object {
        fun fromJson(obj: JSONObject): DownloadThreadListenerSymbols = DownloadThreadListenerSymbols(
            className = obj.optString("className"),
            constructor = ConstructorDescriptor.fromJson(obj.getJSONObject("constructor")),
            onClick = MethodDescriptor.fromJson(obj.getJSONObject("onClick")),
            textViewField = FieldDescriptor.fromJson(obj.getJSONObject("textViewField")),
        )
    }
}

data class RestoredDownloadThreadSymbols(
    val listeners: List<RestoredDownloadThreadListenerSymbols>,
)

data class RestoredDownloadThreadListenerSymbols(
    val listenerClass: Class<*>,
    val constructor: Constructor<*>,
    val onClick: Method,
    val textViewField: Field,
)

data class FullNumberFormatSymbols(
    val formatterMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatterMethods", formatterMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredFullNumberFormatSymbols? {
        return RestoredFullNumberFormatSymbols(
            formatterMethods = formatterMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): FullNumberFormatSymbols = FullNumberFormatSymbols(
            formatterMethods = obj.optJSONArray("formatterMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredFullNumberFormatSymbols(
    val formatterMethods: List<Method>,
)

data class VideoCommentSymbols(
    val disableCommentConstructors: List<ConstructorDescriptor>,
    val quickReplyViewModelMethods: List<MethodDescriptor>,
    val quickReplyDialogMethods: List<MethodDescriptor>,
    val voteWidgetMethods: List<MethodDescriptor>,
    val followWidgetMethods: List<MethodDescriptor>,
    val headerDecorativeMethods: List<MethodDescriptor>,
    val searchUrlsMethod: MethodDescriptor?,
    val emptyPageHooks: List<VideoCommentEmptyPageSymbols>,
    val mainListOnNextMethods: List<MethodDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("disableCommentConstructors", disableCommentConstructors.toJsonArray { it.toJson() })
        .put("quickReplyViewModelMethods", quickReplyViewModelMethods.toJsonArray { it.toJson() })
        .put("quickReplyDialogMethods", quickReplyDialogMethods.toJsonArray { it.toJson() })
        .put("voteWidgetMethods", voteWidgetMethods.toJsonArray { it.toJson() })
        .put("followWidgetMethods", followWidgetMethods.toJsonArray { it.toJson() })
        .put("headerDecorativeMethods", headerDecorativeMethods.toJsonArray { it.toJson() })
        .putOpt("searchUrlsMethod", searchUrlsMethod?.toJson())
        .put("emptyPageHooks", emptyPageHooks.toJsonArray { it.toJson() })
        .put("mainListOnNextMethods", mainListOnNextMethods.toJsonArray { it.toJson() })
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredVideoCommentSymbols? {
        val constructors = disableCommentConstructors.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (constructors.size != disableCommentConstructors.size) return null
        val emptyPages = emptyPageHooks.mapNotNull { it.restore(classLoader) }
        if (emptyPages.size != emptyPageHooks.size) return null
        return RestoredVideoCommentSymbols(
            disableCommentConstructors = constructors,
            quickReplyViewModelMethods = quickReplyViewModelMethods.restoreAll(classLoader) ?: return null,
            quickReplyDialogMethods = quickReplyDialogMethods.restoreAll(classLoader) ?: return null,
            voteWidgetMethods = voteWidgetMethods.restoreAll(classLoader) ?: return null,
            followWidgetMethods = followWidgetMethods.restoreAll(classLoader) ?: return null,
            headerDecorativeMethods = headerDecorativeMethods.restoreAll(classLoader) ?: return null,
            searchUrlsMethod = searchUrlsMethod.restoreOptional(classLoader),
            emptyPageHooks = emptyPages,
            mainListOnNextMethods = mainListOnNextMethods.restoreAll(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoCommentSymbols = VideoCommentSymbols(
            disableCommentConstructors = obj.optJSONArray("disableCommentConstructors").toList {
                ConstructorDescriptor.fromJson(it)
            },
            quickReplyViewModelMethods = obj.optJSONArray("quickReplyViewModelMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            quickReplyDialogMethods = obj.optJSONArray("quickReplyDialogMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            voteWidgetMethods = obj.optJSONArray("voteWidgetMethods").toList { MethodDescriptor.fromJson(it) },
            followWidgetMethods = obj.optJSONArray("followWidgetMethods").toList { MethodDescriptor.fromJson(it) },
            headerDecorativeMethods = obj.optJSONArray("headerDecorativeMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            searchUrlsMethod = obj.optJSONObject("searchUrlsMethod")?.let(MethodDescriptor::fromJson),
            emptyPageHooks = obj.optJSONArray("emptyPageHooks").toList {
                VideoCommentEmptyPageSymbols.fromJson(it)
            },
            mainListOnNextMethods = obj.optJSONArray("mainListOnNextMethods").toList {
                MethodDescriptor.fromJson(it)
            },
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class VideoCommentEmptyPageSymbols(
    val getEmptyPage: MethodDescriptor,
    val defaultInstance: FieldDescriptor,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("getEmptyPage", getEmptyPage.toJson())
        .put("defaultInstance", defaultInstance.toJson())

    fun restore(classLoader: ClassLoader): RestoredVideoCommentEmptyPageSymbols? {
        val methodOwner = classLoader.loadClassOrNull(getEmptyPage.declaringClassName) ?: return null
        val fieldOwner = classLoader.loadClassOrNull(defaultInstance.declaringClassName) ?: return null
        val field = defaultInstance.restore(fieldOwner) ?: return null
        return RestoredVideoCommentEmptyPageSymbols(
            getEmptyPage = getEmptyPage.restore(methodOwner) ?: return null,
            defaultInstance = field.get(null) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): VideoCommentEmptyPageSymbols = VideoCommentEmptyPageSymbols(
            getEmptyPage = MethodDescriptor.fromJson(obj.getJSONObject("getEmptyPage")),
            defaultInstance = FieldDescriptor.fromJson(obj.getJSONObject("defaultInstance")),
        )
    }
}

data class RestoredVideoCommentSymbols(
    val disableCommentConstructors: List<Constructor<*>>,
    val quickReplyViewModelMethods: List<Method>,
    val quickReplyDialogMethods: List<Method>,
    val voteWidgetMethods: List<Method>,
    val followWidgetMethods: List<Method>,
    val headerDecorativeMethods: List<Method>,
    val searchUrlsMethod: Method?,
    val emptyPageHooks: List<RestoredVideoCommentEmptyPageSymbols>,
    val mainListOnNextMethods: List<Method>,
)

data class RestoredVideoCommentEmptyPageSymbols(
    val getEmptyPage: Method,
    val defaultInstance: Any,
)
