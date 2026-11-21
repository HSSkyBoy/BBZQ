package io.github.bbzq.feats.symbol

import android.content.Context
import android.content.SharedPreferences
import android.util.SparseArray
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.UsingFieldMatcher

private fun findSkinResponseClass(classLoader: ClassLoader, bridge: DexKitBridge): Class<*>? {
    fun Class<*>.hasUserGarbSetter() = declaredMethods.any { it.name == "setUserGarb" && it.parameterCount == 1 }
    val searched = runCatching {
        bridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings("user_equip")),
        ).mapNotNull { classLoader.loadClassOrNull(it.name) }
            .firstOrNull { it.hasUserGarbSetter() }
    }.getOrNull()
    return searched ?: classLoader.loadClassOrNull(GARB_DATA_CLASS)?.takeIf { it.hasUserGarbSetter() }
}

internal fun scanAccount(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<AccountSymbols> {
    val candidates = (
        ACCOUNT_CLASS_NAMES.asSequence() +
            findClassNamesBySimpleName(bridge, "BiliAccounts").asSequence()
        )
        .distinct()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
        .toList()

    for (type in candidates) {
        val getMethod = type.declaredMethods
            .filter { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.returnType == type &&
                    (
                        method.parameterCount == 0 ||
                            method.hasParameterTypes("android.content.Context")
                        )
            }
            .sortedWith(compareBy<Method> { if (it.name == "get") 0 else 1 }.thenBy { it.parameterCount })
            .firstOrNull()
            ?.apply { isAccessible = true }
            ?: continue
        val accessKeyMethod = type.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == String::class.java
            }
            .sortedWith(
                compareBy<Method> {
                    when (it.name) {
                        "getAccessKey" -> 0
                        "accessKey" -> 1
                        else -> 2
                    }
                }.thenBy { it.name },
            )
            .firstOrNull { method -> method.name.contains("access", ignoreCase = true) || method.name in SHORT_ACCESS_METHODS }
            ?.apply { isAccessible = true }
            ?: continue
        val isLoginMethod = type.declaredMethods.firstOrNull { method ->
            method.name == "isLogin" && !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 && method.returnType == Boolean::class.javaPrimitiveType
        }?.apply { isAccessible = true } ?: continue
        val midMethod = type.declaredMethods.firstOrNull { method ->
            method.name == "mid" && !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 && method.returnType == Long::class.javaPrimitiveType
        }?.apply { isAccessible = true }

        val symbols = AccountSymbols(
            accountClassName = type.name,
            getMethod = MethodDescriptor.of(getMethod),
            accessKeyMethod = MethodDescriptor.of(accessKeyMethod),
            isLoginMethod = MethodDescriptor.of(isLoginMethod),
            midMethod = midMethod?.let(MethodDescriptor::of),
            evidence = if (type.name in ACCOUNT_CLASS_NAMES) "stableClass" else "dexkitSimpleName",
        )
        return SymbolScanResult.Found(symbols, "${type.name}.${accessKeyMethod.name}", symbols.evidence)
    }
    return SymbolScanResult.Missing("account class or getAccessKey method not verified")
}

