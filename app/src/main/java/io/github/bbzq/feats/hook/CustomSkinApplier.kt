package io.github.bbzq.feats.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.HostAccountResolver
import io.github.bbzq.feats.RoamingEnv
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Applies the portable skin JSON produced by BiliRoaming/BiliRoamingX. */
internal object CustomSkinApplier {
    private val receiverRegistered = AtomicBoolean(false)
    private val applyPending = AtomicBoolean(false)
    private val reapplyPending = AtomicBoolean(false)
    private val reapplyLock = Any()
    private val lastReapplyAt = AtomicLong(0L)
    private var reapplyWindowStartedAt = 0L
    private var reapplyCountInWindow = 0

    fun applyIfChanged(env: RoamingEnv) {
        if (!ModuleSettings.isCustomSkinEnabled(env.prefs)) return
        val config = ModuleSettings.getCustomSkinJson(env.prefs)
        if (config.isBlank()) return
        registerThemeChangeObserver(env)
        val target = resolveTarget(env, config) ?: return
        if (isTargetApplied(target)) {
            // 已应用过:load_equip 仍要幂等确保,放后台线程避免主线程下载
            if (!applyPending.compareAndSet(false, true)) return
            Thread {
                try {
                    ensureLoadEquipApplied(env, config, target)
                } catch (error: Throwable) {
                    env.log("Custom skin load equip ensure failed", error)
                } finally {
                    applyPending.set(false)
                }
            }.apply { name = "BBZQ-CustomSkinLoadEquip" }.start()
            return
        }
        if (!applyPending.compareAndSet(false, true)) return
        Thread {
            try {
                // Several host startup callbacks can arrive before the first background task
                // finishes. Check again here so only the first task writes or downloads.
                if (!isTargetApplied(target)) applyOrRestoreCached(env, config, target)
            } catch (error: Throwable) {
                env.log("Custom skin apply failed", error)
            } finally {
                applyPending.set(false)
            }
        }.apply { name = "BBZQ-CustomSkin" }.start()
    }

