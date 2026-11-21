package io.github.bbzq.feats.symbol

import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher

internal fun findDeclaredMethods(
    bridge: () -> DexKitBridge?,
    classLoader: ClassLoader,
    query: FindMethod.() -> FindMethod,
): List<Method> {
    val currentBridge = bridge() ?: return emptyList()
    return runCatching {
        currentBridge.findMethod(FindMethod.create().query())
            .mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .distinctBy(Method::toGenericString)
    }.getOrDefault(emptyList())
}

internal fun childHookPoint(id: String, found: Boolean, missing: String, evidence: String = "-"): HookPointStatus =
    if (found) {
        HookPointStatus.found(id, id.substringAfterLast('.'), evidence)
    } else {
        HookPointStatus.missing(id, "fail closed: $missing")
    }

internal fun optionalChildHookPoint(id: String, found: Boolean, missing: String, evidence: String = "-"): HookPointStatus =
    if (found) {
        HookPointStatus.found(id, id.substringAfterLast('.'), evidence)
    } else {
        HookPointStatus.optional(id, missing)
    }

internal fun Class<*>.findMethod(name: String, returnType: Class<*>, vararg parameterTypes: Class<*>): Method? =
    allMethods().firstOrNull {
        it.name == name &&
            it.returnType == returnType &&
            it.parameterTypes.contentEquals(parameterTypes)
    }

internal fun Class<*>.findNoArgMethod(name: String): Method? =
    allMethods().firstOrNull { it.name == name && it.parameterCount == 0 }

internal fun Class<*>.findNoArgStringMethod(name: String): Method? =
    findMethod(name, String::class.java)

internal fun Class<*>.findStringSetter(name: String): Method? =
    findMethod(name, Void.TYPE, String::class.java)

internal fun Class<*>.findIntegerSetter(name: String): Method? =
    findMethod(name, Void.TYPE, Int::class.javaObjectType)

internal fun Class<*>?.copyLikeMethods(): List<Method> {
    val type = this ?: return emptyList()
    return type.allMethods()
        .filter {
            !Modifier.isStatic(it.modifiers) &&
                it.returnType == type &&
                it.parameterCount >= 2
        }
        .toList()
}

internal fun findClassByString(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
    string: String,
    validator: (Class<*>) -> Boolean,
): ClassStringScan {
    val currentBridge = bridge() ?: return ClassStringScan(null, error = "DexKitBridge unavailable")
    val names = runCatching {
        currentBridge.findClass(
            FindClass.create()
                .matcher(ClassMatcher.create().usingStrings(string)),
        ).map { it.name }.toList()
    }.getOrElse { throwable ->
        return ClassStringScan(null, error = "DexKit string search failed: ${throwable.scanMessage()}")
    }
    val candidates = names
        .asSequence()
        .flatMap { name -> sequenceOf(name, name.substringBefore('$')).distinct() }
        .mapNotNull { name -> classLoader.loadClassOrNull(name) }
        .distinctBy { it.name }
        .toList()
    val validated = candidates.filter(validator)
    return when (validated.size) {
        1 -> ClassStringScan(type = validated.single(), candidates = candidates.size)
        0 -> ClassStringScan(null, error = "validated class not found", candidates = candidates.size)
        else -> ClassStringScan(
            null,
            error = "ambiguous classes=${validated.joinToString { it.name }.take(180)}",
            candidates = candidates.size,
        )
    }
}

internal fun Class<*>?.installWeight(vararg methods: Method?): Int =
    if (this == null) 0 else declaredConstructors.size + methods.count { it != null }

internal fun Class<*>?.installWeight(methods: List<Method>, vararg moreMethods: Method?): Int =
    if (this == null) 0 else declaredConstructors.size + methods.size + moreMethods.count { it != null }

private fun MutableList<Method>.addParserMethod(
    classLoader: ClassLoader,
    className: String,
    methodName: String,
    vararg parameterTypeNames: String,
) {
    val type = classLoader.loadClassOrNull(className) ?: return
    val parameterTypes = parameterTypeNames.map { resolveParameterType(classLoader, it) }
        .takeIf { it.none { resolved -> resolved == null } }
        ?.filterNotNull()
        ?.toTypedArray()
        ?: return
    val method = type.allMethods().firstOrNull {
        it.name == methodName && it.parameterTypes.contentEquals(parameterTypes)
    } ?: return
    add(method)
}

private fun resolveParameterType(classLoader: ClassLoader, typeName: String): Class<*>? = when (typeName) {
    "int" -> Int::class.javaPrimitiveType
    else -> classLoader.loadClassOrNull(typeName)
}

internal fun Class<*>.noArgMethod(name: String): Method? =
    allMethods().firstOrNull {
        it.name == name &&
            it.parameterCount == 0 &&
            !Modifier.isStatic(it.modifiers)
    }?.apply { isAccessible = true }

internal fun Class<*>.isPlayViewRequestType(): Boolean {
    val methods = allMethods().toList()
    return methods.any { it.name == "getBvid" && it.parameterCount == 0 } &&
        methods.any { method ->
            method.parameterCount == 0 &&
                method.name in setOf(
                    "getVod",
                    "getAid",
                    "getCid",
                    "getEpId",
                    "getIsNeedViewInfo",
                    "isNeedViewInfo",
                )
        }
}

