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

/**
 * 拦截 App 基础组件库（`app_mod_resource`）的下载清单。
 *
 * 宿主通过 `ModuleMoss.list(ListReq)` 拉取资源池清单，再按清单去下载模块文件。
 * 这里把**被选中的池**的模块列表清空，宿主就没有可下载项了。
 *
 * ## 两条必须知道的前提
 *
 * 1. **清单是按需分片下发的，不是全量目录。** 一次响应通常只带 1 个池、1 个模块。
 *    真机实测：`app_mod_resource/manifest/` 下有 17 个池，而单次响应只带了
 *    `appletBasic` 的 1 个模块。所以候选池必须**跨请求累积**，
 *    按"本次响应即全部候选"去整份覆盖的话，设置页里永远只会看到最后请求的那一个池。
 * 2. **只拦再次下载，不删已有文件。** 已经落盘的模块要用户自己去
 *    哔哩哔哩的存储设置里清一次才看得出效果。
 *
 * ## 为什么用 callMethod 而不是安装期解析 builder
 *
 * protobuf-lite 的 `toBuilder()` **声明**返回的是基类 `GeneratedMessageLite$Builder`，
 * 不是具体 builder。任何"解析 `toBuilder().returnType` 再找 `build()`"的写法都会失败。
 * 这里全程走运行期按名反射（`callMethod`），拿到的是实例的真实类，不受此限。
 */
class ComponentPoolBlockHook(env: RoamingEnv) : BaseRoamingHook(env) {

    /** 池名 -> 已见模块名；跨请求累积，见类注释第 1 条。 */
    private val knownPools = linkedMapOf<String, LinkedHashSet<String>>()

    /** 模块名读不出来时的兜底计数：该池在单次响应里见过的最大模块数。 */
    private val poolCountFloor = linkedMapOf<String, Int>()

    private var persistedSignature: String? = null

    override fun startHook() {
        // 两个开关都关时**也要装**：不装就没人去观察清单，设置页里永远没有候选可勾。
        // 观察本身不改写响应，只读几个字段，代价可以忽略。
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

        // 异步版本：第二个参数是 MossResponseHandler，结果从 onNext 回调里来，
        // 所以要换掉 handler 而不是改返回值。
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

    /**
     * 把 delegate 包一层：onNext 收到的 reply 先过滤再转交。
     *
     * 任何一步出错都原样透传原始 reply——宁可这次没拦住，也不能让宿主拿到半成品。
     */
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

    /** 记下这一份分片里出现的池，并在内容变化时写回设置。 */
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

    /**
     * 只在内容真的变了时才写 prefs。
     *
     * 清单请求在一次会话里会来很多次，每次都 `putStringSet` 是纯浪费：
     * 那意味着每次都要把整份集合重新序列化一遍。
     */
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

    /**
     * 返回清空了目标池模块列表的新 reply；没有任何池命中就返回 null（原样放行）。
     */
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
        /** 有界：池数与每池模块名都不允许无限增长。 */
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
