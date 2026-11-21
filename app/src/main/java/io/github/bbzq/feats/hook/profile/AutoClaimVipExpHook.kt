package io.github.bbzq.feats.hook.profile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostAccountCookies
import io.github.bbzq.feats.RoamingEnv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AutoClaimVipExpHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (!ModuleSettings.isAutoClaimVipExpEnabled(prefs)) return
        if (!scheduled.compareAndSet(false, true)) return

        Thread({
            runCatching {
                Thread.sleep(INITIAL_DELAY_MS)
                claimIfNeeded()
            }.onFailure {
                log("AutoClaimVipExpHook failed", it)
            }
        }, "bbzq-vip-exp").apply {
            isDaemon = true
            start()
        }
    }

    private fun claimIfNeeded() {
        val cookieHeader = HostAccountCookies.header(env.hostContext)
        if (cookieHeader.isBlank()) {
            log("AutoClaimVipExpHook: skipped (empty host cookie)")
            return
        }
        val cookies = parseCookies(cookieHeader)
        val sessData = cookies["SESSDATA"]
        val biliJct = cookies["bili_jct"]
        if (sessData.isNullOrBlank() || biliJct.isNullOrBlank()) {
            log("AutoClaimVipExpHook: skipped (missing SESSDATA or bili_jct)")
            return
        }

        val uid = cookies["DedeUserID"].orEmpty().ifBlank { "default" }
        val today = currentShanghaiDate()
        val cachePrefs = env.hostContext.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        val dateKey = "${KEY_LAST_CLAIM_DATE_PREFIX}$uid"
        if (cachePrefs.getString(dateKey, null) == today) {
            return
        }

        val request = Request.Builder()
            .url(VIP_EXPERIENCE_ADD_URL)
            .header("Cookie", cookieHeader)
            .header("Referer", "https://www.bilibili.com/")
            .header("Origin", "https://www.bilibili.com")
            .header("User-Agent", MOBILE_USER_AGENT)
            .post(FormBody.Builder().add("csrf", biliJct).build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                log("AutoClaimVipExpHook: HTTP ${response.code}")
                return
            }
            val json = JSONObject(body)
            val code = json.optInt("code", Int.MIN_VALUE)
            val message = json.optString("message")
            when (code) {
                0 -> {
                    cachePrefs.edit().putString(dateKey, today).apply()
                    log("AutoClaimVipExpHook: claimed daily VIP experience for uid=$uid")
                    showClaimedToast()
                }
                CODE_ALREADY_CLAIMED, CODE_NOT_VIP -> {
                    cachePrefs.edit().putString(dateKey, today).apply()
                    log("AutoClaimVipExpHook: skipped/done code=$code message=$message")
                }
                else -> {
                    log("AutoClaimVipExpHook: failed code=$code message=$message")
                }
            }
        }
    }

    private fun showClaimedToast() {
        val text = runCatching {
            (env.moduleContext ?: env.hostContext).getString(R.string.auto_claim_vip_exp_toast)
        }.getOrElse {
            "已自动领取今日大会员经验"
        }
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(env.hostContext, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseCookies(header: String): Map<String, String> =
        header.split(';')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val name = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (name.isEmpty()) null else name to value
            }
            .toMap()

    private fun currentShanghaiDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date())

    private companion object {
        private const val VIP_EXPERIENCE_ADD_URL = "https://api.bilibili.com/x/vip/experience/add"
        private const val CACHE_PREFS_NAME = "bbzq_vip_exp_cache"
        private const val KEY_LAST_CLAIM_DATE_PREFIX = "last_claim_date_"
        private const val INITIAL_DELAY_MS = 3_000L
        private const val CODE_ALREADY_CLAIMED = 69198
        private const val CODE_NOT_VIP = 69199
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        private val scheduled = AtomicBoolean(false)
        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