    /**
     * Bilibili sends this broadcast after refreshing the equipped official garb (for
     * example after the "new content" prompt). Re-apply after that receiver finishes.
     */
    private fun registerThemeChangeObserver(env: RoamingEnv) {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val action = "${env.packageName}.garb.GARB_CHANGE"
        val filter = IntentFilter(action).apply { priority = -1000 }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra("key_broadcast_data_type", 0) != 1) return
                if (intent.getBooleanExtra(EXTRA_SELF_APPLIED, false)) return
                if (!ModuleSettings.isCustomSkinEnabled(env.prefs)) return
                val current = intent.getStringExtra("key_garb_data").orEmpty()
                val customId = customSkinId(ModuleSettings.getCustomSkinJson(env.prefs))
                val incomingId = runCatching { JSONObject(current).optLong("id") }.getOrDefault(-1L)
                if (customId <= 0L || incomingId == customId) return
                scheduleReapply(env)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 注册非系统广播必须显式声明导出标志
            env.hostContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            env.hostContext.registerReceiver(receiver, filter)
        }
        env.log("Custom skin observer registered for $action")
    }

    private fun scheduleReapply(env: RoamingEnv) {
        if (!reapplyPending.compareAndSet(false, true)) return
        if (!reserveReapplySlot(env)) {
            reapplyPending.set(false)
            return
        }
        Thread {
            try {
                // Let Bilibili's lower-priority garb receiver finish first.
                Thread.sleep(REAPPLY_DELAY_MS)
                reapplyCached(env)
            } catch (error: Throwable) {
                env.log("Custom skin reapply failed", error)
            } finally {
                reapplyPending.set(false)
            }
        }.apply { name = "BBZQ-CustomSkinReapply" }.start()
    }

    private fun reapplyCached(env: RoamingEnv) {
        val raw = ModuleSettings.getCustomSkinJson(env.prefs)
        if (!ModuleSettings.isCustomSkinEnabled(env.prefs) || raw.isBlank()) return
        val target = resolveTarget(env, raw) ?: return
        if (isTargetApplied(target)) return
        applyOrRestoreCached(env, raw, target)
        env.log("Custom skin re-applied after official garb change: id=${target.id}")
    }

    private fun applyOrRestoreCached(env: RoamingEnv, raw: String, target: SkinTarget) {
        if (assetsReady(target)) {
            writeTarget(env, target)
        } else {
            apply(env, raw, target)
        }
    }

    // 幂等确保:文件已在则跳过下载,总是广播让 web 进程重读
    private fun ensureLoadEquipApplied(env: RoamingEnv, raw: String, target: SkinTarget) {
        if (!ModuleSettings.isCustomSkinEnabled(env.prefs)) return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        applyLoadEquip(env, root, target.garbDir)
    }

    private fun apply(env: RoamingEnv, raw: String, target: SkinTarget) {
        val root = JSONObject(raw)
        val skin = root.optJSONObject("user_equip") ?: root
        val packageUrl = skin.optString("package_url")
        require(packageUrl.isNotBlank()) { "Invalid skin JSON: package_url missing" }

        target.garbDir.mkdirs()
        target.assetsDir.mkdirs()
        val archive = File(target.garbDir, "${target.id}.zip")
        URL(packageUrl).openStream().use { input -> archive.outputStream().use(input::copyTo) }
        unzipSafely(archive, target.assetsDir)

        applyLoadEquip(env, root, target.garbDir)
        writeTarget(env, target)
        env.log("Custom skin applied: id=${target.id} version=${target.version}")
    }

    // 下拉动画:loading_url 下载到 garb/load_equip/<base64(url)> 供 web 进程加载,再广播重读
    private fun applyLoadEquip(env: RoamingEnv, root: JSONObject, garbDir: File) {
        val loadEquip = root.optJSONObject("load_equip") ?: return
        val url = loadEquip.optString("loading_url")
        if (url.isBlank()) return
        runCatching {
            val dir = File(garbDir, "load_equip").also { it.mkdirs() }
            val fileName = android.util.Base64.encodeToString(url.toByteArray(), android.util.Base64.NO_WRAP)
            val target = File(dir, fileName)
            if (!target.isFile || target.length() == 0L) {
                URL(url).openStream().use { input -> target.outputStream().use(input::copyTo) }
            }
            env.hostContext.sendBroadcast(Intent("${env.packageName}.garb.LOAD_EQUIP_CHANGE"))
            env.log("Custom skin load equip applied: $fileName")
        }.onFailure {
            env.log("Custom skin load equip failed", it)
        }
    }

    private fun notifyGarbChanged(env: RoamingEnv, garb: String) {
        env.hostContext.sendBroadcast(Intent("${env.packageName}.garb.GARB_CHANGE").apply {
            putExtra("key_broadcast_data_type", 1)
            putExtra("key_garb_data", garb)
            putExtra(EXTRA_SELF_APPLIED, true)
            putExtra("key_theme_change_sync_garb", false)
            putExtra("key_theme_change_should_report", false)
            putExtra("key_theme_change_sync_from_main_process", false)
        })
    }

    private fun resolveTarget(env: RoamingEnv, raw: String): SkinTarget? = runCatching {
        val root = JSONObject(raw)
        val skin = root.optJSONObject("user_equip") ?: root
        val id = skin.optLong("id")
        require(id > 0) { "Invalid skin JSON: id missing" }
        val version = skin.optLong("ver")
        val uid = HostAccountResolver.resolve(env.hostContext, env.classLoader).uid.ifBlank { "0" }
        val garbDir = File(env.hostContext.filesDir, "garb/$uid")
        SkinTarget(id, version, skin, garbDir, File(garbDir, "$id/$version"))
    }.getOrElse {
        env.log("Custom skin target is invalid", it)
        null
    }

    private fun assetsReady(target: SkinTarget): Boolean =
        target.assetsDir.isDirectory && !target.assetsDir.listFiles().isNullOrEmpty()

    private fun isTargetApplied(target: SkinTarget): Boolean {
        if (!assetsReady(target)) return false
        val config = File(target.garbDir, "garb.conf")
        val current = runCatching { JSONObject(config.readText()) }.getOrNull() ?: return false
        return current.optLong("id", -1L) == target.id && current.optLong("ver", -1L) == target.version
    }

    private fun writeTarget(env: RoamingEnv, target: SkinTarget) {
        val garb = toGarb(target.skin, target.assetsDir).toString()
        File(target.garbDir, "garb.conf").apply {
            parentFile?.mkdirs()
            writeText(garb)
        }
        notifyGarbChanged(env, garb)
    }

    private fun reserveReapplySlot(env: RoamingEnv): Boolean = synchronized(reapplyLock) {
        val now = System.currentTimeMillis()
        val last = lastReapplyAt.get()
        if (now - last < REAPPLY_MIN_INTERVAL_MS) {
            env.log("Custom skin reapply skipped: cooldown")
            return@synchronized false
        }
        if (now - reapplyWindowStartedAt >= REAPPLY_WINDOW_MS) {
            reapplyWindowStartedAt = now
            reapplyCountInWindow = 0
        }
        if (reapplyCountInWindow >= MAX_REAPPLIES_PER_WINDOW) {
            env.log("Custom skin reapply skipped: retry limit reached")
            return@synchronized false
        }
        reapplyCountInWindow++
        lastReapplyAt.set(now)
        true
    }

    private fun customSkinId(raw: String): Long = runCatching {
        val root = JSONObject(raw)
        (root.optJSONObject("user_equip") ?: root).optLong("id")
    }.getOrDefault(-1L)

    private fun unzipSafely(zip: File, target: File) {
        val root = target.canonicalFile
        ZipInputStream(zip.inputStream()).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                val output = File(root, entry.name).canonicalFile
                require(output.path.startsWith(root.path + File.separator) || output == root) { "Unsafe skin archive entry" }
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use(stream::copyTo)
                }
                entry = stream.nextEntry
            }
        }
    }

    private fun toGarb(skin: JSONObject, assetsDir: File): JSONObject {
        val paths = assetsDir.listFiles()?.associate { it.nameWithoutExtension to "file://${it.absolutePath}" }.orEmpty()
        val data = skin.optJSONObject("data")
        return JSONObject().apply {
            put("id", skin.optLong("id"))
            put("name", skin.optString("name"))
            put("ver", skin.optLong("ver"))
            put("loadAllFile", true)
            put("fontColor", color(data, "color"))
            put("secondaryPageColor", color(data, "color_second_page"))
            put("darkMode", data?.optString("color_mode") == "light")
            put("mainFontColor", color(data, "color"))
            put("mainDarkMode", data?.optString("color_mode") == "light")
            put("sideBgColor", color(data, "side_bg_color"))
            put("sideLineColor", color(data, "side_line_color"))
            put("tailColor", color(data, "tail_color"))
            put("tailSelectedColor", color(data, "tail_color_selected"))
            put("btnBgStartColor", color(data, "pub_btn_shade_color_top"))
            put("btnBgEndColor", color(data, "pub_btn_shade_color_bottom"))
            put("btnIconColor", color(data, "pub_btn_plus_color"))
            put("hasAnimate", data?.optBoolean("tail_icon_ani") ?: false)
            put("animateLoop", data?.optString("tail_icon_ani_mode") == "cycle")
            put("mineAnimateLoop", data?.optString("head_myself_mp4_play") == "loop")
            put("tailColorModel", data?.optString("tail_icon_mode") == "color")
            put("tailIconColor", color(data, "tail_icon_color"))
            put("tailIconColorNight", color(data, "tail_icon_color_dark"))
            put("tailIconColorSelected", color(data, "tail_icon_color_selected"))
            put("tailIconColorSelectedNight", color(data, "tail_icon_color_selected_dark"))
            put("headBgPath", paths["head_bg"].orEmpty())
            put("headTabBgPath", paths["head_tab_bg"].orEmpty())
            put("sideBgPath", paths["side_bg"].orEmpty())
            put("sideBottomBgPath", paths["side_bg_bottom"].orEmpty())
            put("tailBgPath", paths["tail_bg"].orEmpty())
            put("headMineBgPath", paths["head_myself_bg"].orEmpty())
            put("headMineSquaredBgPath", paths["head_myself_squared_bg"].orEmpty())
            put("headMineBgAnimatorPath", paths["head_myself_mp4_bg"].orEmpty())
            put("btnIconPath", paths["tail_icon_pub_btn_bg"].orEmpty())
            put("btnIconSelectedPath", paths["tail_icon_selected_pub_btn_bg"].orEmpty())
            put("tailIconPath", iconPaths(paths, "tail_icon_"))
            put("tailIconSelectedPath", iconPaths(paths, "tail_icon_selected_"))
            // The host otherwise restores the equipped official skin during startup.
            put("force", true); put("changeable", true); put("primaryOnly", false); put("op", false)
        }
    }

    private fun iconPaths(paths: Map<String, String>, prefix: String) = JSONArray().apply {
        listOf("main", "channel", "dynamic", "shop", "myself").forEach { put(paths["$prefix$it"].orEmpty()) }
    }

    private fun color(data: JSONObject?, name: String): Int = runCatching {
        Color.parseColor(data?.optString(name).orEmpty())
    }.getOrDefault(0)

    private data class SkinTarget(
        val id: Long,
        val version: Long,
        val skin: JSONObject,
        val garbDir: File,
        val assetsDir: File,
    )

    private const val REAPPLY_DELAY_MS = 500L
    private const val REAPPLY_MIN_INTERVAL_MS = 3_000L
    private const val REAPPLY_WINDOW_MS = 30_000L
    private const val MAX_REAPPLIES_PER_WINDOW = 3
    private const val EXTRA_SELF_APPLIED = "io.github.bbzq.extra.SELF_APPLIED_GARB"
}
