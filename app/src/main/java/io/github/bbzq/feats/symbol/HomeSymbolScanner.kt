package io.github.bbzq.feats.symbol

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import org.luckypray.dexkit.DexKitBridge

internal fun scanHomeTopBar(
    classLoader: ClassLoader,
): SymbolScanResult<HomeTopBarSymbols> {
    val menuItemClass = classLoader.loadClassOrNull(HOME_MENU_ITEM_CLASS)
    val gameMenuMethod = menuItemClass?.declaredMethods?.firstOrNull {
        it.name == "b" &&
            it.parameterTypes.contentEquals(arrayOf(Menu::class.java, MenuInflater::class.java))
    }?.apply { isAccessible = true }
    val composeGameMenuMethod = classLoader.loadClassOrNull(
        "tv.danmaku.bili.home.components.topbar.TopRightComponent\$initTopRight\$1\$1",
    )?.declaredMethods?.singleOrNull {
        !Modifier.isStatic(it.modifiers) && it.name == "invoke" && it.returnType == Any::class.java &&
            it.parameterTypes.map { type -> type.name } == listOf("java.util.List", "kotlin.coroutines.Continuation")
    }?.apply { isAccessible = true }

    val baseFragmentClass = HOME_BASE_MAIN_FRAME_FRAGMENTS.firstNotNullOfOrNull(classLoader::loadClassOrNull)
    val baseOnViewCreated = baseFragmentClass?.declaredMethods?.firstOrNull {
        it.name == "onViewCreated" &&
            it.parameterTypes.contentEquals(arrayOf(View::class.java, Bundle::class.java))
    }?.apply { isAccessible = true }

    val mainFragmentClass = classLoader.loadClassOrNull(HOME_MAIN_FRAGMENT)
    val defaultWordMethods = if (mainFragmentClass != null) {
        mainFragmentClass.allMethods()
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount == 1 &&
                    isHomeDefaultSearchWordType(method.parameterTypes[0]) &&
                    !Modifier.isStatic(method.modifiers) &&
                    !Modifier.isAbstract(method.modifiers)
            }
            .distinctBy(Method::toGenericString)
            .toList()
    } else {
        emptyList()
    }

    if (gameMenuMethod == null && composeGameMenuMethod == null && baseOnViewCreated == null && defaultWordMethods.isEmpty()) {
        return SymbolScanResult.Missing("home top bar hook points not found")
    }
    val symbols = HomeTopBarSymbols(
        gameMenuMethod = gameMenuMethod?.let(MethodDescriptor::of),
        composeGameMenuMethod = composeGameMenuMethod?.let(MethodDescriptor::of),
        baseOnViewCreated = baseOnViewCreated?.let(MethodDescriptor::of),
        defaultWordMethods = defaultWordMethods.map(MethodDescriptor::of),
        evidence = "game=${gameMenuMethod != null},compose=${composeGameMenuMethod != null},onViewCreated=${baseOnViewCreated != null},defaultWord=${defaultWordMethods.size}",
    )
    val hookPoints = listOf(
        childHookPoint(HP_HOME_TOP_BAR_GAME, gameMenuMethod != null, "game menu method not found"),
        optionalChildHookPoint(HP_HOME_TOP_BAR_COMPOSE_GAME, composeGameMenuMethod != null, "Compose game menu method not found"),
        childHookPoint(HP_HOME_TOP_BAR_VIEW_CREATED, baseOnViewCreated != null, "base onViewCreated method not found"),
        childHookPoint(
            HP_HOME_TOP_BAR_DEFAULT_WORD,
            defaultWordMethods.isNotEmpty(),
            "default search word method not found",
            "methods=${defaultWordMethods.size}",
        ),
    )
    return SymbolScanResult.Found(symbols, "HomeTopBarPurify", symbols.evidence, hookPoints)
}

