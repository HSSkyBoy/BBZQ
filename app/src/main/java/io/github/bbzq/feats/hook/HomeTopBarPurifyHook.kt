package io.github.bbzq.feats.hook

import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier

class HomeTopBarPurifyHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var composeGameMenuInstalled = false

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
        val symbols = env.symbols?.homeTopBar?.restore(classLoader) ?: return 0
        var count = 0
        symbols.gameMenuMethod?.let { method ->
            if (env.hookBefore(method) { param ->
                if (!ModuleSettings.isHideHomeTopBarPromotionEnabled(prefs)) return@hookBefore
                if (param.thisObject.hasGameMenuAction()) param.result = null
            }) count++
        }
        symbols.composeGameMenuMethod?.takeIf { !composeGameMenuInstalled }?.let { method ->
            if (env.hookBefore(method) { param ->
                if (!ModuleSettings.isHideHomeTopBarPromotionEnabled(prefs)) return@hookBefore
                val source = param.args.firstOrNull() as? List<*> ?: return@hookBefore
                val kept = source.filter { it == null || !it.hasStringField(::isHomeGameMenuAction) }
                if (kept.size != source.size) param.args[0] = kept
            }) {
                composeGameMenuInstalled = true
                count++
            }
        }
        return count
    }

    private fun hookSearchDefaultWord(): Int {
        var count = 0
        count += env.hookSearchDefaultWords()
        val symbols = env.symbols?.homeTopBar?.restore(classLoader) ?: return count

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
            value !is String && value.hasStringField(::isHomeGameMenuAction)
        }
    }

    private fun Any.hasStringField(predicate: (String) -> Boolean): Boolean =
        javaClass.allFields().any { field ->
            if (field.type != String::class.java || Modifier.isStatic(field.modifiers)) return@any false
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

    private companion object {
        private const val SWITCH_TEXT_VIEW_CLASS = "tv.danmaku.bili.widget.SwitchTextView"
    }
}

internal fun isHomeGameMenuAction(action: String): Boolean =
    action == "action://game_center/home/menu" || action.startsWith("action://game_center/home/menu?") ||
        action == "bilibili://game_center/home" || action.startsWith("bilibili://game_center/home?")
