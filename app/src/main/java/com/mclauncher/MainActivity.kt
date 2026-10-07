package com.mclauncher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.mclauncher.core.Accounts
import com.mclauncher.core.Env
import com.mclauncher.core.Installer
import com.mclauncher.core.Loaders
import com.mclauncher.core.Logger
import com.mclauncher.core.Renderers
import com.mclauncher.core.VersionManifest

class MainActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var accountChip: TextView
    private var manifestCache: List<VersionEntry> = emptyList()

    class VersionEntry(val id: String, val type: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Env.ensure()
        Logger.init(Env.logsDir)
        buildUi()
        checkStorage()
    }

    private fun checkStorage() {
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, getString(R.string.storage_needed), Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun rootLayout(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(12), dp(16), dp(12))
        root.setBackgroundColor(0xFF0E1116.toInt())
        return root
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun navButton(text: String): Button {
        val b = Button(this)
        b.text = text
        b.setTextColor(0xFFE8EDF2.toInt())
        b.setBackgroundColor(0xFF1E2630.toInt())
        return b
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(0xFF8A94A3.toInt())
        t.textSize = 13f
        return t
    }

    private fun title(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(0xFFE8EDF2.toInt())
        t.textSize = 18f
        t.setPadding(0, dp(8), 0, dp(4))
        return t
    }

    private fun buildUi() {
        val root = rootLayout()
        val scroll = ScrollView(this)
        scroll.viewTreeObserver.addOnScrollChangedListener { }
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(
            content, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scroll, LinearLayout.LayoutParams(
                0, 0, 1f
            )
        )

        accountChip = TextView(this)
        accountChip.setTextColor(0xFF3D8B4F.toInt())
        accountChip.gravity = Gravity.END
        accountChip.setPadding(0, dp(4), 0, dp(8))

        val headerRow = LinearLayout(this)
        headerRow.orientation = LinearLayout.HORIZONTAL
        val appTitle = TextView(this)
        appTitle.text = getString(R.string.app_name)
        appTitle.setTextColor(0xFFE8EDF2.toInt())
        appTitle.textSize = 22f
        appTitle.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        headerRow.addView(appTitle)
        headerRow.addView(accountChip)

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        val buttons = listOf(
            getString(R.string.nav_play) to { showPlay() },
            getString(R.string.nav_versions) to { showVersions() },
            getString(R.string.nav_accounts) to { showAccounts() },
            getString(R.string.nav_settings) to { showSettings() },
            getString(R.string.nav_logs) to { showLogs() }
        )
        for ((text, action) in buttons) {
            val b = navButton(text)
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(dp(2), dp(4), dp(2), dp(4))
            }
            b.setOnClickListener { action() }
            nav.addView(b)
        }

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.addView(headerRow)
        outer.addView(nav)
        root.addView(
            outer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
        showPlay()
        refreshAccountChip()
    }

    private fun refreshAccountChip() {
        val acc = Env.accounts.current()
        accountChip.text = acc?.name ?: getString(R.string.no_account)
    }

    private fun clearContent() {
        content.removeAllViews()
        refreshAccountChip()
    }

    private fun showPlay() {
        clearContent()
        content.addView(title(getString(R.string.nav_play)))
        val installed = VersionManifest.installed()
        if (installed.isEmpty()) {
            content.addView(label(getString(R.string.need_version)))
        }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, installed)
        content.addView(spinner)
        val rendererLabel = label(getString(R.string.renderer))
        content.addView(rendererLabel)
        val rendererSpinner = Spinner(this)
        rendererSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            Renderers.list.map { it.name + " (" + getString(if (it.tag == "alpha") R.string.tag_alpha else R.string.tag_stable) + ")" }
        )
        rendererSpinner.setSelection(Renderers.list.indexOfFirst { it.id == Env.settings.rendererId }.coerceAtLeast(0))
        content.addView(rendererSpinner)
        val status = label("")
        val playBtn = Button(this)
        playBtn.text = getString(R.string.launch)
        playBtn.setBackgroundColor(0xFF3D8B4F.toInt())
        playBtn.setTextColor(0xFFFFFFFF.toInt())
        playBtn.setOnClickListener {
            if (Env.accounts.current() == null) {
                Toast.makeText(this, getString(R.string.need_account), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (installed.isEmpty()) {
                Toast.makeText(this, getString(R.string.need_version), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selected = spinner.selectedItem as? String ?: return@setOnClickListener
            Env.settings.rendererId = Renderers.list.getOrNull(rendererSpinner.selectedItemPosition)?.id ?: Env.settings.rendererId
            Env.settings.save()
            status.text = getString(R.string.launching)
            Thread {
                val result = Installer.install(selected, Loaders.Type.VANILLA, null, progressStatus(status))
                runOnUiThread {
                    status.text = result.message
                    if (result.success) {
                        startActivity(
                            Intent(this, GameActivity::class.java).putExtra("versionId", result.message.substringAfter("done "))
                        )
                    }
                }
            }.start()
        }
        content.addView(playBtn)
        content.addView(status)
    }

    private fun progressStatus(status: TextView): Installer.Progress {
        return object : Installer.Progress {
            override fun onStatus(text: String) {
                runOnUiThread { status.text = text }
            }

            override fun onDownload(done: Int, total: Int, tag: String) {
                runOnUiThread { status.text = "$done/$total $tag" }
            }
        }
    }

    private fun showVersions() {
        clearContent()
        content.addView(title(getString(R.string.available_versions)))
        val search = EditText(this)
        search.hint = getString(R.string.search_version)
        search.setTextColor(0xFFE8EDF2.toInt())
        content.addView(search)
        val typeSpinner = Spinner(this)
        val types = listOf(
            getString(R.string.loader_vanilla) to Loaders.Type.VANILLA,
            getString(R.string.loader_fabric) to Loaders.Type.FABRIC,
            getString(R.string.loader_forge) to Loaders.Type.FORGE,
            getString(R.string.loader_neoforge) to Loaders.Type.NEOFORGE,
            getString(R.string.loader_optifine) to Loaders.Type.OPTIFINE,
            getString(R.string.loader_quilt) to Loaders.Type.QUILT
        )
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types.map { it.first })
        content.addView(typeSpinner)
        val status = label(getString(R.string.refresh))
        content.addView(status)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(list)
        content.addView(
            scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(380)
            )
        )

        val render = Runnable {
            list.removeAllViews()
            val filter = search.text.toString().lowercase()
            val loaderType = types.getOrNull(typeSpinner.selectedItemPosition)?.second ?: Loaders.Type.VANILLA
            val versions = if (loaderType == Loaders.Type.VANILLA) {
                manifestCache.filter { it.id.lowercase().contains(filter) }
            } else {
                manifestCache.filter { it.id.lowercase().contains(filter) && it.type == "release" }
            }
            for (v in versions.take(200)) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(0, dp(6), 0, dp(6))
                val name = TextView(this)
                name.text = v.id
                name.setTextColor(0xFFE8EDF2.toInt())
                name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val btn = Button(this)
                btn.text = getString(R.string.install)
                btn.setOnClickListener { installVersion(v.id, loaderType, status) }
                row.addView(name)
                row.addView(btn)
                list.addView(row)
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render.run()
            }
        })
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) = render.run()
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        Thread {
            try {
                val arr = VersionManifest.list(true)
                val out = mutableListOf<VersionEntry>()
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    out.add(VersionEntry(e.optString("id"), e.optString("type")))
                }
                manifestCache = out.sortedByDescending { it.id }
                runOnUiThread { status.text = out.size.toString() + " versions"; render.run() }
            } catch (e: Exception) {
                runOnUiThread { status.text = getString(R.string.failed) + ": " + e.message }
            }
        }.start()
    }

    private fun installVersion(mc: String, type: Loaders.Type, status: TextView) {
        Thread {
            try {
                val loaderVersions = if (type == Loaders.Type.VANILLA) listOf(mc) else Loaders.loaderList(mc, type)
                runOnUiThread {
                    if (loaderVersions.isEmpty()) {
                        status.text = getString(R.string.failed) + ": " + type.key
                        return@runOnUiThread
                    }
                    val items = loaderVersions.take(40).toTypedArray()
                    android.app.AlertDialog.Builder(this)
                        .setTitle(type.key + " " + mc)
                        .setItems(items) { _, which ->
                            val lv = items[which]
                            status.text = getString(R.string.installing_loader) + " " + lv
                            Thread {
                                val res = Installer.install(mc, type, lv, progressStatus(status))
                                runOnUiThread { status.text = res.message }
                            }.start()
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = getString(R.string.failed) + ": " + e.message }
            }
        }.start()
    }

    private fun showAccounts() {
        clearContent()
        content.addView(title(getString(R.string.nav_accounts)))
        val input = EditText(this)
        input.hint = getString(R.string.username)
        input.setTextColor(0xFFE8EDF2.toInt())
        content.addView(input)
        val add = Button(this)
        add.text = getString(R.string.add_account)
        add.setOnClickListener {
            if (Env.accounts.add(input.text.toString())) {
                input.text.clear()
                showAccounts()
            }
        }
        content.addView(add)
        val accounts: Accounts = Env.accounts
        for (a in accounts.list) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(6), 0, dp(6))
            val name = TextView(this)
            name.text = a.name + (if (a.name == accounts.active) " [" + getString(R.string.active) + "]" else "")
            name.setTextColor(0xFFE8EDF2.toInt())
            name.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            val select = Button(this)
            select.text = getString(R.string.select)
            select.setOnClickListener {
                accounts.setActiveAccount(a.name)
                showAccounts()
            }
            val remove = Button(this)
            remove.text = getString(R.string.remove)
            remove.setOnClickListener {
                accounts.remove(a.name)
                showAccounts()
            }
            row.addView(name)
            row.addView(select)
            row.addView(remove)
            content.addView(row)
        }
        content.addView(label(getString(R.string.offline_account)))
    }

    private fun showSettings() {
        clearContent()
        content.addView(title(getString(R.string.nav_settings)))
        content.addView(label(getString(R.string.renderer)))
        val rendererSpinner = Spinner(this)
        rendererSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            Renderers.list.map { it.name + " (" + getString(if (it.tag == "alpha") R.string.tag_alpha else R.string.tag_stable) + ")" }
        )
        rendererSpinner.setSelection(Renderers.list.indexOfFirst { it.id == Env.settings.rendererId }.coerceAtLeast(0))
        content.addView(rendererSpinner)
        content.addView(label(getString(R.string.java_runtime)))
        content.addView(label("jre8, jre17, jre21, jre25: " + com.mclauncher.core.JavaRuntimes.available().joinToString(", ")))

        val memory = EditText(this)
        memory.hint = getString(R.string.memory)
        memory.setText(Env.settings.memoryMb.toString())
        memory.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        content.addView(memory)

        val jvmArgs = EditText(this)
        jvmArgs.hint = getString(R.string.jvm_args)
        jvmArgs.setText(Env.settings.jvmArgs)
        content.addView(jvmArgs)

        val token = EditText(this)
        token.hint = "GitHub token"
        token.setText(Env.settings.githubToken)
        content.addView(token)

        val customComponents = EditText(this)
        customComponents.hint = "components url"
        customComponents.setText(Env.settings.urlOverrides["components"] ?: "")
        content.addView(customComponents)

        val customArjx = EditText(this)
        customArjx.hint = "Arjx url"
        customArjx.setText(Env.settings.urlOverrides["renderer-arjx"] ?: "")
        content.addView(customArjx)

        val save = Button(this)
        save.text = getString(R.string.save)
        save.setOnClickListener {
            Env.settings.rendererId = Renderers.list.getOrNull(rendererSpinner.selectedItemPosition)?.id ?: "gl4es"
            Env.settings.memoryMb = memory.text.toString().toIntOrNull() ?: 1024
            Env.settings.jvmArgs = jvmArgs.text.toString()
            Env.settings.githubToken = token.text.toString()
            Env.settings.urlOverrides["components"] = customComponents.text.toString()
            Env.settings.urlOverrides["renderer-arjx"] = customArjx.text.toString()
            Env.settings.save()
            Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show()
        }
        content.addView(save)
    }

    private fun showLogs() {
        clearContent()
        content.addView(title(getString(R.string.nav_logs)))
        val logView = TextView(this)
        logView.setTextColor(0xFF8A94A3.toInt())
        logView.textSize = 11f
        logView.text = Logger.readLog().ifEmpty { getString(R.string.logs_empty) }
        val scroll = ScrollView(this)
        scroll.addView(logView)
        content.addView(
            scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        val refresh = Button(this)
        refresh.text = getString(R.string.refresh)
        refresh.setOnClickListener {
            logView.text = Logger.readLog().ifEmpty { getString(R.string.logs_empty) }
        }
        content.addView(refresh)
    }
}
