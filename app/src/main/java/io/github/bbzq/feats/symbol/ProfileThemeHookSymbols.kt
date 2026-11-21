package io.github.bbzq.feats.symbol

import android.view.View
import io.github.bbzq.feats.allMethods
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.json.JSONArray
import org.json.JSONObject

data class AccountSymbols(
    val accountClassName: String,
    val getMethod: MethodDescriptor,
    val accessKeyMethod: MethodDescriptor,
    val isLoginMethod: MethodDescriptor? = null,
    val midMethod: MethodDescriptor? = null,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accountClassName", accountClassName)
        .put("getMethod", getMethod.toJson())
        .put("accessKeyMethod", accessKeyMethod.toJson())
        .putOpt("isLoginMethod", isLoginMethod?.toJson())
        .putOpt("midMethod", midMethod?.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredAccountSymbols? {
        val accountClass = classLoader.loadClassOrNull(accountClassName) ?: return null
        val get = getMethod.restore(accountClass) ?: return null
        val access = accessKeyMethod.restore(accountClass) ?: return null
        val isLogin = isLoginMethod?.restore(accountClass)
            ?: accountClass.allMethods().firstOrNull {
                it.name == "isLogin" && it.parameterCount == 0 &&
                    (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.javaObjectType)
            }
            ?: return null
        val mid = midMethod?.restore(accountClass)
            ?: accountClass.allMethods().firstOrNull {
                it.name == "mid" && it.parameterCount == 0 &&
                    (it.returnType == Long::class.javaPrimitiveType || it.returnType == Long::class.javaObjectType)
            }
        if (!Modifier.isStatic(get.modifiers)) return null
        if (get.returnType != accountClass) return null
        if (access.returnType != String::class.java || Modifier.isStatic(access.modifiers)) return null
        return RestoredAccountSymbols(accountClass, get, access, isLogin, mid)
    }

    companion object {
        fun fromJson(obj: JSONObject): AccountSymbols = AccountSymbols(
            accountClassName = obj.optString("accountClassName"),
            getMethod = MethodDescriptor.fromJson(obj.getJSONObject("getMethod")),
            accessKeyMethod = MethodDescriptor.fromJson(obj.getJSONObject("accessKeyMethod")),
            isLoginMethod = obj.optJSONObject("isLoginMethod")?.let(MethodDescriptor::fromJson),
            midMethod = obj.optJSONObject("midMethod")?.let(MethodDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredAccountSymbols(
    val accountClass: Class<*>,
    val getMethod: Method,
    val accessKeyMethod: Method,
    val isLoginMethod: Method,
    val midMethod: Method?,
)

data class MineProfileSymbols(
    val fragmentClassName: String,
    val vipViewClassName: String,
    val vipField: FieldDescriptor,
    val managerBindingField: FieldDescriptor? = null,
    val bindingRootField: FieldDescriptor? = null,
    val onResume: MethodDescriptor,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fragmentClassName", fragmentClassName)
        .put("vipViewClassName", vipViewClassName)
        .put("vipField", vipField.toJson())
        .putOpt("managerBindingField", managerBindingField?.toJson())
        .putOpt("bindingRootField", bindingRootField?.toJson())
        .put("onResume", onResume.toJson())
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredMineProfileSymbols? {
        val fragmentClass = classLoader.loadClassOrNull(fragmentClassName) ?: return null
        val vipViewClass = classLoader.loadClassOrNull(vipViewClassName) ?: return null
        val field = vipField.restore(fragmentClass) ?: return null
        val method = onResume.restore(fragmentClass) ?: return null
        if (!vipViewClass.isAssignableFrom(field.type)) return null
        val bindingField = managerBindingField?.restore(vipViewClass)
        val rootField = bindingRootField?.let { descriptor ->
            val owner = bindingField?.type ?: return null
            descriptor.restore(owner)
        }
        return RestoredMineProfileSymbols(fragmentClass, vipViewClass, field, bindingField, rootField, method)
    }

    companion object {
        fun fromJson(obj: JSONObject): MineProfileSymbols = MineProfileSymbols(
            fragmentClassName = obj.optString("fragmentClassName"),
            vipViewClassName = obj.optString("vipViewClassName"),
            vipField = FieldDescriptor.fromJson(obj.getJSONObject("vipField")),
            managerBindingField = obj.optJSONObject("managerBindingField")?.let(FieldDescriptor::fromJson),
            bindingRootField = obj.optJSONObject("bindingRootField")?.let(FieldDescriptor::fromJson),
            onResume = MethodDescriptor.fromJson(obj.getJSONObject("onResume")),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredMineProfileSymbols(
    val fragmentClass: Class<*>,
    val vipViewClass: Class<*>,
    val vipField: Field,
    val managerBindingField: Field?,
    val bindingRootField: Field?,
    val onResume: Method,
) {
    fun resolveVipView(fragment: Any): View? {
        val holder = vipField.get(fragment) ?: return null
        if (holder is View) return holder
        val binding = managerBindingField?.get(holder) ?: return null
        return bindingRootField?.get(binding) as? View
    }
}

data class CustomSkinSymbols(
    val resolverMethod: MethodDescriptor,
    // 皮肤响应注入(setUserGarb/setLoadEquip)与解析入口,缺失时降级为广播方案
    val skinResponseClassName: String? = null,
    val skinResponseUserGarbSetter: MethodDescriptor? = null,
    val skinResponseLoadEquipSetter: MethodDescriptor? = null,
    val skinResolveMethod: MethodDescriptor? = null,
    // 进度条图标(play_icon)三个 URL getter:左拉/右拉/不拉表现图
    val videoPlayerIconGetters: List<MethodDescriptor> = emptyList(),
    // blkv 工厂(写下拉动画配置)与其读取出口 get(String,Object)(读取拦截)
    val blkvPrefsFactory: MethodDescriptor? = null,
    val blkvGetMethods: List<MethodDescriptor> = emptyList(),
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("resolverMethod", resolverMethod.toJson())
        .putOpt("skinResponseClassName", skinResponseClassName)
        .putOpt("skinResponseUserGarbSetter", skinResponseUserGarbSetter?.toJson())
        .putOpt("skinResponseLoadEquipSetter", skinResponseLoadEquipSetter?.toJson())
        .putOpt("skinResolveMethod", skinResolveMethod?.toJson())
        .put("videoPlayerIconGetters", org.json.JSONArray(videoPlayerIconGetters.map { it.toJson() }))
        .putOpt("blkvPrefsFactory", blkvPrefsFactory?.toJson())
        .put("blkvGetMethods", org.json.JSONArray(blkvGetMethods.map { it.toJson() }))
        .put("evidence", evidence)

    fun restore(classLoader: ClassLoader): RestoredCustomSkinSymbols? {
        val resolver = resolverMethod.restoreOptional(classLoader) ?: return null
        val skinResponseClass = skinResponseClassName?.let(classLoader::loadClassOrNull)
        val userGarbSetter = skinResponseUserGarbSetter?.restoreOptional(classLoader)
        val loadEquipSetter = skinResponseLoadEquipSetter?.restoreOptional(classLoader)
        val skinResolveMethod = skinResolveMethod?.restoreOptional(classLoader)
        val videoPlayerIconGetters = videoPlayerIconGetters.mapNotNull { it.restoreOptional(classLoader) }
        val blkvPrefsFactory = blkvPrefsFactory?.restoreOptional(classLoader)
        val blkvGetMethods = blkvGetMethods.mapNotNull { it.restoreOptional(classLoader) }
        return RestoredCustomSkinSymbols(
            resolverMethod = resolver,
            skinResponseClass = skinResponseClass,
            skinResponseUserGarbSetter = userGarbSetter,
            skinResponseLoadEquipSetter = loadEquipSetter,
            skinResolveMethod = skinResolveMethod,
            videoPlayerIconGetters = videoPlayerIconGetters,
            blkvPrefsFactory = blkvPrefsFactory,
            blkvGetMethods = blkvGetMethods,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomSkinSymbols = CustomSkinSymbols(
            resolverMethod = MethodDescriptor.fromJson(obj.getJSONObject("resolverMethod")),
            skinResponseClassName = obj.optString("skinResponseClassName").takeIf { it.isNotBlank() },
            skinResponseUserGarbSetter = obj.optJSONObject("skinResponseUserGarbSetter")?.let(MethodDescriptor::fromJson),
            skinResponseLoadEquipSetter = obj.optJSONObject("skinResponseLoadEquipSetter")?.let(MethodDescriptor::fromJson),
            skinResolveMethod = obj.optJSONObject("skinResolveMethod")?.let(MethodDescriptor::fromJson),
            videoPlayerIconGetters = obj.optJSONArray("videoPlayerIconGetters")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let(MethodDescriptor::fromJson)
                }
            } ?: emptyList(),
            blkvPrefsFactory = obj.optJSONObject("blkvPrefsFactory")?.let(MethodDescriptor::fromJson),
            blkvGetMethods = obj.optJSONArray("blkvGetMethods")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let(MethodDescriptor::fromJson)
                }
            } ?: emptyList(),
            evidence = obj.optString("evidence", "-"),
        )
    }
}

data class RestoredCustomSkinSymbols(
    val resolverMethod: Method,
    val skinResponseClass: Class<*>?,
    val skinResponseUserGarbSetter: Method?,
    val skinResponseLoadEquipSetter: Method?,
    val skinResolveMethod: Method?,
    val videoPlayerIconGetters: List<Method>,
    val blkvPrefsFactory: Method?,
    val blkvGetMethods: List<Method>,
)

data class CustomThemeSymbols(
    val themeHelperClassName: String,
    val themeHelperColorArray: FieldDescriptor,
    val themeNameClassName: String,
    val themeNameField: FieldDescriptor,
    val builtInThemesClassName: String? = null,
    val builtInThemesField: FieldDescriptor? = null,
    val themeColorsClassName: String? = null,
    val skinListMethod: MethodDescriptor,
    val themeListClickClassName: String,
    val skinClassName: String,
    val themeProcessorClassName: String,
    val themeResetMethods: List<MethodDescriptor>,
    val themeIdHelperClassName: String? = null,
    val themeIdHelperColorId: FieldDescriptor? = null,
    val columnHelperClassName: String? = null,
    val columnHelperColorArray: FieldDescriptor? = null,
    val evidence: String,
    val skinResponseClassName: String? = null,
    val skinResponseUserGarbSetter: MethodDescriptor? = null,
    val skinResponseLoadEquipSetter: MethodDescriptor? = null,
    val skinResolveMethod: MethodDescriptor? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("themeHelperClassName", themeHelperClassName)
        .put("themeHelperColorArray", themeHelperColorArray.toJson())
        .put("themeNameClassName", themeNameClassName)
        .put("themeNameField", themeNameField.toJson())
        .putOpt("builtInThemesClassName", builtInThemesClassName)
        .putOpt("builtInThemesField", builtInThemesField?.toJson())
        .putOpt("themeColorsClassName", themeColorsClassName)
        .put("skinListMethod", skinListMethod.toJson())
        .put("themeListClickClassName", themeListClickClassName)
        .put("skinClassName", skinClassName)
        .put("themeProcessorClassName", themeProcessorClassName)
        .put("themeResetMethods", themeResetMethods.toJsonArray { it.toJson() })
        .putOpt("themeIdHelperClassName", themeIdHelperClassName)
        .putOpt("themeIdHelperColorId", themeIdHelperColorId?.toJson())
        .putOpt("columnHelperClassName", columnHelperClassName)
        .putOpt("columnHelperColorArray", columnHelperColorArray?.toJson())
        .put("evidence", evidence)
        .putOpt("skinResponseClassName", skinResponseClassName)
        .putOpt("skinResponseUserGarbSetter", skinResponseUserGarbSetter?.toJson())
        .putOpt("skinResponseLoadEquipSetter", skinResponseLoadEquipSetter?.toJson())
        .putOpt("skinResolveMethod", skinResolveMethod?.toJson())

    fun restore(classLoader: ClassLoader): RestoredCustomThemeSymbols? {
        val themeHelperClass = classLoader.loadClassOrNull(themeHelperClassName) ?: return null
        val themeNameClass = classLoader.loadClassOrNull(themeNameClassName) ?: return null
        val builtInThemesClass = builtInThemesClassName?.let(classLoader::loadClassOrNull)
        val themeColorsClass = themeColorsClassName?.let(classLoader::loadClassOrNull)
        val skinListMethod = skinListMethod.restoreOptional(classLoader) ?: return null
        val themeListClickClass = classLoader.loadClassOrNull(themeListClickClassName) ?: return null
        val skinClass = classLoader.loadClassOrNull(skinClassName) ?: return null
        val themeProcessorClass = classLoader.loadClassOrNull(themeProcessorClassName) ?: return null
        val colorArray = themeHelperColorArray.restore(themeHelperClass) ?: return null
        val themeName = themeNameField.restore(themeNameClass) ?: return null
        val allThemes = builtInThemesField?.let { descriptor -> builtInThemesClass?.let(descriptor::restore) }
        val resetMethods = themeResetMethods.restoreAll(classLoader) ?: return null
        val themeIdHelper = themeIdHelperClassName?.let(classLoader::loadClassOrNull)
        val themeIdColorId = themeIdHelperColorId?.let { themeIdHelper?.let(it::restore) }
        val columnHelper = columnHelperClassName?.let(classLoader::loadClassOrNull)
        val columnColorArray = columnHelperColorArray?.let { columnHelper?.let(it::restore) }
        val skinResponseClass = skinResponseClassName?.let(classLoader::loadClassOrNull)
        val userGarbSetter = skinResponseUserGarbSetter?.restoreOptional(classLoader)
        val loadEquipSetter = skinResponseLoadEquipSetter?.restoreOptional(classLoader)
        val skinResolveMethod = skinResolveMethod?.restoreOptional(classLoader)
        return RestoredCustomThemeSymbols(
            themeHelperClass, colorArray, themeName, allThemes, themeColorsClass,
            skinListMethod, themeListClickClass, skinClass, themeProcessorClass, resetMethods,
            themeIdHelper, themeIdColorId, columnHelper, columnColorArray,
            skinResponseClass, userGarbSetter, loadEquipSetter, skinResolveMethod,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomThemeSymbols = CustomThemeSymbols(
            themeHelperClassName = obj.optString("themeHelperClassName"),
            themeHelperColorArray = FieldDescriptor.fromJson(obj.getJSONObject("themeHelperColorArray")),
            themeNameClassName = obj.optString("themeNameClassName"),
            themeNameField = FieldDescriptor.fromJson(obj.getJSONObject("themeNameField")),
            builtInThemesClassName = obj.optString("builtInThemesClassName").takeIf { it.isNotBlank() },
            builtInThemesField = obj.optJSONObject("builtInThemesField")?.let(FieldDescriptor::fromJson),
            themeColorsClassName = obj.optString("themeColorsClassName").takeIf { it.isNotBlank() },
            skinListMethod = MethodDescriptor.fromJson(obj.getJSONObject("skinListMethod")),
            themeListClickClassName = obj.optString("themeListClickClassName"),
            skinClassName = obj.optString("skinClassName"),
            themeProcessorClassName = obj.optString("themeProcessorClassName"),
            themeResetMethods = obj.optJSONArray("themeResetMethods").toList { MethodDescriptor.fromJson(it) },
            themeIdHelperClassName = obj.optString("themeIdHelperClassName").takeIf { it.isNotBlank() },
            themeIdHelperColorId = obj.optJSONObject("themeIdHelperColorId")?.let(FieldDescriptor::fromJson),
            columnHelperClassName = obj.optString("columnHelperClassName").takeIf { it.isNotBlank() },
            columnHelperColorArray = obj.optJSONObject("columnHelperColorArray")?.let(FieldDescriptor::fromJson),
            evidence = obj.optString("evidence", "-"),
            skinResponseClassName = obj.optString("skinResponseClassName").takeIf { it.isNotBlank() },
            skinResponseUserGarbSetter = obj.optJSONObject("skinResponseUserGarbSetter")?.let(MethodDescriptor::fromJson),
            skinResponseLoadEquipSetter = obj.optJSONObject("skinResponseLoadEquipSetter")?.let(MethodDescriptor::fromJson),
            skinResolveMethod = obj.optJSONObject("skinResolveMethod")?.let(MethodDescriptor::fromJson),
        )
    }
}

data class RestoredCustomThemeSymbols(
    val themeHelperClass: Class<*>,
    val themeHelperColorArray: Field,
    val themeName: Field,
    val allThemes: Field?,
    val themeColorsClass: Class<*>?,
    val skinListMethod: Method,
    val themeListClickClass: Class<*>,
    val skinClass: Class<*>,
    val themeProcessorClass: Class<*>,
    val themeResetMethods: List<Method>,
    val themeIdHelperClass: Class<*>?,
    val themeIdHelperColorId: Field?,
    val columnHelperClass: Class<*>?,
    val columnHelperColorArray: Field?,
    val skinResponseClass: Class<*>?,
    val skinResponseUserGarbSetter: Method?,
    val skinResponseLoadEquipSetter: Method?,
    val skinResolveMethod: Method?,
)