internal fun findConcreteImplementors(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
    interfaceName: String,
): ControllerClassScan {
    val candidates = linkedSetOf<Class<*>>()
    val errors = ArrayList<String>()
    val currentBridge = bridge()
    if (currentBridge != null) {
        runCatching {
            currentBridge.findClass(
                FindClass.create()
                    .matcher(ClassMatcher.create().addInterface(interfaceName)),
            )
        }.onFailure { throwable ->
            errors += "DexKit implementor search failed for $interfaceName: ${throwable.scanMessage()}"
        }.getOrNull()?.forEach { classData ->
            runCatching {
                Class.forName(classData.name, false, classLoader)
            }.onSuccess { type ->
                candidates += type
            }.onFailure { throwable ->
                errors += "restore ${classData.name} failed: ${throwable.scanMessage()}"
            }
        }
    } else {
        errors += "DexKitBridge unavailable for $interfaceName"
    }

    val interfaceType = classLoader.loadClassOrNull(interfaceName)
    val classes = candidates
        .asSequence()
        .filter { type -> type.isConcreteHookClass() }
        .filter { type -> interfaceType?.isAssignableFrom(type) == true }
        .distinctBy { it.name }
        .toList()
    return ControllerClassScan(
        classes = classes,
        error = errors.takeIf { it.isNotEmpty() }?.joinToString("; ")?.take(360),
    )
}

internal fun Class<*>.currentPositionMethods(): List<Method> =
    allMethods()
        .filter {
            it.isConcreteInstanceHookMethod() &&
                it.name == "getCurrentPosition" &&
                it.parameterCount == 0 &&
                it.returnType.isNumericType()
        }
        .onEach { it.isAccessible = true }
        .toList()

internal fun Class<*>.stateMethods(stateMethodNames: Set<String>): List<Method> =
    allMethods()
        .filter {
            it.isConcreteInstanceHookMethod() &&
                it.name in stateMethodNames &&
                it.parameterCount == 0 &&
                it.returnType.isNumericType()
        }
        .onEach { it.isAccessible = true }
        .toList()

internal fun Class<*>.seekMethods(): List<Method> =
    allMethods()
        .filter {
            it.isConcreteInstanceHookMethod() &&
                it.isSeekToMethod()
        }
        .onEach { it.isAccessible = true }
        .toList()

internal fun Method.isConcreteInstanceHookMethod(): Boolean =
    !Modifier.isStatic(modifiers) &&
        !Modifier.isAbstract(modifiers) &&
        !declaringClass.isInterface

internal fun Class<*>.isConcreteHookClass(): Boolean =
    !isInterface && !Modifier.isAbstract(modifiers)

private fun Method.isSeekToMethod(): Boolean {
    if (name != "seekTo" || parameterCount !in 1..2) return false
    if (!parameterTypes[0].isNumericType()) return false
    return parameterCount == 1 || parameterTypes[1].isBooleanType()
}

internal fun Class<*>.isNumericType(): Boolean =
    this == Int::class.javaPrimitiveType ||
        this == Int::class.javaObjectType ||
        this == Long::class.javaPrimitiveType ||
        this == Long::class.javaObjectType

private fun Class<*>.isBooleanType(): Boolean =
    this == Boolean::class.javaPrimitiveType || this == Boolean::class.javaObjectType

internal fun findClassNamesBySimpleName(
    bridge: () -> DexKitBridge?,
    simpleName: String,
): List<String> {
    val currentBridge = bridge() ?: return emptyList()
    return try {
        currentBridge.findClass(
            FindClass.create()
                .searchPackages("com.bilibili", "tv.danmaku")
                .matcher(ClassMatcher.create().className(simpleName, StringMatchType.Contains)),
        ).map { it.name }.toList()
    } catch (_: Throwable) {
        emptyList()
    }
}

/** 欄位型別可能無法解析（例如宿主內附的華為 SDK 缺類），此時視為沒有欄位，不讓整個掃描失敗。 */
internal fun Class<*>.safeDeclaredFields(): List<java.lang.reflect.Field> =
    runCatching { declaredFields.toList() }.getOrDefault(emptyList())

internal fun findClassNamesByNameContains(
    bridge: () -> DexKitBridge?,
    terms: List<String>,
): List<String> {
    val currentBridge = bridge() ?: return emptyList()
    return terms.flatMap { term ->
        try {
            currentBridge.findClass(
                FindClass.create()
                    .matcher(ClassMatcher.create().className(term, StringMatchType.Contains)),
            ).map { it.name }.toList()
        } catch (t: Throwable) {
            throw IllegalStateException("DexKit class name search failed term=$term: ${t.scanMessage()}", t)
        }
    }.distinct()
}

internal data class ControllerClassScan(
    val classes: List<Class<*>>,
    val error: String?,
)

internal data class ClassStringScan(
    val type: Class<*>?,
    val error: String? = null,
    val candidates: Int = 0,
) {
    fun missingReason(defaultReason: String): String =
        error?.let { "$defaultReason: $it" } ?: defaultReason
}

internal sealed class SymbolScanResult<out T> {
    data class Found<T : Any>(
        val value: T,
        val target: String,
        val evidence: String,
        val hookPoints: List<HookPointStatus> = emptyList(),
    ) : SymbolScanResult<T>()

    data class Missing(
        val reason: String,
        val hookPoints: List<HookPointStatus> = emptyList(),
    ) : SymbolScanResult<Nothing>()
}

internal const val STORY_PAGER_PLAYER = "com.bilibili.video.story.player.StoryPagerPlayer"

internal const val KOTLIN_UNIT = "kotlin.Unit"

internal val PLAY_VIEW_METHOD_NAMES = setOf("executePlayView", "executePlayViewUnite", "playViewUnite", "playView")

internal val PLAYER_MOSS_CANDIDATES = arrayOf(
    "com.bapis.bilibili.app.playerunite.v1.PlayerMoss",
    "com.bapis.bilibili.app.playerunite.v1.KPlayerMoss",
)

internal const val STORY_SEEK_BAR_CLASS = "com.bilibili.video.story.view.StorySeekBar"

internal const val INTERACT_LAYER_SERVICE =
    "tv.danmaku.biliplayerv2.service.interact.biz.InteractLayerService"
