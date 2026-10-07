package com.mclauncher.core

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(dir: File) {
        dir.mkdirs()
        file = File(dir, "latest.txt")
    }

    @Synchronized
    fun log(tag: String, msg: String) {
        val line = fmt.format(Date()) + " [" + tag + "] " + msg
        Log.i("mclauncher", "$tag: $msg")
        try {
            file?.appendText(line + "\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun readLog(limit: Int = 400): String {
        val f = file ?: return ""
        if (!f.isFile) return ""
        return try {
            val lines = f.readText().split("\n")
            if (lines.size <= limit) f.readText() else lines.takeLast(limit).joinToString("\n")
        } catch (_: Exception) {
            ""
        }
    }
}
