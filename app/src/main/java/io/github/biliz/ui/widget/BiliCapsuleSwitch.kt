package io.github.biliz.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.github.biliz.R

/**
 * 贴近 B 站官方原生现代风格的药丸胶囊开关组件。
 *
 * - 46dp x 26dp 黄金药丸比例；
 * - 开启态为标志性哔哩粉色 (#FB7299)；
 * - 关闭态为柔和自然浅灰色 (#E3E5E7 / 深色 #363638)；
 * - 浮雕纯白圆形滑块带微投影，支持 200ms 平滑减速动画。
 */
class BiliCapsuleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var isChecked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateThumb(value)
                onCheckedChangeListener?.invoke(this, value)
            }
        }

    fun setCheckedSilently(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            animProgress = if (checked) 1f else 0f
            invalidate()
        }
    }

    var onCheckedChangeListener: ((BiliCapsuleSwitch, Boolean) -> Unit)? = null

    private var animProgress = 0f
    private var animator: ValueAnimator? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    // 默认尺寸 46dp x 26dp
    private val defaultWidth = dp(46f)
    private val defaultHeight = dp(26f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 0, 0, 0)
    }

    private val trackRect = RectF()

    private val activeTrackColor = context.getColor(R.color.accent_pink)
    private val inactiveTrackColor = runCatching {
        val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isNight) Color.parseColor("#363638") else Color.parseColor("#E3E5E7")
    }.getOrDefault(Color.LTGRAY)

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(defaultWidth.toInt(), widthMeasureSpec)
        val h = resolveSize(defaultHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = dp(1f)
        trackRect.set(padding, padding, w - padding, h - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = trackRect.height() / 2f
        // 轨道颜色渐变插值
        val currentTrackColor = blendColor(inactiveTrackColor, activeTrackColor, animProgress)
        trackPaint.color = currentTrackColor
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        // 滑块 Thumb
        val thumbRadius = radius - dp(2.5f)
        val minX = trackRect.left + dp(2.5f) + thumbRadius
        val maxX = trackRect.right - dp(2.5f) - thumbRadius
        val currentX = minX + (maxX - minX) * animProgress
        val currentY = trackRect.centerY()

        // 柔和微投影
        canvas.drawCircle(currentX, currentY + dp(1f), thumbRadius + dp(0.5f), shadowPaint)
        // 纯白滑块
        canvas.drawCircle(currentX, currentY, thumbRadius, thumbPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        playSoundEffect(SoundEffectConstants.CLICK)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        isChecked = !isChecked
        return true
    }

    private fun animateThumb(targetChecked: Boolean) {
        animator?.cancel()
        val startProgress = animProgress
        val endProgress = if (targetChecked) 1f else 0f
        animator = ValueAnimator.ofFloat(startProgress, endProgress).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                animProgress = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun blendColor(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio).toInt()
        val r = (Color.red(from) * inverseRatio + Color.red(to) * ratio).toInt()
        val g = (Color.green(from) * inverseRatio + Color.green(to) * ratio).toInt()
        val b = (Color.blue(from) * inverseRatio + Color.blue(to) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
}
