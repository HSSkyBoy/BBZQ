package io.github.biliz.feats.hook

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore

class FullNumberFormatHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return

        val methods = env.symbols?.fullNumberFormat?.restore(classLoader)?.formatterMethods.orEmpty()
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) return@hookBefore

                val rawNumber = when (val value = param.args.firstOrNull()) {
                    is Long -> value
                    is Int -> value.toLong()
                    else -> return@hookBefore
                }

                if (rawNumber >= 0) {
                    param.result = rawNumber.toString()
                }
            }
        }

        hookMineCounts()
        hookSpaceCounts()
        log("startHook: FullNumberFormat, formatterMethods=${methods.size}")
    }

    /**
     * Newer clients write the compact values directly when the Mine page binds its account data,
     * bypassing the legacy NumberFormat utility.  Restore the raw values after the page resumes.
     */
    private fun hookMineCounts() {
        val onResume = env.symbols?.mineProfile?.restore(classLoader)?.onResume ?: return
        env.hookAfter(onResume) { param ->
            if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) return@hookAfter
            val fragment = param.thisObject ?: return@hookAfter
            val root = fragment.rootView() ?: return@hookAfter
            root.post { applyMineCounts(fragment, root) }
        }
    }

    /** The author-space header still receives BiliMemberCard directly on current clients. */
    private fun hookSpaceCounts() {
        SPACE_HEADER_FRAGMENT_CLASSES.asSequence()
            .mapNotNull(::loadClassOrNull)
            .flatMap { type -> type.declaredMethods.asSequence() }
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.any { it.name.endsWith(".BiliMemberCard") }
            }
            .distinctBy { it.toGenericString() }
            .forEach { method ->
                method.isAccessible = true
                env.hookAfter(method) { param ->
                    if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) return@hookAfter
                    val card = param.args.firstOrNull { it?.javaClass?.name?.endsWith(".BiliMemberCard") == true }
                        ?: return@hookAfter
                    val fragment = param.thisObject ?: return@hookAfter
                    val root = fragment.rootView() ?: return@hookAfter
                    root.post { applySpaceCounts(root, card) }
                }
            }
    }

    private fun applyMineCounts(fragment: Any, root: View) {
        if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) return
        val account = fragment.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 0 && method.returnType.name.endsWith(".AccountMine")
        }?.runCatching { invoke(fragment) }?.getOrNull() ?: return
        root.setCountText("following_count", account.readLong("dynamic"))
        root.setCountText("attention_count", account.readLong("following"))
        root.setCountText("fans_count", account.readLong("follower"))
    }

    private fun applySpaceCounts(root: View, card: Any) {
        if (!ModuleSettings.isFullNumberFormatEnabled(prefs)) return
        root.setCountText("fans", card.readLong("mFollowers", "followers"))
        root.setCountText("attentions", card.readLong("mFollowings", "followings"))
        val likes = card.readField("likes")?.readLong("likeNum", "mLikeNum")
        root.setCountText("likes", likes)
    }

    private fun Any.rootView(): View? = javaClass.methods.firstOrNull {
        it.name == "getView" && it.parameterCount == 0 && View::class.java.isAssignableFrom(it.returnType)
    }?.runCatching { invoke(this@rootView) as? View }?.getOrNull()

    private fun View.setCountText(entryName: String, value: Long?) {
        value ?: return
        findTextView(entryName)?.text = value.toString()
    }

    private fun View.findTextView(entryName: String): TextView? {
        val id = resources.getIdentifier(entryName, "id", context.packageName)
        if (id != 0) findViewById<TextView>(id)?.let { return it }
        return findTextViewByEntryName(this, entryName)
    }

    private fun findTextViewByEntryName(view: View, entryName: String): TextView? {
        if (view is TextView && runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() == entryName) {
            return view
        }
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findTextViewByEntryName(view.getChildAt(index), entryName)?.let { return it }
        }
        return null
    }

    private fun Any.readLong(vararg names: String): Long? = readField(*names)?.let { value ->
        (value as? Number)?.toLong() ?: value?.toString()?.toLongOrNull()
    }

    private fun Any.readField(vararg names: String): Any? {
        var type: Class<*>? = javaClass
        while (type != null) {
            type.declaredFields.firstOrNull { it.name in names }?.let { field ->
                return runCatching {
                    field.isAccessible = true
                    field.get(this@readField)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun loadClassOrNull(name: String): Class<*>? = runCatching {
        Class.forName(name, false, classLoader)
    }.getOrNull()

    private companion object {
        val SPACE_HEADER_FRAGMENT_CLASSES = arrayOf(
            "com.bilibili.app.authorspace.ui.SpaceHeaderFragment2",
            "com.bilibili.p4439app.authorspace.ui.SpaceHeaderFragment2",
        )
    }
}
