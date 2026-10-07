package com.mclauncher.input

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.KeyEvent
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import com.mclauncher.core.InputBridge
import com.mclauncher.core.Keycodes

class GameIME : InputMethodService() {

    private var persian = false
    private var shift = false

    private val enRows = listOf(
        "1234567890",
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )
    private val faRows = listOf(
        "۱۲۳۴۵۶۷۸۹۰",
        "ضصثقفغعهخحج",
        "شسیبلاتنمکگ",
        "ظطزرذدپوچ"
    )

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xF0161C24.toInt())
        root.setPadding(8, 8, 8, 8)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        val lang = keyButton(if (persian) "FA" else "EN") {
            persian = !persian
            onCreateInputView()?.let { setInputView(it) }
        }
        val shiftBtn = keyButton(if (shift) "SHIFT*" else "shift") {
            shift = !shift
            onCreateInputView()?.let { setInputView(it) }
        }
        val space = keyButton(" ") {
            commitChar(' ')
            sendKey(KeyEvent.KEYCODE_SPACE, ' ')
        }
        val back = keyButton("DEL") {
            sendKey(KeyEvent.KEYCODE_DEL, '\b')
        }
        val enter = keyButton("ENTER") {
            sendKey(KeyEvent.KEYCODE_ENTER, '\n')
        }
        val hide = keyButton("v") {
            requestHideSelf(0)
        }
        for (b in listOf(lang, shiftBtn, space, back, enter, hide)) {
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(4, 4, 4, 4)
            }
            top.addView(b)
        }
        root.addView(top)

        val rows = if (persian) faRows else enRows
        for (row in rows) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (c in row) {
                val b = keyButton(if (shift) c.uppercaseChar().toString() else c.toString()) {
                    val ch = if (shift && !persian) c.uppercaseChar() else c
                    commitChar(ch)
                    val androidCode = androidCodeFor(ch)
                    sendKey(androidCode, ch)
                    if (shift) {
                        shift = false
                        onCreateInputView()?.let { setInputView(it) }
                    }
                }
                b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.setMargins(3, 3, 3, 3)
                }
                line.addView(b)
            }
            root.addView(line)
        }
        return root
    }

    private fun androidCodeFor(c: Char): Int {
        return when {
            c.isLetter() && c.lowercaseChar() in 'a'..'z' ->
                KeyEvent.KEYCODE_A + (c.lowercaseChar() - 'a')
            c in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            c == ' ' -> KeyEvent.KEYCODE_SPACE
            else -> 0
        }
    }

    private fun commitChar(c: Char) {
        InputBridge.char(c, 0)
    }

    private fun sendKey(androidCode: Int, c: Char) {
        val glfw = Keycodes.fromAndroid(androidCode)
        if (glfw != 0) {
            InputBridge.key(glfw, c, 0, 0, true)
            InputBridge.key(glfw, c, 0, 0, false)
        }
    }

    private fun keyButton(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 14f
        b.setBackgroundColor(0xFF1E2630.toInt())
        b.setTextColor(0xFFE8EDF2.toInt())
        b.setPadding(4, 8, 4, 8)
        b.minimumHeight = 120
        b.setOnClickListener { onClick() }
        return b
    }
}