internal fun scanCustomSkin(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<CustomSkinSymbols> {
    val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
    val resolverMethod = runCatching {
        currentBridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().usingStrings("shouldApplyForceOpGarb =")),
        ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .firstOrNull { method ->
                method.parameterCount == 1 &&
                    method.returnType != Void.TYPE &&
                    method.returnType.name.contains("GarbDetail")
            }
    }.getOrNull() ?: return SymbolScanResult.Missing("garb resolver method not found")
    resolverMethod.isAccessible = true

    // 皮肤响应模型:含 setUserGarb / setLoadEquip setter,
    // load_equip(下拉动画)与 user_equip 一起在解析 /x/resource/show/skin 时注入
    val skinResponseClass = findSkinResponseClass(classLoader, currentBridge)
    val skinResponseUserGarbSetter = skinResponseClass?.declaredMethods?.firstOrNull {
        it.name == "setUserGarb" && it.parameterCount == 1
    }?.apply { isAccessible = true }
    val skinResponseLoadEquipSetter = skinResponseClass?.declaredMethods?.firstOrNull {
        it.name == "setLoadEquip" && it.parameterCount == 1
    }?.apply { isAccessible = true }

    // 皮肤解析入口方法(宽松匹配,与 scanCustomTheme 的规则一致)
    val skinResolveMethod = runCatching {
        currentBridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().usingStrings("shouldApplyForceOpGarb =")),
        ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .firstOrNull { it.parameterCount == 1 && it.returnType != Void.TYPE }
            ?.apply { isAccessible = true }
    }.getOrNull()

    // 进度条图标(play_icon)的三个 URL getter:类名限定 PlayerIcon,
    // 命中 protobuf PlayerIcon 与番剧 JSON 模型,排除 Description 等无关类
    val videoPlayerIconGetters = runCatching {
        listOf("getDragLeftPng", "getDragRightPng", "getMiddlePng").flatMap { methodName ->
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().name(methodName)),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .filter { method ->
                    method.parameterCount == 0 &&
                        !Modifier.isAbstract(method.modifiers) &&
                        !method.declaringClass.isInterface &&
                        method.declaringClass.name.contains("PlayerIcon")
                }
        }.distinctBy { "${it.declaringClass.name}.${it.name}" }
            .map { it.apply { isAccessible = true } }
    }.getOrDefault(emptyList())

    // blkv 工厂(返回 SharedPrefX,非标准 SharedPreferences):反射调用写入下拉动画配置
    val blkvPrefsFactory = runCatching {
        currentBridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().usingStrings(".blkv")),
        ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .firstOrNull { method ->
                method.parameterCount == 4 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[3] == Int::class.javaPrimitiveType &&
                    method.returnType.name.contains("Shared")
            }
            ?.apply { isAccessible = true }
    }.getOrNull()

    // SharedPrefX 实现类的 get(String,Object):所有 blkv 读取的出口,
    // hook 后读 garb_load_equip_conf 强制返回自制配置,摆脱广播/缓存竞态
    val blkvGetMethods = runCatching {
        currentBridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().addInterface("com.bilibili.lib.blkv.SharedPrefX")),
        ).mapNotNull { runCatching { classLoader.loadClassOrNull(it.name) }.getOrNull() }
            .flatMap { clazz ->
                clazz.declaredMethods.asSequence()
                    .filter { method ->
                        method.name == "get" && method.parameterCount == 2 &&
                            method.parameterTypes[0] == String::class.java &&
                            !method.parameterTypes[1].isPrimitive
                    }
                    .map { it.apply { isAccessible = true } }
                    .toList()
            }.distinctBy { "${it.declaringClass.name}.${it.name}" }
    }.getOrDefault(emptyList())

    val symbols = CustomSkinSymbols(
        resolverMethod = MethodDescriptor.of(resolverMethod),
        skinResponseClassName = skinResponseClass?.name,
        skinResponseUserGarbSetter = skinResponseUserGarbSetter?.let(MethodDescriptor::of),
        skinResponseLoadEquipSetter = skinResponseLoadEquipSetter?.let(MethodDescriptor::of),
        skinResolveMethod = skinResolveMethod?.let(MethodDescriptor::of),
        videoPlayerIconGetters = videoPlayerIconGetters.map(MethodDescriptor::of),
        blkvPrefsFactory = blkvPrefsFactory?.let(MethodDescriptor::of),
        blkvGetMethods = blkvGetMethods.map(MethodDescriptor::of),
        evidence = "resolver=${resolverMethod.declaringClass.name}.${resolverMethod.name}" +
            ",userGarb=${skinResponseUserGarbSetter != null}" +
            ",loadEquip=${skinResponseLoadEquipSetter != null}" +
            ",videoPlayerIcon=${videoPlayerIconGetters.size}" +
            ",blkvFactory=${blkvPrefsFactory != null}" +
            ",blkvGets=${blkvGetMethods.size}",
    )
    return SymbolScanResult.Found(symbols, resolverMethod.declaringClass.name, symbols.evidence)
}

