package io.github.bbzq.feats.hook

import android.net.Uri
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.getObjectField
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredVideoCommentSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.Unit
import org.json.JSONObject

class VideoCommentHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return

        val disableComment = ModuleSettings.isCommentDisableEnabled(prefs)
        val noQuickReply = ModuleSettings.isCommentNoQuickReplyEnabled(prefs)
        val noVote = ModuleSettings.isCommentNoVoteEnabled(prefs)
        val noFollow = ModuleSettings.isCommentNoFollowEnabled(prefs)
        val noSearch = ModuleSettings.isCommentNoSearchEnabled(prefs)
        val noEmptyPage = ModuleSettings.isCommentNoEmptyPageEnabled(prefs)
        val cleanupActive = currentReplyCleanupOptions().active

        if (!disableComment && !noQuickReply && !noVote && !noFollow && !noSearch && !noEmptyPage && !cleanupActive) {
            log("startHook: VideoComment all features disabled, zero hooks installed")
            return
        }

        val kotlinHooks = (if (cleanupActive || noSearch || noEmptyPage || noVote) hookKotlinReplyCleanup() else 0) + env.hookComposeComments()
        val symbols = env.symbols?.videoComment?.restore(classLoader)
        if (symbols == null) {
            log("startHook: VideoComment symbols unavailable, Kotlin hooks=$kotlinHooks")
            return
        }

        var count = kotlinHooks
        if (disableComment) count += hookDisableComment(symbols)
        if (noQuickReply) count += hookQuickReply(symbols)
        if (noVote) count += hookVoteWidgets(symbols)
        if (noFollow) count += hookFollowWidgets(symbols)
        if (noSearch) count += hookSearchUrls(symbols)
        if (noEmptyPage) count += hookEmptyPage(symbols)
        if (cleanupActive) count += hookMainListCleanup(symbols)
        log("startHook: VideoComment, methods=$count")
        isInstalled = true
    }

    private fun hookDisableComment(symbols: RestoredVideoCommentSymbols): Int {
        symbols.disableCommentConstructors.forEach { ctor ->
            env.hookBefore(ctor) { param ->
                runCatching {
                    if (!ModuleSettings.isCommentDisableEnabled(prefs)) return@runCatching
                    val listIndex = param.args.indexOfFirst { it is List<*> }
                    if (listIndex < 0) return@runCatching
                    val items = param.args[listIndex] as? List<*> ?: return@runCatching
                    param.args[listIndex] = items.filterNot { item ->
                        val name = item?.javaClass?.name.orEmpty()
                        name.contains("CommentTabPageProvider") || name.contains("CommentTab")
                    }
                }.onFailure {
                    log("VideoComment disable comment hook failed", it)
                }
            }
        }
        return symbols.disableCommentConstructors.size
    }

    private fun hookQuickReply(symbols: RestoredVideoCommentSymbols): Int {
        val viewModelCount = hookQuickReplyByViewModel(symbols.quickReplyViewModelMethods)
        val dialogCount = hookQuickReplyDialogFlow(symbols.quickReplyDialogMethods)
        return viewModelCount + dialogCount
    }

    private fun hookQuickReplyByViewModel(methods: List<Method>): Int {
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    if (!ModuleSettings.isCommentNoQuickReplyEnabled(prefs)) return@runCatching
                    val action = if (param.args.size >= 2 && param.args[1] == false) {
                        param.thisObject
                    } else {
                        param.args.firstOrNull()
                    } ?: return@runCatching
                    if (!action.shouldBlockPublishDialogAction()) return@runCatching
                    param.result = null
                }.onFailure {
                    log("VideoComment quick reply hook failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
        }
        return methods.size
    }

    private fun hookQuickReplyDialogFlow(methods: List<Method>): Int {
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    if (!ModuleSettings.isCommentNoQuickReplyEnabled(prefs)) return@runCatching
                    val intent = param.args.firstOrNull() ?: return@runCatching
                    if (!intent.shouldBlockQuickReplyDialog()) return@runCatching
                    param.result = Unit
                }.onFailure {
                    log("VideoComment quick reply dialog hook failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
        }
        return methods.size
    }

    private fun hookVoteWidgets(symbols: RestoredVideoCommentSymbols): Int {
        symbols.voteWidgetMethods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    if (ModuleSettings.isCommentNoVoteEnabled(prefs)) {
                        param.result = null
                    }
                }.onFailure {
                    log("VideoComment vote hook failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
        }
        return symbols.voteWidgetMethods.size
    }

    private fun hookFollowWidgets(symbols: RestoredVideoCommentSymbols): Int {
        var count = 0

        symbols.followWidgetMethods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    if (ModuleSettings.isCommentNoFollowEnabled(prefs)) {
                        param.result = null
                    }
                }.onFailure {
                    log("VideoComment follow widget hook failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
            count += 1
        }

        symbols.headerDecorativeMethods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    if (!ModuleSettings.isCommentNoFollowEnabled(prefs)) return@runCatching
                    @Suppress("UNCHECKED_CAST")
                    val items = param.args[0] as? List<Any?> ?: return@runCatching
                    items.forEach { item ->
                        item?.javaClass?.declaredFields?.forEach { field ->
                            field.isAccessible = true
                            val value = runCatching { field.get(item) }.getOrNull() ?: return@forEach
                            if (value.javaClass.simpleName.contains("Follow", ignoreCase = true)) {
                                runCatching { field.set(item, null) }
                            }
                        }
                    }
                }.onFailure {
                    log("VideoComment decorative follow hook failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
            count += 1
        }

        return count
    }

    private fun hookSearchUrls(symbols: RestoredVideoCommentSymbols): Int {
        val method = symbols.searchUrlsMethod ?: return 0

        env.hookAfter(method) { param ->
            runCatching {
                if (KotlinMossBridge.isRaw()) return@runCatching
                if (!ModuleSettings.isCommentNoSearchEnabled(prefs)) return@runCatching
                val urls = param.result as? Map<*, *> ?: return@runCatching
                val kept = urls.filterValues { value ->
                    val schema = value?.let { noArgMethod(it.javaClass, "getAppUrlSchema")?.invoke(it) as? String }
                    schema == null || !isSearchUrl(schema)
                }
                if (kept.size != urls.size) param.result = kept
            }.onFailure {
                log("VideoComment search url hook failed", it)
            }
        }
        return 1
    }

    private fun hookEmptyPage(symbols: RestoredVideoCommentSymbols): Int {
        symbols.emptyPageHooks.forEach { hook ->
            env.hookAfter(hook.getEmptyPage) { param ->
                runCatching {
                    if (ModuleSettings.isCommentNoEmptyPageEnabled(prefs)) {
                        param.result = hook.defaultInstance
                    }
                }.onFailure {
                    log("VideoComment empty page hook failed at ${hook.getEmptyPage.declaringClass.name}.${hook.getEmptyPage.name}", it)
                }
            }
        }
        return symbols.emptyPageHooks.size
    }

    private fun hookMainListCleanup(symbols: RestoredVideoCommentSymbols): Int {
        symbols.mainListOnNextMethods.forEach { onNext ->
            env.hookBefore(onNext) { param ->
                runCatching {
                    val reply = param.args.firstOrNull() ?: return@runCatching
                    val options = currentReplyCleanupOptions()
                    if (!options.active) return@runCatching
                    cleanupReplyPayload(reply, options)
                }.onFailure {
                    log("VideoComment main list cleanup failed at ${onNext.declaringClass.name}.${onNext.name}", it)
                }
            }
        }
        return symbols.mainListOnNextMethods.size
    }

    private fun hookKotlinReplyCleanup(): Int = listOf(
        "mainList" to "MainListReply",
        "detailList" to "DetailListReply",
        "dialogList" to "DialogListReply",
        "foldList" to "FoldListResp",
        "hotspotPage" to "HotspotPageResp",
        "replyInfo" to "ReplyInfoReply",
    ).sumOf { (rpc, reply) ->
        val enabled = {
            currentReplyCleanupOptions().active || ModuleSettings.isCommentNoSearchEnabled(prefs) ||
                ModuleSettings.isCommentNoEmptyPageEnabled(prefs) || ModuleSettings.isCommentNoVoteEnabled(prefs)
        }
        val cleanup: (Any) -> Any = { payload ->
            cleanupReplyPayload(payload, currentReplyCleanupOptions(), ::cleanupKotlinReplyDecorations, rpc == "replyInfo")
            payload
        }
        val javaHooks = if (rpc == "mainList") 0 else env.hookJavaMoss(
            "com.bapis.bilibili.main.community.reply.v1.ReplyMoss", rpc,
            "com.bapis.bilibili.main.community.reply.v1.${rpc.replaceFirstChar(Char::uppercaseChar)}Req",
            "com.bapis.bilibili.main.community.reply.v1.$reply", enabled,
        ) { payload -> copyProtobufReply(payload) { cleanup(it) } }
        javaHooks + env.hookKotlinMoss(
            "com.bapis.bilibili.main.community.reply.v1.KReplyMoss",
            rpc,
            "com.bapis.bilibili.main.community.reply.v1.$reply",
            enabled = enabled,
            requestName = if (rpc == "mainList") "com.bapis.bilibili.main.community.reply.v1.MainListReq" else null,
            requestFilter = if (rpc == "mainList") ::disableSearchUnderline else null,
            filter = cleanup,
        )
    }

    private fun disableSearchUnderline(request: Any): Any {
        if (!ModuleSettings.isCommentNoSearchEnabled(prefs)) return request
        val extra = request.javaClass.getMethod("getExtra").invoke(request) as? String ?: return request
        val json = if (extra.isBlank()) JSONObject() else JSONObject(extra)
        if (json.has("disable_underline")) return request
        val builder = newProtobufBuilder(request)
        builder.javaClass.getMethod("setExtra", String::class.java).apply { isAccessible = true }
            .invoke(builder, json.put("disable_underline", true).toString())
        return builder.javaClass.getMethod("build").apply { isAccessible = true }.invoke(builder) ?: request
    }

    private fun isSearchUrl(value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        uri.scheme.equals("bilibili", ignoreCase = true) && uri.host.equals("search", ignoreCase = true)
    }.getOrDefault(false)

    private fun cleanupKotlinReplyDecorations(reply: Any) {
        if (ModuleSettings.isCommentNoEmptyPageEnabled(prefs) && noArgMethod(reply.javaClass, "hasSubjectControl")?.invoke(reply) == true) {
            val control = noArgMethod(reply.javaClass, "getSubjectControl")?.invoke(reply)
            if (control != null && noArgMethod(control.javaClass, "hasEmptyPage")?.invoke(control) == true) {
                noArgMethod(control.javaClass, "clearEmptyPage")?.invoke(control)
            }
        }
        if (noArgMethod(reply.javaClass, "hasContent")?.invoke(reply) != true) return
        val content = noArgMethod(reply.javaClass, "getContent")?.invoke(reply) ?: return
        if (ModuleSettings.isCommentNoVoteEnabled(prefs) && noArgMethod(content.javaClass, "hasVote")?.invoke(content) == true) {
            noArgMethod(content.javaClass, "clearVote")?.invoke(content)
        }
        if (!ModuleSettings.isCommentNoSearchEnabled(prefs)) return
        val urls = noArgMethod(content.javaClass, "getUrlsMap")?.invoke(content) as? Map<*, *> ?: return
        val kept = urls.filterValues { url ->
            val schema = url?.let { noArgMethod(it.javaClass, "getAppUrlSchema")?.invoke(it) as? String }
            schema == null || !isSearchUrl(schema)
        }
        if (kept.size == urls.size) return
        val builder = newProtobufBuilder(content)
        builder.javaClass.getMethod("clearUrls").apply { isAccessible = true }.invoke(builder)
        builder.javaClass.getMethod("putAllUrls", Map::class.java).apply { isAccessible = true }.invoke(builder, kept)
        val updated = builder.javaClass.getMethod("build").apply { isAccessible = true }.invoke(builder) ?: return
        reply.javaClass.getDeclaredMethod("setContent", content.javaClass).apply { isAccessible = true }
            .invoke(reply, updated)
    }

    private fun currentReplyCleanupOptions(): ReplyCleanupOptions {
        val keywords = if (ModuleSettings.isCommentKeywordFilterEnabled(prefs)) {
            ModuleSettings.parseCommentKeywords(ModuleSettings.getCommentKeywordsText(prefs))
        } else {
            emptyList()
        }
        val minLevel = if (ModuleSettings.isCommentMinLevelEnabled(prefs)) {
            ModuleSettings.getCommentMinLevel(prefs)
        } else {
            0
        }
        return ReplyCleanupOptions(
            removeQoe = ModuleSettings.isCommentNoQoeEnabled(prefs),
            removeOperation = ModuleSettings.isCommentNoOperationEnabled(prefs),
            removeHotspot = ModuleSettings.isCommentNoHotspotEnabled(prefs),
            keywords = keywords,
            minLevel = minLevel,
        )
    }

    private fun cleanupReplyPayload(
        reply: Any, options: ReplyCleanupOptions, decorate: ((Any) -> Unit)? = null, removeSingleReply: Boolean = false,
    ) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val pending = ArrayDeque<Any>()
        pending += reply

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            decorate?.invoke(current)

            val methods = replyCleanupMethods.getOrPut(current.javaClass) {
                resolveReplyCleanupMethods(current.javaClass)
            }
            if (options.removeQoe) {
                methods.clearQoe?.let { runCatching { it.invoke(current) } }
            }
            if (options.removeOperation) {
                methods.clearOperations.forEach { method ->
                    runCatching { method.invoke(current) }
                }
            }

            if (options.removeHotspot) {
                removeHotspotCards(current, methods)
            }

            methods.singleReplies.forEach { getter ->
                val fieldName = getter.name.removePrefix("get")
                if (noArgMethod(current.javaClass, "has$fieldName")?.invoke(current) != true) return@forEach
                val child = getter.invoke(current) ?: return@forEach
                val clear = noArgMethod(current.javaClass, "clear$fieldName")
                val removable = fieldName in setOf("UpTop", "AdminTop", "VoteTop") ||
                    (removeSingleReply && current === reply && fieldName == "Reply")
                if (removable && options.filtersActive && clear != null && shouldRemoveReply(child, options)) {
                    clear.invoke(current)
                } else {
                    pending += child
                }
            }

            methods.replyLists.forEach { (getter, remove) ->
                val children = getter.invoke(current) as? List<*> ?: return@forEach
                for (index in children.indices.reversed()) {
                    val child = children[index] ?: continue
                    if (options.filtersActive && remove != null && shouldRemoveReply(child, options)) {
                        remove.invoke(current, index)
                    } else {
                        pending += child
                    }
                }
            }
        }
    }

    private fun removeHotspotCards(target: Any, methods: ReplyCleanupMethods) {
        val getCards = methods.getMixedCardsList ?: return
        val removeCard = methods.removeMixedCards ?: return
        val cards = runCatching { getCards.invoke(target) as? List<*> }.getOrNull() ?: return
        for (index in cards.indices.reversed()) {
            val card = cards[index] ?: continue
            val itemCase = noArgMethod(card.javaClass, "getItemCase")
                ?.let { runCatching { it.invoke(card) }.getOrNull() }
            if (itemCase?.toString() == HOTSPOT_ITEM_CASE) {
                runCatching { removeCard.invoke(target, index) }
            }
        }
    }

    private fun shouldRemoveReply(node: Any, options: ReplyCleanupOptions): Boolean {
        if (options.keywords.isNotEmpty()) {
            val message = readReplyMessage(node)
            if (message != null && options.keywords.any { message.contains(it, ignoreCase = true) }) {
                return true
            }
        }
        if (options.minLevel > 0) {
            val level = readReplyMemberLevel(node)
            if (level != null && level < options.minLevel) {
                return true
            }
        }
        return false
    }

    private fun readReplyMessage(node: Any): String? {
        val getContent = noArgMethod(node.javaClass, "getContent") ?: return null
        val content = runCatching { getContent.invoke(node) }.getOrNull() ?: return null
        val getMessage = noArgMethod(content.javaClass, "getMessage") ?: return null
        return runCatching { getMessage.invoke(content) as? String }.getOrNull()
    }

    private fun readReplyMemberLevel(node: Any): Int? {
        if (noArgMethod(node.javaClass, "hasMember")?.invoke(node) == false) return null
        val getMember = noArgMethod(node.javaClass, "getMember") ?: return null
        val member = runCatching { getMember.invoke(node) }.getOrNull() ?: return null
        // reply.v1 Member exposes the commenter level via level_info.current_level;
        // fall back to a flat getLevel() when the schema differs.
        if (noArgMethod(member.javaClass, "hasLevel")?.invoke(member) != false) {
            intFromNoArg(member, "getLevel")?.let { return it }
        }
        if (noArgMethod(member.javaClass, "hasLevelInfo")?.invoke(member) == false) return null
        val getLevelInfo = noArgMethod(member.javaClass, "getLevelInfo") ?: return null
        val levelInfo = runCatching { getLevelInfo.invoke(member) }.getOrNull() ?: return null
        if (noArgMethod(levelInfo.javaClass, "hasCurrentLevel")?.invoke(levelInfo) == false) return null
        return intFromNoArg(levelInfo, "getCurrentLevel")
    }

    private fun intFromNoArg(target: Any, name: String): Int? {
        val method = noArgMethod(target.javaClass, name) ?: return null
        return runCatching { (method.invoke(target) as? Number)?.toInt() }.getOrNull()
    }

    private fun noArgMethod(type: Class<*>, name: String): Method? =
        noArgMethodCache.getOrPut(type.name + "#" + name) {
            MethodLookup(
                type.declaredMethods
                    .firstOrNull { it.name == name && it.parameterCount == 0 }
                    ?.apply { isAccessible = true },
            )
        }.method

    private fun resolveReplyCleanupMethods(type: Class<*>): ReplyCleanupMethods =
        ReplyCleanupMethods(
            clearQoe = type.declaredMethods
                .firstOrNull { it.name == "clearQoe" && it.parameterCount == 0 }
                ?.apply { isAccessible = true },
            clearOperations = type.declaredMethods
                .filter { it.name in CLEAR_OPERATION_METHOD_NAMES && it.parameterCount == 0 }
                .distinctBy(Method::toGenericString)
                .onEach { it.isAccessible = true }
                .toList(),
            getMixedCardsList = type.declaredMethods
                .firstOrNull { it.name == "getMixedCardsList" && it.parameterCount == 0 && List::class.java.isAssignableFrom(it.returnType) },
            removeMixedCards = type.declaredMethods
                .firstOrNull {
                    it.name == "removeMixedCards" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                ?.apply { isAccessible = true },
            replyLists = type.declaredMethods.filter { method ->
                if (Modifier.isStatic(method.modifiers) || method.parameterCount != 0 ||
                    !method.name.startsWith("get") || !method.name.endsWith("List") ||
                    !List::class.java.isAssignableFrom(method.returnType)) false else {
                    val element = (method.genericReturnType as? ParameterizedType)?.actualTypeArguments?.singleOrNull()
                    method.name in setOf("getRepliesList", "getTopRepliesList") ||
                        (element as? Class<*>)?.name == "com.bapis.bilibili.main.community.reply.v1.ReplyInfo"
                }
            }.map { getter ->
                getter.isAccessible = true
                getter to type.declaredMethods.singleOrNull {
                    it.name == "remove${getter.name.removePrefix("get").removeSuffix("List")}" &&
                        it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                }?.apply { isAccessible = true }
            },
            singleReplies = type.declaredMethods.filter {
                !Modifier.isStatic(it.modifiers) && it.name.startsWith("get") && it.parameterCount == 0 &&
                    it.returnType.name == "com.bapis.bilibili.main.community.reply.v1.ReplyInfo"
            }.onEach { it.isAccessible = true },
        )

    private fun Class<*>.isCommentActionType(): Boolean {
        val simpleName = simpleName
        val className = name
        return className.contains(".comment3.", ignoreCase = true) &&
            (simpleName.contains("Action", ignoreCase = true) ||
                simpleName.contains("Intent", ignoreCase = true))
    }

    private fun Any.shouldBlockPublishDialogAction(): Boolean =
        resolvePublishDialogIntent()?.shouldBlockQuickReplyDialog() == true

    private fun Any.shouldBlockQuickReplyDialog(): Boolean {
        if (!isQuickReplyDialogIntentType()) return false

        val fields = publishDialogIntentFields.getOrPut(javaClass) { resolvePublishDialogIntentFields(javaClass) }
        val isReply = runCatching { getObjectField("f184778b") as? Boolean }.getOrNull()
            ?: runCatching { fields.isReply?.get(this) as? Boolean }.getOrNull()
            ?: false
        if (!isReply) return false

        val posName = runCatching { getObjectField("f184787k")?.toString().orEmpty() }.getOrDefault("")
            .ifBlank {
                runCatching { fields.pos?.get(this)?.toString().orEmpty() }.getOrDefault("")
            }
        if (posName.isBlank()) return true

        return when {
            posName.contains("REPLY_BUTTON", ignoreCase = true) -> false
            posName.contains("BAR", ignoreCase = true) -> false
            posName.contains("MORE_MENU", ignoreCase = true) -> false
            posName.contains("INPUT", ignoreCase = true) -> false
            posName.contains("CARD", ignoreCase = true) -> true
            posName.contains("ITEM", ignoreCase = true) -> true
            posName.contains("TEXT", ignoreCase = true) -> true
            posName.contains("REPLY", ignoreCase = true) -> true
            else -> true
        }
    }

    private fun Any.isQuickReplyDialogIntentType(): Boolean {
        val className = javaClass.name
        return className.endsWith("PublishDialogIntent", ignoreCase = true) ||
            className.contains("PublishDialogIntent", ignoreCase = true) ||
            (
                className.contains(".comment3.", ignoreCase = true) &&
                    javaClass.simpleName.contains("DialogIntent", ignoreCase = true) &&
                    javaClass.declaredFields.any { field ->
                        field.type.isEnum || field.type == Boolean::class.javaPrimitiveType
                    }
            )
    }

    private fun Any.resolvePublishDialogIntent(): Any? {
        if (isQuickReplyDialogIntentType()) return this
        val field = publishDialogActionIntentFields.getOrPut(javaClass) {
            FieldLookup(
                javaClass.declaredFields.firstOrNull { field ->
                    field.type.isQuickReplyDialogIntentType()
                }?.apply { isAccessible = true },
            )
        }.field ?: return null
        return runCatching { field.get(this) }.getOrNull()
    }

    private fun Class<*>.isQuickReplyDialogIntentType(): Boolean {
        val className = name
        return className.endsWith("PublishDialogIntent", ignoreCase = true) ||
            className.contains("PublishDialogIntent", ignoreCase = true) ||
            (
                className.contains(".comment3.", ignoreCase = true) &&
                    simpleName.contains("DialogIntent", ignoreCase = true) &&
                    declaredFields.any { field ->
                        field.type.isEnum || field.type == Boolean::class.javaPrimitiveType
                    }
            )
    }

    private fun resolvePublishDialogIntentFields(type: Class<*>): PublishDialogIntentFields {
        val fields = type.declaredFields.onEach { it.isAccessible = true }
        val booleanFields = fields.filter { it.type == Boolean::class.javaPrimitiveType }
        return PublishDialogIntentFields(
            isReply = booleanFields.getOrNull(1),
            pos = fields.firstOrNull { it.type.isEnum && it.type.simpleName == "Pos" },
        )
    }

    private companion object {
        private const val HOTSPOT_ITEM_CASE = "HOTSPOT"
        private val CLEAR_OPERATION_METHOD_NAMES = setOf("clearOperation", "clearOperationV2")
        private val replyCleanupMethods = ConcurrentHashMap<Class<*>, ReplyCleanupMethods>()
        private val noArgMethodCache = ConcurrentHashMap<String, MethodLookup>()
        private val publishDialogActionIntentFields = ConcurrentHashMap<Class<*>, FieldLookup>()
        private val publishDialogIntentFields = ConcurrentHashMap<Class<*>, PublishDialogIntentFields>()
    }
}

private data class FieldLookup(val field: Field?)

private data class MethodLookup(val method: Method?)

private data class PublishDialogIntentFields(
    val isReply: Field?,
    val pos: Field?,
)

private data class ReplyCleanupOptions(
    val removeQoe: Boolean,
    val removeOperation: Boolean,
    val removeHotspot: Boolean,
    val keywords: List<String>,
    val minLevel: Int,
) {
    val filtersActive: Boolean get() = keywords.isNotEmpty() || minLevel > 0
    val active: Boolean get() = removeQoe || removeOperation || removeHotspot || filtersActive
}

private data class ReplyCleanupMethods(
    val clearQoe: Method?,
    val clearOperations: List<Method>,
    val getMixedCardsList: Method?,
    val removeMixedCards: Method?,
    val replyLists: List<Pair<Method, Method?>>,
    val singleReplies: List<Method>,
)
