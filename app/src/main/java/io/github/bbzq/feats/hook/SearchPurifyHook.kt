package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.hookAfterMethod

class SearchPurifyHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        val cleanHot = ModuleSettings.isSearchHotCleanEnabled(prefs)
        val cleanSuggest = ModuleSettings.isSearchSuggestCleanEnabled(prefs)
        val blockAds = ModuleSettings.isSearchResultAdBlockEnabled(prefs)

        if (!cleanHot && !cleanSuggest && !blockAds) return

        if (cleanHot) {
            installSearchSquarePurify()
        }

        if (cleanSuggest) {
            installSearchSuggestPurify()
        }

        if (blockAds) {
            installSearchResultAdBlock()
        }
    }

    private fun installSearchResultAdBlock() {
        runCatching {
            val responseClass = classLoader.loadClass("com.bapis.bilibili.polymer.app.search.v1.SearchAllResponse")
            env.hookAfterMethod(responseClass, "getItemList") { param ->
                val items = param.result as? List<*> ?: return@hookAfterMethod
                if (items.none(::isAdItem)) return@hookAfterMethod
                param.result = items.filterNot(::isAdItem)
            }
            log("SearchPurifyHook: SearchAllResponse.getItemList hook installed")
        }.onFailure {
            log("SearchPurifyHook: failed to hook SearchAllResponse.getItemList", it)
        }
    }

    private fun isAdItem(item: Any?): Boolean {
        val itemCase = item?.callMethod("getCardItemCase")?.toString()
        return itemCase == "CM" || itemCase == "PURCHASE"
    }

    private fun installSearchSquarePurify() {
        runCatching {
            val squareTypeClass = classLoader.loadClass("com.bilibili.search2.api.SearchSquareType")
            env.hookAfterMethod(squareTypeClass, "getType") { param ->
                val type = param.result as? String ?: return@hookAfterMethod
                if (type == "trending" || type == "recommend") {
                    param.result = "clean_filtered"
                }
            }
            log("SearchPurifyHook: SearchSquareType.getType hook installed")
        }.onFailure {
            log("SearchPurifyHook: failed to hook SearchSquareType.getType", it)
        }
    }

    private fun installSearchSuggestPurify() {
        runCatching {
            val suggestClass = classLoader.loadClass("com.bilibili.search2.api.SearchSuggest")
            env.hookAfterMethod(suggestClass, "getList") { param ->
                param.result = emptyList<Any>()
            }
            log("SearchPurifyHook: SearchSuggest.getList hook installed")
        }.onFailure {
            log("SearchPurifyHook: failed to hook SearchSuggest.getList", it)
        }
    }
}
