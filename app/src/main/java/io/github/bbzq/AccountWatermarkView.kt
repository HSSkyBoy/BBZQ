package io.github.bbzq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal class AccountWatermarkView(
    context: Context,
    private val watermark: String,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.watermark)
        textSize = 13f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val xStep = 230f * resources.displayMetrics.density
        val yStep = 96f * resources.displayMetrics.density
        var y = -height.toFloat()
        while (y < height * 2f) {
            var x = -width.toFloat()
            while (x < width * 2f) {
                canvas.save()
                canvas.rotate(-24f, x, y)
                canvas.drawText(watermark, x, y, paint)
                canvas.restore()
                x += xStep
            }
            y += yStep
        }
    }
}
