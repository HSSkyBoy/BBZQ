package io.github.bbzq.feats.hook

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import io.github.bbzq.feats.BilibiliSponsorBlock

class SkipVideoAdMarkerDrawableWrapper(
    private val wrapped: Drawable,
    private val minMarkerWidthPx: Float,
    private val segmentsProvider: () -> Pair<Long, List<BilibiliSponsorBlock.Segment>>?,
    private val colorForCategory: (String) -> Int,
    private val onSegmentsDrawn: (() -> Unit)? = null,
) : Drawable(), Drawable.Callback {

    private val trackBoundsF = RectF()
    private val segmentRect = RectF()

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        alpha = 255
        style = Paint.Style.FILL
    }

    init {
        wrapped.callback = this
    }

    override fun draw(canvas: Canvas) {
        wrapped.draw(canvas)

        val data = segmentsProvider() ?: return
        val durationMs = data.first
        val segments = data.second
        if (durationMs <= 0L || segments.isEmpty()) return

        val bounds = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        trackBoundsF.set(bounds)
        val availableWidth = trackBoundsF.width()
        if (availableWidth <= 0f) return

        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        val radius = trackBoundsF.height() / 2f
        val saveCount = canvas.save()
        canvas.clipRect(trackBoundsF)

        val trackLeft = trackBoundsF.left
        val trackRight = trackBoundsF.right
        val trackTop = trackBoundsF.top
        val trackBottom = trackBoundsF.bottom

        segments.forEach { segment ->
            val startMs = segment.segment.getOrNull(0)?.times(1000f) ?: return@forEach
            val endMs = segment.segment.getOrNull(1)?.times(1000f) ?: return@forEach
            if (endMs <= startMs) return@forEach

            val duration = durationMs.toFloat()
            val startRatio = (startMs / duration).coerceIn(0f, 1f)
            val endRatio = (endMs / duration).coerceIn(0f, 1f)
            if (endRatio <= 0f || startRatio >= 1f) return@forEach

            val rawStart = trackLeft + startRatio * availableWidth
            val rawEnd = trackLeft + endRatio * availableWidth
            val left = rawStart.coerceIn(trackLeft, trackRight)
            val right = rawEnd.coerceIn(trackLeft, trackRight)
            if (right <= left) return@forEach

            val markerLeft: Float
            val markerRight: Float
            if (isRtl) {
                markerLeft = (trackRight - (right - trackLeft)).coerceIn(trackLeft, trackRight)
                markerRight = (trackRight - (left - trackLeft)).coerceIn(trackLeft, trackRight)
            } else {
                markerLeft = left
                markerRight = right
            }

            val safeRight = (markerLeft + minMarkerWidthPx).coerceAtMost(trackRight)
            val safeLeft = if (safeRight - markerLeft >= minMarkerWidthPx) {
                markerLeft
            } else {
                (markerRight - minMarkerWidthPx).coerceAtLeast(trackLeft)
            }
            val safeEnd = markerRight.coerceAtLeast(safeRight)
            if (safeEnd <= safeLeft) return@forEach

            segmentRect.set(safeLeft, trackTop, safeEnd, trackBottom)
            fillPaint.color = colorForCategory(segment.category)
            canvas.drawRoundRect(segmentRect, radius, radius, fillPaint)
        }

        canvas.restoreToCount(saveCount)
        onSegmentsDrawn?.invoke()
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        wrapped.setBounds(left, top, right, bottom)
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        wrapped.bounds = bounds
    }

    override fun setAlpha(alpha: Int) {
        wrapped.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wrapped.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = wrapped.opacity

    override fun getIntrinsicWidth(): Int = wrapped.intrinsicWidth

    override fun getIntrinsicHeight(): Int = wrapped.intrinsicHeight

    override fun isStateful(): Boolean = wrapped.isStateful

    override fun onStateChange(state: IntArray): Boolean = wrapped.setState(state)

    override fun onLevelChange(level: Int): Boolean = wrapped.setLevel(level)

    override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean =
        wrapped.setLayoutDirection(layoutDirection)

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    fun getWrappedDrawable(): Drawable = wrapped
}
