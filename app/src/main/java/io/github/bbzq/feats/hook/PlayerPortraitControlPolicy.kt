package io.github.bbzq.feats.hook

import java.lang.reflect.Method

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

    fun isSafeLegacyWidgetBooleanMethod(methodName: String): Boolean =
        methodName !in EXCLUDED_VIEW_BOOLEAN_METHODS

    fun overridesInheritedMethod(method: Method): Boolean {
        val pending = ArrayDeque<Class<*>>()
        val seen = HashSet<Class<*>>()
        method.declaringClass.superclass?.let(pending::add)
        pending.addAll(method.declaringClass.interfaces)
        while (pending.isNotEmpty()) {
            val type = pending.removeFirst()
            if (!seen.add(type)) continue
            if (type.declaredMethods.any { it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes) }) {
                return true
            }
            type.superclass?.let(pending::add)
            pending.addAll(type.interfaces)
        }
        return false
    }

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
        val flagIndex = when {
            parameterTypeNames.getOrNull(0) == LONG_CLASS -> 1
            parameterTypeNames.getOrNull(0) == STRING_CLASS && parameterTypeNames.getOrNull(1) == LONG_CLASS -> 2
            else -> return false
        }
        val flag = parameterTypeNames.getOrNull(flagIndex) ?: return false
        if (flag != "boolean" && flag != "java.lang.Boolean") return false
        return parameterTypeNames.drop(flagIndex + 1).all {
            it == "int" || it == DEFAULT_CONSTRUCTOR_MARKER
        }
    }

    fun isHostClassName(className: String): Boolean =
        FRAMEWORK_PACKAGES.none { className.startsWith(it) }

    fun isProtectedContainerClassName(className: String): Boolean =
        className.startsWith("android.view.") ||
            className.contains("ComposeView", ignoreCase = true)

    private val FRAMEWORK_PACKAGES = listOf("java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.")
    private const val LONG_CLASS = "java.lang.Long"
    private const val STRING_CLASS = "java.lang.String"
    private const val COMPOSER_CLASS = "androidx.compose.runtime.Composer"
    private const val FUNCTION2_CLASS = "kotlin.jvm.functions.Function2"
    private const val DEFAULT_CONSTRUCTOR_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
}
