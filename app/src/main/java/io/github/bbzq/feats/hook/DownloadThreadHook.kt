package io.github.bbzq.feats.hook

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredDownloadThreadListenerSymbols

class DownloadThreadHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isCustomDownloadThreadEnabled(prefs)) return

        val restored = env.symbols?.downloadThread?.restore(classLoader)
        if (restored == null) {
            log("startHook: DownloadThread skipped because symbols are unavailable")
            return
        }

        var count = 0
        restored.listeners.forEach { listener ->
            count += hookListener(listener)
        }

        if (count > 0) {
            log("startHook: DownloadThread, methods=$count")
        } else {
            log("startHook: DownloadThread, no verified hooks found")
        }
    }

    private fun hookListener(symbols: RestoredDownloadThreadListenerSymbols): Int {
        var count = 0
        env.hookAfter(symbols.constructor) { param ->
            val view = param.args.firstOrNull { it is TextView } as? TextView ?: return@hookAfter
            view.post {
                runCatching {
                    val parent = view.parent as? ViewGroup ?: return@runCatching
                    val custom = view.tag as? Int == 1
                    if (custom) {
                        val moduleContext = env.moduleContext ?: run {
                            log("DownloadThread label skipped because module context is unavailable")
                            return@runCatching
                        }
                        view.text = moduleContext.getString(R.string.custom_download_thread_label)
                    }
                    parent.getChildAt(1)?.visibility = if (custom) View.VISIBLE else View.INVISIBLE
                }.onFailure { throwable ->
                    log("DownloadThread label update failed", throwable)
                }
            }
        }
        count++

        env.hookBefore(symbols.onClick) { param ->
            val view = runCatching { symbols.textViewField.get(param.thisObject) as? TextView }.getOrNull()
                ?: return@hookBefore
            if (view.tag as? Int != 1) return@hookBefore

            view.tag = ModuleSettings.getCustomDownloadConcurrency(prefs)
        }
        count++
        return count
    }
}
