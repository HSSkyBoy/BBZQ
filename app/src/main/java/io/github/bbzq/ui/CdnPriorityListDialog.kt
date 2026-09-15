package io.github.bbzq.ui

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R

class CdnPriorityListDialog(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val isCellular: Boolean,
    private val onApplied: () -> Unit,
) {
    private val prefKey = if (isCellular) ModuleSettings.KEY_CDN_CELLULAR_PRIORITY else ModuleSettings.KEY_CDN_WIFI_PRIORITY
    private val enabledKey = if (isCellular) ModuleSettings.KEY_CDN_CELLULAR_ENABLED else ModuleSettings.KEY_CDN_WIFI_ENABLED
    private val selectedHosts = ArrayList<String>()
    private var isEnabled = false

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun show() {
        isEnabled = prefs.getBoolean(enabledKey, false)
        selectedHosts.clear()
        selectedHosts.addAll(ModuleSettings.getCdnPriorityList(prefs, prefKey))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        val hintView = TextView(context).apply {
            text = context.getString(R.string.cdn_priority_hint)
            textSize = 12f
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(hintView)

        val listView = ListView(context).apply {
            dividerHeight = dp(1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
        }
        root.addView(listView)

        lateinit var adapter: BaseAdapter
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = selectedHosts.size
            override fun getItem(position: Int): String = selectedHosts[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val host = getItem(position)
                val endpointName = ModuleSettings.cdnEndpoints.firstOrNull { it.host == host }?.name ?: host

                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                }

                val titleCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = "${position + 1}. $endpointName"
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                    })
                    addView(TextView(context).apply {
                        text = host
                        textSize = 11f
                    })
                }
                row.addView(titleCol)

                if (position > 0) {
                    row.addView(Button(context).apply {
                        text = "↑"
                        setOnClickListener {
                            val temp = selectedHosts[position]
                            selectedHosts[position] = selectedHosts[position - 1]
                            selectedHosts[position - 1] = temp
                            adapter.notifyDataSetChanged()
                        }
                    }, LinearLayout.LayoutParams(dp(44), dp(36)).apply { marginEnd = dp(4) })
                }
                if (position < selectedHosts.size - 1) {
                    row.addView(Button(context).apply {
                        text = "↓"
                        setOnClickListener {
                            val temp = selectedHosts[position]
                            selectedHosts[position] = selectedHosts[position + 1]
                            selectedHosts[position + 1] = temp
                            adapter.notifyDataSetChanged()
                        }
                    }, LinearLayout.LayoutParams(dp(44), dp(36)).apply { marginEnd = dp(4) })
                }

                row.addView(Button(context).apply {
                    text = "✕"
                    setOnClickListener {
                        selectedHosts.removeAt(position)
                        adapter.notifyDataSetChanged()
                    }
                }, LinearLayout.LayoutParams(dp(44), dp(36)))

                return row
            }
        }
        listView.adapter = adapter

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }

        btnRow.addView(Button(context).apply {
            text = context.getString(R.string.cdn_priority_add)
            setOnClickListener {
                if (selectedHosts.size >= ModuleSettings.MAX_CDN_PRIORITY_NODES) {
                    Toast.makeText(context, context.getString(R.string.cdn_priority_limit), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                showAddDialog { addedHost ->
                    if (!selectedHosts.contains(addedHost)) {
                        selectedHosts.add(addedHost)
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        btnRow.addView(Button(context).apply {
            text = context.getString(R.string.cdn_priority_clear)
            setOnClickListener {
                selectedHosts.clear()
                adapter.notifyDataSetChanged()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        root.addView(btnRow)

        val title = if (isCellular) {
            context.getString(R.string.cdn_priority_dialog_title_cellular)
        } else {
            context.getString(R.string.cdn_priority_dialog_title_wifi)
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(root)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                prefs.edit()
                    .putBoolean(enabledKey, selectedHosts.isNotEmpty())
                    .apply()
                ModuleSettings.saveCdnPriorityList(prefs, prefKey, selectedHosts)
                onApplied()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showAddDialog(onSelected: (String) -> Unit) {
        val endpoints = ModuleSettings.cdnEndpoints
        val labels = (endpoints.map { "${it.name}\n${it.host}" } + context.getString(R.string.custom_cdn_host_custom)).toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(R.string.custom_cdn_host_dialog_title)
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                if (which < endpoints.size) {
                    onSelected(endpoints[which].host)
                } else {
                    val input = android.widget.EditText(context).apply {
                        setSingleLine(true)
                        hint = "upos-sz-mirrorali.bilivideo.com"
                    }
                    AlertDialog.Builder(context)
                        .setTitle(R.string.custom_cdn_host_custom)
                        .setView(input)
                        .setPositiveButton(R.string.dialog_save) { _, _ ->
                            val host = ModuleSettings.normalizeCdnHost(input.text?.toString())
                            if (host != null) {
                                onSelected(host)
                            } else {
                                Toast.makeText(context, context.getString(R.string.custom_cdn_host_invalid), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
