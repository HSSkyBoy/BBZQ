package io.github.bbzq.feats.symbol

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal fun scanShare(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<ShareSymbols> {
    val legacyClass = classLoader.loadClassOrNull(SHARE_LEGACY_RESULT)
    val legacyGetLink = legacyClass?.findNoArgStringMethod("getLink")
    val legacyGetContent = legacyClass?.findNoArgStringMethod("getContent")
    val legacyGetShareMode = legacyClass?.findNoArgMethod("getShareMode")
    val legacySetLink = legacyClass?.findStringSetter("setLink")
    val legacySetContent = legacyClass?.findStringSetter("setContent")
    val legacySetShareMode = legacyClass?.findIntegerSetter("setShareMode")
    val legacyCount = listOfNotNull(
        legacyGetLink,
        legacyGetContent,
        legacyGetShareMode,
        legacySetLink,
        legacySetContent,
        legacySetShareMode,
    ).size

    val shareChannelsClass = classLoader.loadClassOrNull(SHARE_CHANNELS)
    val shareChannelsGetCopyLink = shareChannelsClass?.findNoArgStringMethod("getCopyLink")
    val shareChannelsGetJumpLink = shareChannelsClass?.findNoArgStringMethod("getJumpLink")
    val shareChannelsGetText = shareChannelsClass?.findNoArgStringMethod("getText")
    val shareChannelsSetCopyLink = shareChannelsClass?.findStringSetter("setCopyLink")
    val shareChannelsSetJumpLink = shareChannelsClass?.findStringSetter("setJumpLink")
    val shareChannelsSetText = shareChannelsClass?.findStringSetter("setText")
    val shareChannelItemClass = classLoader.loadClassOrNull(SHARE_CHANNEL_ITEM)
    val shareChannelItemGetJumpLink = shareChannelItemClass?.findNoArgStringMethod("getJumpLink")
    val shareChannelItemSetJumpLink = shareChannelItemClass?.findStringSetter("setJumpLink")
    val shareChannelsCount = listOfNotNull(
        shareChannelsGetCopyLink,
        shareChannelsGetJumpLink,
        shareChannelsGetText,
        shareChannelsSetCopyLink,
        shareChannelsSetJumpLink,
        shareChannelsSetText,
        shareChannelItemGetJumpLink,
        shareChannelItemSetJumpLink,
    ).size

    val shareBaseInfoScan = findShareBaseInfoClass(classLoader, bridge)
    val shareBaseInfoClass = shareBaseInfoScan.type
    val shareBaseInfoCount = shareBaseInfoClass?.declaredConstructors
        ?.count { it.isShareBaseInfoConstructor() }
        ?: 0

    val shareContentClass = SHARE_CONTENT_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
    val shareContentCopyMethods = shareContentClass?.copyLikeMethods().orEmpty()
    val shareContentGetLink = shareContentClass?.findNoArgStringMethod("getLink")
    val shareContentGetContent = shareContentClass?.findNoArgStringMethod("getContent")
    val shareContentGetMode = shareContentClass?.findNoArgMethod("getMode")
    val shareContentCount = shareContentClass.installWeight(
        shareContentCopyMethods,
        shareContentGetLink,
        shareContentGetContent,
        shareContentGetMode,
    )

    val shareBiliContentClass = SHARE_BILI_CONTENT_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
    val shareBiliContentCopyMethods = shareBiliContentClass?.copyLikeMethods().orEmpty()
    val shareBiliContentGetDescription = shareBiliContentClass?.findNoArgStringMethod("getDescription")
    val shareBiliContentGetContentUrl = shareBiliContentClass?.findNoArgStringMethod("getContentUrl")
    val shareBiliContentCount = shareBiliContentClass.installWeight(
        shareBiliContentCopyMethods,
        shareBiliContentGetDescription,
        shareBiliContentGetContentUrl,
    )

    val copyHandlerClass = SHARE_COPY_UTILITY_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
    val copyContentClass = SHARE_COPY_CONTENT_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        ?: copyHandlerClass?.declaredMethods
            ?.asSequence()
            ?.flatMap { it.parameterTypes.asSequence() }
            ?.distinct()
            ?.firstOrNull { type ->
                !type.isPrimitive &&
                    !type.isInterface &&
                    type.declaredConstructors.any { ctor ->
                        ctor.parameterTypes.contentEquals(arrayOf(String::class.java))
                    } &&
                    (type.findNoArgStringMethod("c") != null || type.findNoArgStringMethod("mo127c") != null)
            }
    val copyContentGetters = listOfNotNull(
        copyContentClass?.findNoArgStringMethod("c"),
        copyContentClass?.findNoArgStringMethod("mo127c"),
    )
    val copyContentCount = copyContentClass.installWeight(copyContentGetters)

    val copyUtilityMethods = copyHandlerClass
        ?.allMethods()
        ?.filter {
            Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }
        ?.toList()
        .orEmpty()
        .ifEmpty {
            // 9.20 起 CopyShareHandler 佔用 copy.a，靜態剪貼簿工具方法遷至根包（如 t930.a(String)）
            findDeclaredMethods(bridge, classLoader) {
                matcher(
                    MethodMatcher.create()
                        .modifiers(Modifier.STATIC)
                        .paramTypes(String::class.java)
                        .usingStrings(SHARE_COPY_CLIPBOARD_LOG),
                )
            }.filter {
                Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }.distinctBy(Method::toGenericString)
        }
    val copyUtilityCount = copyUtilityMethods.size
    val hookPoints = listOf(
        optionalChildHookPoint(HP_SHARE_LEGACY, legacyCount > 0, "legacy share result hooks not found", "methods=$legacyCount"),
        optionalChildHookPoint(
            HP_SHARE_CHANNELS,
            shareChannelsCount > 0,
            "share channels link/text hooks not found",
            "methods=$shareChannelsCount",
        ),
        optionalChildHookPoint(
            HP_SHARE_BASE_INFO,
            shareBaseInfoCount > 0,
            shareBaseInfoScan.missingReason("share base info class not found"),
            "constructors=$shareBaseInfoCount",
        ),
        optionalChildHookPoint(HP_SHARE_CONTENT, shareContentCount > 0, "share content hooks not found", "methods=$shareContentCount"),
        optionalChildHookPoint(HP_SHARE_BILI_CONTENT, shareBiliContentCount > 0, "share bili content hooks not found", "methods=$shareBiliContentCount"),
        optionalChildHookPoint(HP_SHARE_COPY_CONTENT, copyContentCount > 0, "copy content hooks not found", "methods=$copyContentCount"),
        optionalChildHookPoint(HP_SHARE_COPY_UTILITY, copyUtilityCount > 0, "copy utility hook not found", "methods=$copyUtilityCount"),
    )
    val total = legacyCount + shareChannelsCount + shareBaseInfoCount +
        shareContentCount + shareBiliContentCount + copyContentCount + copyUtilityCount + 1
    val symbols = ShareSymbols(
        legacyGetLink = legacyGetLink?.let(MethodDescriptor::of),
        legacyGetContent = legacyGetContent?.let(MethodDescriptor::of),
        legacyGetShareMode = legacyGetShareMode?.let(MethodDescriptor::of),
        legacySetLink = legacySetLink?.let(MethodDescriptor::of),
        legacySetContent = legacySetContent?.let(MethodDescriptor::of),
        legacySetShareMode = legacySetShareMode?.let(MethodDescriptor::of),
        shareChannelsGetCopyLink = shareChannelsGetCopyLink?.let(MethodDescriptor::of),
        shareChannelsGetJumpLink = shareChannelsGetJumpLink?.let(MethodDescriptor::of),
        shareChannelsGetText = shareChannelsGetText?.let(MethodDescriptor::of),
        shareChannelsSetCopyLink = shareChannelsSetCopyLink?.let(MethodDescriptor::of),
        shareChannelsSetJumpLink = shareChannelsSetJumpLink?.let(MethodDescriptor::of),
        shareChannelsSetText = shareChannelsSetText?.let(MethodDescriptor::of),
        shareChannelItemGetJumpLink = shareChannelItemGetJumpLink?.let(MethodDescriptor::of),
        shareChannelItemSetJumpLink = shareChannelItemSetJumpLink?.let(MethodDescriptor::of),
        shareBaseInfoClassName = shareBaseInfoClass?.name,
        shareContentClassName = shareContentClass?.name,
        shareContentCopyMethods = shareContentCopyMethods.map(MethodDescriptor::of),
        shareContentGetLink = shareContentGetLink?.let(MethodDescriptor::of),
        shareContentGetContent = shareContentGetContent?.let(MethodDescriptor::of),
        shareContentGetMode = shareContentGetMode?.let(MethodDescriptor::of),
        shareBiliContentClassName = shareBiliContentClass?.name,
        shareBiliContentCopyMethods = shareBiliContentCopyMethods.map(MethodDescriptor::of),
        shareBiliContentGetDescription = shareBiliContentGetDescription?.let(MethodDescriptor::of),
        shareBiliContentGetContentUrl = shareBiliContentGetContentUrl?.let(MethodDescriptor::of),
        copyContentClassName = copyContentClass?.name,
        copyContentGetters = copyContentGetters.map(MethodDescriptor::of),
        copyUtilityMethods = copyUtilityMethods.map(MethodDescriptor::of),
        evidence = "methods=$total",
    )
    return SymbolScanResult.Found(symbols, "ClipboardFallback", symbols.evidence, hookPoints)
}

internal fun scanTeenagersMode(classLoader: ClassLoader): SymbolScanResult<TeenagersModeSymbols> {
    val methods = TEENAGERS_MODE_ACTIVITIES.mapNotNull { className ->
        classLoader.loadClassOrNull(className)?.let { type ->
            type.allMethods()
                .firstOrNull {
                    it.name == "onCreate" &&
                        it.returnType == Void.TYPE &&
                        it.parameterTypes.contentEquals(arrayOf(Bundle::class.java))
                }
        }
    }
    if (methods.isEmpty()) return SymbolScanResult.Missing("teenagers mode dialog activity not found")
    val symbols = TeenagersModeSymbols(
        onCreateMethods = methods.map(MethodDescriptor::of),
        evidence = "activities=${methods.size}",
    )
    return SymbolScanResult.Found(symbols, methods.joinToString("|") { "${it.declaringClass.name}.${it.name}" }, symbols.evidence)
}

internal fun scanSettings(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<SettingsSymbols> {
    val fragmentNames = (
        SETTINGS_FRAGMENT_NAMES.asSequence() +
            findClassNamesBySimpleName(bridge, "BiliPreferencesFragment").asSequence() +
            findClassNamesBySimpleName(bridge, "WideBiliPreferencesFragment").asSequence()
        ).distinct()

    val methods = fragmentNames
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .mapNotNull { type ->
            type.declaredMethods.firstOrNull { method ->
                method.name == "onCreatePreferences" && method.parameterCount == 2
            }?.apply { isAccessible = true }
        }
        .distinctBy { it.declaringClass.name + "#" + it.name + it.parameterTypes.joinToString { type -> type.name } }
        .toList()

    val preferenceClass = PREFERENCE_CLASS_NAMES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        ?: return SymbolScanResult.Missing("preference class not found")
    if (methods.isEmpty()) {
        return SymbolScanResult.Missing("onCreatePreferences fragment method not found")
    }
    val symbols = SettingsSymbols(
        fragmentMethods = methods.map(MethodDescriptor::of),
        preferenceClassName = preferenceClass.name,
        evidence = "fragments=${methods.size},preference=${preferenceClass.name}",
    )
    return SymbolScanResult.Found(symbols, methods.joinToString("|") { it.declaringClass.name }, symbols.evidence)
}

internal fun scanBlockUpdate(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<BlockUpdateSymbols> {
    val helperMethods = classLoader.loadClassOrNull("tv.danmaku.bili.update.api.UpdateHelper")?.declaredMethods
        .orEmpty().filter { method ->
            Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE && when (method.name) {
                "checkUpdateInStartup" -> method.parameterTypes.map { it.name } ==
                    listOf("android.app.Activity", "tv.danmaku.bili.update.api.updater.IUpdater")
                "checkUpdateAndShowDialog" -> method.parameterTypes.map { it.name } ==
                    listOf("android.content.Context", "tv.danmaku.bili.update.api.updater.IUpdater")
                "checkInternalUpdateFlag" -> method.parameterTypes.contentEquals(arrayOf(Context::class.java))
                else -> false
            }
        }.map(MethodDescriptor::of)
    val direct = classLoader.loadClassOrNull("Bx1.c")?.declaredMethods?.singleOrNull {
        it.isBlockUpdateCheckCandidate() && !it.isBridge && !it.isSynthetic
    }
    if (direct != null) {
        val symbols = BlockUpdateSymbols(MethodDescriptor.of(direct), "verifiedNetworkOwner,helpers=${helperMethods.size}", helperMethods)
        return SymbolScanResult.Found(symbols, "${direct.declaringClass.name}.${direct.name}", symbols.evidence)
    }
    val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
    val searchErrors = ArrayList<String>()
    val signatureMethodData = runCatching {
        currentBridge.findMethod(
            FindMethod.create()
                .matcher(
                    MethodMatcher.create()
                        .returnType(BILI_UPGRADE_INFO)
                        .paramTypes(Context::class.java),
                ),
        )
    }.getOrElse { throwable ->
        searchErrors += "signature Context->BiliUpgradeInfo: ${throwable.scanMessage()}"
        emptyList()
    }
    val stringMethodData = BLOCK_UPDATE_STRING_RULES.asSequence()
        .mapNotNull { strings ->
            runCatching {
                currentBridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            strings.fold(
                                MethodMatcher.create()
                                    .paramTypes(Context::class.java),
                            ) { matcher, string -> matcher.usingStrings(string) },
                        ),
                )
            }.getOrElse { throwable ->
                searchErrors += strings.joinToString("+") + ": " + throwable.scanMessage()
                null
            }
        }
        .flatMap { it.asSequence() }
        .distinctBy { it.descriptor }
        .toList()
    if (signatureMethodData.isEmpty() && stringMethodData.isEmpty() && searchErrors.isNotEmpty()) {
        return SymbolScanResult.Missing("update check method search failed: ${searchErrors.joinToString("; ")}")
    }
    val restoreErrors = ArrayList<String>()
    val stringCandidates = stringMethodData
        .mapNotNull { data ->
            runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                null
            }
        }
        .filter { it.isBlockUpdateCheckCandidate() }
        .distinctBy(Method::toGenericString)
        .toList()
    val stringCandidateKeys = stringCandidates.map(Method::toGenericString).toSet()
    val candidates = (signatureMethodData.asSequence() + stringMethodData.asSequence())
        .distinctBy { it.descriptor }
        .mapNotNull { data ->
            runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                null
            }
        }
        .filter { it.isBlockUpdateCheckCandidate() }
        .distinctBy(Method::toGenericString)
        .toList()
    val supplierCandidates = candidates
        .mapNotNull { method ->
            method.findBlockUpdateSupplierInterface()?.let { supplierInterface ->
                method to supplierInterface
            }
        }
    val leafSupplierCandidates = supplierCandidates
        .filterNot { (method, supplierInterface) ->
            method.declaringClass.hasBlockUpdateDelegateField(supplierInterface)
        }
    val method = when {
        stringCandidates.size == 1 -> stringCandidates.single()
        leafSupplierCandidates.size == 1 -> leafSupplierCandidates.single().first
        supplierCandidates.size == 1 -> supplierCandidates.single().first
        candidates.size == 1 -> candidates.single()
        candidates.isEmpty() -> {
            val restoreHint = restoreErrors.take(2).joinToString("; ").takeIf { it.isNotBlank() }
            return SymbolScanResult.Missing(
                buildString {
                    append("update check method not found")
                    append(": signature=${signatureMethodData.size},string=${stringMethodData.size}")
                    append(",restored=0")
                    if (restoreHint != null) append(",restore=$restoreHint")
                },
            )
        }
        else -> return SymbolScanResult.Missing(
            "update check method ambiguous: " +
                "candidates=${candidates.size},supplier=${supplierCandidates.size},leaf=${leafSupplierCandidates.size}," +
                candidates.joinToString(limit = 4) { it.declaringClass.name },
        )
    }
    val symbols = BlockUpdateSymbols(
        checkMethod = MethodDescriptor.of(method),
        helperMethods = helperMethods,
        evidence = "${method.declaringClass.name}.${method.name}," +
            "signature=${signatureMethodData.size},string=${stringMethodData.size}," +
            "stringHit=${method.toGenericString() in stringCandidateKeys}",
    )
    return SymbolScanResult.Found(symbols, symbols.evidence, symbols.evidence)
}

