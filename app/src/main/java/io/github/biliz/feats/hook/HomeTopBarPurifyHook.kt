package io.github.biliz.feats.hook

import android.widget.TextView
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allFields
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore

class HomeTopBarPurifyHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        val hideGameMenu = ModuleSettings.isHideHomeTopBarPromotionEnabled(prefs)
        val hideSearchDefaultWord = ModuleSettings.isHideHomeSearchDefaultWordEnabled(prefs)
        if (!hideGameMenu && !hideSearchDefaultWord) {
            log("startHook: HomeTopBarPurify disabled")
            return
        }

        val count =
            (if (hideGameMenu) hookGameMenuItem() else 0) +
                (if (hideSearchDefaultWord) hookSearchDefaultWord() else 0)
        if (count == 0) {
            log("startHook: HomeTopBarPurify no hook point found")
        } else {
            log("startHook: HomeTopBarPurify methods=$count")
        }
    }

    private fun hookGameMenuItem(): Int {
        val method = env.symbols?.homeTopBar?.restore(classLoader)?.gameMenuMethod ?: return 0
        env.hookBefore(method) { param ->
            if (!ModuleSettings.isHideHomeTopBarPromotionEnabled(prefs)) return@hookBefore
            if (param.thisObject.hasGameMenuAction()) {
                param.result = null
            }
        }
        return 1
    }

    private fun hookSearchDefaultWord(): Int {
        var count = 0
        val symbols = env.symbols?.homeTopBar?.restore(classLoader) ?: return 0

        symbols.baseOnViewCreated?.let { method ->
            env.hookAfter(method) { param ->
                if (ModuleSettings.isHideHomeSearchDefaultWordEnabled(prefs)) {
                    clearSearchText(param.thisObject)
                }
            }
            count += 1
        }

        symbols.defaultWordMethods.forEach { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isHideHomeSearchDefaultWordEnabled(prefs)) return@hookBefore
                clearSearchText(param.thisObject)
                param.result = null
            }
            count += 1
        }

        return count
    }

    private fun clearSearchText(fragment: Any?) {
        val searchText = fragment.findSearchTextView() ?: return
        runCatching {
            searchText.clearAnimation()
            searchText.text = ""
        }.onFailure {
            log("HomeTopBarPurify failed to clear search text", it)
        }
    }

    private fun Any?.hasGameMenuAction(): Boolean {
        if (this == null) return false
        return javaClass.allFields().any { field ->
            val value = runCatching { field.get(this) }.getOrNull() ?: return@any false
            value !is String && value.hasStringField(::isGameMenuAction)
        }
    }

    private fun Any.hasStringField(predicate: (String) -> Boolean): Boolean =
        javaClass.allFields().any { field ->
            if (field.type != String::class.java) return@any false
            val value = runCatching { field.get(this) as? String }.getOrNull() ?: return@any false
            predicate(value)
        }

    private fun Any?.findSearchTextView(): TextView? {
        if (this == null) return null
        return javaClass.allFields()
            .firstNotNullOfOrNull { field ->
                if (field.type.name != SWITCH_TEXT_VIEW_CLASS) return@firstNotNullOfOrNull null
                runCatching { field.get(this) as? TextView }.getOrNull()
            }
    }

    private fun isGameMenuAction(action: String): Boolean =
        action == GAME_MENU_ACTION || action.startsWith("$GAME_MENU_ACTION?")

    private companion object {
        private const val SWITCH_TEXT_VIEW_CLASS = "tv.danmaku.bili.widget.SwitchTextView"
        private const val GAME_MENU_ACTION = "action://game_center/home/menu"
    }
}
