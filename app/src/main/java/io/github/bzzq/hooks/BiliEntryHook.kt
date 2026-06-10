package io.github.bzzq.hooks

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Hook to inject module entry point into Bilibili's UI (Settings and Mine page).
 * Mimics BiliRoaming's entry placement.
 */
class BiliEntryHook(
    override val targetPackageName: String,
) : AppHook {
    override fun install(xposed: XposedInterface, packageReady: PackageReadyParam, log: (String, Throwable?) -> Unit) {
        val classLoader = packageReady.getClassLoader()

        // 1. Hook SettingActivity (Settings page)
        val settingActivityNames = listOf(
            "com.bilibili.app.comm.setting.v2.SettingActivity",
            "com.bilibili.app.comm.setting.SettingActivity"
        )
        settingActivityNames.forEach { className ->
            runCatching {
                val clazz = Class.forName(className, false, classLoader)
                val onCreate = clazz.getDeclaredMethod("onCreate", android.os.Bundle::class.java)
                xposed.hook(onCreate).intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.thisObject as Activity
                    injectSettingEntry(activity, log)
                    result
                }
            }.onFailure {
                // Silently fail if class or method is not found
            }
        }

        // 2. Hook MineFragment (Mine page)
        runCatching {
            val mineFragmentClass = Class.forName("com.bilibili.app.comm.mine.MineFragment", false, classLoader)
            val onViewCreated = mineFragmentClass.getDeclaredMethod("onViewCreated", View::class.java, android.os.Bundle::class.java)
            xposed.hook(onViewCreated).intercept { chain ->
                chain.proceed()
                val view = chain.getArg(0) as View
                injectMineEntry(view, log)
            }
        }.onFailure {
            // Silently fail
        }
    }

    private fun injectSettingEntry(activity: Activity, log: (String, Throwable?) -> Unit) {
        activity.window.decorView.postDelayed({
            runCatching {
                val decor = activity.window.decorView as ViewGroup
                val toolbar = findToolbar(decor) ?: return@runCatching
                val parent = toolbar.parent as? ViewGroup ?: return@runCatching
                
                if (parent.findViewWithTag<View>("bzzq_entry") != null) return@runCatching

                val entry = createEntryView(activity).apply {
                    tag = "bzzq_entry"
                }
                
                val index = parent.indexOfChild(toolbar)
                parent.addView(entry, index + 1)
            }
        }, 400)
    }

    private fun injectMineEntry(view: View, log: (String, Throwable?) -> Unit) {
        view.postDelayed({
            runCatching {
                val root = view as? ViewGroup ?: return@runCatching
                val recyclerView = findRecyclerView(root) ?: return@runCatching
                val parent = recyclerView.parent as? ViewGroup ?: return@runCatching
                
                if (parent.findViewWithTag<View>("bzzq_mine_entry") != null) return@runCatching
                
                val entry = createEntryView(view.context as Activity).apply {
                    tag = "bzzq_mine_entry"
                    val params = layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.setMargins(0, dp(view.context as Activity, 8), 0, dp(view.context as Activity, 8))
                    layoutParams = params
                }
                parent.addView(entry, 0)
            }
        }, 600)
    }

    private fun createEntryView(activity: Activity): View {
        val versionName = runCatching {
            activity.packageManager.getPackageInfo("io.github.bzzq", 0).versionName
        }.getOrDefault("unknown")

        return TextView(activity).apply {
            text = "bzzq 模块设置 v$versionName"
            textSize = 16f
            setTextColor(Color.parseColor("#FB7299")) // Bilibili Pink
            val paddingH = dp(activity, 16)
            val paddingV = dp(activity, 12)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            setOnClickListener {
                runCatching {
                    val intent = Intent()
                    intent.setClassName("io.github.bzzq", "io.github.bzzq.SettingsActivity")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                }.onFailure {
                    // Fallback to action if className fails
                    val intent = Intent("android.intent.action.MAIN")
                    intent.setClassName("io.github.bzzq", "io.github.bzzq.SettingsActivity")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                }
            }
        }
    }

    private fun findToolbar(view: View): View? {
        if (view.javaClass.name.contains("Toolbar") || view.javaClass.name.contains("TitleBar")) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val v = findToolbar(view.getChildAt(i))
                if (v != null) return v
            }
        }
        return null
    }

    private fun findRecyclerView(view: View): View? {
        if (view.javaClass.name.contains("RecyclerView")) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val v = findRecyclerView(view.getChildAt(i))
                if (v != null) return v
            }
        }
        return null
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
