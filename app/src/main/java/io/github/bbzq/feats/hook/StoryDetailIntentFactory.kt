package io.github.bbzq.feats.hook

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import java.util.concurrent.atomic.AtomicLong

internal object StoryDetailIntentFactory {
    fun rewrite(
        intent: Intent,
        backends: List<StoryDetailBackend>
    ): Rewrite = runCatching {
        val component = intent.component

        var snapshot = StoryActivityLaunchSnapshot(
            dataUri = intent.data?.toString(),
            componentPackage = component?.packageName,
            targetPackage = intent.`package`,
            aid = intent.extraToken(AID_EXTRA),
            avid = intent.extraToken(AVID_EXTRA),
            bvid = intent.extraToken(BVID_EXTRA),
            preloadCid = null
        )
        var cidResolved = false
        var firstReason: StoryLaunchSkip? = null

        backends.forEach { backend ->
            var outcome = StoryDetailRoutePolicy.planActivityLaunch(snapshot, backend)
            if (
                outcome is StoryActivityLaunchOutcome.Skipped &&
                outcome.reason == StoryLaunchSkip.MISSING_PRELOAD_CID &&
                !cidResolved
            ) {
                cidResolved = true
                snapshot = snapshot.copy(preloadCid = resolvePreloadCid(intent))
                if (snapshot.preloadCid != null) {
                    outcome = StoryDetailRoutePolicy.planActivityLaunch(snapshot, backend)
                }
            }
            when (outcome) {
                is StoryActivityLaunchOutcome.Skipped -> {
                    if (firstReason == null) firstReason = outcome.reason
                }

                is StoryActivityLaunchOutcome.Planned -> {

                    val built = buildIntent(intent, outcome.plan, backend)
                    if (built != null) return@runCatching Rewrite.Applied(built, backend)
                }
            }
        }
        Rewrite.Skipped(firstReason)
    }.getOrElse { Rewrite.Skipped(null) }
    private fun resolvePreloadCid(intent: Intent): Long? {
        val uri = intent.data
        StoryDetailRoutePolicy.parsePlayerPreloadCid(
            runCatching { uri?.getQueryParameter(PLAYER_PRELOAD_EXTRA) }.getOrNull()
        )?.let { return it }
        StoryDetailRoutePolicy.parsePlayerPreloadCid(
            intent.extraToken(PLAYER_PRELOAD_EXTRA)
        )?.let { return it }
        intent.extraToken(CID_EXTRA)?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        return runCatching { uri?.getQueryParameter(CID_EXTRA) }
            .getOrNull()
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    }

    private fun buildIntent(
        intent: Intent,
        plan: StoryActivityLaunchPlan,
        backend: StoryDetailBackend
    ): Intent? {
        val rewritten = Intent(intent)
        return when (plan) {
            is StoryActivityLaunchPlan.Legacy -> {
                rewritten.data = Uri.parse(plan.detailUri)
                rewritten.component = ComponentName(TARGET_PACKAGE, backend.activityClassName)
                rewritten.takeIf { validateLegacyIntent(it, plan, backend) }
            }

            is StoryActivityLaunchPlan.United -> {
                val preloadToken = nextPlayerPreloadToken()
                UNITED_REPLACED_EXTRAS.forEach(rewritten::removeExtra)
                rewritten.data = Uri.parse(plan.detailUri)
                rewritten.component = ComponentName(TARGET_PACKAGE, backend.activityClassName)
                rewritten.putExtra(PLAYER_PRELOAD_EXTRA, preloadToken)
                rewritten.putExtra(BLROUTER_TARGET_URL_EXTRA, plan.targetUrl)
                rewritten.putExtra(BLROUTER_PAGE_NAME_EXTRA, UNITED_VIDEO_PAGE)
                rewritten.putExtra(BLROUTER_MATCH_RULE_EXTRA, UNITED_VIDEO_PAGE)
                rewritten.putExtra(JUMP_FROM_EXTRA, DETAIL_SOURCE)
                rewritten.putExtra(AID_EXTRA, plan.aid)
                rewritten.putExtra(CID_EXTRA, plan.cid)
                rewritten.putExtra(BVID_EXTRA, "")
                rewritten.putExtra(FROM_EXTRA, DETAIL_SOURCE)
                rewritten.takeIf {
                    validateUnitedIntent(it, plan, backend, preloadToken)
                }
            }
        }
    }

