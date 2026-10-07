package com.mclauncher.core

import org.json.JSONObject
import java.io.File

class Settings private constructor(private val file: File) {
    var rendererId: String = "gl4es"
    var memoryMb: Int = 1024
    var jvmArgs: String = ""
    var githubToken: String = ""
    var libglEs: String = ""
    val javaOverrides: MutableMap<String, String> = mutableMapOf()
    val urlOverrides: MutableMap<String, String> = mutableMapOf()

    fun urlFor(key: String, fallback: String): String {
        return urlOverrides[key]?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun save() {
        val o = JSONObject()
        o.put("rendererId", rendererId)
        o.put("memoryMb", memoryMb)
        o.put("jvmArgs", jvmArgs)
        o.put("githubToken", githubToken)
        o.put("libglEs", libglEs)
        val jo = JSONObject()
        javaOverrides.forEach { (k, v) -> jo.put(k, v) }
        o.put("javaOverrides", jo)
        val uo = JSONObject()
        urlOverrides.forEach { (k, v) -> uo.put(k, v) }
        o.put("urlOverrides", uo)
        file.parentFile?.mkdirs()
        file.writeText(o.toString(2))
    }

    companion object {
        fun load(file: File): Settings {
            val s = Settings(file)
            if (file.isFile) runCatching {
                val o = JSONObject(file.readText())
                s.rendererId = o.optString("rendererId", "gl4es")
                s.memoryMb = o.optInt("memoryMb", 1024)
                s.jvmArgs = o.optString("jvmArgs", "")
                s.githubToken = o.optString("githubToken", "")
                s.libglEs = o.optString("libglEs", "")
                o.optJSONObject("javaOverrides")?.let { jo ->
                    jo.keys().forEach { k -> s.javaOverrides[k] = jo.optString(k) }
                }
                o.optJSONObject("urlOverrides")?.let { uo ->
                    uo.keys().forEach { k -> s.urlOverrides[k] = uo.optString(k) }
                }
            }
            return s
        }
    }
}