private fun isHomeDefaultSearchWordType(type: Class<*>): Boolean =
    type.name == HOME_DEFAULT_SEARCH_WORD_CLASS || (type.isInterface && HOME_DEFAULT_SEARCH_WORD_ACCESSORS.all { accessor ->
        type.allMethods().any { it.name == accessor && it.parameterCount == 0 }
    })

internal fun scanBottomBar(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<BottomBarSymbols> {
    val tabHostClasses = buildSet {
        addAll(BOTTOM_TAB_HOST_CLASSES.mapNotNull(classLoader::loadClassOrNull))
        addAll(findClassNamesBySimpleName(bridge, "TabHost").mapNotNull(classLoader::loadClassOrNull))
    }
    val tabHostSetTabsMethods = tabHostClasses
        .asSequence()
        .flatMap { it.allMethods() }
        .filter { method ->
            method.name == "setTabs" &&
                method.parameterCount == 1 &&
                List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.returnType == Void.TYPE
        }
        .distinctBy(Method::toGenericString)
        .toList()
    val tabHostGetTabsMethods = tabHostClasses
        .asSequence()
        .flatMap { it.allMethods() }
        .filter { method ->
            method.name == "getTabs" &&
                method.parameterCount == 0 &&
                List::class.java.isAssignableFrom(method.returnType)
        }
        .distinctBy(Method::toGenericString)
        .toList()
    val baseOnViewCreatedMethods = HOME_BASE_MAIN_FRAME_FRAGMENTS
        .asSequence()
        .mapNotNull(classLoader::loadClassOrNull)
        .flatMap { it.allMethods() }
        .filter { method ->
            method.name == "onViewCreated" &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == View::class.java &&
                method.parameterTypes[1] == Bundle::class.java &&
                method.returnType == Void.TYPE
        }
        .distinctBy(Method::toGenericString)
        .toList()

    if (tabHostSetTabsMethods.isEmpty() || tabHostGetTabsMethods.isEmpty()) {
        return SymbolScanResult.Missing("bottom bar hook points not found")
    }
    val symbols = BottomBarSymbols(
        tabHostSetTabsMethods = tabHostSetTabsMethods.map(MethodDescriptor::of),
        tabHostGetTabsMethods = tabHostGetTabsMethods.map(MethodDescriptor::of),
        baseOnViewCreatedMethods = baseOnViewCreatedMethods.map(MethodDescriptor::of),
        evidence = "setTabs=${tabHostSetTabsMethods.size},getTabs=${tabHostGetTabsMethods.size},onViewCreated=${baseOnViewCreatedMethods.size}",
    )
    val hookPoints = listOf(
        childHookPoint(
            HP_BOTTOM_BAR_TAB_HOST,
            tabHostSetTabsMethods.isNotEmpty() && tabHostGetTabsMethods.isNotEmpty(),
            "TabHost getTabs/setTabs methods not found",
            "setTabs=${tabHostSetTabsMethods.size},getTabs=${tabHostGetTabsMethods.size}",
        ),
        optionalChildHookPoint(
            HP_BOTTOM_BAR_VIEW_CREATED,
            baseOnViewCreatedMethods.isNotEmpty(),
            "BaseMainFrameFragment.onViewCreated not found",
            "methods=${baseOnViewCreatedMethods.size}",
        ),
    )
    return SymbolScanResult.Found(symbols, "BottomBar", symbols.evidence, hookPoints)
}

internal fun scanHomeRecommendFeed(
    classLoader: ClassLoader,
): SymbolScanResult<HomeRecommendFeedSymbols> {
    val holderDataClass = classLoader.loadClassOrNull(PEGASUS_HOLDER_DATA)
        ?: return SymbolScanResult.Missing("PegasusHolderData class not found")
    val responseClasses = PEGASUS_RESPONSE_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
    if (responseClasses.isEmpty()) return SymbolScanResult.Missing("PegasusResponse class not found")

    val getHolderType = holderDataClass.allMethods().firstOrNull {
        it.name == "getHolderType" &&
            it.parameterCount == 0 &&
            it.returnType == String::class.java &&
            !Modifier.isStatic(it.modifiers)
    } ?: return SymbolScanResult.Missing("getHolderType not found")

    val responseGetItems = responseClasses.mapNotNull { responseClass ->
        val getItems = responseClass.allMethods().firstOrNull {
            it.name == "getItems" &&
                it.parameterCount == 0 &&
                List::class.java.isAssignableFrom(it.returnType) &&
                !Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers)
        } ?: return@mapNotNull null
        val itemsField = responseClass.allFields()
            .filter { List::class.java.isAssignableFrom(it.type) }
            .singleOrNull()
        PegasusResponseGetItemsSymbols(MethodDescriptor.of(getItems), itemsField?.let(FieldDescriptor::of))
    }.distinctBy { it.getItems.declaringClassName + "#" + it.getItems.name }
    if (responseGetItems.isEmpty()) return SymbolScanResult.Missing("PegasusResponse.getItems not found")

    val baseDataClass = BASE_PEGASUS_DATA_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
    val holderStyleClass = classLoader.loadClassOrNull(PEGASUS_HOLDER_STYLE)
    val adInfoClass = classLoader.loadClassOrNull(AD_INFO)
    val symbols = HomeRecommendFeedSymbols(
        responseGetItems = responseGetItems,
        getHolderType = MethodDescriptor.of(getHolderType),
        getBizType = holderDataClass.noArgMethod("getBizType")?.let(MethodDescriptor::of),
        getHolderStyle = holderDataClass.noArgMethod("getHolderStyle")?.let(MethodDescriptor::of),
        isSmallCard = holderStyleClass?.allMethods()?.firstOrNull {
            it.name == "isSmallCard" &&
                it.parameterCount == 0 &&
                it.returnType == Boolean::class.javaPrimitiveType
        }?.let(MethodDescriptor::of),
        getAdInfo = baseDataClass?.noArgMethod("getAdInfo")?.let(MethodDescriptor::of),
        getCardType = baseDataClass?.noArgMethod("getCardType")?.let(MethodDescriptor::of),
        getCardGoto = baseDataClass?.noArgMethod("getCardGoto")?.let(MethodDescriptor::of),
        getGoTo = baseDataClass?.noArgMethod("getGoTo")?.let(MethodDescriptor::of),
        getUri = baseDataClass?.noArgMethod("getUri")?.let(MethodDescriptor::of),
        adInfoClassName = adInfoClass?.name,
        evidence = "responses=${responseGetItems.size},holder=${holderDataClass.name},base=${baseDataClass?.name}",
    )
    return SymbolScanResult.Found(symbols, responseGetItems.joinToString("|") { it.getItems.declaringClassName }, symbols.evidence)
}

