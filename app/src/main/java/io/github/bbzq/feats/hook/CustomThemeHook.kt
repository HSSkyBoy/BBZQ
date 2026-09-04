package io.github.bbzq.feats.hook

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.util.SparseArray
import android.view.View
import android.widget.EditText
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.fieldOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.newInstanceOrNull
import io.github.bbzq.feats.replace
import io.github.bbzq.feats.setBooleanField
import io.github.bbzq.feats.setIntField
import io.github.bbzq.feats.setObjectField
import io.github.bbzq.feats.symbol.RestoredCustomSkinSymbols
import io.github.bbzq.feats.symbol.RestoredCustomThemeSymbols
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Adds two virtual theme entries to Bilibili, following the custom-theme flow used by
 * BiliRoaming while keeping all settings in BBZQ's remote preferences.
 */
class CustomThemeHook(env: RoamingEnv) : BaseRoamingHook(env) {
    // play_icon 配置缓存:raw 指纹变化(皮肤切换)时重解析
    private var cachedSkinRaw: String? = null
    private var cachedPlayIconConfig: JSONObject? = null

    override fun startHook() {
        val customSkinEnabled = ModuleSettings.isCustomSkinEnabled(prefs)
        // A portable garb contains its own colors. It must win over this module's
        // virtual color-theme feature, otherwise its reset hook can undo the garb.
        val customColorEnabled = ModuleSettings.isCustomThemeEnabled(prefs) && !customSkinEnabled
        if (customSkinEnabled) {
            CustomSkinApplier.applyIfChanged(env)
            val skinSymbols = env.symbols?.customSkin?.restore(classLoader)
            if (skinSymbols == null) {
                log("Custom skin resolver missing; using broadcast fallback")
            } else {
                hookSkinResolver(skinSymbols.resolverMethod)
                // 皮肤响应/进度条图标/下拉动画注入(customSkin 符号独立于 customTheme)
                hookSkinResponse(skinSymbols)
                hookPlayIcon(skinSymbols)
                applyLoadEquipConf(skinSymbols)
                hookBlkvGet(skinSymbols)
            }
            // Suppressing the reset also stops an already-equipped theme from overriding
            // the imported skin when MainActivity restores it on startup.
            val themeSymbols = env.symbols?.customTheme?.restore(classLoader)
            if (themeSymbols != null) suppressThemeReset(themeSymbols)
        }
        if (!customColorEnabled) {
            log("startHook: customSkin=$customSkinEnabled customColor=$customColorEnabled")
            return
        }
        val symbols = env.symbols?.customTheme?.restore(classLoader) ?: run {
            log("CustomTheme skipped: symbols missing or failed to restore")
            return
        }
        val initialColor = ModuleSettings.getCustomThemeColor(prefs)
        if (customColorEnabled) {
            installThemeMaps(symbols, initialColor)
            hookThemeList(symbols)
            hookThemeClick(symbols)
        }
        suppressThemeReset(symbols)
        log("startHook: customColor=$customColorEnabled customSkin=$customSkinEnabled resetMethods=${symbols.themeResetMethods.size}")
    }

    /**
     * Bilibili re-applies the equipped theme during [MAIN_ACTIVITY].onPostCreate, which would
     * override the injected color theme or imported skin. Skip the reset only on that path so
     * our garb wins while leaving genuine user-initiated theme switches untouched.
     */
    private fun suppressThemeReset(symbols: RestoredCustomThemeSymbols) {
        symbols.themeResetMethods.forEach { method ->
            env.replace(method) { param ->
                if (Thread.currentThread().stackTrace.any {
                        it.className == MAIN_ACTIVITY && it.methodName == "onPostCreate"
                    }
                ) {
                    null
                } else {
                    param.invokeOriginalMethod()
                }
            }
        }
    }

    private fun hookSkinResolver(resolveMethod: java.lang.reflect.Method) {
        env.replace(resolveMethod) { param ->
            if (!ModuleSettings.isCustomSkinEnabled(prefs)) {
                return@replace param.invokeOriginalMethod()
            }
            val skin = customSkinEquip() ?: return@replace param.invokeOriginalMethod()
            parseHostJson(skin.toString(), resolveMethod.returnType) ?: param.invokeOriginalMethod()
        }
        log("Custom skin resolver hook installed: ${resolveMethod.declaringClass.name}.${resolveMethod.name}")
    }

