package io.github.bbzq.feats.hook

internal object PlayerPortraitControlPolicy {
    private val UNSAFE_BOOLEAN_FIELD_TOKENS = listOf(
        "init",
        "destroy",
        "dispose",
        "detach",
        "attach",
        "active",
        "alive",
        "ready",
        "release",
        "closed",
    )

    private val EXCLUDED_VIEW_BOOLEAN_METHODS = setOf(
        "performClick",
        "performLongClick",
        "hasOverlappingRendering",
        "isOpaque",
        "isShown",
        "isEnabled",
        "isClickable",
        "isFocusable",
        "isFocusableInTouchMode",
        "isAttachedToWindow",
        "canScrollHorizontally",
        "canScrollVertically",
        "requestFocus",
        "hasFocus",
        "hasFocusable",
        "isDirty",
        "isLayoutRequested",
        "isInLayout",
        "isLaidOut",
        "isHardwareAccelerated",
        "isPressed",
        "isSelected",
        "isActivated",
        "isHovered",
        "willNotDraw",
        "willNotCacheDrawing",
        "fitSystemWindows",
        "dispatchPopulateAccessibilityEvent",
        "isInEditMode",
        "isInTouchMode",
        "isSaveEnabled",
        "isVerticalScrollBarEnabled",
        "isHorizontalScrollBarEnabled",
    )

    private val STORY_CONTROL_NAME_TOKENS = listOf(
        "story",
        "portrait",
        "fullscreen",
        "control",
        "entrance",
        "visible",
        "show",
    )

    private val STORY_FUNCTION2_NAME_TOKENS = listOf(
        "story",
        "portrait",
        "fullscreen",
        "control",
        "entrance",
        "ui",
    )

    fun isSafeBooleanFieldToSuppress(fieldName: String): Boolean {
        val lower = fieldName.lowercase()
        return UNSAFE_BOOLEAN_FIELD_TOKENS.none { lower.contains(it) }
    }

    fun isSafeLegacyWidgetBooleanMethod(methodName: String): Boolean =
        methodName !in EXCLUDED_VIEW_BOOLEAN_METHODS

    fun isStoryServiceBooleanMethod(methodName: String): Boolean {
        if (methodName in EXCLUDED_VIEW_BOOLEAN_METHODS) return false
        val lower = methodName.lowercase()
        if (UNSAFE_BOOLEAN_FIELD_TOKENS.any { lower.contains(it) }) return false
        return STORY_CONTROL_NAME_TOKENS.any { lower.contains(it) }
    }

    fun isComposableMethodSignature(returnTypeName: String, parameterTypeNames: List<String>): Boolean {
        if (returnTypeName != "void") return false
        val composerIndex = parameterTypeNames.indexOf(COMPOSER_CLASS)
        if (composerIndex < 0 || composerIndex + 1 >= parameterTypeNames.size) return false
        return parameterTypeNames[composerIndex + 1] == "int"
    }

    fun isStoryFunction2Method(methodName: String, returnTypeName: String): Boolean {
        if (returnTypeName != FUNCTION2_CLASS) return false
        val lower = methodName.lowercase()
        return STORY_FUNCTION2_NAME_TOKENS.any { lower.contains(it) }
    }

    fun isStoryInitialConfigConstructor(parameterTypeNames: List<String>): Boolean {
        if (parameterTypeNames.size < 2) return false
        if (parameterTypeNames[0] != "java.lang.Long") return false
        if (parameterTypeNames[1] != "boolean" && parameterTypeNames[1] != "java.lang.Boolean") return false
        return parameterTypeNames.drop(2).all {
            it == "int" || it == DEFAULT_CONSTRUCTOR_MARKER
        }
    }

    fun isProtectedContainerClassName(className: String): Boolean =
        className.startsWith("android.view.") ||
            className.contains("ComposeView", ignoreCase = true)

    private const val COMPOSER_CLASS = "androidx.compose.runtime.Composer"
    private const val FUNCTION2_CLASS = "kotlin.jvm.functions.Function2"
    private const val DEFAULT_CONSTRUCTOR_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
}