private fun Method.isBlockUpdateCheckCandidate(): Boolean {
    if (parameterTypes.contentEquals(arrayOf(Context::class.java)).not()) return false
    if (returnType.name != BILI_UPGRADE_INFO) return false
    if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) return false
    if (declaringClass.isInterface || Modifier.isAbstract(declaringClass.modifiers)) return false
    return true
}

private fun Method.findBlockUpdateSupplierInterface(): Class<*>? =
    declaringClass.interfaces.firstOrNull { candidate ->
        candidate.allMethods().any { method ->
            method.parameterTypes.contentEquals(arrayOf(Context::class.java)) &&
                method.returnType.name == BILI_UPGRADE_INFO
        }
    }

private fun Class<*>.hasBlockUpdateDelegateField(supplierInterface: Class<*>): Boolean =
    allFields().any { field ->
        !Modifier.isStatic(field.modifiers) &&
            field.type != this &&
            supplierInterface.isAssignableFrom(field.type)
    }

internal fun scanDownloadThreadListeners(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<List<DownloadThreadListenerSymbols>> {
    val classNames = findClassNamesByNameContains(
        bridge = bridge,
        terms = DOWNLOAD_THREAD_LISTENER_CLASS_TERMS,
    )
    val listeners = classNames
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
        .mapNotNull { type -> type.toDownloadThreadListenerSymbols() }
        .distinctBy { it.className }
        .toList()

    if (listeners.isEmpty()) {
        return SymbolScanResult.Missing("download thread listener class not verified")
    }
    return SymbolScanResult.Found(
        listeners,
        listeners.joinToString("|") { it.className },
        "dexkitClassName+TextViewCtor+onClick+TextViewField",
    )
}

private fun Class<*>.toDownloadThreadListenerSymbols(): DownloadThreadListenerSymbols? {
    if (isInterface || Modifier.isAbstract(modifiers)) return null
    val constructor = declaredConstructors.firstOrNull { ctor ->
        ctor.parameterTypes.any { TextView::class.java.isAssignableFrom(it) }
    }?.apply { isAccessible = true } ?: return null

    val onClick = allMethods().firstOrNull { method ->
        method.name == "onClick" &&
            method.parameterCount == 1 &&
            View::class.java.isAssignableFrom(method.parameterTypes[0])
    } ?: return null

    val textViewField = allFields().firstOrNull { field ->
        TextView::class.java.isAssignableFrom(field.type)
    } ?: return null

    return DownloadThreadListenerSymbols(
        className = name,
        constructor = ConstructorDescriptor.of(constructor),
        onClick = MethodDescriptor.of(onClick),
        textViewField = FieldDescriptor.of(textViewField),
    )
}

internal fun Class<*>.isKotlinContinuationTypeName(): Boolean =
    name == "kotlin.coroutines.Continuation" ||
        name == "kotlin.coroutines.jvm.internal.ContinuationImpl"

private fun findShareBaseInfoClass(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): ClassStringScan {
    return findClassByString(classLoader, bridge, SHARE_BASE_INFO_TO_STRING) { type ->
        type.isShareBaseInfoType()
    }
}

private fun Class<*>.isShareBaseInfoType(): Boolean =
    declaredConstructors.any { it.isShareBaseInfoConstructor() }

private fun java.lang.reflect.Constructor<*>.isShareBaseInfoConstructor(): Boolean =
    parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java, String::class.java))