internal fun scanHomeRecommendTabs(
    classLoader: ClassLoader,
): SymbolScanResult<HomeRecommendTabSymbols> {
    val homeFragmentClasses = HOME_FRAGMENT_V2_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
        .toList()
    if (homeFragmentClasses.isEmpty()) {
        return SymbolScanResult.Missing("HomeFragmentV2 class not found")
    }

    val buildTabMethods = homeFragmentClasses
        .asSequence()
        .flatMap { it.declaredMethods.asSequence() }
        .filter { it.isHomeRecommendTabBuildMethod() }
        .distinctBy(Method::toGenericString)
        .toList()
    val buildTabsMethod = buildTabMethods.singleOrNull()
        ?: return SymbolScanResult.Missing("home recommend tab build method candidates=${buildTabMethods.size}")

    val tabResourceClassName = buildTabsMethod.listParameterGenericTypeName(0)
    val tabResourceClass = tabResourceClassName?.let(classLoader::loadClassOrNull)
        ?: HOME_TAB_RESOURCE_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        ?: return SymbolScanResult.Missing("home recommend tab resource class not found")
    val stringFields = tabResourceClass.declaredFields
        .filter { field ->
            field.type == String::class.java &&
                !Modifier.isStatic(field.modifiers)
        }
        .onEach { it.isAccessible = true }
    if (stringFields.size < 3) {
        return SymbolScanResult.Missing("home recommend tab string fields not found")
    }

    val symbols = HomeRecommendTabSymbols(
        buildTabsMethod = MethodDescriptor.of(buildTabsMethod),
        idField = FieldDescriptor.of(stringFields[0]),
        titleField = FieldDescriptor.of(stringFields[1]),
        uriField = FieldDescriptor.of(stringFields[2]),
        reporterIdField = stringFields.getOrNull(3)?.let(FieldDescriptor::of),
        evidence = "method=${buildTabsMethod.name},resource=${tabResourceClass.name},strings=${stringFields.size}",
    )
    return SymbolScanResult.Found(
        symbols,
        "${buildTabsMethod.declaringClass.name}.${buildTabsMethod.name}",
        symbols.evidence,
    )
}

