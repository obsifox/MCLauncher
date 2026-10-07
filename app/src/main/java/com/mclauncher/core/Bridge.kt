package com.mclauncher.core

import java.io.File
import java.net.URLClassLoader

object Bridge {

    private var callbackBridgeClass: Class<*>? = null

    @Synchronized
    fun loadNativeLibs(): Boolean {
        return try {
            val libDir = File(Env.componentsDir, "lib")
            val order = listOf("libbytehook.so", "liblinkerhook.so", "libexithook.so", "libpojavexec.so", "libpojavexec_awt.so")
            for (name in order) {
                val f = File(libDir, name)
                if (f.isFile) {
                    System.load(f.absolutePath)
                    Logger.log("Bridge", "loaded " + name)
                }
            }
            true
        } catch (e: Throwable) {
            Logger.log("Bridge", "load failed: " + e.message)
            false
        }
    }

    @Synchronized
    fun callbackBridge(): Class<*>? {
        if (callbackBridgeClass != null) return callbackBridgeClass
        return try {
            val jar = File(Env.componentsDir, "components/lwjgl3/lwjgl-glfw-classes.jar")
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), Bridge::class.java.classLoader)
            callbackBridgeClass = Class.forName("org.lwjgl.glfw.CallbackBridge", true, loader)
            callbackBridgeClass
        } catch (e: Throwable) {
            Logger.log("Bridge", "callback class load failed: " + e.message)
            null
        }
    }
}