internal fun scanFullNumberFormat(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<FullNumberFormatSymbols> {
    val formatterClasses = NUMBER_FORMAT_CLASS_NAMES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
        .toList()
    val legacyMethods = formatterClasses
        .asSequence()
        .flatMap { type -> type.allMethods() }
        .filter { method -> method.isFullNumberFormatterMethod() }
        .toList()
    // The KMP formatter (万/亿 threshold rules) backs newer screens; its core class is only
    // reachable through the remote-config key it reads, so find it by that string.
    val kmpCoreClass = bridge()?.let { currentBridge ->
        runCatching {
            currentBridge.findMethod(
                FindMethod.create().matcher(MethodMatcher.create().usingStrings(KMP_NUMBER_FORMAT_RULE_KEY)),
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader).declaringClass }.getOrNull() }
                .distinctBy { it.name }
                .singleOrNull()
        }.getOrNull()
    }
    val kmpMethods = (formatterClasses + listOfNotNull(kmpCoreClass))
        .asSequence()
        .plus(KMP_NUMBER_FORMAT_FACADE_NAMES.mapNotNull { classLoader.loadClassOrNull(it) })
        .distinctBy { it.name }
        .flatMap { type -> type.declaredMethods.asSequence() }
        .filter { method -> method.isKmpNumberFormatterMethod() }
        .toList()
    // Other call sites inline their own "万"/"亿" formatting; collect compact-count formatters by
    // their unit strings. The hook only rewrites a result that provably equals the compact form
    // of its numeric argument, so a loose match here cannot corrupt unrelated text.
    val heuristicMethods = bridge()?.let { currentBridge ->
        COMPACT_UNIT_STRINGS.flatMap { unit ->
            runCatching {
                currentBridge.findMethod(
                    FindMethod.create().matcher(MethodMatcher.create().usingStrings(unit)),
                ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            }.getOrDefault(emptyList())
        }
    }.orEmpty()
        .filter { method ->
            method.returnType == String::class.java &&
                method.parameterCount in 1..3 &&
                method.parameterTypes[0].let { it == Long::class.javaPrimitiveType || it == Int::class.javaPrimitiveType } &&
                !method.declaringClass.name.startsWith("kotlin.") &&
                !method.declaringClass.name.startsWith("android")
        }
        .onEach { it.isAccessible = true }
        .take(MAX_HEURISTIC_NUMBER_METHODS)
    val methods = (legacyMethods + kmpMethods + heuristicMethods).distinctBy(Method::toGenericString)
    if (methods.isEmpty()) return SymbolScanResult.Missing("number formatter methods not found")

    val symbols = FullNumberFormatSymbols(
        formatterMethods = methods.map(MethodDescriptor::of),
        evidence = "classes=${formatterClasses.size},legacy=${legacyMethods.size},kmp=${kmpMethods.size}," +
            "kmpCore=${kmpCoreClass?.name ?: "missing"},heuristic=${heuristicMethods.size}",
    )
    return SymbolScanResult.Found(
        symbols,
        methods.joinToString("|") { "${it.declaringClass.name}.${it.name}" },
        symbols.evidence,
    )
}

