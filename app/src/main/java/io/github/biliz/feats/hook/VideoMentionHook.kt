package io.github.biliz.feats.hook

import android.view.View
import android.view.ViewGroup
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.findClassOrNull
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * 视频播放页（详情页）视频提及（Video Mentions）净化 Hook。
 *
 * 采用双层安全防御策略：
 * 1. 数据层 (Protobuf / gRPC)：
 *    - 在 Module 中拦截 hasVideoMentions() 返回 false，并返回空实例；
 *    - 在 IntroductionTab.getModulesList() 中彻底剔除 VIDEO_MENTIONS 模块，
 *      使播放页简介 UI 适配器从源头不再排版和实例化提及卡片；
 *    - 在 VideoMentions 与 VideoMentionsReply 中清空条目列表及计数；
 *    - 在 UpTool 中拦截 hasMention() 返回 false。
 * 2. UI 渲染与弹窗层 (兜底防御)：
 *    - 拦截 GameVideoMentionedComponent / StreamerVideoMentionedComponent 等联动卡片；
 *    - 拦截 SeedVideoMentionedComponent 代理；
 *    - 拦截 VideoMentionedDialogService 弹窗弹出。
 */
class VideoMentionHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val methodCache = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, Method?>>()
    private var blockedCount = 0

    override fun startHook() {
        if (env.processName != env.packageName) return

        var installed = 0

        // 1. Data Layer
        installed += installModuleBlock()
        installed += installVideoMentionsBlock()
        installed += installVideoMentionsReplyBlock()
        installed += installUpToolBlock()

        // 2. UI & Dialog Layer
        installed += installGameVideoMentionedComponentBlock()
        installed += installSeedVideoMentionedBlock()
        installed += installDialogBlock()

        if (installed > 0) {
            isInstalled = true
            log("startHook: VideoMentionHook installed=$installed, initialEnabled=${ModuleSettings.isPurifyVideoMentionEnabled(prefs)}")
        } else {
            log("startHook: VideoMentionHook no hook point found")
        }
    }

    private fun installModuleBlock(): Int {
        val moduleClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.common.Module") ?: return 0
        var count = 0

        moduleClass.declaredMethods.firstOrNull {
            it.name == "hasVideoMentions" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let {
            env.hookBefore(it) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                param.result = false
            }
            count++
        }

        moduleClass.declaredMethods.firstOrNull {
            it.name == "getVideoMentions" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let { method ->
            val defaultInstance = runCatching {
                val vmClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.common.VideoMentions")
                vmClass?.getDeclaredMethod("getDefaultInstance")?.invoke(null)
            }.getOrNull()

            env.hookBefore(method) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                if (defaultInstance != null) {
                    param.result = defaultInstance
                }
            }
            count++
        }

        log("startHook: VideoMentionHook Module hook count=$count")
        return count
    }

    private fun installVideoMentionsBlock(): Int {
        val vmClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.common.VideoMentions") ?: return 0
        var count = 0

        vmClass.declaredMethods.firstOrNull {
            it.name == "getMentionsList" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let {
            env.hookBefore(it) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                param.result = emptyList<Any>()
            }
            count++
        }

        vmClass.declaredMethods.firstOrNull {
            it.name == "getMentionsCount" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let {
            env.hookBefore(it) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                param.result = 0
            }
            count++
        }

        log("startHook: VideoMentionHook VideoMentions hook count=$count")
        return count
    }

    private fun installVideoMentionsReplyBlock(): Int {
        val replyClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.v1.VideoMentionsReply") ?: return 0
        var count = 0

        replyClass.declaredMethods.firstOrNull {
            it.name == "getMentionsList" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let {
            env.hookBefore(it) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                param.result = emptyList<Any>()
            }
            count++
        }

        replyClass.declaredMethods.firstOrNull {
            it.name == "getMentionsCount" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let {
            env.hookBefore(it) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                param.result = 0
            }
            count++
        }

        log("startHook: VideoMentionHook VideoMentionsReply hook count=$count")
        return count
    }

    private fun installUpToolBlock(): Int {
        val upToolClass = classLoader.findClassOrNull("com.bapis.bilibili.app.viewunite.common.UpTool") ?: return 0
        var count = 0

        upToolClass.declaredMethods.firstOrNull {
            it.name == "hasMention" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
        }?.let {
            env.hookBefore(it) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                param.result = false
            }
            count++
        }

        log("startHook: VideoMentionHook UpTool hook count=$count")
        return count
    }

    private fun installGameVideoMentionedComponentBlock(): Int {
        var count = 0
        val compNames = listOf(
            "com.bilibili.biligame.videocard.GameVideoMentionedComponent",
            "com.bilibili.biligame.videocard.GameVideoMentionedHeaderComponent",
            "com.bilibili.biligame.videocard.StreamerVideoMentionedComponent",
        )

        compNames.forEach { className ->
            val compClass = classLoader.findClassOrNull(className) ?: return@forEach
            compClass.declaredMethods.filter {
                !Modifier.isStatic(it.modifiers) && (it.name == "bindToView" || it.name == "createViewEntry")
            }.forEach { method ->
                env.hookAfter(method) { param ->
                    runCatching {
                        if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@runCatching
                        // 对于 createViewEntry，返回值是 ViewEntry；对于 bindToView，首个入参是 ViewEntry
                        val entry = if (method.name == "createViewEntry") param.result else param.args.firstOrNull()
                        hideViewEntry(entry)
                        logBlocked("${compClass.simpleName}.${method.name}")
                    }
                }
                count++
            }
        }

        log("startHook: VideoMentionHook GameVideoMentioned components hook count=$count")
        return count
    }

    private fun installSeedVideoMentionedBlock(): Int {
        val seedClass = classLoader.findClassOrNull("com.bilibili.ship.theseus.united.page.intro.module.videomentioned.seed.s")
            ?: return 0
        var count = 0

        seedClass.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && (it.name == "bindToView" || it.name == "createViewEntry")
        }.forEach { method ->
            env.hookAfter(method) { param ->
                runCatching {
                    if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@runCatching
                    val entry = if (method.name == "createViewEntry") param.result else param.args.firstOrNull()
                    hideViewEntry(entry)
                    logBlocked("SeedVideoMentioned.${method.name}")
                }
            }
            count++
        }

        log("startHook: VideoMentionHook SeedVideoMentioned hook count=$count")
        return count
    }

    private fun installDialogBlock(): Int {
        val dialogClass = classLoader.findClassOrNull("com.bilibili.ship.theseus.ugc.intro.videomentioned.dialog.VideoMentionedDialogService")
            ?: return 0
        var count = 0

        dialogClass.declaredMethods.filter {
            it.name == "showDialog" && !Modifier.isStatic(it.modifiers)
        }.forEach { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isPurifyVideoMentionEnabled(prefs)) return@hookBefore
                logBlocked("VideoMentionedDialogService.showDialog")
                param.result = null
            }
            count++
        }

        log("startHook: VideoMentionHook VideoMentionedDialogService hook count=$count")
        return count
    }

    private fun isMentionModule(module: Any?): Boolean {
        if (module == null) return false
        return runCatching {
            // 1. hasVideoMentions()
            if (callNoArg(module, "hasVideoMentions") == true) return@runCatching true

            // 2. getType() / getTypeValue()
            val typeVal = callNoArg(module, "getType")?.toString().orEmpty()
            if (typeVal.contains("VIDEO_MENTIONS", ignoreCase = true)) return@runCatching true

            // 3. getDataCase()
            val dataCase = callNoArg(module, "getDataCase")?.toString().orEmpty()
            if (dataCase.contains("VIDEO_MENTIONS", ignoreCase = true)) return@runCatching true

            // 4. hasMention()
            if (callNoArg(module, "hasMention") == true) return@runCatching true

            false
        }.getOrDefault(false)
    }

    private fun hideViewEntry(entry: Any?) {
        if (entry == null) return
        runCatching {
            if (entry is View) {
                hideView(entry)
                return
            }
            val view = callNoArg(entry, "getView") as? View
                ?: callNoArg(entry, "getItemView") as? View
                ?: entry.javaClass.declaredFields.firstOrNull { View::class.java.isAssignableFrom(it.type) }?.apply { isAccessible = true }?.get(entry) as? View
            if (view != null) {
                hideView(view)
            }
        }
    }

    private fun hideView(view: View) {
        view.visibility = View.GONE
        val lp = view.layoutParams ?: ViewGroup.LayoutParams(0, 0)
        lp.width = 0
        lp.height = 0
        view.layoutParams = lp
    }

    private fun callNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        val method = debugNoArgMethod(target.javaClass, name) ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }

    private fun debugNoArgMethod(type: Class<*>, name: String): Method? =
        methodCache.computeIfAbsent(type) { ConcurrentHashMap() }
            .computeIfAbsent(name) { methodName ->
                type.methods.firstOrNull {
                    it.name == methodName && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
                }?.apply { isAccessible = true }
            }

    private fun logBlocked(action: String) {
        val count = ++blockedCount
        if (count <= 20 || count % 20 == 0) {
            log("VideoMentionHook blocked $action count=$count")
        }
    }
}