    private fun validateLegacyIntent(
        intent: Intent,
        plan: StoryActivityLaunchPlan.Legacy,
        backend: StoryDetailBackend
    ): Boolean = intent.data?.toString() == plan.detailUri &&
        intent.component?.packageName == TARGET_PACKAGE &&
        intent.component?.className == backend.activityClassName

    private fun validateUnitedIntent(
        intent: Intent,
        plan: StoryActivityLaunchPlan.United,
        backend: StoryDetailBackend,
        preloadToken: String
    ): Boolean = intent.data?.toString() == plan.detailUri &&
        intent.component?.packageName == TARGET_PACKAGE &&
        intent.component?.className == backend.activityClassName &&
        intent.getStringExtra(PLAYER_PRELOAD_EXTRA) == preloadToken &&
        preloadToken.all(Char::isDigit) &&
        intent.getStringExtra(BLROUTER_TARGET_URL_EXTRA) == plan.targetUrl &&
        intent.getStringExtra(BLROUTER_PAGE_NAME_EXTRA) == UNITED_VIDEO_PAGE &&
        intent.getStringExtra(BLROUTER_MATCH_RULE_EXTRA) == UNITED_VIDEO_PAGE &&
        intent.getLongExtra(AID_EXTRA, -1L) == plan.aid &&
        intent.getLongExtra(CID_EXTRA, -1L) == plan.cid &&
        intent.getStringExtra(BVID_EXTRA) == "" &&
        intent.getIntExtra(JUMP_FROM_EXTRA, -1) == DETAIL_SOURCE &&
        intent.getIntExtra(FROM_EXTRA, -1) == DETAIL_SOURCE

    @Suppress("DEPRECATION")
    private fun Intent.extraToken(key: String): String? = runCatching {
        when (val value = extras?.get(key)) {
            is Number -> value.toLong().takeIf { it > 0L }?.toString()
            is String -> value.trim().takeIf(String::isNotEmpty)
            else -> null
        }
    }.getOrNull()

    private fun nextPlayerPreloadToken(): String = playerPreloadSequence.updateAndGet { current ->
        if (current >= MAX_PLAYER_PRELOAD_TOKEN) 1L else current + 1L
    }.toString()

    private const val TARGET_PACKAGE = "tv.danmaku.bili"
    private const val PLAYER_PRELOAD_EXTRA = "player_preload"
    private const val BLROUTER_TARGET_URL_EXTRA = "blrouter.targeturl"
    private const val BLROUTER_PAGE_NAME_EXTRA = "blrouter.pagename"
    private const val BLROUTER_MATCH_RULE_EXTRA = "blrouter.matchrule"
    private const val JUMP_FROM_EXTRA = "jumpFrom"
    private const val AID_EXTRA = "aid"
    private const val AVID_EXTRA = "avid"
    private const val CID_EXTRA = "cid"
    private const val BVID_EXTRA = "bvid"
    private const val FROM_EXTRA = "from"
    private const val UNITED_VIDEO_PAGE = "bilibili://united_video/"
    private const val DETAIL_SOURCE = 7
    private const val MAX_PLAYER_PRELOAD_TOKEN = 999_999_998L
    private val playerPreloadSequence = AtomicLong((System.nanoTime() and 0x3fff_ffffL).coerceAtLeast(1L))
    private val UNITED_REPLACED_EXTRAS = listOf(
        PLAYER_PRELOAD_EXTRA, BLROUTER_TARGET_URL_EXTRA, BLROUTER_PAGE_NAME_EXTRA,
        BLROUTER_MATCH_RULE_EXTRA, JUMP_FROM_EXTRA, AID_EXTRA, AVID_EXTRA, CID_EXTRA, BVID_EXTRA, FROM_EXTRA,
    )

    sealed interface Rewrite {
        data class Applied(val intent: Intent, val backend: StoryDetailBackend) : Rewrite
        data class Skipped(val reason: StoryLaunchSkip?) : Rewrite
    }
}