private fun Method.isHomeRecommendTabBuildMethod(): Boolean {
    if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) return false
    if (parameterCount != 1) return false
    if (!List::class.java.isAssignableFrom(returnType)) return false
    if (!List::class.java.isAssignableFrom(parameterTypes[0])) return false
    val returnTypeName = genericReturnType.typeName
    val parameterTypeName = genericParameterTypes.firstOrNull()?.typeName.orEmpty()
    return "BasePrimaryMultiPageFragment" in returnTypeName &&
        "main2.resource." in parameterTypeName
}

private fun Method.listParameterGenericTypeName(index: Int): String? {
    val typeName = genericParameterTypes.getOrNull(index)?.typeName ?: return null
    if ('<' !in typeName || '>' !in typeName) return null
    return typeName
        .substringAfter('<')
        .substringBefore('>')
        .substringBefore(',')
        .trim()
        .removePrefix("? extends ")
        .removePrefix("? super ")
        .takeIf { it.isNotBlank() }
}

internal fun scanHomeComponentHide(
    classLoader: ClassLoader,
): SymbolScanResult<HomeComponentHideSymbols> {
    val fragmentClass = classLoader.loadClassOrNull(ANDROIDX_FRAGMENT_CLASS)
    val fragmentLifecycleMethods = listOfNotNull(
        fragmentClass?.findMethod("onViewCreated", Void.TYPE, View::class.java, Bundle::class.java),
        fragmentClass?.findMethod("onHiddenChanged", Void.TYPE, Boolean::class.javaPrimitiveType!!),
    ).distinctBy(Method::toGenericString)
    val baseClasses = BASE_HOME_FRAGMENT_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
        .toList()
    val methods = baseClasses
        .asSequence()
        .flatMap { type ->
            HOME_COMPONENT_LIFECYCLE_METHODS.asSequence().flatMap { methodName ->
                type.allMethods().asSequence().filter { it.name == methodName }
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()
    val componentCatalog = baseClasses
        .asSequence()
        .flatMap { it.declaredMethods.asSequence() }
        .filter {
            it.parameterCount == 0 &&
                List::class.java.isAssignableFrom(it.returnType) &&
                !Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers)
        }
        .distinctBy(Method::toGenericString)
        .singleOrNull()
    val fragmentLifecycleHookPoint = childHookPoint(
        HP_HOME_COMPONENT_HIDE_FRAGMENT,
        fragmentLifecycleMethods.isNotEmpty(),
        "androidx fragment lifecycle methods not found",
        "methods=${fragmentLifecycleMethods.size}",
    )
    if (fragmentLifecycleMethods.isEmpty()) {
        return SymbolScanResult.Missing(
            "androidx fragment lifecycle methods not found",
            hookPoints = listOf(fragmentLifecycleHookPoint),
        )
    }
    val symbols = HomeComponentHideSymbols(
        fragmentLifecycleMethods = fragmentLifecycleMethods.map(MethodDescriptor::of),
        baseHomeFragmentMethods = methods.map(MethodDescriptor::of),
        componentCatalogMethod = componentCatalog?.let(MethodDescriptor::of),
        evidence = "fragment=${fragmentLifecycleMethods.size},baseLifecycle=${methods.size},catalog=${componentCatalog != null}",
    )
    val hookPoints = listOf(
        fragmentLifecycleHookPoint,
        childHookPoint(
            HP_HOME_COMPONENT_HIDE_CATALOG,
            componentCatalog != null,
            "home component catalog list method not found",
            "method=${componentCatalog?.name}",
        ),
    )
    return SymbolScanResult.Found(
        symbols,
        (fragmentLifecycleMethods + methods).joinToString("|") { "${it.declaringClass.name}.${it.name}" },
        symbols.evidence,
        hookPoints,
    )
}

private const val HP_HOME_TOP_BAR_GAME = "HomeTopBarPurifyHook.GameMenu"

private const val HP_HOME_TOP_BAR_COMPOSE_GAME = "HomeTopBarPurifyHook.ComposeGameMenu"

private const val HP_HOME_TOP_BAR_VIEW_CREATED = "HomeTopBarPurifyHook.OnViewCreated"

private const val HP_HOME_TOP_BAR_DEFAULT_WORD = "HomeTopBarPurifyHook.DefaultSearchWord"

private const val HP_BOTTOM_BAR_TAB_HOST = "BottomBarHook.TabHost"

private const val HP_BOTTOM_BAR_VIEW_CREATED = "BottomBarHook.BaseOnViewCreated"

private const val HP_HOME_COMPONENT_HIDE_FRAGMENT = "HomeComponentHideHook.FragmentLifecycle"

private const val HP_HOME_COMPONENT_HIDE_CATALOG = "HomeComponentHideHook.ComponentCatalog"

private const val HOME_MENU_ITEM_CLASS = "com.bilibili.lib.homepage.startdust.menu.a"

private val HOME_BASE_MAIN_FRAME_FRAGMENTS = setOf(
    "tv.danmaku.bili.ui.main2.basic.BaseMainFrameFragment",
    "tv.danmaku.bili.ui.main2.MainFragment",
)

private const val HOME_MAIN_FRAGMENT = "tv.danmaku.bili.ui.main2.MainFragment"

private const val HOME_DEFAULT_SEARCH_WORD_CLASS = "com.bilibili.app.comm.list.common.api.b"

private val HOME_DEFAULT_SEARCH_WORD_ACCESSORS =
    listOf("getDefaultWordGoto", "getDefaultWordType", "getShowFront", "getFlashScreenQueryId")

private val BOTTOM_TAB_HOST_CLASSES = setOf(
    "com.bilibili.lib.homepage.widget.TabHost",
    "tv.danmaku.bili.ui.main2.widget.TabHost",
)

private val PEGASUS_RESPONSE_CLASSES = arrayOf(
    "com.bilibili.pegasus.data.base.PegasusResponse",
    "com.bilibili.pegasus.data.request.PegasusResponseWrapper",
)

private const val PEGASUS_HOLDER_DATA = "com.bilibili.pegasus.PegasusHolderData"

private val BASE_PEGASUS_DATA_CLASSES = arrayOf(
    "com.bilibili.pegasus.data.base.BasePegasusData",
)

private const val PEGASUS_HOLDER_STYLE = "com.bilibili.pegasus.HolderStyle"

private const val AD_INFO = "com.bilibili.adcommon.data.IAdInfo"

private val HOME_FRAGMENT_V2_CLASSES = arrayOf(
    "tv.danmaku.bili.ui.main2.HomeFragmentV2",
)

private val HOME_TAB_RESOURCE_CLASSES = arrayOf(
    "tv.danmaku.bili.ui.main2.resource.l",
    "tv.danmaku.bili.ui.main2.resource.z",
)

private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"

private val BASE_HOME_FRAGMENT_CLASSES = arrayOf(
    "tv.danmaku.bili.home.tab.page.BaseHomeFragment",
)

private val HOME_COMPONENT_LIFECYCLE_METHODS = listOf(
    "onAttach",
    "onCreate",
    "onCreateView",
    "onViewCreated",
    "onResume",
)