// formatNumber(long|int, String, int) is the stable facade; the core class takes
// (long, int, boolean) and (int, int) and returns the formatted string directly.
private fun Method.isKmpNumberFormatterMethod(): Boolean {
    if (!Modifier.isStatic(modifiers) || returnType != String::class.java) return false
    val params = parameterTypes
    val first = params.firstOrNull()
    if (first != Long::class.javaPrimitiveType && first != Int::class.javaPrimitiveType) return false
    val facade = name == "formatNumber" && params.size == 3 &&
        params[1] == String::class.java && params[2] == Int::class.javaPrimitiveType
    val coreLong = params.size == 3 && first == Long::class.javaPrimitiveType &&
        params[1] == Int::class.javaPrimitiveType && params[2] == Boolean::class.javaPrimitiveType
    val coreInt = params.size == 2 && first == Int::class.javaPrimitiveType &&
        params[1] == Int::class.javaPrimitiveType
    return facade || (declaringClass.name !in KMP_NUMBER_FORMAT_FACADE_NAMES && (coreLong || coreInt))
}

private fun Method.isFullNumberFormatterMethod(): Boolean {
    if (!Modifier.isStatic(modifiers)) return false
    if (returnType != String::class.java) return false
    if (name !in FULL_NUMBER_FORMAT_METHOD_NAMES) return false
    val params = parameterTypes
    if (params.size !in 1..2) return false
    val first = params[0]
    if (first != Long::class.javaPrimitiveType && first != Int::class.javaPrimitiveType) return false
    if (params.size == 2 && params[1] != String::class.java) return false
    return true
}

