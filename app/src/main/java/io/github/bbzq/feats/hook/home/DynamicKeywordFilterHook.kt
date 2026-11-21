package io.github.bbzq.feats.hook.home

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.hook.network.hookJavaMoss
import io.github.bbzq.feats.hook.network.hookKotlinMoss

internal fun RoamingEnv.hookDynamicKeywordFilter(): Int {
    val enabled = {
        ModuleSettings.isDynamicKeywordFilterEnabled(prefs) &&
            ModuleSettings.getDynamicKeywords(prefs).isNotEmpty()
    }
    var installed = 0

    // Nested list replies: DynAllReply / DynVideoReply -> getDynamicList() -> getListList()
    listOf(
        Triple("dynAll", "com.bapis.bilibili.app.dynamic.v2.DynAllReq", "com.bapis.bilibili.app.dynamic.v2.DynAllReply"),
        Triple("dynVideo", "com.bapis.bilibili.app.dynamic.v2.DynVideoReq", "com.bapis.bilibili.app.dynamic.v2.DynVideoReply"),
    ).forEach { (rpc, reqClass, replyClass) ->
        installed += hookKotlinMoss(K_DYNAMIC_MOSS, rpc, replyClass, enabled = enabled) { reply ->
            filterNestedDynamicListReply(reply, rpc)
        }
        installed += hookJavaMoss(JAVA_DYNAMIC_MOSS, rpc, reqClass, replyClass, enabled = enabled) { reply ->
            filterNestedDynamicListReply(reply, rpc)
        }
    }

    // Direct list replies: DynAllPersonalReply / DynVideoPersonalReply -> getListList()
    listOf(
        Triple("dynAllPersonal", "com.bapis.bilibili.app.dynamic.v2.DynAllPersonalReq", "com.bapis.bilibili.app.dynamic.v2.DynAllPersonalReply"),
        Triple("dynVideoPersonal", "com.bapis.bilibili.app.dynamic.v2.DynVideoPersonalReq", "com.bapis.bilibili.app.dynamic.v2.DynVideoPersonalReply"),
    ).forEach { (rpc, reqClass, replyClass) ->
        installed += hookKotlinMoss(K_DYNAMIC_MOSS, rpc, replyClass, enabled = enabled) { reply ->
            filterDirectDynamicListReply(reply, rpc)
        }
        installed += hookJavaMoss(JAVA_DYNAMIC_MOSS, rpc, reqClass, replyClass, enabled = enabled) { reply ->
            filterDirectDynamicListReply(reply, rpc)
        }
    }

    return installed
}

private fun RoamingEnv.filterNestedDynamicListReply(reply: Any, rpc: String): Any {
    val keywords = ModuleSettings.getDynamicKeywords(prefs)
    if (keywords.isEmpty()) return reply
    val dynList = reply.callMethod("getDynamicList") ?: return reply
    val items = dynList.callMethod("getListList") as? List<*> ?: return reply
    if (items.isEmpty()) return reply

    val kept = items.filterNot { item -> item != null && DynamicKeywordMatcher.matchesItem(item, keywords) != null }
    val removed = items.size - kept.size
    if (removed <= 0) return reply

    val updatedList = rebuildListContainer(dynList, kept) ?: return reply
    val replyBuilder = reply.javaClass.getMethod("toBuilder").invoke(reply) ?: return reply
    val setter = replyBuilder.javaClass.methods.firstOrNull {
        it.name == "setDynamicList" && it.parameterCount == 1 && it.parameterTypes[0].isInstance(updatedList)
    } ?: return reply
    setter.invoke(replyBuilder, updatedList)
    val rebuilt = replyBuilder.javaClass.getMethod("build").invoke(replyBuilder) ?: return reply
    log("DynamicKeywordFilter[$rpc]: filtered $removed / ${items.size} dynamic item(s)")
    return rebuilt
}

