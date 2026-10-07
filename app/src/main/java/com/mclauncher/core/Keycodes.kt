package com.mclauncher.core

object Keycodes {

    fun fromAndroid(androidKeycode: Int): Int {
        return when (androidKeycode) {
            android.view.KeyEvent.KEYCODE_0 -> 0x30
            android.view.KeyEvent.KEYCODE_1 -> 0x31
            android.view.KeyEvent.KEYCODE_2 -> 0x32
            android.view.KeyEvent.KEYCODE_3 -> 0x33
            android.view.KeyEvent.KEYCODE_4 -> 0x34
            android.view.KeyEvent.KEYCODE_5 -> 0x35
            android.view.KeyEvent.KEYCODE_6 -> 0x36
            android.view.KeyEvent.KEYCODE_7 -> 0x37
            android.view.KeyEvent.KEYCODE_8 -> 0x38
            android.view.KeyEvent.KEYCODE_9 -> 0x39
            android.view.KeyEvent.KEYCODE_A -> 0x41
            android.view.KeyEvent.KEYCODE_B -> 0x42
            android.view.KeyEvent.KEYCODE_C -> 0x43
            android.view.KeyEvent.KEYCODE_D -> 0x44
            android.view.KeyEvent.KEYCODE_E -> 0x45
            android.view.KeyEvent.KEYCODE_F -> 0x46
            android.view.KeyEvent.KEYCODE_G -> 0x47
            android.view.KeyEvent.KEYCODE_H -> 0x48
            android.view.KeyEvent.KEYCODE_I -> 0x49
            android.view.KeyEvent.KEYCODE_J -> 0x4A
            android.view.KeyEvent.KEYCODE_K -> 0x4B
            android.view.KeyEvent.KEYCODE_L -> 0x4C
            android.view.KeyEvent.KEYCODE_M -> 0x4D
            android.view.KeyEvent.KEYCODE_N -> 0x4E
            android.view.KeyEvent.KEYCODE_O -> 0x4F
            android.view.KeyEvent.KEYCODE_P -> 0x50
            android.view.KeyEvent.KEYCODE_Q -> 0x51
            android.view.KeyEvent.KEYCODE_R -> 0x52
            android.view.KeyEvent.KEYCODE_S -> 0x53
            android.view.KeyEvent.KEYCODE_T -> 0x54
            android.view.KeyEvent.KEYCODE_U -> 0x55
            android.view.KeyEvent.KEYCODE_V -> 0x56
            android.view.KeyEvent.KEYCODE_W -> 0x57
            android.view.KeyEvent.KEYCODE_X -> 0x58
            android.view.KeyEvent.KEYCODE_Y -> 0x59
            android.view.KeyEvent.KEYCODE_Z -> 0x5A
            android.view.KeyEvent.KEYCODE_SPACE -> 0x20
            android.view.KeyEvent.KEYCODE_ENTER -> 0x10D
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x11C
            android.view.KeyEvent.KEYCODE_TAB -> 0x09
            android.view.KeyEvent.KEYCODE_ESCAPE -> 0x100
            android.view.KeyEvent.KEYCODE_BACK -> 0x100
            android.view.KeyEvent.KEYCODE_DEL -> 0x08
            android.view.KeyEvent.KEYCODE_FORWARD_DEL -> 0x0FF
            android.view.KeyEvent.KEYCODE_DPAD_UP -> 0x101
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 0x102
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 0x104
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 0x103
            android.view.KeyEvent.KEYCODE_PAGE_UP -> 0x105
            android.view.KeyEvent.KEYCODE_PAGE_DOWN -> 0x106
            android.view.KeyEvent.KEYCODE_MOVE_HOME -> 0x107
            android.view.KeyEvent.KEYCODE_MOVE_END -> 0x108
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT, android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x110
            android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> 0x111
            android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_ALT_RIGHT -> 0x112
            android.view.KeyEvent.KEYCODE_CAPS_LOCK -> 0x118
            android.view.KeyEvent.KEYCODE_F1 -> 0x122
            android.view.KeyEvent.KEYCODE_F2 -> 0x123
            android.view.KeyEvent.KEYCODE_F3 -> 0x124
            android.view.KeyEvent.KEYCODE_F4 -> 0x125
            android.view.KeyEvent.KEYCODE_F5 -> 0x126
            android.view.KeyEvent.KEYCODE_F6 -> 0x127
            android.view.KeyEvent.KEYCODE_F7 -> 0x128
            android.view.KeyEvent.KEYCODE_F8 -> 0x129
            android.view.KeyEvent.KEYCODE_F9 -> 0x12A
            android.view.KeyEvent.KEYCODE_F10 -> 0x12B
            android.view.KeyEvent.KEYCODE_F11 -> 0x12C
            android.view.KeyEvent.KEYCODE_F12 -> 0x12D
            android.view.KeyEvent.KEYCODE_MINUS -> 0x2D
            android.view.KeyEvent.KEYCODE_EQUALS -> 0x2E
            android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> 0x2F
            android.view.KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x30
            android.view.KeyEvent.KEYCODE_BACKSLASH -> 0x31
            android.view.KeyEvent.KEYCODE_SEMICOLON -> 0x33
            android.view.KeyEvent.KEYCODE_APOSTROPHE -> 0x34
            android.view.KeyEvent.KEYCODE_COMMA -> 0x2C
            android.view.KeyEvent.KEYCODE_PERIOD -> 0x35
            android.view.KeyEvent.KEYCODE_SLASH -> 0x36
            android.view.KeyEvent.KEYCODE_GRAVE -> 0x37
            android.view.KeyEvent.KEYCODE_INSERT -> 0x109
            android.view.KeyEvent.KEYCODE_NUM_LOCK -> 0x11C
            else -> 0
        }
    }

    fun mods(shift: Boolean, ctrl: Boolean, alt: Boolean): Int {
        var m = 0
        if (shift) m = m or 1
        if (ctrl) m = m or 2
        if (alt) m = m or 4
        return m
    }
}
