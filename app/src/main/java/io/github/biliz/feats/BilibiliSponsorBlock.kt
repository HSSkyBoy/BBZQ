package io.github.biliz.feats

import io.github.biliz.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class BilibiliSponsorBlock(
    private val bvid: String,
    private val cid: String,
    private val enabledCategories: Set<String>,
) {
    fun getSegments(): FetchResult {
        val trimmedBvid = bvid.trim()
        if (trimmedBvid.isEmpty()) {
            return FetchResult(FetchStatus.FAILED, emptyList())
        }

        val hash = trimmedBvid.sha256()
        require(hash.length >= HASH_PREFIX_LENGTH) { "SHA-256 hash length must be at least $HASH_PREFIX_LENGTH" }
        val hashPrefix = hash.take(HASH_PREFIX_LENGTH)

        val result = getSegmentsWithFallback(hashPrefix, trimmedBvid)
        return result.filterByCategories(enabledCategories).filterByCid(cid)
    }

    private fun getSegmentsWithFallback(hashPrefix: String, targetBvid: String): FetchResult {
        var lastResult: FetchResult? = null
        for (baseUrl in BASE_URLS) {
            val result = fetchSegments(buildRequest(baseUrl, hashPrefix), targetBvid)
            lastResult = result
            if (result.status != FetchStatus.FAILED && result.status != FetchStatus.NOT_FOUND) {
                return result
            }
        }
        return lastResult ?: FetchResult(FetchStatus.FAILED, emptyList())
    }

    private fun buildRequest(baseUrl: String, hashPrefix: String): Request =
        Request.Builder()
            .url("$baseUrl$hashPrefix")
            .header("accept", "application/json")
            .header("origin", REQUEST_ORIGIN)
            .header("user-agent", USER_AGENT)
            .header("x-ext-version", BuildConfig.RELEASE_NAME)
            .build()

    private fun fetchSegments(request: Request, targetBvid: String): FetchResult {
        return try {
            httpClient.newCall(request).execute().use { response ->
                val statusCode = response.code
                when {
                    statusCode == 404 -> FetchResult(FetchStatus.NOT_FOUND, emptyList(), statusCode)
                    !response.isSuccessful -> FetchResult(FetchStatus.FAILED, emptyList(), statusCode)
                    else -> {
                        val body = response.body.string()
                        if (body.isBlank()) {
                            FetchResult(FetchStatus.EMPTY, emptyList(), statusCode)
                        } else {
                            parseSegments(body, targetBvid, statusCode)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            FetchResult(FetchStatus.FAILED, emptyList())
        }
    }

    private fun parseSegments(
        json: String,
        targetBvid: String,
        statusCode: Int,
    ): FetchResult {
        return try {
            val payload = JSONArray(json)
            if (payload.length() == 0) {
                return FetchResult(FetchStatus.EMPTY, emptyList(), statusCode)
            }

            for (index in 0 until payload.length()) {
                val videoEntry = payload.optJSONObject(index) ?: continue
                if (videoEntry.optString("videoID") != targetBvid) continue

                val segments = videoEntry.optJSONArray("segments")
                    ?.toSegmentList()
                    .orEmpty()
                    .filter(::isSkippableSegment)
                    .sortedBy { it.segment[0] }

                return if (segments.isEmpty()) {
                    FetchResult(FetchStatus.EMPTY, emptyList(), statusCode)
                } else {
                    FetchResult(FetchStatus.SUCCESS, segments, statusCode)
                }
            }

            // 伺服器有該 Hash Prefix 的資料，但列表中未包含此目標 BVID
            FetchResult(FetchStatus.VIDEO_NOT_IN_DB, emptyList(), statusCode)
        } catch (_: JSONException) {
            FetchResult(FetchStatus.FAILED, emptyList(), statusCode)
        }
    }

    private fun JSONArray.toSegmentList(): List<Segment> {
        val items = ArrayList<Segment>(length())
        for (index in 0 until length()) {
            val segment = optJSONObject(index)?.toSegment() ?: continue
            items += segment
        }
        return items
    }

    private fun JSONObject.toSegment(): Segment? {
        val segmentArray = optJSONArray("segment") ?: return null
        if (segmentArray.length() < 2) return null

        val start = segmentArray.optDouble(0, Double.NaN)
        val end = segmentArray.optDouble(1, Double.NaN)
        if (!start.isFinite() || !end.isFinite() || end <= start) return null

        return Segment(
            segment = floatArrayOf(start.toFloat(), end.toFloat()),
            cid = optString("cid"),
            uuid = optString("UUID"),
            category = optString("category"),
            actionType = optString("actionType"),
            videoDuration = optInt("videoDuration"),
            locked = optInt("locked"),
            votes = optInt("votes"),
        )
    }

    private fun isSkippableSegment(segment: Segment): Boolean =
        segment.actionType.equals(ACTION_SKIP, ignoreCase = true)

    private fun FetchResult.filterByCategories(categories: Set<String>): FetchResult {
        if (segments.isEmpty()) return this
        if (categories.isEmpty()) return copy(status = FetchStatus.FILTERED_BY_CATEGORY, segments = emptyList())

        val filtered = segments.filter { it.category in categories }
        return copy(
            status = if (filtered.isEmpty() && status == FetchStatus.SUCCESS) {
                FetchStatus.FILTERED_BY_CATEGORY
            } else {
                status
            },
            segments = filtered,
        )
    }

    private fun FetchResult.filterByCid(targetCid: String): FetchResult {
        if (segments.isEmpty()) return this
        if (targetCid.isBlank()) return this

        val filtered = segments.filter { it.cid.isBlank() || it.cid == targetCid }
        return copy(
            status = if (filtered.isEmpty() && status == FetchStatus.SUCCESS) FetchStatus.FILTERED_BY_CID else status,
            segments = filtered,
        )
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02x".format(it) }

    data class Segment(
        val segment: FloatArray,
        val cid: String,
        val uuid: String,
        val category: String,
        val actionType: String,
        val videoDuration: Int,
        val locked: Int,
        val votes: Int,
    ) {
        val start: Float get() = segment.getOrElse(0) { 0f }
        val end: Float get() = segment.getOrElse(1) { 0f }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Segment
            return segment.contentEquals(other.segment) &&
                cid == other.cid &&
                uuid == other.uuid &&
                category == other.category &&
                actionType == other.actionType &&
                videoDuration == other.videoDuration &&
                locked == other.locked &&
                votes == other.votes
        }

        override fun hashCode(): Int {
            var result = segment.contentHashCode()
            result = 31 * result + cid.hashCode()
            result = 31 * result + uuid.hashCode()
            result = 31 * result + category.hashCode()
            result = 31 * result + actionType.hashCode()
            result = 31 * result + videoDuration
            result = 31 * result + locked
            result = 31 * result + votes
            return result
        }
    }

    data class FetchResult(
        val status: FetchStatus,
        val segments: List<Segment>,
        val httpStatusCode: Int? = null,
    )

    enum class FetchStatus {
        SUCCESS,
        EMPTY,
        FILTERED_BY_CATEGORY,
        FILTERED_BY_CID,
        NOT_FOUND,          // HTTP 404：伺服器無此 Hash 前綴紀錄
        VIDEO_NOT_IN_DB,    // HTTP 200：Hash 前綴命中，但 payload 中無該特定 BVID
        FAILED,             // 網路連線失敗或非預期 HTTP 錯誤碼
    }

    private companion object {
        private const val ACTION_SKIP = "skip"
        private const val HASH_PREFIX_LENGTH = 4
        private const val REQUEST_ORIGIN = "BILIZ"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android; Xposed; NkBe) BILIZ/1.2.1"

        private val BASE_URLS = listOf(
            "https://bsbsb.top/api/skipSegments/",
            "https://www.bsbsb.xyz/api/skipSegments/",
            "http://154.222.28.109/api/skipSegments/",
            "https://biliz.nkbe.top:9876/api/skipSegments/",
            "http://103.236.70.57:9876/api/skipSegments/",
        )

        private val httpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }
    }
}

