package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfterMethod

class SearchPurifyHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        val cleanHot = ModuleSettings.isSearchHotCleanEnabled(prefs)
        val cleanSuggest = ModuleSettings.isSearchSuggestCleanEnabled(prefs)

        if (!cleanHot && !cleanSuggest) return

        if (cleanHot) {
            installSearchSquarePurify()
        }

        if (cleanSuggest) {
            installSearchSuggestPurify()
        }
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
