package com.mclauncher.core

import java.lang.reflect.Method

object InputBridge {

    private const val EVENT_TYPE_CHAR = 1000
    private const val EVENT_TYPE_CHAR_MODS = 1001
    private const val EVENT_TYPE_CURSOR_POS = 1003
    private const val EVENT_TYPE_KEY = 1005
    private const val EVENT_TYPE_MOUSE_BUTTON = 1006
    private const val EVENT_TYPE_SCROLL = 1007
    private const val EVENT_TYPE_WINDOW_SIZE = 1008

    private var methods: MutableMap<String, Method> = mutableMapOf()
    private var initialized = false

    @Synchronized
    private fun init(): Boolean {
        if (initialized) return methods.isNotEmpty()
        initialized = true
        val cls = Bridge.callbackBridge() ?: return false
        for (m in cls.methods) {
            methods[m.name] = m
        }
        Logger.log("InputBridge", "api methods: " + methods.keys.filter { it.startsWith("send") || it.startsWith("nativeSend") }.sorted())
        return true
    }

    private fun sendData(type: Int, data: String) {
        try {
            methods["sendData"]?.invoke(null, type, data)
        } catch (e: Throwable) {
            Logger.log("InputBridge", "sendData failed: " + e.message)
        }
    }

    fun key(glfwKeycode: Int, keyChar: Char, scancode: Int, mods: Int, isDown: Boolean) {
        if (!init()) return
        val state = if (isDown) 1 else 0
        val sk = methods["sendKeycode"]
        if (sk != null) {
            try {
                sk.invoke(null, glfwKeycode, keyChar, scancode, mods, isDown)
                return
            } catch (_: Throwable) {
            }
        }
        try {
            methods["sendKeyPress"]?.let { m ->
                val ps = m.parameterTypes
                when (ps.size) {
                    5 -> m.invoke(null, glfwKeycode, keyChar, scancode, mods, isDown)
                    4 -> m.invoke(null, glfwKeycode, scancode, mods, isDown)
                    3 -> m.invoke(null, glfwKeycode, mods, isDown)
                    1 -> m.invoke(null, glfwKeycode)
                }
                return
            }
        } catch (_: Throwable) {
        }
        try {
            methods["nativeSendKey"]?.invoke(null, glfwKeycode, scancode, state, mods)
        } catch (_: Throwable) {
            sendData(EVENT_TYPE_KEY, "$glfwKeycode,$scancode,$state,$mods")
        }
        if (keyChar != '\u0000' && isDown) char(keyChar, mods)
    }

    fun char(c: Char, mods: Int = 0) {
        if (!init()) return
        val code = c.toInt()
        try {
            methods["sendChar"]?.let { m ->
                val ps = m.parameterTypes
                if (ps.size == 2) m.invoke(null, c, mods) else m.invoke(null, c)
                return
            }
        } catch (_: Throwable) {
        }
        try {
            methods["nativeSendChar"]?.invoke(null, c)
            return
        } catch (_: Throwable) {
        }
        sendData(EVENT_TYPE_CHAR, code.toString())
    }

    fun cursor(x: Float, y: Float) {
        if (!init()) return
        try {
            methods["sendCursorPos"]?.invoke(null, x, y)
            return
        } catch (_: Throwable) {
        }
        try {
            methods["nativeSendCursorPos"]?.invoke(null, x, y)
            return
        } catch (_: Throwable) {
        }
        sendData(EVENT_TYPE_CURSOR_POS, x.toInt().toString() + "," + y.toInt())
    }

    fun mouseButton(button: Int, isDown: Boolean) {
        if (!init()) return
        val state = if (isDown) 1 else 0
        try {
            methods["sendMouseButton"]?.invoke(null, button, isDown)
            return
        } catch (_: Throwable) {
        }
        try {
            methods["nativeSendMouseButton"]?.invoke(null, button, state, 0)
            return
        } catch (_: Throwable) {
        }
        sendData(EVENT_TYPE_MOUSE_BUTTON, "$button,$state,0")
    }

    fun scroll(x: Double, y: Double) {
        if (!init()) return
        try {
            methods["sendScroll"]?.invoke(null, x, y)
            return
        } catch (_: Throwable) {
        }
        try {
            methods["nativeSendScroll"]?.invoke(null, x, y)
            return
        } catch (_: Throwable) {
        }
        sendData(EVENT_TYPE_SCROLL, x.toInt().toString() + "," + y.toInt())
    }

    fun windowSize(w: Int, h: Int) {
        if (!init()) return
        try {
            methods["sendUpdateWindowSize"]?.invoke(null, w, h)
            return
        } catch (_: Throwable) {
        }
        try {
            methods["nativeSendScreenSize"]?.invoke(null, w, h)
            return
        } catch (_: Throwable) {
        }
        sendData(EVENT_TYPE_WINDOW_SIZE, "$w,$h")
    }

    fun grabbing(grab: Boolean) {
        if (!init()) return
        try {
            methods["nativeSetGrabbing"]?.invoke(null, grab)
        } catch (_: Throwable) {
        }
    }

    fun inputReady(ready: Boolean) {
        if (!init()) return
        try {
            methods["nativeSetInputReady"]?.invoke(null, ready)
        } catch (_: Throwable) {
        }
    }
}
