package io.github.bbzq.feats.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.HostAccountResolver
import io.github.bbzq.feats.RoamingEnv
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Applies the portable skin JSON produced by BiliRoaming/BiliRoamingX. */
internal object CustomSkinApplier {
    private val receiverRegistered = AtomicBoolean(false)
    private val reapplyPending = AtomicBoolean(false)

    fun applyIfChanged(env: RoamingEnv) {
        if (!ModuleSettings.isCustomSkinEnabled(env.prefs)) return
        val config = ModuleSettings.getCustomSkinJson(env.prefs)
        if (config.isBlank()) return
        registerThemeChangeObserver(env)
        Thread {
            runCatching { apply(env, config) }
                .onFailure { env.log("Custom skin apply failed", it) }
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
        env.hostContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra("key_broadcast_data_type", 0) != 1) return
                if (!ModuleSettings.isCustomSkinEnabled(env.prefs)) return
                val current = intent.getStringExtra("key_garb_data").orEmpty()
                val customId = customSkinId(ModuleSettings.getCustomSkinJson(env.prefs))
                val incomingId = runCatching { JSONObject(current).optLong("id") }.getOrDefault(-1L)
                if (customId <= 0L || incomingId == customId) return
                scheduleReapply(env)
            }
        }, filter)
        env.log("Custom skin observer registered for $action")
    }

    private fun scheduleReapply(env: RoamingEnv) {
        if (!reapplyPending.compareAndSet(false, true)) return
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
        val skin = JSONObject(raw).optJSONObject("user_equip") ?: JSONObject(raw)
        val id = skin.optLong("id")
        val version = skin.optLong("ver")
        val uid = HostAccountResolver.resolve(env.hostContext, env.classLoader).uid.ifBlank { "0" }
        val garbDir = File(env.hostContext.filesDir, "garb/$uid")
        val assetsDir = File(garbDir, "$id/$version")
        if (!assetsDir.isDirectory || assetsDir.listFiles().isNullOrEmpty()) {
            apply(env, raw)
            return
        }
        val garb = toGarb(skin, assetsDir).toString()
        File(garbDir, "garb.conf").writeText(garb)
        notifyGarbChanged(env, garb)
        env.log("Custom skin re-applied after official garb change: id=$id")
    }

    private fun apply(env: RoamingEnv, raw: String) {
        val root = JSONObject(raw)
        val skin = root.optJSONObject("user_equip") ?: root
        val id = skin.optLong("id")
        val version = skin.optLong("ver")
        val packageUrl = skin.optString("package_url")
        require(id > 0 && packageUrl.isNotBlank()) { "Invalid skin JSON: id/package_url missing" }

        val uid = HostAccountResolver.resolve(env.hostContext, env.classLoader).uid.ifBlank { "0" }
        val garbDir = File(env.hostContext.filesDir, "garb/$uid").apply { mkdirs() }
        val assetsDir = File(garbDir, "$id/$version").apply { mkdirs() }
        val archive = File(garbDir, "$id.zip")
        URL(packageUrl).openStream().use { input -> archive.outputStream().use(input::copyTo) }
        unzipSafely(archive, assetsDir)

        val garb = toGarb(skin, assetsDir).toString()
        File(garbDir, "garb.conf").writeText(garb)
        notifyGarbChanged(env, garb)
        env.log("Custom skin applied: id=$id version=$version")
    }

    private fun notifyGarbChanged(env: RoamingEnv, garb: String) {
        env.hostContext.sendBroadcast(Intent("${env.packageName}.garb.GARB_CHANGE").apply {
            putExtra("key_broadcast_data_type", 1)
            putExtra("key_garb_data", garb)
            putExtra("key_theme_change_sync_garb", false)
            putExtra("key_theme_change_should_report", false)
            putExtra("key_theme_change_sync_from_main_process", false)
        })
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

    private const val REAPPLY_DELAY_MS = 500L
}
