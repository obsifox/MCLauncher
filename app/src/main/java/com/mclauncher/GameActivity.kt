package com.mclauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mclauncher.core.Bridge
import com.mclauncher.core.Components
import com.mclauncher.core.Env
import com.mclauncher.core.InputBridge
import com.mclauncher.core.Installer
import com.mclauncher.core.JavaRuntimes
import com.mclauncher.core.Keycodes
import com.mclauncher.core.Launcher
import com.mclauncher.core.Logger
import com.mclauncher.core.Renderers
import com.oracle.dalvik.VMLauncher
import net.kdt.pojavlaunch.utils.JREUtils

class GameActivity : Activity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var statusText: TextView
    private var kbdOverlay: View? = null
    private var running = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Env.ensure()
        Logger.init(Env.logsDir)
        val versionId = intent.getStringExtra("versionId") ?: ""

        val root = FrameLayout(this)
        surfaceView = SurfaceView(this)
        root.addView(
            surfaceView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        statusText = TextView(this)
        statusText.setTextColor(Color.WHITE)
        statusText.textSize = 14f
        statusText.gravity = Gravity.CENTER
        root.addView(
            statusText, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.END
        val kbdBtn = Button(this)
        kbdBtn.text = getString(R.string.keyboard)
        val exitBtn = Button(this)
        exitBtn.text = getString(R.string.exit_game)
        bar.addView(kbdBtn)
        bar.addView(exitBtn)
        root.addView(
            bar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        setContentView(root)

        kbdBtn.setOnClickListener { toggleKeyboard() }
        exitBtn.setOnClickListener { finish() }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (!running) {
                    running = true
                    startGame(versionId, holder.surface)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                InputBridge.windowSize(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }
        })

        surfaceView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                InputBridge.cursor(event.x, event.y)
                InputBridge.mouseButton(0, true)
            }
            MotionEvent.ACTION_UP -> {
                InputBridge.cursor(event.x, event.y)
                InputBridge.mouseButton(0, false)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val dy = event.getY(0) - event.getHistoricalY(0, 0)
                    if (kotlin.math.abs(dy) > 8f) InputBridge.scroll(0.0, (dy / 40.0).toDouble())
                } else {
                    InputBridge.cursor(event.x, event.y)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) InputBridge.mouseButton(1, true)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) InputBridge.mouseButton(1, false)
            }
        }
    }

    private fun startGame(versionId: String, surface: Any) {
        statusText.text = getString(R.string.launching)
        Thread {
            try {
                if (!Components.ensure(progress())) throw IllegalStateException("runtime components missing")
                if (!Bridge.loadNativeLibs()) throw IllegalStateException("native bridge load failed")
                val config = Launcher.prepare(versionId)
                if (JavaRuntimes.home(config.runtimeId) == null) {
                    throw IllegalStateException("java runtime " + config.runtimeId + " not installed")
                }
                if (!Renderers.ensure(Renderers.byId(Env.settings.rendererId), progress())) {
                    Logger.log("GameActivity", "renderer not ready, continuing with configuration")
                }
                Launcher.applyEnvironment(config)
                JREUtils.setupExitMethod(applicationContext)
                JREUtils.initializeHooks()
                JREUtils.chdir(config.gameDir.absolutePath)
                JREUtils.setupBridgeWindow(surface)
                InputBridge.inputReady(false)
                val args = mutableListOf("java")
                args.addAll(config.jvmArgs)
                args.add(config.mainClass)
                args.addAll(config.gameArgs)
                Logger.log("GameActivity", "launching " + config.info.id + " with " + args.size + " args")
                val code = VMLauncher.launchJVM(args.toTypedArray())
                Logger.log("GameActivity", "game exit code: $code")
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.failed) + " (" + code + ")", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Throwable) {
                Logger.log("GameActivity", "launch failed: " + e.message)
                runOnUiThread {
                    statusText.text = getString(R.string.failed) + ": " + e.message
                    Toast.makeText(this, e.message ?: "error", Toast.LENGTH_LONG).show()
                }
                running = false
            }
        }.start()
    }

    private fun progress(): Installer.Progress {
        return object : Installer.Progress {
            override fun onStatus(text: String) {
                runOnUiThread { statusText.text = text }
            }

            override fun onDownload(done: Int, total: Int, tag: String) {
                runOnUiThread { statusText.text = "$done/$total $tag" }
            }
        }
    }

    private fun progress(f: (String) -> Unit): Installer.Progress {
        return object : Installer.Progress {
            override fun onStatus(text: String) = f(text)
            override fun onDownload(done: Int, total: Int, tag: String) = f("$done/$total $tag")
        }
    }

    private fun toggleKeyboard() {
        if (kbdOverlay != null) {
            hideKeyboardOverlay()
            return
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.parseColor("#E6161C24"))
        box.setPadding(16, 12, 16, 12)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val field = EditText(this)
        field.hint = getString(R.string.keyboard)
        field.setTextColor(Color.WHITE)
        field.setSingleLine(true)
        field.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val hide = Button(this)
        hide.text = getString(R.string.hide_kbd)
        row.addView(field)
        row.addView(hide)
        box.addView(row)

        field.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val glfw = Keycodes.fromAndroid(keyCode)
                if (glfw != 0) {
                    InputBridge.key(glfw, event.displayLabel, 0, 0, true)
                    InputBridge.key(glfw, event.displayLabel, 0, 0, false)
                    return@setOnKeyListener true
                }
            }
            false
        }

        field.addTextChangedListener(object : android.text.TextWatcher {
            private var lastLen = 0
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                lastLen = s?.length ?: 0
            }

            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
            }

            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s ?: return
                if (text.length < lastLen) {
                    InputBridge.key(0x08, '\b', 0, 0, true)
                    InputBridge.key(0x08, '\b', 0, 0, false)
                } else {
                    for (i in lastLen until text.length) {
                        InputBridge.char(text[i], 0)
                    }
                }
            }
        })

        hide.setOnClickListener {
            hideKeyboardOverlay()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM
        try {
            wm.addView(box, params)
            kbdOverlay = box
            field.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {
            Logger.log("GameActivity", "keyboard overlay failed: " + e.message)
        }
    }

    private fun hideKeyboardOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        kbdOverlay?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        kbdOverlay = null
    }

    override fun onDestroy() {
        hideKeyboardOverlay()
        super.onDestroy()
    }
}
