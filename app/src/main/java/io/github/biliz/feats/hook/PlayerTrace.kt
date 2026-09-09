package io.github.biliz.feats.hook

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Structured diagnostic tracer for the entire Bilibili player quality pipeline:
 * Preload -> PlayView RPC Request -> PlayView RPC Response -> Quality Strategy -> Quality Switching.
 */
object PlayerTrace {
    private const val TAG = "BILIz-PlayerTrace"
    private const val MAX_TRACE_LOGS = 60
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    data class TraceEntry(
        val timestamp: Long,
        val stage: String,
        val message: String,
        val isWarning: Boolean = false,
    ) {
        val formattedTime: String
            get() = timeFormat.format(Date(timestamp))

        override fun toString(): String = "[$formattedTime][$stage] $message"
    }

    private val traceHistory = ConcurrentLinkedDeque<TraceEntry>()

    fun getRecentTraces(): List<TraceEntry> = traceHistory.toList()

    private fun addEntry(stage: String, message: String, isWarning: Boolean = false) {
        val entry = TraceEntry(System.currentTimeMillis(), stage, message, isWarning)
        traceHistory.addLast(entry)
        while (traceHistory.size > MAX_TRACE_LOGS) {
            traceHistory.pollFirst()
        }
        if (isWarning) {
            Log.w(TAG, "[$stage] $message")
        } else {
            Log.i(TAG, "[$stage] $message")
        }
    }

    fun d(stage: String, message: String) = addEntry(stage, message, false)
    fun w(stage: String, message: String) = addEntry(stage, message, true)

    fun logPreload(methodName: String, blocked: Boolean) {
        val action = if (blocked) "BLOCKED (low-quality stream prevented)" else "ALLOWED"
        d("PRELOAD", "$methodName -> $action")
    }

    fun logPlayViewRequest(bvid: String, fnval: Long, qn: Long, trial: Boolean) {
        d("PLAYVIEW-REQ", "bvid=$bvid fnval=$fnval targetQn=$qn isNeedTrial=$trial")
    }

    fun logPlayViewResponse(
        bvid: String,
        selectedQn: Long?,
        availableQns: List<Long>,
        reordered: Boolean,
        vipCleared: Boolean,
    ) {
        d(
            "PLAYVIEW-RESP",
            "bvid=$bvid selectedQn=$selectedQn availableQns=$availableQns reordered=$reordered vipCleared=$vipCleared",
        )
    }

    fun logStrategy(
        methodName: String,
        inputArgs: String,
        decisionQn: Any?,
    ) {
        d("STRATEGY", "$methodName args=[$inputArgs] -> decision=$decisionQn")
    }

    fun logQualitySwitch(fromQn: Any?, toQn: Any?, reason: String) {
        val isDowngrade = ((toQn as? Number)?.toLong() ?: 0) < ((fromQn as? Number)?.toLong() ?: 0)
        val warn = isDowngrade
        addEntry(
            "QUALITY-SWITCH",
            "Quality switched: $fromQn -> $toQn (reason=$reason)",
            warn,
        )
    }
}