internal fun scanCustomTheme(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<CustomThemeSymbols> {
    val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
    val themeStoreActivity = classLoader.loadClassOrNull(THEME_STORE_ACTIVITY)
        ?: return SymbolScanResult.Missing("ThemeStoreActivity not found")
    val skinListClass = classLoader.loadClassOrNull(BILI_SKIN_LIST)
        ?: return SymbolScanResult.Missing("BiliSkinList not found")
    val skinClass = classLoader.loadClassOrNull(BILI_SKIN)
        ?: return SymbolScanResult.Missing("BiliSkin not found")

    // 9.15 起 theme_entries_last_key 只出现在同类的读写方法里,颜色表 getter 本身不再引用它,
    // 因此先按字符串定位 helper 类,再找与静态 SparseArray 同名的 (Context)int getter
    val themeHelperCandidates = currentBridge.findMethod(
        FindMethod.create().matcher(MethodMatcher.create().usingStrings("theme_entries_last_key")),
    ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull()?.declaringClass }
        .distinctBy { it.name }
    val (themeHelperClass, colorArrayField) = themeHelperCandidates.firstNotNullOfOrNull { type ->
        type.declaredMethods.asSequence()
            .filter {
                Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                    it.returnType == Int::class.javaPrimitiveType
            }
            .firstNotNullOfOrNull { method ->
                type.declaredFields.firstOrNull {
                    it.name == method.name && it.type == SparseArray::class.java && Modifier.isStatic(it.modifiers)
                }
            }
            ?.apply { isAccessible = true }
            ?.let { type to it }
    } ?: return SymbolScanResult.Missing("theme color SparseArray not found: helpers=${themeHelperCandidates.size}")

    val themeColorsClass = currentBridge.findClass(
        FindClass.create().matcher(ClassMatcher.create().usingStrings("GarbThemeColors(garb=")),
    ).mapNotNull { classLoader.loadClassOrNull(it.name) }
        .firstOrNull { type -> type.declaredConstructors.any { Modifier.isPrivate(it.modifiers) } }

    // 新版只剩 (BiliSkinList) 一个参数,旧版带 (BiliSkinList, boolean)
    val skinListMethod = themeStoreActivity.declaredMethods.firstOrNull {
        it.parameterTypes.firstOrNull() == skinListClass &&
            (
                it.parameterTypes.size == 1 ||
                    (it.parameterTypes.size == 2 && it.parameterTypes[1] == Boolean::class.javaPrimitiveType)
                )
    }?.apply { isAccessible = true }
        ?: return SymbolScanResult.Missing("theme skin list method not found")

    // 按預設主題檔名定位，不依賴包名（9.19 起位於根包）
    val themeProcessorClass = findDeclaredMethods(bridge, classLoader) {
        matcher(MethodMatcher.create().usingStrings(THEME_DEFAULT_LIST_FILE))
    }.map { it.declaringClass }
        .distinctBy { it.name }
        .firstOrNull { type -> type.safeDeclaredFields().count { it.type == skinListClass } > 1 }
        ?: return SymbolScanResult.Missing("theme processor class not found")
    val themeResetMethods = findDeclaredMethods(bridge, classLoader) {
        matcher(
            MethodMatcher.create()
                .declaredClass(themeProcessorClass)
                .paramCount(0)
                .returnType(Void.TYPE)
                .usingStrings(THEME_DEFAULT_LIST_FILE),
        )
    }.ifEmpty {
        themeProcessorClass.declaredMethods.filter {
            it.parameterCount == 0 && it.modifiers == 0
        }
    }.onEach { it.isAccessible = true }
    if (themeResetMethods.isEmpty()) {
        return SymbolScanResult.Missing("theme reset methods not found")
    }

    // 9.19 有多個點擊監聽，只有主題列表項的 onClick 會讀 BiliSkin.mId
    val clickListeners = themeStoreActivity.declaredClasses.filter {
        it.interfaces.contains(View.OnClickListener::class.java)
    }
    val skinIdReaders = findDeclaredMethods(bridge, classLoader) {
        matcher(
            MethodMatcher.create().name("onClick").addUsingField(
                UsingFieldMatcher().field(FieldMatcher.create().declaredClass(skinClass).name("mId")),
            ),
        )
    }.map { it.declaringClass }.toSet()
    val themeListClickClass = clickListeners.firstOrNull { it in skinIdReaders }
        ?: clickListeners.firstOrNull()
        ?: return SymbolScanResult.Missing("theme list click listener not found")

    val themeNameClass = findThemeNameClass(classLoader, currentBridge)
        ?: return SymbolScanResult.Missing("theme name map class not found")
    val themeNameField = themeNameClass.declaredFields.firstOrNull {
        it.type == Map::class.java && Modifier.isStatic(it.modifiers)
    }?.apply { isAccessible = true }
        ?: return SymbolScanResult.Missing("theme name map field not found")

    // 優先按 GarbThemeColors 構造函數的調用者所讀的靜態 Map 定位（9.19 為 pia0.c），舊版再按類名搜尋
    val builtInThemeCandidate = themeColorsClass?.let { findBuiltInThemeMap(currentBridge, classLoader, it) }
        ?: findClassNamesByNameContains(bridge, listOf("theme", "garb"))
        .mapNotNull(classLoader::loadClassOrNull)
        .firstNotNullOfOrNull { type ->
            type.safeDeclaredFields().firstOrNull {
                it.type == Map::class.java && Modifier.isStatic(it.modifiers)
            }?.apply { isAccessible = true }?.let { type to it }
        }

    // Mirror BiliRoamingX's response replacement at Bilibili's parsed model boundary.
    // This narrows the hook to skin data instead of intercepting every OkHttp response.
    val skinResponseClass = findSkinResponseClass(classLoader, currentBridge)
    val skinResponseUserGarbSetter = skinResponseClass?.declaredMethods?.firstOrNull {
        it.name == "setUserGarb" && it.parameterCount == 1
    }?.apply { isAccessible = true }
    val skinResponseLoadEquipSetter = skinResponseClass?.declaredMethods?.firstOrNull {
        it.name == "setLoadEquip" && it.parameterCount == 1
    }?.apply { isAccessible = true }
    val skinResolveMethod = runCatching {
        currentBridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().usingStrings("shouldApplyForceOpGarb =")),
        ).mapNotNull { it.getMethodInstance(classLoader) }
            .firstOrNull { it.parameterCount == 1 && it.returnType != Void.TYPE }
            ?.apply { isAccessible = true }
    }.getOrNull()

    val symbols = CustomThemeSymbols(
        themeHelperClassName = themeHelperClass.name,
        themeHelperColorArray = FieldDescriptor.of(colorArrayField),
        themeNameClassName = themeNameClass.name,
        themeNameField = FieldDescriptor.of(themeNameField),
        builtInThemesClassName = builtInThemeCandidate?.first?.name,
        builtInThemesField = builtInThemeCandidate?.second?.let(FieldDescriptor::of),
        themeColorsClassName = themeColorsClass?.name,
        skinListMethod = MethodDescriptor.of(skinListMethod),
        themeListClickClassName = themeListClickClass.name,
        skinClassName = skinClass.name,
        themeProcessorClassName = themeProcessorClass.name,
        themeResetMethods = themeResetMethods.map(MethodDescriptor::of),
        evidence = "helper=${themeHelperClass.name},processor=${themeProcessorClass.name},reset=${themeResetMethods.size},builtIn=${builtInThemeCandidate != null}",
        skinResponseClassName = skinResponseClass?.name,
        skinResponseUserGarbSetter = skinResponseUserGarbSetter?.let(MethodDescriptor::of),
        skinResponseLoadEquipSetter = skinResponseLoadEquipSetter?.let(MethodDescriptor::of),
        skinResolveMethod = skinResolveMethod?.let(MethodDescriptor::of),
    )
    return SymbolScanResult.Found(symbols, themeStoreActivity.name, symbols.evidence)
}

