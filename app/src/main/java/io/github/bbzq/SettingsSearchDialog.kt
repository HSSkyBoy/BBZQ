package io.github.bbzq

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal object SettingsSearchDialog {
    fun show(
        activity: SettingsActivity,
        targets: List<SettingsSearchTarget>,
        onPick: (SettingsSearchTarget) -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        val titleColor = activity.getColor(R.color.title_text)
        val summaryColor = activity.getColor(R.color.summary_text)
        val accent = activity.getColor(R.color.accent_pink)
        val cardColor = activity.getColor(R.color.card_background)
        val pageColor = activity.getColor(R.color.page_background)
        val targetByKey = targets.associateBy { it.item.key }

        var dialog: AlertDialog? = null
        var picked = false
        fun pick(target: SettingsSearchTarget) {
            if (picked) return
            picked = true
            dialog?.dismiss()
            onPick(target)
        }

        val editor = EditText(activity).apply {
            hint = activity.getString(R.string.settings_search_hint)
            setTextColor(titleColor)
            setHintTextColor(colorWithAlpha(summaryColor, 0x99))
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(
                (14 * density).toInt(),
                (11 * density).toInt(),
                (14 * density).toInt(),
                (11 * density).toInt(),
            )
            background = GradientDrawable().apply {
                cornerRadius = 10f * density
                setColor(pageColor)
            }
        }

        val resultContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val resultHeight = minOf(
            (360 * density).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.44f).toInt(),
        )
        val resultScroll = object : ScrollView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(resultHeight, MeasureSpec.AT_MOST),
                )
            }
        }.apply {
            addView(
                resultContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        fun renderResults(query: String) {
            resultContainer.removeAllViews()
            val results = SettingsSearchMatcher.searchMatches(
                query = query,
                items = targets.map(SettingsSearchTarget::item),
            )
            if (query.isBlank() || results.isEmpty()) {
                resultContainer.addView(
                    TextView(activity).apply {
                        text = activity.getString(
                            if (query.isBlank()) {
                                R.string.settings_search_prompt
                            } else {
                                R.string.settings_search_empty
                            },
                        )
                        gravity = Gravity.CENTER
                        setTextColor(summaryColor)
                        textSize = 13f
                        alpha = 0.72f
                        setPadding(
                            (12 * density).toInt(),
                            (36 * density).toInt(),
                            (12 * density).toInt(),
                            (36 * density).toInt(),
                        )
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                return
            }

            results.forEach { match ->
                val item = match.item
                val target = targetByKey[item.key] ?: return@forEach
                resultContainer.addView(
                    LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(
                            (12 * density).toInt(),
                            (10 * density).toInt(),
                            (12 * density).toInt(),
                            (10 * density).toInt(),
                        )
                        isClickable = true
                        isFocusable = true
                        contentDescription = "${item.title}. ${item.section}"
                        setOnClickListener { pick(target) }
                        addView(
                            TextView(activity).apply {
                                text = highlightedSettingsSearchText(item.title, match.titleRanges, accent)
                                setTextColor(titleColor)
                                textSize = 15f
                            },
                        )
                        if (item.detail.isNotBlank()) {
                            addView(
                                TextView(activity).apply {
                                    text = highlightedSettingsSearchText(item.detail, match.detailRanges, accent)
                                    setTextColor(summaryColor)
                                    textSize = 12f
                                    alpha = 0.82f
                                    maxLines = 2
                                    ellipsize = TextUtils.TruncateAt.END
                                },
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply { topMargin = (3 * density).toInt() },
                            )
                        }
                        addView(
                            TextView(activity).apply {
                                text = highlightedSettingsSearchText(item.section, match.sectionRanges, accent)
                                setTextColor(summaryColor)
                                textSize = 11f
                                alpha = 0.68f
                            },
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = (3 * density).toInt() },
                        )
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = (4 * density).toInt() },
                )
            }
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(editable: Editable?) {
                renderResults(editable?.toString().orEmpty())
            }
        })
        renderResults("")

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cardColor)
            setPadding(
                (20 * density).toInt(),
                (16 * density).toInt(),
                (20 * density).toInt(),
                (8 * density).toInt(),
            )
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.settings_search_title)
                    setTextColor(titleColor)
                    textSize = 19f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                },
            )
            addView(
                editor,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (12 * density).toInt() },
            )
            addView(
                resultScroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (10 * density).toInt() },
            )
        }

        dialog = AlertDialog.Builder(activity)
            .setView(container)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
        )
        dialog?.setOnShowListener { editor.requestFocus() }
        dialog?.show()
    }

    private fun highlightedSettingsSearchText(
        value: String,
        ranges: List<IntRange>,
        accent: Int,
    ): CharSequence {
        if (value.isEmpty() || ranges.isEmpty()) return value
        return SpannableString(value).apply {
            ranges.forEach { range ->
                val start = range.first.coerceIn(0, value.length)
                val end = (range.last + 1).coerceIn(start, value.length)
                if (start >= end) return@forEach
                setSpan(
                    BackgroundColorSpan(colorWithAlpha(accent, 0x32)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(accent),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
