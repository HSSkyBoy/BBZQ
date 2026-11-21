package io.github.bbzq.feats.symbol

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import org.json.JSONObject

data class StoryPlayerAdSymbols(
    val feedGetItems: MethodDescriptor?,
    val pagerListMethods: List<MethodDescriptor>,
    val rerankInvokeSuspend: MethodDescriptor?,
    val kotlinUnitField: FieldDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .putOpt("feedGetItems", feedGetItems?.toJson())
        .put("pagerListMethods", pagerListMethods.toJsonArray { it.toJson() })
        .putOpt("rerankInvokeSuspend", rerankInvokeSuspend?.toJson())
        .putOpt("kotlinUnitField", kotlinUnitField?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryPlayerAdSymbols? {
        val feed = feedGetItems?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val pager = pagerListMethods.mapNotNull { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@mapNotNull null
            descriptor.restore(owner)
        }
        if (pager.size != pagerListMethods.size) return null
        val rerank = rerankInvokeSuspend?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)
        }
        val unit = kotlinUnitField?.let { descriptor ->
            val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return@let null
            descriptor.restore(owner)?.get(null)
        }
        if (rerankInvokeSuspend != null && rerank == null) return null
        if (kotlinUnitField != null && unit == null) return null
        return RestoredStoryPlayerAdSymbols(feed, pager, rerank, unit)
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryPlayerAdSymbols = StoryPlayerAdSymbols(
            feedGetItems = obj.optJSONObject("feedGetItems")?.let(MethodDescriptor::fromJson),
            pagerListMethods = obj.optJSONArray("pagerListMethods").toList { MethodDescriptor.fromJson(it) },
            rerankInvokeSuspend = obj.optJSONObject("rerankInvokeSuspend")?.let(MethodDescriptor::fromJson),
            kotlinUnitField = obj.optJSONObject("kotlinUnitField")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredStoryPlayerAdSymbols(
    val feedGetItems: Method?,
    val pagerListMethods: List<Method>,
    val rerankInvokeSuspend: Method?,
    val kotlinUnit: Any?,
)

data class StoryFullscreenSymbols(
    val onCreate: MethodDescriptor,
    val onWindowFocusChanged: MethodDescriptor,
    val onResume: MethodDescriptor?,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("onCreate", onCreate.toJson())
        .put("onWindowFocusChanged", onWindowFocusChanged.toJson())
        .putOpt("onResume", onResume?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryFullscreenSymbols? {
        val create = onCreate.restoreOptional(classLoader) ?: return null
        val focus = onWindowFocusChanged.restoreOptional(classLoader) ?: return null
        val resume = onResume.restoreOptional(classLoader)
        if (onResume != null && resume == null) return null
        return RestoredStoryFullscreenSymbols(
            onCreate = create,
            onWindowFocusChanged = focus,
            onResume = resume,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryFullscreenSymbols = StoryFullscreenSymbols(
            onCreate = MethodDescriptor.fromJson(obj.getJSONObject("onCreate")),
            onWindowFocusChanged = MethodDescriptor.fromJson(obj.getJSONObject("onWindowFocusChanged")),
            onResume = obj.optJSONObject("onResume")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredStoryFullscreenSymbols(
    val onCreate: Method,
    val onWindowFocusChanged: Method,
    val onResume: Method?,
)

data class StoryDanmakuSymbols(
    val commentShowMethods: List<MethodDescriptor>,
    val commentHideMethods: List<MethodDescriptor>,
    val introCommentShowMethod: MethodDescriptor?,
    val introCommentDismissMethod: MethodDescriptor?,
    val setDanmakuOpacity: MethodDescriptor,
    val updateCanvas: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("commentShowMethods", commentShowMethods.toJsonArray { it.toJson() })
        .put("commentHideMethods", commentHideMethods.toJsonArray { it.toJson() })
        .putOpt("introCommentShowMethod", introCommentShowMethod?.toJson())
        .putOpt("introCommentDismissMethod", introCommentDismissMethod?.toJson())
        .put("setDanmakuOpacity", setDanmakuOpacity.toJson())
        .put("updateCanvas", updateCanvas.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryDanmakuSymbols? {
        val showMethods = commentShowMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (showMethods.size != commentShowMethods.size) return null
        val hideMethods = commentHideMethods.mapNotNull { it.restoreOptional(classLoader) }
        if (hideMethods.size != commentHideMethods.size) return null
        val introShowMethod = introCommentShowMethod.restoreOptional(classLoader)
        if (introCommentShowMethod != null && introShowMethod == null) return null
        val introDismissMethod = introCommentDismissMethod.restoreOptional(classLoader)
        if (introCommentDismissMethod != null && introDismissMethod == null) return null
        val setOpacity = setDanmakuOpacity.restoreOptional(classLoader) ?: return null
        val canvas = updateCanvas.restoreOptional(classLoader) ?: return null
        return RestoredStoryDanmakuSymbols(
            commentShowMethods = showMethods,
            commentHideMethods = hideMethods,
            introCommentShowMethod = introShowMethod,
            introCommentDismissMethod = introDismissMethod,
            setDanmakuOpacity = setOpacity,
            updateCanvas = canvas,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryDanmakuSymbols = StoryDanmakuSymbols(
            commentShowMethods = obj.optJSONArray("commentShowMethods").toList { MethodDescriptor.fromJson(it) },
            commentHideMethods = obj.optJSONArray("commentHideMethods").toList { MethodDescriptor.fromJson(it) },
            introCommentShowMethod = obj.optJSONObject("introCommentShowMethod")?.let(MethodDescriptor::fromJson),
            introCommentDismissMethod = obj.optJSONObject("introCommentDismissMethod")
                ?.let(MethodDescriptor::fromJson),
            setDanmakuOpacity = MethodDescriptor.fromJson(obj.getJSONObject("setDanmakuOpacity")),
            updateCanvas = MethodDescriptor.fromJson(obj.getJSONObject("updateCanvas")),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredStoryDanmakuSymbols(
    val commentShowMethods: List<Method>,
    val commentHideMethods: List<Method>,
    val introCommentShowMethod: Method?,
    val introCommentDismissMethod: Method?,
    val setDanmakuOpacity: Method,
    val updateCanvas: Method,
)

data class StoryComponentAlphaSymbols(
    val infoConstructors: List<ConstructorDescriptor>,
    val rightConstructors: List<ConstructorDescriptor>,
    val bottomConstructors: List<ConstructorDescriptor>,
    val seekBarConstructors: List<ConstructorDescriptor>,
    val fragmentOnCreateView: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("infoConstructors", infoConstructors.toJsonArray { it.toJson() })
        .put("rightConstructors", rightConstructors.toJsonArray { it.toJson() })
        .put("bottomConstructors", bottomConstructors.toJsonArray { it.toJson() })
        .put("seekBarConstructors", seekBarConstructors.toJsonArray { it.toJson() })
        .put("fragmentOnCreateView", fragmentOnCreateView.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredStoryComponentAlphaSymbols? {
        val infoType = classLoader.loadClassOrNull(STORY_INFO_MODULE) ?: return null
        val rightType = classLoader.loadClassOrNull(STORY_RIGHT_MODULE) ?: return null
        val bottomType = classLoader.loadClassOrNull(STORY_BOTTOM_MODULE) ?: return null
        val seekBarType = classLoader.loadClassOrNull(STORY_SEEK_BAR) ?: return null
        return RestoredStoryComponentAlphaSymbols(
            infoConstructors = infoConstructors.mapNotNull { it.restore(infoType) }
                .takeIf { it.size == infoConstructors.size } ?: return null,
            rightConstructors = rightConstructors.mapNotNull { it.restore(rightType) }
                .takeIf { it.size == rightConstructors.size } ?: return null,
            bottomConstructors = bottomConstructors.mapNotNull { it.restore(bottomType) }
                .takeIf { it.size == bottomConstructors.size } ?: return null,
            seekBarConstructors = seekBarConstructors.mapNotNull { it.restore(seekBarType) }
                .takeIf { it.size == seekBarConstructors.size } ?: return null,
            fragmentOnCreateView = fragmentOnCreateView.restoreOptional(classLoader) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): StoryComponentAlphaSymbols = StoryComponentAlphaSymbols(
            infoConstructors = obj.optJSONArray("infoConstructors").toList { ConstructorDescriptor.fromJson(it) },
            rightConstructors = obj.optJSONArray("rightConstructors").toList { ConstructorDescriptor.fromJson(it) },
            bottomConstructors = obj.optJSONArray("bottomConstructors").toList { ConstructorDescriptor.fromJson(it) },
            seekBarConstructors = obj.optJSONArray("seekBarConstructors").toList { ConstructorDescriptor.fromJson(it) },
            fragmentOnCreateView = MethodDescriptor.fromJson(obj.getJSONObject("fragmentOnCreateView")),
            evidence = obj.optString("evidence", "-"),
        )

        private const val STORY_INFO_MODULE = "com.bilibili.video.story.module.StoryInfoModule"
        private const val STORY_RIGHT_MODULE = "com.bilibili.video.story.module.StoryRightModule"
        private const val STORY_BOTTOM_MODULE = "com.bilibili.video.story.module.StoryBottomModule"
        private const val STORY_SEEK_BAR = "com.bilibili.video.story.view.StorySeekBar"
    }
}

data class RestoredStoryComponentAlphaSymbols(
    val infoConstructors: List<Constructor<*>>,
    val rightConstructors: List<Constructor<*>>,
    val bottomConstructors: List<Constructor<*>>,
    val seekBarConstructors: List<Constructor<*>>,
    val fragmentOnCreateView: Method,
)