    /**
     * Substitute the skin model while Bilibili parses /x/resource/show/skin.
     * Thus every official refresh receives the imported equip before it can update UI/state.
     */
    private fun hookSkinResponse(skinSymbols: RestoredCustomSkinSymbols) {
        val userGarbSetter = skinSymbols.skinResponseUserGarbSetter ?: run {
            log("Custom skin response hooks skipped: skin response class missing")
            return
        }
        env.hookBefore(userGarbSetter) { param ->
            if (!ModuleSettings.isCustomSkinEnabled(prefs)) return@hookBefore
            val skin = customSkinEquip() ?: return@hookBefore
            val replacement = parseHostJson(skin.toString(), userGarbSetter.parameterTypes[0]) ?: return@hookBefore
            param.args[0] = replacement
        }
        skinSymbols.skinResponseLoadEquipSetter?.let { loadEquipSetter ->
            env.hookBefore(loadEquipSetter) { param ->
                if (!ModuleSettings.isCustomSkinEnabled(prefs)) return@hookBefore
                val loadEquip = customSkinConfig()?.optJSONObject("load_equip") ?: return@hookBefore
                val replacement = parseHostJson(loadEquip.toString(), loadEquipSetter.parameterTypes[0]) ?: return@hookBefore
                param.args[0] = replacement
            }
        }
        log("Custom skin response hook installed: ${userGarbSetter.declaringClass.name}")
    }

    // 进度条图标(play_icon)注入:直接替换 PlayerIcon 模型三个 URL getter 的返回值
    private fun hookPlayIcon(skinSymbols: RestoredCustomSkinSymbols) {
        if (skinSymbols.videoPlayerIconGetters.isEmpty()) {
            log("Custom skin play icon hook skipped: playerIcon getters missing")
            return
        }
        skinSymbols.videoPlayerIconGetters.forEach { getter ->
            env.hookAfter(getter) { param ->
                if (!ModuleSettings.isCustomSkinEnabled(prefs)) return@hookAfter
                val playIcon = playIconConfig() ?: return@hookAfter
                val url = when (getter.name) {
                    "getDragLeftPng" -> playIcon.optString("drag_left_png")
                    "getDragRightPng" -> playIcon.optString("drag_right_png")
                    "getMiddlePng" -> playIcon.optString("middle_png")
                    else -> return@hookAfter
                }
                if (url.isNotBlank()) param.result = url
            }
            log("Custom skin play icon hook installed: ${getter.declaringClass.name}.${getter.name}")
        }
    }

    /** play_icon 配置:按 raw 指纹缓存,皮肤切换时自动重解析 */
    private fun playIconConfig(): JSONObject? {
        val raw = ModuleSettings.getCustomSkinJson(prefs)
        if (raw.isBlank()) return null
        if (cachedSkinRaw != raw) {
            cachedSkinRaw = raw
            cachedPlayIconConfig = runCatching { JSONObject(raw).optJSONObject("play_icon") }.getOrNull()
        }
        return cachedPlayIconConfig
    }

    // 下拉动画配置写入 blkv(web 进程读它才知道动画文件名),再广播让其重读
    private fun applyLoadEquipConf(skinSymbols: RestoredCustomSkinSymbols) {
        val factory = skinSymbols.blkvPrefsFactory ?: run {
            log("Custom skin load equip conf skipped: blkv factory missing")
            return
        }
        val root = customSkinConfig() ?: return
        val loadEquip = root.optJSONObject("load_equip") ?: run {
            log("Custom skin load equip conf skipped: no load_equip")
            return
        }
        if (loadEquip.optString("loading_url").isBlank() || loadEquip.optLong("id") <= 0L) {
            log("Custom skin load equip conf skipped: load_equip incomplete")
            return
        }
        runCatching {
            // 工厂是静态方法,传 null 接收者
            val prefs = factory.invoke(null, env.hostContext, LOAD_EQUIP_CONF_PREFS_FILE, false, 0) ?: return@runCatching
            if (!writeBlkv(prefs, LOAD_EQUIP_CONF_KEY, loadEquip.toString())) {
                log("Custom skin load equip conf write failed: no putString api on ${prefs.javaClass.name}")
                return@runCatching
            }
            // 先写再广播,web 进程收到广播后重读才能拿到新配置
            env.hostContext.sendBroadcast(Intent("${env.packageName}.garb.LOAD_EQUIP_CHANGE"))
            log("Custom skin load equip conf written: id=${loadEquip.optLong("id")} via ${factory.declaringClass.name}.${factory.name}")
        }.onFailure {
            log("Custom skin load equip conf write failed", it)
        }
    }