private fun findBuiltInThemeMap(
    bridge: DexKitBridge,
    classLoader: ClassLoader,
    colorsClass: Class<*>,
): Pair<Class<*>, java.lang.reflect.Field>? = runCatching {
    bridge.findMethod(
        FindMethod.create().matcher(
            MethodMatcher.create().addInvoke(MethodMatcher.create().declaredClass(colorsClass).name("<init>")),
        ),
    ).asSequence()
        .flatMap { it.usingFields.asSequence() }
        .mapNotNull { runCatching { it.field.getFieldInstance(classLoader) }.getOrNull() }
        .firstOrNull { it.type == Map::class.java && Modifier.isStatic(it.modifiers) }
        ?.apply { isAccessible = true }
        ?.let { it.declaringClass to it }
}.getOrNull()

private fun findThemeNameClass(classLoader: ClassLoader, bridge: DexKitBridge): Class<*>? {
    val candidates = bridge.findClass(
        FindClass.create().matcher(ClassMatcher.create().usingStrings(".garb.GARB_CHANGE")),
    ).map { it.name }.toList() + bridge.findClass(
        FindClass.create().matcher(ClassMatcher.create().usingStrings("white")),
    ).map { it.name }.toList()
    val types = candidates.asSequence().distinct().mapNotNull(classLoader::loadClassOrNull)
        .filter { type ->
            type.safeDeclaredFields().any { Modifier.isStatic(it.modifiers) && it.type == Map::class.java }
        }
        .toList()
    // "white" 在新版命中多个颜色名映射(如 HtmlColors),按主题 id 映射内容挑出真正的主题名表
    return types.firstOrNull { type ->
        type.safeDeclaredFields().any { field ->
            Modifier.isStatic(field.modifiers) && field.type == Map::class.java &&
                runCatching {
                    field.isAccessible = true
                    val map = field.get(null) as? Map<*, *>
                    map?.get("white") == 8 && map["pink"] == 2
                }.getOrDefault(false)
        }
    } ?: types.firstOrNull()
}

