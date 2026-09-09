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
 * 贴近 B 站官方原生现代风格的横向药丸胶囊分段选择器。
 *
 * - 全药丸圆角胶囊 (高度 30dp，圆角 15dp)；
 * - 选中态：高对比度 B 站粉 (#FB7299) 填充底色 + 纯白粗体；
 * - 未选中态：官方浅灰底色 (#F1F2F3 / 夜间 #28282A) + 灰色文字 (#61666D)；
 * - 支持快速横向平滑滚动与即时触控反馈。
 */
class BiliSegmentedGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    data class Item(val key: String, val label: String)

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private var items: List<Item> = emptyList()
    var selectedKey: String? = null
        private set

    var onSelectionChanged: ((key: String) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float) = (v * density + 0.5f).toInt()

    private val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val activeBgColor = context.getColor(R.color.accent_pink)
    private val activeTextColor = Color.WHITE

    private val inactiveBgColor = if (isNight) Color.parseColor("#28282A") else Color.parseColor("#F1F2F3")
    private val inactiveTextColor = if (isNight) Color.parseColor("#9E9E9E") else Color.parseColor("#61666D")

    private val itemViews = mutableMapOf<String, TextView>()

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(
            container,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
        )
    }

    fun setItems(newItems: List<Item>, currentKey: String? = null) {
        items = newItems
        selectedKey = currentKey ?: newItems.firstOrNull()?.key
        rebuildViews()
    }

    fun select(key: String, notify: Boolean = true) {
        if (selectedKey != key) {
            selectedKey = key
            updateViewsState()
            if (notify) {
                onSelectionChanged?.invoke(key)
            }
        }
    }

    private fun rebuildViews() {
        container.removeAllViews()
        itemViews.clear()

        items.forEachIndexed { index, item ->
            val textView = TextView(context).apply {
                text = item.label
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    playSoundEffect(SoundEffectConstants.CLICK)
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    select(item.key, notify = true)
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(30f),
            ).apply {
                if (index > 0) marginStart = dp(8f)
            }

            container.addView(textView, lp)
            itemViews[item.key] = textView
        }

        updateViewsState()
    }

    private fun updateViewsState() {
        val radius = dp(15f).toFloat()
        itemViews.forEach { (key, view) ->
            val isSelected = key == selectedKey
            view.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(if (isSelected) activeBgColor else inactiveBgColor)
            }
            view.setTextColor(if (isSelected) activeTextColor else inactiveTextColor)
            view.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}
