package io.github.bbzq.feats.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPortraitControlPolicyTest {

    @Test
    fun booleanFieldFilterRejectsLifecycleFlagsAndAllowsStoryFlags() {
        assertTrue(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("showStoryControl"))
        assertTrue(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("playerStoryControlsEnabled"))
        assertTrue(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("a"))

        assertFalse(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("isInitialized"))
        assertFalse(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("destroyed"))
        assertFalse(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("isDisposed"))
        assertFalse(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("attached"))
        assertFalse(PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress("isActive"))
    }

    @Test
    fun legacyWidgetBooleanFilterProtectsViewMethods() {
        assertTrue(PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod("m91790H"))
        assertTrue(PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod("isStorySupported"))

        assertFalse(PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod("performClick"))
        assertFalse(PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod("hasOverlappingRendering"))
        assertFalse(PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod("isAttachedToWindow"))
        assertFalse(PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod("isShown"))
    }

    @Test
    fun storyServiceBooleanMethodMatchesOnlyStoryControlFlags() {
        assertTrue(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("isStoryControlsEnabled"))
        assertTrue(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("shouldShowFullscreenControl"))
        assertTrue(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("getPortraitEntranceVisible"))

        assertFalse(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("isInitialized"))
        assertFalse(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("isActive"))
        assertFalse(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("a"))
        assertFalse(PlayerPortraitControlPolicy.isStoryServiceBooleanMethod("performClick"))
    }

    @Test
    fun composableMethodSignatureDetectsVoidComposerInt() {
        assertTrue(
            PlayerPortraitControlPolicy.isComposableMethodSignature(
                returnTypeName = "void",
                parameterTypeNames = listOf("androidx.compose.runtime.Composer", "int"),
            ),
        )
        assertTrue(
            PlayerPortraitControlPolicy.isComposableMethodSignature(
                returnTypeName = "void",
                parameterTypeNames = listOf("androidx.compose.ui.Modifier", "androidx.compose.runtime.Composer", "int", "int"),
            ),
        )

        assertFalse(
            PlayerPortraitControlPolicy.isComposableMethodSignature(
                returnTypeName = "boolean",
                parameterTypeNames = listOf("androidx.compose.runtime.Composer", "int"),
            ),
        )
        assertFalse(
            PlayerPortraitControlPolicy.isComposableMethodSignature(
                returnTypeName = "void",
                parameterTypeNames = listOf("java.lang.String"),
            ),
        )
    }

    @Test
    fun storyFunction2MatcherTargetsOnlyStoryUiSlots() {
        assertTrue(
            PlayerPortraitControlPolicy.isStoryFunction2Method(
                methodName = "getStoryControlUi",
                returnTypeName = "kotlin.jvm.functions.Function2",
            ),
        )
        assertTrue(
            PlayerPortraitControlPolicy.isStoryFunction2Method(
                methodName = "fullscreenControlUi",
                returnTypeName = "kotlin.jvm.functions.Function2",
            ),
        )

        assertFalse(
            PlayerPortraitControlPolicy.isStoryFunction2Method(
                methodName = "getStoryControlUi",
                returnTypeName = "java.lang.Object",
            ),
        )
        assertFalse(
            PlayerPortraitControlPolicy.isStoryFunction2Method(
                methodName = "a",
                returnTypeName = "kotlin.jvm.functions.Function2",
            ),
        )
    }

    @Test
    fun initialConfigConstructorRequiresLongAndBooleanPrefix() {
        assertTrue(
            PlayerPortraitControlPolicy.isStoryInitialConfigConstructor(
                listOf("java.lang.Long", "boolean"),
            ),
        )
        assertTrue(
            PlayerPortraitControlPolicy.isStoryInitialConfigConstructor(
                listOf("java.lang.Long", "boolean", "int", "kotlin.jvm.internal.DefaultConstructorMarker"),
            ),
        )

        assertFalse(
            PlayerPortraitControlPolicy.isStoryInitialConfigConstructor(
                listOf("java.lang.Object", "boolean"),
            ),
        )
        assertFalse(
            PlayerPortraitControlPolicy.isStoryInitialConfigConstructor(
                listOf("java.lang.Long", "java.lang.String"),
            ),
        )
    }

    @Test
    fun protectedContainersIncludeComposeView() {
        assertTrue(
            PlayerPortraitControlPolicy.isProtectedContainerClassName("androidx.compose.ui.platform.AndroidComposeView"),
        )
        assertTrue(
            PlayerPortraitControlPolicy.isProtectedContainerClassName("androidx.compose.ui.platform.ComposeView"),
        )
        assertTrue(
            PlayerPortraitControlPolicy.isProtectedContainerClassName("android.view.ViewGroup"),
        )
        assertFalse(
            PlayerPortraitControlPolicy.isProtectedContainerClassName("com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullStoryWidget"),
        )
    }
}