    // 反射写 blkv:优先标准 SharedPreferences 路径,失败逐级降级适配 B 站 SharedPrefX
    private fun writeBlkv(prefs: Any, key: String, value: String): Boolean {
        runCatching {
            val editor = prefs.javaClass.getMethod("edit").invoke(prefs) ?: return@runCatching
            editor.javaClass.getMethod("putString", String::class.java, String::class.java)
                .invoke(editor, key, value)
            commitIfPresent(editor)
            return true
        }
        runCatching {
            prefs.javaClass.getMethod("putString", String::class.java, String::class.java)
                .invoke(prefs, key, value)
            commitIfPresent(prefs)
            return true
        }
        runCatching {
            val editMethod = prefs.javaClass.methods.firstOrNull { m ->
                m.parameterCount == 0 && m.returnType.name.contains("Editor")
            } ?: return@runCatching
            val editor = editMethod.invoke(prefs) ?: return@runCatching
            val put = editor.javaClass.methods.firstOrNull { m ->
                m.name == "putString" && m.parameterCount == 2
            } ?: return@runCatching
            put.invoke(editor, key, value)
            commitIfPresent(editor)
            return true
        }
        return false
    }

    private fun commitIfPresent(editor: Any) {
        runCatching { editor.javaClass.getMethod("apply").invoke(editor) }
            .getOrElse { runCatching { editor.javaClass.getMethod("commit").invoke(editor) } }
    }

    // 读取边界拦截:所有 blkv 读取经过 SharedPrefX.get,读下拉配置时强制返回自制 JSON,
    // 摆脱广播时序/跨进程缓存导致的时好时坏
    private fun hookBlkvGet(skinSymbols: RestoredCustomSkinSymbols) {
        if (skinSymbols.blkvGetMethods.isEmpty()) {
            log("Custom skin blkv get hook skipped: SharedPrefX get methods missing")
            return
        }
        skinSymbols.blkvGetMethods.forEach { getter ->
            env.hookAfter(getter) { param ->
                if (!ModuleSettings.isCustomSkinEnabled(prefs)) return@hookAfter
                if (param.args.getOrNull(0) != LOAD_EQUIP_CONF_KEY) return@hookAfter
                val root = customSkinConfig() ?: return@hookAfter
                val loadEquip = root.optJSONObject("load_equip") ?: return@hookAfter
                if (loadEquip.optString("loading_url").isBlank() || loadEquip.optLong("id") <= 0L) {
                    return@hookAfter
                }
                param.result = loadEquip.toString()
                log("Custom skin blkv get injected: ${getter.declaringClass.name}.${getter.name}")
            }
            log("Custom skin blkv get hook installed: ${getter.declaringClass.name}.${getter.name}")
        }
    }

    private fun customSkinConfig(): JSONObject? = runCatching {
        val raw = ModuleSettings.getCustomSkinJson(prefs)
        if (raw.isBlank()) return null
        JSONObject(raw)
    }.getOrNull()

    private fun customSkinEquip(): JSONObject? {
        val root = customSkinConfig() ?: return null
        return (root.optJSONObject("user_equip") ?: root).takeIf { it.optLong("id") > 0L }
    }

