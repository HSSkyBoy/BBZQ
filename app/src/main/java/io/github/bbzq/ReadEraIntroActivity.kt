package io.github.bbzq

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ReadEraIntroActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.page_background))
            setPadding(dp(20), dp(24), dp(20), dp(24))
            addView(createTitle())
            addView(
                createCard(
                    "ReadEra 后台朗读解锁",
                    "解除免费版对「后台朗读（TTS）」的限制",
                ),
            )
            addView(
                createCard(
                    "已适配版本",
                    "ReadEra 26.05.20 (+2300)",
                ),
            )
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.page_background))
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    private fun createTitle(): TextView {
        return TextView(this).apply {
            text = "ReadEra 解锁"
            textSize = 28f
            setTextColor(getColor(R.color.title_text_alt))
            setPadding(0, 0, 0, dp(16))
        }
    }

    private fun createCard(title: String, body: String, onClick: (() -> Unit)? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(getColor(R.color.card_background))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            }

            addView(TextView(this@ReadEraIntroActivity).apply {
                text = title
                textSize = 15f
                setTextColor(getColor(R.color.accent_pink))
            })
            addView(TextView(this@ReadEraIntroActivity).apply {
                text = body
                textSize = 16f
                setTextColor(getColor(R.color.body_text))
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
