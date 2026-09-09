package io.github.biliz.feats.hook

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.ProgressBar
import io.github.biliz.feats.BilibiliSponsorBlock

internal object SkipVideoAdMarkerRenderer {
    private val sharedRect = RectF()

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        alpha = 255
        style = Paint.Style.FILL
    }

    fun draw(
        progressBar: ProgressBar,
        canvas: Canvas,
        durationMs: Long,
        segments: List<BilibiliSponsorBlock.Segment>,
        colorForCategory: (String) -> Int,
    ) {
        if (durationMs <= 0L || segments.isEmpty()) return
        val track = progressBar.markerTrackBounds() ?: return
        val availableWidth = track.width()
        if (availableWidth <= 0f) return

        val density = progressBar.resources.displayMetrics.density
        val minMarkerWidth = MIN_MARKER_WIDTH_DP * density
        val isRtl = progressBar.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val radius = track.height() / 2f
        val saveCount = canvas.save()
        canvas.clipRect(track)

        segments.forEach { segment ->
            val startMs = segment.segment.getOrNull(0)?.times(1000f) ?: return@forEach
            val endMs = segment.segment.getOrNull(1)?.times(1000f) ?: return@forEach
            if (endMs <= startMs) return@forEach

            val duration = durationMs.toFloat()
            val startRatio = (startMs / duration).coerceIn(0f, 1f)
            val endRatio = (endMs / duration).coerceIn(0f, 1f)
            if (endRatio <= 0f || startRatio >= 1f) return@forEach

            val rawStart = track.left + startRatio * availableWidth
            val rawEnd = track.left + endRatio * availableWidth
            val left = rawStart.coerceIn(track.left, track.right)
            val right = rawEnd.coerceIn(track.left, track.right)
            if (right <= left) return@forEach

            val markerLeft: Float
            val markerRight: Float
            if (isRtl) {
                markerLeft = (track.right - (right - track.left)).coerceIn(track.left, track.right)
                markerRight = (track.right - (left - track.left)).coerceIn(track.left, track.right)
            } else {
                markerLeft = left
                markerRight = right
            }

            val safeRight = (markerLeft + minMarkerWidth).coerceAtMost(track.right)
            val safeLeft = if (safeRight - markerLeft >= minMarkerWidth) {
                markerLeft
            } else {
                (markerRight - minMarkerWidth).coerceAtLeast(track.left)
            }
            val safeEnd = markerRight.coerceAtLeast(safeRight)
            if (safeEnd <= safeLeft) return@forEach

            sharedRect.set(safeLeft, track.top, safeEnd, track.bottom)
            fillPaint.color = colorForCategory(segment.category)
            canvas.drawRoundRect(sharedRect, radius, radius, fillPaint)
        }

        canvas.restoreToCount(saveCount)
    }

    private fun ProgressBar.markerTrackBounds(): RectF? {
        val width = width
        val height = height
        if (width <= 0 || height <= 0) return null

        val contentLeft = paddingLeft.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentRight = (width - paddingRight).toFloat()
        val contentBottom = (height - paddingBottom).toFloat()
        if (contentRight <= contentLeft || contentBottom <= contentTop) return null

        val bounds = progressDrawable?.bounds
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            val relativeRightLimit = (contentRight - contentLeft).coerceAtLeast(0f)
            val relativeBottomLimit = (contentBottom - contentTop).coerceAtLeast(0f)
            val relativeBounds = bounds.left >= 0 &&
                bounds.right.toFloat() <= relativeRightLimit + BOUNDS_EPSILON &&
                bounds.top >= 0 &&
                bounds.bottom.toFloat() <= relativeBottomLimit + BOUNDS_EPSILON

            val left = if (relativeBounds) contentLeft + bounds.left else bounds.left.toFloat()
            val right = if (relativeBounds) contentLeft + bounds.right else bounds.right.toFloat()
            val top = if (relativeBounds) contentTop + bounds.top else bounds.top.toFloat()
            val bottom = if (relativeBounds) contentTop + bounds.bottom else bounds.bottom.toFloat()
            if (right > left &&
                bottom > top &&
                left >= 0f &&
                right <= width.toFloat() &&
                top >= 0f &&
                bottom <= height.toFloat()
            ) {
                return RectF(left, top, right, bottom)
            }
        }

        return RectF(contentLeft, contentTop, contentRight, contentBottom)
    }

    private const val MIN_MARKER_WIDTH_DP = 3f
    private const val BOUNDS_EPSILON = 1f
}