    private fun parseHostJson(json: String, targetClass: Class<*>): Any? {
        val jsonClass = sequenceOf("com.alibaba.fastjson.JSON", "com.alibaba.fastjson2.JSON")
            .mapNotNull { name -> runCatching { classLoader.loadClass(name) }.getOrNull() }
            .firstOrNull()
            ?: return null
        val parseObject = jsonClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name == "parseObject" &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java, Class::class.java))
        } ?: return null
        return runCatching { parseObject.invoke(null, json, targetClass) }.getOrNull()
    }

    /** The web process has its own color tables and does not run the theme list UI. */
    fun insertColorForWebProcess() {
        if (!ModuleSettings.isCustomThemeEnabled(prefs)) return
        val symbols = env.symbols?.customTheme?.restore(classLoader) ?: return
        val color = ModuleSettings.getCustomThemeColor(prefs)
        installThemeMaps(symbols, color)
        symbols.columnHelperColorArray?.let { field ->
            putColorArray(field, color)
        }
        symbols.themeIdHelperColorId?.let { field ->
            runCatching {
                val ids = field.get(null) as? SparseArray<Any?> ?: return@runCatching
                ids.put(CUSTOM_THEME_ID1, CUSTOM_THEME_ID1)
                ids.put(CUSTOM_THEME_ID2, CUSTOM_THEME_ID2)
            }
        }
        log("CustomTheme web colors installed: ${formatColor(color)}")
    }

    // 下拉动画由 web 进程渲染,该进程不跑完整皮肤流程,单独补写配置并装读取拦截
    fun insertLoadEquipForWebProcess() {
        if (!ModuleSettings.isCustomSkinEnabled(prefs)) return
        val skinSymbols = env.symbols?.customSkin?.restore(classLoader) ?: return
        applyLoadEquipConf(skinSymbols)
        hookBlkvGet(skinSymbols)
    }

    private fun installThemeMaps(symbols: RestoredCustomThemeSymbols, primaryColor: Int) {
        runCatching {
            @Suppress("UNCHECKED_CAST")
            (symbols.themeName.get(null) as? MutableMap<String, Int>)?.apply {
                put("custom1", CUSTOM_THEME_ID1)
                put("custom2", CUSTOM_THEME_ID2)
            }
            putColorArray(symbols.themeHelperColorArray, primaryColor)
            buildTheme(primaryColor, symbols.themeColorsClass)?.let { theme ->
                @Suppress("UNCHECKED_CAST")
                (symbols.allThemes?.get(null) as? MutableMap<Long, Any>)?.apply {
                    put(CUSTOM_THEME_ID1.toLong(), theme)
                    put(CUSTOM_THEME_ID2.toLong(), theme)
                }
            }
        }.onFailure { log("CustomTheme map installation failed", it) }
    }

    private fun putColorArray(field: Field, primaryColor: Int) {
        val array = field.get(null) as? SparseArray<IntArray> ?: return
        val colors = generateColorArray(primaryColor)
        array.put(CUSTOM_THEME_ID1, colors)
        array.put(CUSTOM_THEME_ID2, colors)
    }

    private fun hookThemeList(symbols: RestoredCustomThemeSymbols) {
        env.hookBefore(symbols.skinListMethod) { param ->
            val listHolder = param.args.firstOrNull() ?: return@hookBefore
            val list = listHolder.javaClass.fieldOrNull("mList")?.let { field ->
                runCatching { field.get(listHolder) as? MutableList<Any> }.getOrNull()
            } ?: return@hookBefore
            if (list.any { it.readIntField("mId") == CUSTOM_THEME_ID1 || it.readIntField("mId") == CUSTOM_THEME_ID2 }) {
                return@hookBefore
            }
            val skin = symbols.skinClass.newInstanceOrNull() ?: return@hookBefore
            val id = if (ModuleSettings.getCustomThemeColor(prefs) == ModuleSettings.DEFAULT_CUSTOM_THEME_COLOR) {
                CUSTOM_THEME_ID1
            } else {
                CUSTOM_THEME_ID2
            }
            skin.setIntField("mId", id)
            skin.setObjectField("mName", "自定义颜色")
            skin.setBooleanField("mIsFree", true)
            list.add(minOf(3, list.size), skin)
        }
    }

    private fun hookThemeClick(symbols: RestoredCustomThemeSymbols) {
        val method = symbols.themeListClickClass.allMethods().firstOrNull {
            it.name == "onClick" && it.parameterTypes.contentEquals(arrayOf(View::class.java))
        } ?: return
        env.hookBefore(method) { param ->
            val view = param.args.firstOrNull() as? View ?: return@hookBefore
            val skin = view.tag ?: return@hookBefore
            val id = skin.readIntField("mId")
            if (id != CUSTOM_THEME_ID1 && id != CUSTOM_THEME_ID2) return@hookBefore

            val dialogInput = EditText(view.context).apply {
                setSingleLine(true)
                setSelectAllOnFocus(true)
                setText(formatColor(ModuleSettings.getCustomThemeColor(prefs)).removePrefix("#"))
                hint = "RRGGBB"
                inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
            val dialog = AlertDialog.Builder(view.context)
                .setTitle("自定义颜色")
                .setMessage("输入 6 位十六进制主色，例如 FB7299")
                .setView(dialogInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val color = parseColor(dialogInput.text?.toString()) ?: run {
                        dialogInput.error = "请输入有效的 RRGGBB 色码"
                        return@setOnClickListener
                    }
                    prefs.edit().putInt(ModuleSettings.KEY_CUSTOM_THEME_COLOR, color).apply()
                    installThemeMaps(symbols, color)
                    skin.setIntField("mId", if (id == CUSTOM_THEME_ID1) CUSTOM_THEME_ID2 else CUSTOM_THEME_ID1)
                    dialog.dismiss()
                    param.invokeOriginalMethod()
                }
            }
            dialog.show()
            param.result = null
        }
    }

    private fun buildTheme(primaryColor: Int, themeClass: Class<*>?): Any? {
        themeClass ?: return null
        val constructor = themeClass.declaredConstructors.firstOrNull { Modifier.isPrivate(it.modifiers) }
            ?: themeClass.declaredConstructors.maxByOrNull { it.parameterTypes.size }
            ?: return null
        constructor.isAccessible = true
        if (constructor.parameterCount != 10) return null
        val garbClass = constructor.parameterTypes[0]
        val garb = garbClass.newInstanceOrNull() ?: garbClass.declaredConstructors
            .firstOrNull { it.parameterCount == 9 }
            ?.apply { isAccessible = true }
            ?.runCatching {
                newInstance(0L, true, true, "", 0L, 0L, 0L, 0L, 0L)
            }?.getOrNull()
            ?: return null
        val dayNight = constructor.parameterTypes[1].enumConstants?.firstOrNull() ?: return null
        val packed = primaryColor.toLong() and 0xFFFFFFFFL shl 32
        return runCatching {
            constructor.newInstance(
                garb,
                dayNight,
                packed,
                packed,
                Color.WHITE.toLong() shl 32,
                Color.WHITE.toLong() shl 32,
                Color.WHITE.toLong() shl 32,
                Color.WHITE.toLong() shl 32,
                Color.WHITE.toLong() shl 32,
                true,
            )
        }.getOrNull()
    }

    private fun Any.readIntField(name: String): Int? =
        javaClass.allFields().firstOrNull { it.name == name }?.let { field ->
            runCatching { (field.get(this) as? Number)?.toInt() }.getOrNull()
        }

    private fun parseColor(raw: String?): Int? {
        val value = raw?.trim()?.removePrefix("#") ?: return null
        if (!value.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return runCatching { Color.parseColor("#$value") }.getOrNull()
    }

    private fun formatColor(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    private companion object {
        private const val CUSTOM_THEME_ID1 = 114514
        private const val CUSTOM_THEME_ID2 = 1919810
        private const val MAIN_ACTIVITY = "tv.danmaku.bili.MainActivityV2"
        // B 站 blkv 存储下拉刷新动画配置的 key,以及存储该配置的 blkv 文件名
        private const val LOAD_EQUIP_CONF_KEY = "garb_load_equip_conf"
        private const val LOAD_EQUIP_CONF_PREFS_FILE = "instance.bili_preference"

        private fun generateColorArray(primaryColor: Int): IntArray {
            val colors = IntArray(4)
            val hsv = FloatArray(3)
            val result = FloatArray(3)
            Color.colorToHSV(primaryColor, hsv)
            colors[0] = primaryColor
            hsv.copyInto(result)
            result[2] -= result[2] * 0.2f
            colors[1] = Color.HSVToColor(result)
            hsv.copyInto(result)
            result[2] = (result[2] + result[2] * 0.1f).coerceAtMost(1f)
            colors[2] = Color.HSVToColor(result)
            colors[3] = -0x4c000000 or (colors[1] and 0xFFFFFF)
            return colors
        }
    }
}
