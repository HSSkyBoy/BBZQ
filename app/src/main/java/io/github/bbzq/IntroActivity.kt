package io.github.bbzq

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class IntroActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("unknown")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.page_background))
            setPadding(dp(20), dp(24), dp(20), dp(24))
            addView(createTitle())
            addView(createCard(getString(R.string.intro_module_title), getString(R.string.intro_module_body, versionName)))
            addView(
                createCard(
                    getString(R.string.intro_desc_title),
                    getString(R.string.intro_desc_body),
                ),
            )

            addView(
                createCard(
                    "功能开关",
                    "跳过视频广告位于 BBZQ 设置页的“播放净化”分组；开启后进入视频会提示广告片段加载结果。",
                ),
            )
            addView(
                createCard(
                    "打开 BBZQ 设置",
                    "也可以直接从这里进入设置页调整所有功能开关。",
                ) {
                    startActivity(Intent(this@IntroActivity, SettingsActivity::class.java))
                },
            )
            addView(
                createCard(
                    getString(R.string.intro_launcher_title),
                    getString(R.string.intro_launcher_body),
                ),
            )
            addView(
                createCard(
                    "WO Mic 解锁",
                    "伪造订阅、解锁高级音量、拦截广告，并提供已适配的 WO Mic 5.3 下载。点击查看详情。",
                ) {
                    startActivity(Intent(this@IntroActivity, WoMicIntroActivity::class.java))
                },
            )
            addView(
                createCard(
                    "ReadEra 解锁",
                    "解锁 ReadEra 后台朗读无限制：熄屏/后台不再暂停，不再弹付费提示。点击查看详情。",
                ) {
                    startActivity(Intent(this@IntroActivity, ReadEraIntroActivity::class.java))
                },
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
            text = getString(R.string.app_name)
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

            addView(TextView(this@IntroActivity).apply {
                text = title
                textSize = 15f
                setTextColor(getColor(R.color.accent_pink))
            })
            addView(TextView(this@IntroActivity).apply {
                text = body
                textSize = 16f
                setTextColor(getColor(R.color.body_text))
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