internal fun scanVideoComment(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<VideoCommentSymbols> {
    val disableCommentConstructors = THESEUS_TAB_PAGER_SERVICE
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { it.declaredConstructors.asSequence() }
        .onEach { it.isAccessible = true }
        .map(ConstructorDescriptor::of)
        .toList()

    val commentViewModelClasses = COMMENT_VIEW_MODEL_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
        .toList()
    val quickReplyActionScan = findQuickReplyActionClasses(classLoader, bridge)
    val quickReplyActionClasses = quickReplyActionScan.classes

    val quickReplyViewModelMethods = commentViewModelClasses
        .asSequence()
        .flatMap { type ->
            (sequenceOf(type) + type.declaredClasses.asSequence()).flatMap { it.declaredMethods.asSequence() }
        }
        .filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterCount == 1 &&
                !method.name.contains("lambda", ignoreCase = true) &&
                method.parameterTypes.firstOrNull()?.isCommentActionType(quickReplyActionClasses) == true
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val quickReplyDispatcherMethods = commentViewModelClasses
        .asSequence()
        .flatMap { type ->
            type.declaredClasses.asSequence().flatMap { it.declaredMethods.asSequence() }
        }
        .filter { method ->
            method.returnType == Void.TYPE &&
                method.parameterCount == 1 &&
                !method.name.contains("lambda", ignoreCase = true) &&
                method.parameterTypes.firstOrNull()?.isCommentActionBaseType(quickReplyActionClasses) == true
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val quickReplyActionBaseMethods = (
        COMMENT_ACTION_BASE_CLASSES.asSequence().mapNotNull { classLoader.loadClassOrNull(it) } +
            quickReplyActionClasses.asSequence() +
            quickReplyDispatcherMethods.asSequence().mapNotNull { it.parameterTypes.firstOrNull() }
        )
        .distinctBy { it.name }
        .flatMap { type ->
            type.allMethods().filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0].isInterface &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    !Modifier.isStatic(method.modifiers)
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val quickReplyActionMethods = (quickReplyViewModelMethods + quickReplyDispatcherMethods + quickReplyActionBaseMethods)
        .distinctBy(Method::toGenericString)

    val quickReplyDialogMethods = QUICK_REPLY_DIALOG_COLLECTOR_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { type ->
            type.declaredMethods.asSequence().filter { method ->
                method.parameterCount == 2 &&
                    !method.name.contains("lambda", ignoreCase = true) &&
                    method.parameterTypes.firstOrNull()?.isQuickReplyDialogIntentType() == true &&
                    method.parameterTypes.getOrNull(1)?.let { Continuation::class.java.isAssignableFrom(it) } == true
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val voteWidgetMethods = (CMT_VOTE_WIDGET_CLASSES + CMT_MOUNT_WIDGET_CLASSES + COMMENT_VOTE_VIEW_CLASSES)
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { type ->
            type.declaredMethods.asSequence().filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount >= 1 &&
                    !method.name.contains("lambda", ignoreCase = true) &&
                    !method.name.endsWith("\$default") &&
                    method.name.substringBefore('$') in VOTE_WIDGET_METHOD_NAMES
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val followWidgetMethods = COMMENT_FOLLOW_WIDGET_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { type ->
            type.declaredMethods.asSequence().filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount >= 1 &&
                    !method.name.contains("lambda", ignoreCase = true) &&
                    (method.name in FOLLOW_WIDGET_METHOD_NAMES || method.isFollowWidgetBindOverride(type))
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val headerDecorativeMethods = COMMENT_HEADER_DECORATIVE_VIEW_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { type ->
            type.declaredMethods.asSequence().filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterCount >= 1 &&
                    !method.name.contains("lambda", ignoreCase = true) &&
                    method.name in HEADER_DECORATIVE_METHOD_NAMES &&
                    List::class.java.isAssignableFrom(method.parameterTypes[0])
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()

    val searchUrlsMethod = COMMENT_CONTENT_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .mapNotNull { it.allMethods().firstOrNull { method -> method.name == "internalGetUrls" && method.parameterCount == 0 } }
        .firstOrNull()

    val emptyPageHooks = scanVideoCommentEmptyPageHooks(classLoader)
    val mainListOnNextMethods = COMMENT_MAIN_LIST_OBSERVERS
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .mapNotNull { it.allMethods().firstOrNull { method -> method.name == "onNext" && method.parameterCount == 1 } }
        .distinctBy(Method::toGenericString)
        .toList()

    val total = disableCommentConstructors.size + quickReplyActionMethods.size + quickReplyDialogMethods.size +
        voteWidgetMethods.size + followWidgetMethods.size + headerDecorativeMethods.size +
        (if (searchUrlsMethod != null) 1 else 0) + emptyPageHooks.size + mainListOnNextMethods.size
    if (total == 0) return SymbolScanResult.Missing("video comment hook points not found")

    val symbols = VideoCommentSymbols(
        disableCommentConstructors = disableCommentConstructors,
        quickReplyViewModelMethods = quickReplyActionMethods.map(MethodDescriptor::of),
        quickReplyDialogMethods = quickReplyDialogMethods.map(MethodDescriptor::of),
        voteWidgetMethods = voteWidgetMethods.map(MethodDescriptor::of),
        followWidgetMethods = followWidgetMethods.map(MethodDescriptor::of),
        headerDecorativeMethods = headerDecorativeMethods.map(MethodDescriptor::of),
        searchUrlsMethod = searchUrlsMethod?.let(MethodDescriptor::of),
        emptyPageHooks = emptyPageHooks,
        mainListOnNextMethods = mainListOnNextMethods.map(MethodDescriptor::of),
        evidence = "constructors=${disableCommentConstructors.size},quick=${quickReplyActionMethods.size + quickReplyDialogMethods.size},quickScan=${quickReplyActionScan.evidence},widgets=${voteWidgetMethods.size + followWidgetMethods.size + headerDecorativeMethods.size},empty=${emptyPageHooks.size},main=${mainListOnNextMethods.size}",
    )
    val quickReplyCount = quickReplyActionMethods.size + quickReplyDialogMethods.size
    val widgetCount = voteWidgetMethods.size + followWidgetMethods.size + headerDecorativeMethods.size
    val hookPoints = listOf(
        childHookPoint(
            HP_VIDEO_COMMENT_DISABLE,
            disableCommentConstructors.isNotEmpty(),
            "disable comment constructor hook not found",
            "constructors=${disableCommentConstructors.size}",
        ),
        childHookPoint(
            HP_VIDEO_COMMENT_QUICK_REPLY,
            quickReplyCount > 0,
            "quick reply hook methods not found",
            "methods=$quickReplyCount,${quickReplyActionScan.evidence}",
        ),
        childHookPoint(HP_VIDEO_COMMENT_WIDGETS, widgetCount > 0, "comment widget hook methods not found", "methods=$widgetCount"),
        childHookPoint(HP_VIDEO_COMMENT_SEARCH, searchUrlsMethod != null, "comment search url method not found"),
        childHookPoint(HP_VIDEO_COMMENT_EMPTY_PAGE, emptyPageHooks.isNotEmpty(), "empty page hook points not found", "hooks=${emptyPageHooks.size}"),
        childHookPoint(HP_VIDEO_COMMENT_MAIN_LIST, mainListOnNextMethods.isNotEmpty(), "main list onNext hook methods not found", "methods=${mainListOnNextMethods.size}"),
    )
    return SymbolScanResult.Found(symbols, "VideoComment", symbols.evidence, hookPoints)
}

// 关注按钮的绑定入口是它直接实现的单方法接口(9.15 为 r,9.17 为 g0),名称随混淆漂移
private fun Method.isFollowWidgetBindOverride(owner: Class<*>): Boolean =
    parameterCount == 1 &&
        owner.interfaces.any { iface ->
            iface.declaredMethods.any { it.name == name && it.parameterTypes.contentEquals(parameterTypes) }
        }

private fun scanVideoCommentEmptyPageHooks(classLoader: ClassLoader): List<VideoCommentEmptyPageSymbols> {
    val subjectControl = COMMENT_SUBJECT_CONTROL_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
        .mapNotNull { type ->
            val emptyPageClass = classLoader.loadClassOrNull(type.name.replace("SubjectControl", "EmptyPage"))
                ?: return@mapNotNull null
            val defaultInstance = emptyPageClass.declaredFields.firstOrNull { it.name == "DEFAULT_INSTANCE" }
                ?.apply { isAccessible = true } ?: return@mapNotNull null
            val method = type.allMethods().firstOrNull { it.name == "getEmptyPage" && it.parameterCount == 0 }
                ?: return@mapNotNull null
            VideoCommentEmptyPageSymbols(MethodDescriptor.of(method), FieldDescriptor.of(defaultInstance))
        }
    val subjectDesc = COMMENT_SUBJECT_DESC_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
        .mapNotNull { type ->
            val emptyPageClass = classLoader.loadClassOrNull(type.name.replace("SubjectDescriptionReply", "EmptyPage"))
                ?: return@mapNotNull null
            val defaultInstance = emptyPageClass.declaredFields.firstOrNull { it.name == "DEFAULT_INSTANCE" }
                ?.apply { isAccessible = true } ?: return@mapNotNull null
            val method = type.allMethods().firstOrNull { it.name == "getEmptyPage" && it.parameterCount == 0 }
                ?: return@mapNotNull null
            VideoCommentEmptyPageSymbols(MethodDescriptor.of(method), FieldDescriptor.of(defaultInstance))
        }
    return (subjectControl + subjectDesc).distinctBy { it.getEmptyPage.declaringClassName }
}

private fun findQuickReplyActionClasses(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): QuickReplyActionClassScan {
    val currentBridge = bridge() ?: return QuickReplyActionClassScan(
        classes = emptyList(),
        candidates = 0,
        error = "DexKitBridge unavailable",
    )
    val stringCandidates = runCatching {
        currentBridge.findClass(
            FindClass.create()
                .matcher(ClassMatcher.create().usingStrings(QUICK_REPLY_SHOW_PUBLISH_DIALOG_STRING)),
        ).map { it.name }.toList()
    }.getOrElse { throwable ->
        return QuickReplyActionClassScan(
            classes = emptyList(),
            candidates = 0,
            error = throwable.scanMessage(),
        )
    }
    val classes = stringCandidates
        .asSequence()
        .flatMap { name -> sequenceOf(name, name.substringBefore('$')).distinct() }
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .filter { it.hasQuickReplyPublishDialogReference() }
        .flatMap { type ->
            generateSequence(type) { it.superclass?.takeIf { parent -> parent != Any::class.java } }
                .filter { it.isCommentActionBaseType(emptyList()) || it.hasQuickReplyPublishDialogReference() }
        }
        .distinctBy { it.name }
        .toList()
    return QuickReplyActionClassScan(
        classes = classes,
        candidates = stringCandidates.size,
        error = null,
    )
}

private fun Class<*>.hasQuickReplyPublishDialogReference(): Boolean =
    declaredFields.any { field -> field.type.isQuickReplyDialogIntentType() } ||
        declaredClasses.any { child ->
            child.declaredFields.any { field -> field.type.isQuickReplyDialogIntentType() } &&
                child.declaredFields.isNotEmpty()
        } ||
        allMethods().any { method ->
            method.returnType.isQuickReplyDialogIntentType() ||
                method.parameterTypes.any { it.isQuickReplyDialogIntentType() }
        }

private fun Class<*>.isCommentActionType(actionBases: List<Class<*>> = emptyList()): Boolean {
    if (actionBases.any { it.isAssignableFrom(this) }) return true
    val simpleName = simpleName
    val className = name
    return className.contains(".comment3.", ignoreCase = true) &&
        (simpleName.contains("Action", ignoreCase = true) ||
            simpleName.contains("Intent", ignoreCase = true))
}

private fun Class<*>.isCommentActionBaseType(actionBases: List<Class<*>> = emptyList()): Boolean {
    if (actionBases.any { it == this || it.isAssignableFrom(this) || this.isAssignableFrom(it) }) return true
    if (name == "Kj.c" || name == "Wk.c") return true
    if (simpleName.contains("Action", ignoreCase = true) && (name.startsWith("Kj.") || name.startsWith("Wk."))) return true
    return allMethods().any { method ->
        method.name == "a" &&
            method.returnType == Void.TYPE &&
            method.parameterCount == 2 &&
            method.parameterTypes[0].isInterface &&
            method.parameterTypes[1] == Boolean::class.javaPrimitiveType
    }
}

private fun Class<*>.isQuickReplyDialogIntentType(): Boolean {
    val className = name
    return className.endsWith("PublishDialogIntent", ignoreCase = true) ||
        className.contains("PublishDialogIntent", ignoreCase = true) ||
        (
            className.contains(".comment3.", ignoreCase = true) &&
                simpleName.contains("DialogIntent", ignoreCase = true) &&
                declaredFields.any { field ->
                    field.type.isEnum || field.type == Boolean::class.javaPrimitiveType
                }
        )
}

private data class QuickReplyActionClassScan(
    val classes: List<Class<*>>,
    val candidates: Int,
    val error: String?,
) {
    val evidence: String =
        "quickClasses=${classes.size},quickCandidates=$candidates" +
            error?.let { ",quickError=${it.take(120)}" }.orEmpty()
}

private const val HP_SHARE_LEGACY = "ShareHook.LegacyShareClickResult"

private const val HP_SHARE_CHANNELS = "ShareHook.ShareChannels"

private const val HP_SHARE_BASE_INFO = "ShareHook.ShareBaseInfo"

private const val HP_SHARE_CONTENT = "ShareHook.ShareContent"

private const val HP_SHARE_BILI_CONTENT = "ShareHook.ShareBiliContent"

private const val HP_SHARE_COPY_CONTENT = "ShareHook.CopyContent"

private const val HP_SHARE_COPY_UTILITY = "ShareHook.CopyUtility"

private const val HP_VIDEO_COMMENT_DISABLE = "VideoCommentHook.DisableComment"

private const val HP_VIDEO_COMMENT_QUICK_REPLY = "VideoCommentHook.QuickReply"

private const val HP_VIDEO_COMMENT_WIDGETS = "VideoCommentHook.Widgets"

private const val HP_VIDEO_COMMENT_SEARCH = "VideoCommentHook.SearchUrls"

private const val HP_VIDEO_COMMENT_EMPTY_PAGE = "VideoCommentHook.EmptyPage"

private const val HP_VIDEO_COMMENT_MAIN_LIST = "VideoCommentHook.MainList"

private val SETTINGS_FRAGMENT_NAMES = listOf(
    "com.bilibili.app.preferences.BiliPreferencesActivity\$BiliPreferencesFragment",
    "com.bilibili.app.preferences.fragment.WideBiliPreferencesFragment",
    "com.bilibili.app.preferences.fragment.BaseWidePreferenceFragment",
)

private val PREFERENCE_CLASS_NAMES = listOf(
    "com.bilibili.app.preferences.settingWide.CornerPreference",
    "tv.danmaku.bili.widget.preference.BLPreference",
    "androidx.preference.Preference",
)

private const val SHARE_LEGACY_RESULT = "com.bilibili.lib.sharewrapper.online.api.ShareClickResult"

private const val SHARE_CHANNELS = "com.bilibili.lib.sharewrapper.online.api.ShareChannels"

private const val SHARE_CHANNEL_ITEM = "com.bilibili.lib.sharewrapper.online.api.ShareChannels\$ChannelItem"

private const val SHARE_BASE_INFO_TO_STRING = "ShareBaseInfo(title="

private val SHARE_CONTENT_CLASSES = arrayOf(
    "kntr.common.share.domain.v1.ShareContent",
)

private val SHARE_BILI_CONTENT_CLASSES = arrayOf(
    "kntr.common.share.domain.v1.ShareBiliContent",
)

private val SHARE_COPY_CONTENT_CLASSES = arrayOf(
    "kntr.common.share.common.handler.copy.b",
)

private val SHARE_COPY_UTILITY_CLASSES = arrayOf(
    "kntr.common.share.common.handler.copy.a",
)

private const val SHARE_COPY_CLIPBOARD_LOG = "copyToClipBoard:"

private val TEENAGERS_MODE_ACTIVITIES = arrayOf(
    "com.bilibili.app.preferences.TeenagersModeDialogActivity",
    "tv.danmaku.bili.ui.teenagersmode.TeenagersModeDialogActivity",
    "com.bilibili.teenagersmode.ui.TeenagersModeDialogActivity",
    "com.bilibili.teenagersmode.ui.TeenagersModeActivity",
    "com.bilibili.teenagersmode.osteen.OSTeensParentControlRedirectActivity",
)

private val DOWNLOAD_THREAD_LISTENER_CLASS_TERMS = listOf(
    "Download",
    "download",
    "Thread",
    "thread",
    "Listener",
    "listener",
    "Cache",
    "cache",
    "Offline",
    "offline",
)

private val NUMBER_FORMAT_CLASS_NAMES = listOf(
    "com.bilibili.base.util.NumberFormat",
    "com.bilibili.n9.util.NumberFormat",
    "com.bilibili.lib.utils.NumberFormat",
)

private val FULL_NUMBER_FORMAT_METHOD_NAMES = setOf(
    "format",
    "formatWithComma",
    "formatTitle",
    "formatStr",
    "formatWithInter",
    "formatLong",
    "formatByEng",
)

private val COMPACT_UNIT_STRINGS = listOf("万", "亿", "萬", "億")

private const val MAX_HEURISTIC_NUMBER_METHODS = 120

private const val KMP_NUMBER_FORMAT_RULE_KEY = "localization.number_format_rule"

private val KMP_NUMBER_FORMAT_FACADE_NAMES = listOf("kntr.base.localization.NumberFormat_androidKt")

private val THESEUS_TAB_PAGER_SERVICE = arrayOf(
    "com.bilibili.ship.theseus.united.page.tab.TheseusTabPagerService",
)

private val COMMENT_VIEW_MODEL_CLASSES = arrayOf(
    "com.bilibili.app.comment3.viewmodel.CommentViewModel",
)

private val COMMENT_ACTION_BASE_CLASSES = arrayOf(
    "fou",
    "wtt",
    "Kj.c",
    "Wk.c",
)

private const val QUICK_REPLY_SHOW_PUBLISH_DIALOG_STRING = "ShowPublishDialog(args="

private val QUICK_REPLY_DIALOG_COLLECTOR_CLASSES = (1..50).flatMap { i ->
    listOf(
        "com.bilibili.app.comment3.ui.CommentContainerImpl\$attachRepository\$$i",
        "com.bilibili.app.comment3.ui.CommentContainerImpl\$attachRepository\$$i\$2",
    )
}.toTypedArray()

private val CMT_VOTE_WIDGET_CLASSES = arrayOf(
    "com.bilibili.app.comment.ext.widgets.CmtVoteWidget",
)

private val CMT_MOUNT_WIDGET_CLASSES = arrayOf(
    "com.bilibili.app.comment.ext.widgets.CmtMountWidget",
)

private val COMMENT_VOTE_VIEW_CLASSES = arrayOf(
    "com.bilibili.app.comment3.ui.widget.CommentVoteView",
)

private val COMMENT_FOLLOW_WIDGET_CLASSES = arrayOf(
    "com.bilibili.app.comm.comment2.phoenix.view.CommentFollowWidget",
)

private val COMMENT_HEADER_DECORATIVE_VIEW_CLASSES = arrayOf(
    "com.bilibili.app.comment3.ui.widget.CommentHeaderDecorativeView",
)

private val COMMENT_CONTENT_CLASSES = arrayOf(
    "com.bapis.bilibili.main.community.reply.v1.Content",
)

private val COMMENT_SUBJECT_CONTROL_CLASSES = arrayOf(
    "com.bapis.bilibili.main.community.reply.v1.SubjectControl",
)

private val COMMENT_SUBJECT_DESC_CLASSES = arrayOf(
    "com.bapis.bilibili.main.community.reply.v2.SubjectDescriptionReply",
)

private val COMMENT_MAIN_LIST_OBSERVERS = arrayOf(
    "com.bapis.bilibili.main.community.reply.v1.ReplyMossKtxKt\$suspendMainList\$\$inlined\$suspendCall\$1",
    "com.bapis.bilibili.main.community.reply.v1.KReplyMoss\$mainList\$\$inlined\$suspendCall\$2",
)

private val VOTE_WIDGET_METHOD_NAMES = setOf("a", "setData", "setVoteData", "bindData", "update", "refresh", "bind", "onBind")

private val FOLLOW_WIDGET_METHOD_NAMES = setOf("W", "setData", "bindData", "update", "refresh", "bind", "onBind")

private val HEADER_DECORATIVE_METHOD_NAMES = setOf("a", "setData", "bindData", "update", "refresh", "submitList")

private const val BILI_UPGRADE_INFO = "tv.danmaku.bili.update.model.BiliUpgradeInfo"

private val BLOCK_UPDATE_STRING_RULES = listOf(
    listOf("Do sync http request."),
    listOf("Http request result %s, saved to file cache."),
    listOf("Nothing to update, clean caches."),
    listOf("code", "message"),
)