internal fun scanMineProfile(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<MineProfileSymbols> {
    val fragmentClass = (
        MINE_FRAGMENT_CLASS_NAMES.asSequence() +
            findClassNamesBySimpleName(bridge, "HomeUserCenterFragment").asSequence()
        )
        .distinct()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .firstOrNull { type ->
            type.declaredMethods.any { method -> method.name == "onResume" && method.parameterCount == 0 }
        }
        ?: return SymbolScanResult.Missing("HomeUserCenterFragment not verified")

    val vipViewClass = (
        MINE_VIP_VIEW_CLASS_NAMES.asSequence() +
            findClassNamesBySimpleName(bridge, "MineVipEntranceView").asSequence() +
            findClassNamesBySimpleName(bridge, "VipEntranceView").asSequence()
        )
        .distinct()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .firstOrNull()

    val onResume = fragmentClass.declaredMethods.firstOrNull { method ->
        method.name == "onResume" && method.parameterCount == 0
    }?.apply { isAccessible = true }
        ?: return SymbolScanResult.Missing("onResume not found")

    if (vipViewClass != null) {
        val vipField = fragmentClass.declaredFields.firstOrNull { field ->
            vipViewClass.isAssignableFrom(field.type)
        }?.apply { isAccessible = true }
            ?: return SymbolScanResult.Missing("vip view field not found")

        val symbols = MineProfileSymbols(
            fragmentClassName = fragmentClass.name,
            vipViewClassName = vipViewClass.name,
            vipField = FieldDescriptor.of(vipField),
            onResume = MethodDescriptor.of(onResume),
            evidence = "fragment=${fragmentClass.name},vip=${vipViewClass.name},field=${vipField.name}",
        )
        return SymbolScanResult.Found(symbols, "${fragmentClass.name}.${onResume.name}", symbols.evidence)
    }

    val managerClass = MINE_VIP_MANAGER_CLASS_NAMES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        ?: return SymbolScanResult.Missing("mine vip view or manager class not found")
    val viewBindingClass = classLoader.loadClassOrNull("androidx.viewbinding.ViewBinding")
        ?: return SymbolScanResult.Missing("ViewBinding class not found")
    val managerField = fragmentClass.declaredFields.firstOrNull { field ->
        managerClass.isAssignableFrom(field.type)
    }?.apply { isAccessible = true }
        ?: return SymbolScanResult.Missing("mine vip manager field not found")
    val bindingField = managerClass.declaredFields.firstOrNull { field ->
        viewBindingClass.isAssignableFrom(field.type)
    }?.apply { isAccessible = true }
        ?: return SymbolScanResult.Missing("mine vip manager binding field not found")
    val rootField = bindingField.type.declaredFields.firstOrNull { field ->
        View::class.java.isAssignableFrom(field.type)
    }?.apply { isAccessible = true }
        ?: return SymbolScanResult.Missing("mine vip binding root view field not found")

    val symbols = MineProfileSymbols(
        fragmentClassName = fragmentClass.name,
        vipViewClassName = managerClass.name,
        vipField = FieldDescriptor.of(managerField),
        managerBindingField = FieldDescriptor.of(bindingField),
        bindingRootField = FieldDescriptor.of(rootField),
        onResume = MethodDescriptor.of(onResume),
        evidence = "fragment=${fragmentClass.name},manager=${managerClass.name},field=${managerField.name},root=${rootField.name}",
    )
    return SymbolScanResult.Found(symbols, "${fragmentClass.name}.${onResume.name}", symbols.evidence)
}

private val ACCOUNT_CLASS_NAMES = listOf(
    "com.bilibili.lib.accounts.BiliAccounts",
    "com.bilibili.app.accounts.BiliAccounts",
)

private val SHORT_ACCESS_METHODS = setOf("a", "b")

private const val GARB_DATA_CLASS = "tv.danmaku.bili.ui.garb.model.GarbData"

private val MINE_FRAGMENT_CLASS_NAMES = listOf(
    "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment",
)

private val MINE_VIP_VIEW_CLASS_NAMES = listOf(
    "tv.danmaku.bili.ui.main2.mine.widgets.MineVipEntranceView",
    "tv.danmaku.bili.ui.main2.mine.modularvip.VipEntranceView",
)

private val MINE_VIP_MANAGER_CLASS_NAMES = listOf(
    "tv.danmaku.bili.ui.main2.mine.modularvip.MineVipModuleManager",
)

private const val THEME_STORE_ACTIVITY = "tv.danmaku.bili.ui.theme.ThemeStoreActivity"

private const val BILI_SKIN_LIST = "tv.danmaku.bili.ui.theme.api.BiliSkinList"

private const val BILI_SKIN = "tv.danmaku.bili.ui.theme.api.BiliSkin"

private const val THEME_DEFAULT_LIST_FILE = "theme2-default"
