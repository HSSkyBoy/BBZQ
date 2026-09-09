package io.github.biliz.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.biliz.R

/**
 * 贴近 B 站顶栏分区的现代滑动药丸胶囊分类栏。
 *
 * 纯中文字符标签，零 emoji：
 * 0: 全部
 * 1: 播放与视听
 * 2: 净化与拦截
 * 3: 界面与装扮
 * 4: 通用与工具
 * 5: 关于与备份
 */
class BiliCategoryTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    companion object {
        val CATEGORIES = listOf(
            "全部",
            "播放与视听",
            "净化与拦截",
            "界面与装扮",
            "通用与工具",
            "关于与备份",
        )
    }

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(dp(12f), dp(4f), dp(12f), dp(4f))
    }

    var selectedIndex: Int = 0
        private set

    var onCategorySelected: ((index: Int) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density + 0.5f).toInt()

    private val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val activeBgColor = context.getColor(R.color.accent_pink)
    private val activeTextColor = Color.WHITE

    private val inactiveBgColor = if (isNight) Color.parseColor("#222226") else Color.parseColor("#FFFFFF")
    private val inactiveTextColor = if (isNight) Color.parseColor("#A0A0A0") else Color.parseColor("#61666D")
    private val inactiveStrokeColor = if (isNight) Color.parseColor("#333338") else Color.parseColor("#E3E5E7")

    private val tabViews = mutableListOf<TextView>()

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(Color.TRANSPARENT)
        addView(
            container,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )
        buildTabs()
    }

    private fun buildTabs() {
        container.removeAllViews()
        tabViews.clear()

        CATEGORIES.forEachIndexed { index, title ->
            val textView = TextView(context).apply {
                text = title
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    playSoundEffect(SoundEffectConstants.CLICK)
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    select(index, notify = true)
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32f),
            ).apply {
                if (index > 0) marginStart = dp(8f)
            }

            container.addView(textView, lp)
            tabViews.add(textView)
        }

        updateTabStates()
    }

    fun select(index: Int, notify: Boolean = true) {
        if (index in CATEGORIES.indices && selectedIndex != index) {
            selectedIndex = index
            updateTabStates()
            scrollToCenter(index)
            if (notify) {
                onCategorySelected?.invoke(index)
            }
        }
    }

    private fun updateTabStates() {
        val radius = dp(16f).toFloat()
        tabViews.forEachIndexed { index, view ->
            val isSelected = index == selectedIndex
            view.background = GradientDrawable().apply {
                cornerRadius = radius
                if (isSelected) {
                    setColor(activeBgColor)
                } else {
                    setColor(inactiveBgColor)
                    setStroke(dp(1f), inactiveStrokeColor)
                }
            }
            view.setTextColor(if (isSelected) activeTextColor else inactiveTextColor)
            view.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            view.elevation = if (isSelected) dp(4f).toFloat() else dp(2f).toFloat()
        }
    }

    private fun scrollToCenter(index: Int) {
        val targetView = tabViews.getOrNull(index) ?: return
        post {
            val viewLeft = targetView.left
            val viewWidth = targetView.width
            val scrollWidth = width
            val scrollX = viewLeft - (scrollWidth / 2) + (viewWidth / 2)
            smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }
}