private fun RoamingEnv.filterDirectDynamicListReply(reply: Any, rpc: String): Any {
    val keywords = ModuleSettings.getDynamicKeywords(prefs)
    if (keywords.isEmpty()) return reply
    val items = reply.callMethod("getListList") as? List<*> ?: return reply
    if (items.isEmpty()) return reply

    val kept = items.filterNot { item -> item != null && DynamicKeywordMatcher.matchesItem(item, keywords) != null }
    val removed = items.size - kept.size
    if (removed <= 0) return reply

    val rebuilt = rebuildListContainer(reply, kept) ?: return reply
    log("DynamicKeywordFilter[$rpc]: filtered $removed / ${items.size} dynamic item(s)")
    return rebuilt
}

private fun rebuildListContainer(container: Any, kept: List<Any?>): Any? = runCatching {
    val builder = container.javaClass.getMethod("toBuilder").invoke(container) ?: return null
    builder.javaClass.getMethod("clearList").invoke(builder)
    if (kept.isNotEmpty()) {
        builder.javaClass.getMethod("addAllList", Iterable::class.java).invoke(builder, kept)
    }
    builder.javaClass.getMethod("build").invoke(builder)
}.getOrNull()

internal object DynamicKeywordMatcher {
    private const val MAX_FORWARD_DEPTH = 2

    fun matchesItem(item: Any, keywords: List<String>): String? {
        if (keywords.isEmpty()) return null
        val texts = collectItemTexts(item, depth = 0)
        return findMatchedKeyword(texts, keywords)
    }

    fun findMatchedKeyword(texts: List<String>, keywords: List<String>): String? {
        if (texts.isEmpty() || keywords.isEmpty()) return null
        for (keyword in keywords) {
            if (keyword.isBlank()) continue
            if (texts.any { it.contains(keyword, ignoreCase = true) }) {
                return keyword
            }
        }
        return null
    }

    private fun collectItemTexts(item: Any, depth: Int): List<String> {
        if (depth > MAX_FORWARD_DEPTH) return emptyList()
        val out = ArrayList<String>(16)

        val modules = item.callMethod("getModulesList") as? List<*>
        modules?.forEach { module ->
            if (module != null) {
                collectModuleTexts(module, depth, out)
            }
        }

        item.callMethod("getExtend")?.let { extend ->
            out.addNonBlank(extend.callMethod("getUpName") as? String)
            out.addNonBlank(extend.callMethod("getOrigName") as? String)
            collectDescriptionList(extend.callMethod("getDescList") as? List<*>, out)
            collectDescriptionList(extend.callMethod("getOrigDescList") as? List<*>, out)
            extend.callMethod("getOpusSummary")?.let { collectOpusSummaryTexts(it, out) }
        }
        return out
    }

