package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier

internal fun RoamingEnv.hookComposeComments(): Int {
    var installed = 0
    if (ModuleSettings.isCommentNoVoteEnabled(prefs)) {
        val renderer = classLoader.findClassOrNull("kntr.common.comment.card.renderer.comment.CommentVoteRenderer")
        renderer?.declaredMethods.orEmpty().filter {
            it.returnType == Void.TYPE && !Modifier.isAbstract(it.modifiers) &&
                it.parameterTypes.any { parameter -> parameter.name == "androidx.compose.runtime.Composer" }
        }.forEach { method ->
            if (hookBefore(method) { param ->
                if (ModuleSettings.isCommentNoVoteEnabled(prefs)) param.result = null
            }) installed++
        }
    }
    if (ModuleSettings.isCommentNoFollowEnabled(prefs)) {
        val model = classLoader.findClassOrNull("kntr.common.comment.card.model.comment.CommentUserInfoRendererModel")
        model?.let(CommentComposePolicy::followConstructor)?.let { constructor ->
            if (hookBefore(constructor) { param ->
                if (ModuleSettings.isCommentNoFollowEnabled(prefs) && CommentComposePolicy.isFollow(param.args.getOrNull(1))) {
                    param.args[1] = null
                }
            }) installed++
        }
    }
    if (ModuleSettings.isCommentNoQuickReplyEnabled(prefs)) {
        val action = classLoader.findClassOrNull("kntr.common.comment.card.action.CardAction")
        val types = action?.let(CommentComposePolicy::quickReplyTypes).orEmpty()
        if (action != null && types.isNotEmpty()) {
            (listOf("CardStore") + ('a'..'z').map(Char::toString)).mapNotNull {
                classLoader.findClassOrNull("kntr.common.comment.card.store.$it")
            }.flatMap { owner ->
                owner.declaredMethods.filter {
                    !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) &&
                        it.returnType == Void.TYPE && it.parameterCount == 1 &&
                        it.parameterTypes[0] != Any::class.java && it.parameterTypes[0].isAssignableFrom(action)
                }
            }.distinctBy { it.toGenericString() }.forEach { method ->
                if (hookBefore(method) { param ->
                    if (!ModuleSettings.isCommentNoQuickReplyEnabled(prefs)) return@hookBefore
                    val value = param.args.firstOrNull() ?: return@hookBefore
                    if (CommentComposePolicy.isQuickReply(value, types)) param.result = null
                }) installed++
            }
        }
    }
    log("Compose comment hooks installed: $installed")
    return installed
}
