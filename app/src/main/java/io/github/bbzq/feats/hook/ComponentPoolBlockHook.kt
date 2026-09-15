package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

class ComponentPoolBlockHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val knownPools = linkedMapOf<String, LinkedHashSet<String>>()

    private val poolCountFloor = linkedMapOf<String, Int>()

    private var persistedSignature: String? = null

    override fun startHook() {
        val mossClass = MOSS_CLASSES.firstNotNullOfOrNull(classLoader::findClassOrNull)
        val requestClass = REQUEST_CLASSES.firstNotNullOfOrNull(classLoader::findClassOrNull)
        if (mossClass == null || requestClass == null) {
            log("ComponentPoolBlock: ModuleMoss/ListReq not found on this host")
            return
        }

        var installed = 0

        mossClass.declaredMethods.firstOrNull {
            it.name in METHOD_NAMES &&
                it.parameterTypes.contentEquals(arrayOf(requestClass)) &&
                !Modifier.isStatic(it.modifiers) &&
                !it.returnType.isPrimitive
        }?.let { sync ->
            env.hookAfter(sync) { param ->
                val reply = param.result ?: return@hookAfter
                observe(reply)
                filtered(reply)?.let { param.result = it }
            }
            installed++
        }

        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)
        if (handlerClass != null) {
            mossClass.declaredMethods.firstOrNull {
                it.name in METHOD_NAMES &&
                    it.parameterTypes.contentEquals(arrayOf(requestClass, handlerClass)) &&
                    it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers)
            }?.let { async ->
                env.hookBefore(async) { param ->
                    val delegate = param.args.getOrNull(1) ?: return@hookBefore
                    param.args[1] = wrapHandler(handlerClass, delegate)
                }
                installed++
            }
        }

        if (installed == 0) {
            log("ComponentPoolBlock: no list method matched on ModuleMoss")
            return
        }
        isInstalled = true
        log("ComponentPoolBlock: installed on $installed entry point(s)")
    }

    private fun wrapHandler(handlerClass: Class<*>, delegate: Any): Any =
        Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass),
            InvocationHandler { _, method, args ->
                val forwarded = arrayOfNulls<Any?>(args?.size ?: 0)
                args?.forEachIndexed { index, value -> forwarded[index] = value }
                if (method.name == "onNext" && forwarded.size == 1) {
                    forwarded[0]?.let { reply ->
                        runCatching {
                            observe(reply)
                            forwarded[0] = filtered(reply) ?: reply
                        }.onFailure { log("ComponentPoolBlock: async transform failed", it) }
                    }
                }
                method.invoke(delegate, *forwarded)
            },
        )

    private fun observe(reply: Any) {
        runCatching {
            val pools = reply.callMethod("getPoolsList") as? List<*> ?: return@runCatching
            var changed = false
            pools.filterNotNull().forEach { pool ->
                val name = (pool.callMethod("getName") as? String)?.trim().orEmpty()
                if (name.isEmpty()) return@forEach
                val modules = (pool.callMethod("getModulesList") as? List<*>).orEmpty()
                val seen = knownPools[name] ?: run {
                    if (knownPools.size >= MAX_POOLS) return@forEach
                    changed = true
                    LinkedHashSet<String>().also { knownPools[name] = it }
                }
                modules.filterNotNull().forEach { module ->
                    val moduleName = (module.callMethod("getModuleName") as? String)?.trim().orEmpty()
                    if (moduleName.isNotEmpty() && seen.size < MAX_MODULES_PER_POOL) {
                        if (seen.add(moduleName)) changed = true
                    }
                }
                val floor = poolCountFloor[name] ?: 0
                if (modules.size > floor) {
                    poolCountFloor[name] = modules.size
                    changed = true
                }
            }
            if (changed) persistKnownPools()
        }.onFailure { log("ComponentPoolBlock: observe failed", it) }
    }

    private fun persistKnownPools() {
        val encoded = knownPools.entries
            .sortedBy { it.key }
            .map { (name, modules) ->
                val count = maxOf(modules.size, poolCountFloor[name] ?: 0)
                ModuleSettings.encodeComponentPool(name, count)
            }
            .toSet()
        val signature = encoded.joinToString("")
        if (signature == persistedSignature) return
        persistedSignature = signature
        runCatching {
            prefs.edit().putStringSet(ModuleSettings.KEY_KNOWN_COMPONENT_POOLS, encoded.toMutableSet()).apply()
        }.onFailure { log("ComponentPoolBlock: persist failed", it) }
    }

    private fun filtered(reply: Any): Any? = runCatching {
        val blockAll = ModuleSettings.isBlockAllComponentPoolsEnabled(prefs)
        val blocked = if (blockAll) emptySet() else ModuleSettings.getBlockedComponentPools(prefs)
        if (!blockAll && blocked.isEmpty()) return@runCatching null

        val pools = (reply.callMethod("getPoolsList") as? List<*>)?.filterNotNull().orEmpty()
        if (pools.isEmpty()) return@runCatching null

        val rebuilt = ArrayList<Any>(pools.size)
        var changed = false
        pools.forEach { pool ->
            val name = (pool.callMethod("getName") as? String)?.trim().orEmpty()
            if (blockAll || (name.isNotEmpty() && name in blocked)) {
                val cleared = pool.callMethod("toBuilder")
                    ?.apply { callMethod("clearModules") }
                    ?.callMethod("build")
                if (cleared != null) {
                    rebuilt += cleared
                    changed = true
                    return@forEach
                }
            }
            rebuilt += pool
        }
        if (!changed) return@runCatching null

        val builder = reply.callMethod("toBuilder") ?: return@runCatching null
        builder.callMethod("clearPools")
        rebuilt.forEach { builder.callMethod("addPools", it) }
        builder.callMethod("build")
    }.onFailure { log("ComponentPoolBlock: filter failed", it) }.getOrNull()

    private companion object {
        const val MAX_POOLS = 256
        const val MAX_MODULES_PER_POOL = 512

        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"

        val MOSS_CLASSES = listOf(
            "com.bapis.bilibili.app.resource.v1.ModuleMoss",
            "com.bapis.bilibili.app.resource.v1.KModuleMoss",
            "com.bapis.bilibili.p4218app.resource.p4240v1.ModuleMoss",
            "com.bapis.bilibili.p4218app.resource.p4240v1.KModuleMoss",
        )
        val REQUEST_CLASSES = listOf(
            "com.bapis.bilibili.app.resource.v1.ListReq",
            "com.bapis.bilibili.app.resource.v1.KListReq",
            "com.bapis.bilibili.p4218app.resource.p4240v1.ListReq",
            "com.bapis.bilibili.p4218app.resource.p4240v1.KListReq",
        )
        val METHOD_NAMES = listOf("executeList", "list")
    }
}