    private fun collectModuleTexts(module: Any, depth: Int, out: MutableList<String>) {
        // 1. Author name
        module.callMethod("getModuleAuthor")?.let { authorModule ->
            out.addNonBlank(authorModule.callMethod("getAuthor")?.callMethod("getName") as? String)
            out.addNonBlank(authorModule.callMethod("getPtimeLabelText") as? String)
        }
        module.callMethod("getModuleAuthorForward")?.let { forwardAuthor ->
            out.addNonBlank(forwardAuthor.callMethod("getTitle")?.callMethod("toString") as? String)
        }

        // 2. Main description text & rich-text segments
        module.callMethod("getModuleDesc")?.let { descModule ->
            out.addNonBlank(descModule.callMethod("getText") as? String)
            collectDescriptionList(descModule.callMethod("getDescList") as? List<*>, out)
        }

        // 3. Title & Opus summary
        module.callMethod("getModuleTitle")?.let { titleModule ->
            out.addNonBlank(titleModule.callMethod("getTitle") as? String)
        }
        module.callMethod("getModuleOpusSummary")?.let { opus ->
            collectOpusSummaryTexts(opus, out)
        }

        // 4. Dynamic body card (archive / article / common / pgc / season / reposted forward)
        module.callMethod("getModuleDynamic")?.let { dynModule ->
            dynModule.callMethod("getDynArchive")?.let { archive ->
                out.addNonBlank(archive.callMethod("getTitle") as? String)
                out.addNonBlank(archive.callMethod("getDesc") as? String)
            }
            dynModule.callMethod("getDynPgc")?.let { pgc ->
                out.addNonBlank(pgc.callMethod("getTitle") as? String)
            }
            dynModule.callMethod("getDynArticle")?.let { article ->
                out.addNonBlank(article.callMethod("getTitle") as? String)
                out.addNonBlank(article.callMethod("getDesc") as? String)
            }
            dynModule.callMethod("getDynCommon")?.let { common ->
                out.addNonBlank(common.callMethod("getTitle") as? String)
                out.addNonBlank(common.callMethod("getDesc") as? String)
            }
            dynModule.callMethod("getDynUgcSeason")?.let { season ->
                out.addNonBlank(season.callMethod("getTitle") as? String)
            }
            if (depth < MAX_FORWARD_DEPTH) {
                dynModule.callMethod("getDynForward")?.callMethod("getItem")?.let { origItem ->
                    out.addAll(collectItemTexts(origItem, depth + 1))
                }
            }
        }

        // 5. Embedded additional cards (goods / commerce / common / ugc / up / pgc)
        module.callMethod("getModuleAdditional")?.let { additional ->
            additional.callMethod("getGoods")?.let { goods ->
                out.addNonBlank(goods.callMethod("getRcmdDesc") as? String)
                (goods.callMethod("getGoodsItemsList") as? List<*>)?.forEach { goodsItem ->
                    if (goodsItem != null) {
                        out.addNonBlank(goodsItem.callMethod("getTitle") as? String)
                        out.addNonBlank(goodsItem.callMethod("getBrief") as? String)
                        out.addNonBlank(goodsItem.callMethod("getAdMark") as? String)
                        out.addNonBlank(goodsItem.callMethod("getAppName") as? String)
                    }
                }
            }
            additional.callMethod("getCommon")?.let { common ->
                out.addNonBlank(common.callMethod("getTitle") as? String)
                out.addNonBlank(common.callMethod("getDesc1") as? String)
                out.addNonBlank(common.callMethod("getDesc2") as? String)
                out.addNonBlank(common.callMethod("getHeadText") as? String)
            }
            additional.callMethod("getUgc")?.let { ugc ->
                out.addNonBlank(ugc.callMethod("getTitle") as? String)
                out.addNonBlank(ugc.callMethod("getDescSecond") as? String)
            }
            additional.callMethod("getUp")?.let { up ->
                out.addNonBlank(up.callMethod("getTitle") as? String)
                out.addNonBlank(up.callMethod("getDescText1") as? String)
            }
            additional.callMethod("getPgc")?.let { pgc ->
                out.addNonBlank(pgc.callMethod("getTitle") as? String)
                out.addNonBlank(pgc.callMethod("getDescText1") as? String)
            }
        }
    }

    private fun collectOpusSummaryTexts(opus: Any, out: MutableList<String>) {
        collectParagraphTexts(opus.callMethod("getTitle"), out)
        collectParagraphTexts(opus.callMethod("getSummary"), out)
        (opus.callMethod("getSummariesList") as? List<*>)?.forEach { paragraph ->
            collectParagraphTexts(paragraph, out)
        }
    }

    private fun collectParagraphTexts(paragraph: Any?, out: MutableList<String>) {
        val nodes = paragraph?.callMethod("getText")?.callMethod("getNodesList") as? List<*> ?: return
        val combined = buildString {
            nodes.forEach { node ->
                val word = node?.callMethod("getWord")?.callMethod("getWords") as? String
                if (!word.isNullOrEmpty()) append(word)
            }
        }
        out.addNonBlank(combined)
    }

    private fun collectDescriptionList(descriptions: List<*>?, out: MutableList<String>) {
        if (descriptions.isNullOrEmpty()) return
        val combined = buildString {
            descriptions.forEach { entry ->
                val text = entry?.callMethod("getText") as? String
                if (!text.isNullOrEmpty()) append(text)
            }
        }
        out.addNonBlank(combined)
    }

    private fun MutableList<String>.addNonBlank(value: String?) {
        val trimmed = value?.trim()
        if (!trimmed.isNullOrEmpty()) add(trimmed)
    }
}

private const val K_DYNAMIC_MOSS = "com.bapis.bilibili.app.dynamic.v2.KDynamicMoss"
private const val JAVA_DYNAMIC_MOSS = "com.bapis.bilibili.app.dynamic.v2.DynamicMoss"
