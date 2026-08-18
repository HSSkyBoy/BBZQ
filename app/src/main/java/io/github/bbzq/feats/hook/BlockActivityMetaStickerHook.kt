package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.util.Collections

class BlockActivityMetaStickerHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockActivityMetaStickerEnabled(prefs)) {
            log("startHook: BlockActivityMetaSticker disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        var installed = 0
        installed += installDmViewReplyGetterHooks()
        installed += installOnNextHook()

        if (installed == 0) {
            log("startHook: BlockActivityMetaSticker no hook point found")
        } else {
            log("startHook: BlockActivityMetaSticker installed $installed hook point(s)")
        }
    }

    /**
     * Hooks [getActivityMetaList] and [getActivityMetaCount] on [DmViewReply] so that any
     * downstream consumer sees an empty list / zero count, without touching the immutable
     * protobuf field.
     */
    private fun installDmViewReplyGetterHooks(): Int {
        val dmViewReplyClass = findDmViewReplyClass() ?: run {
            log("startHook: BlockActivityMetaSticker DmViewReply class not found")
            return 0
        }

        var hookCount = 0

        // Hook getActivityMetaList() → emptyList()
        val getListMethod = dmViewReplyClass.allMethods().firstOrNull { method ->
            method.name == "getActivityMetaList" && method.parameterCount == 0
        }
        if (getListMethod != null) {
            env.hookBefore(getListMethod) { param ->
                param.result = Collections.emptyList<Any>()
            }
            log("startHook: BlockActivityMetaSticker getActivityMetaList at ${dmViewReplyClass.name}")
            hookCount++
        } else {
            log("startHook: BlockActivityMetaSticker getActivityMetaList not found on ${dmViewReplyClass.name}")
        }

        // Hook getActivityMetaCount() → 0
        val getCountMethod = dmViewReplyClass.allMethods().firstOrNull { method ->
            method.name == "getActivityMetaCount" && method.parameterCount == 0
        }
        if (getCountMethod != null) {
            env.hookBefore(getCountMethod) { param ->
                param.result = 0
            }
            log("startHook: BlockActivityMetaSticker getActivityMetaCount at ${dmViewReplyClass.name}")
            hookCount++
        } else {
            log("startHook: BlockActivityMetaSticker getActivityMetaCount not found on ${dmViewReplyClass.name}")
        }

        return hookCount
    }

    /**
     * Hooks the coroutine `onNext` callback that delivers the freshly-parsed [DmViewReply].
     * Before the reply reaches any consumer we locate the [activityMeta_] field and overwrite it
     * with the empty value sourced from [getDefaultInstance], preventing the sticker from ever
     * being scheduled.
     *
     * This is a defence-in-depth hook; the getter hooks above are the primary mechanism.
     */
    private fun installOnNextHook(): Int {
        val onNextClass = findOnNextClass() ?: run {
            log("startHook: BlockActivityMetaSticker onNext class not found")
            return 0
        }
        val dmViewReplyClass = findDmViewReplyClass() ?: run {
            log("startHook: BlockActivityMetaSticker DmViewReply class unavailable for onNext hook")
            return 0
        }

        val onNextMethod = onNextClass.allMethods().firstOrNull { method ->
            method.name == "onNext" && method.parameterCount == 1
        } ?: run {
            log("startHook: BlockActivityMetaSticker onNext method not found in ${onNextClass.name}")
            return 0
        }

        // Locate getDefaultInstance() and the activityMeta_ field lazily on first invocation.
        var activityMetaField: java.lang.reflect.Field? = null
        var defaultInstanceActMetaValue: Any? = null
        var fieldLookupAttempted = false

        env.hookBefore(onNextMethod) { param ->
            val reply = param.args.getOrNull(0) ?: return@hookBefore
            if (!dmViewReplyClass.isInstance(reply)) return@hookBefore

            runCatching {
                if (!fieldLookupAttempted) {
                    fieldLookupAttempted = true
                    val defaultInstance = dmViewReplyClass.callStaticMethod("getDefaultInstance")
                    if (defaultInstance != null) {
                        val field = dmViewReplyClass.allFields().firstOrNull { f ->
                            f.name == "activityMeta_"
                        }
                        if (field != null) {
                            activityMetaField = field
                            defaultInstanceActMetaValue = field.get(defaultInstance)
                        }
                    }
                }

                val field = activityMetaField ?: return@hookBefore
                val emptyValue = defaultInstanceActMetaValue ?: return@hookBefore
                field.set(reply, emptyValue)
            }.onFailure { throwable ->
                log("BlockActivityMetaSticker onNext scrub failed: ${throwable.message}", throwable)
            }
        }

        log("startHook: BlockActivityMetaSticker onNext at ${onNextClass.name}.onNext")
        return 1
    }

    /**
     * Attempts to locate the [DmViewReply] class.  The class name is stable and non-obfuscated,
     * so we try the known canonical name directly.
     */
    private fun findDmViewReplyClass(): Class<*>? {
        DM_VIEW_REPLY_CANDIDATE_NAMES.forEach { name ->
            classLoader.findClassOrNull(name)?.let { return it }
        }
        return null
    }

    /**
     * Attempts to locate the anonymous `onNext` class generated by the Kotlin coroutine compiler
     * for `DmMossKtxKt.suspendDmView`.  The name is non-obfuscated.
     */
    private fun findOnNextClass(): Class<*>? {
        ON_NEXT_CANDIDATE_NAMES.forEach { name ->
            classLoader.findClassOrNull(name)?.let { return it }
        }
        return null
    }

    private companion object {
        private val DM_VIEW_REPLY_CANDIDATE_NAMES = listOf(
            "com.bapis.bilibili.community.service.dm.v1.DmViewReply",
        )

        private val ON_NEXT_CANDIDATE_NAMES = listOf(
            // The inlined suspendCall lambda class — name is stable across B站 builds.
            "com.bapis.bilibili.community.service.dm.v1.DmMossKtxKt\$suspendDmView\$\$inlined\$suspendCall\$1",
        )
    }
}
