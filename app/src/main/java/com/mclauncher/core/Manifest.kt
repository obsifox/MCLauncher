package com.mclauncher.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LibArtifact(val path: String, val url: String, val sha1: String?, val size: Long)
data class LibDownload(val name: String, val artifact: LibArtifact?, val rules: JSONArray?, val url: String?)
data class AssetEntry(val hash: String, val size: Long)

class VersionInfo private constructor() {
    var id: String = ""
    var mainClass: String = ""
    var inheritsFrom: String = ""
    var minecraftArguments: String = ""
    var gameArgs: JSONArray? = null
    var jvmArgs: JSONArray? = null
    var libraries: MutableList<LibDownload> = mutableListOf()
    var assetIndexId: String = ""
    var assetIndexUrl: String = ""
    var assetIndexSha1: String? = null
    var assets: String = ""
    var javaMajor: Int = 8
    var clientUrl: String = ""
    var clientSha1: String? = null
    var clientSize: Long = 0
    var type: String = "release"
    var jarPath: String = ""

    companion object {
        fun parse(file: File): VersionInfo = parse(JSONObject(file.readText()))

        fun parse(o: JSONObject): VersionInfo {
            val v = VersionInfo()
            v.id = o.optString("id")
            v.mainClass = o.optString("mainClass")
            v.inheritsFrom = o.optString("inheritsFrom", "")
            v.minecraftArguments = o.optString("minecraftArguments", "")
            v.type = o.optString("type", "release")
            v.assets = o.optString("assets", "legacy")
            o.optJSONObject("arguments")?.let { args ->
                v.gameArgs = args.optJSONArray("game")
                v.jvmArgs = args.optJSONArray("jvm")
            }
            o.optJSONArray("libraries")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val lib = arr.getJSONObject(i)
                    val dl = lib.optJSONObject("downloads")
                    val artifact = dl?.optJSONObject("artifact")?.let { a ->
                        LibArtifact(
                            a.optString("path"),
                            a.optString("url"),
                            a.optString("sha1", "").takeIf { it.isNotEmpty() },
                            a.optLong("size", 0)
                        )
                    }
                    v.libraries.add(
                        LibDownload(
                            lib.optString("name"),
                            artifact,
                            lib.optJSONArray("rules"),
                            lib.optString("url", "").takeIf { it?.isNotEmpty() == true })
                    )
                }
            }
            o.optJSONObject("assetIndex")?.let { ai ->
                v.assetIndexId = ai.optString("id")
                v.assetIndexUrl = ai.optString("url")
                v.assetIndexSha1 = ai.optString("sha1", "").takeIf { it.isNotEmpty() }
            }
            o.optJSONObject("javaVersion")?.let { jv -> v.javaMajor = jv.optInt("majorVersion", 8) }
            o.optJSONObject("downloads")?.optJSONObject("client")?.let { c ->
                v.clientUrl = c.optString("url")
                v.clientSha1 = c.optString("sha1", "").takeIf { it.isNotEmpty() }
                v.clientSize = c.optLong("size", 0)
            }
            v.jarPath = o.optString("jar", "")
            return v
        }
    }

    fun merged(parent: VersionInfo?): VersionInfo {
        if (parent == null) return this
        val m = VersionInfo()
        m.id = id
        m.mainClass = mainClass.ifEmpty { parent.mainClass }
        m.minecraftArguments = minecraftArguments.ifEmpty { parent.minecraftArguments }
        m.gameArgs = gameArgs ?: parent.gameArgs
        m.jvmArgs = jvmArgs ?: parent.jvmArgs
        m.libraries = parent.libraries.toMutableList()
        m.libraries.addAll(libraries)
        m.assetIndexId = assetIndexId.ifEmpty { parent.assetIndexId }
        m.assetIndexUrl = assetIndexUrl.ifEmpty { parent.assetIndexUrl }
        m.assetIndexSha1 = assetIndexSha1 ?: parent.assetIndexSha1
        m.assets = assets.ifEmpty { parent.assets }
        m.javaMajor = if (javaMajor != 8) javaMajor else parent.javaMajor
        m.clientUrl = clientUrl.ifEmpty { parent.clientUrl }
        m.clientSha1 = clientSha1 ?: parent.clientSha1
        m.clientSize = if (clientSize != 0L) clientSize else parent.clientSize
        m.type = type
        m.jarPath = jarPath.ifEmpty { parent.jarPath }
        return m
    }
}

object RuleParser {
    fun allowed(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var result = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val os = rule.optJSONObject("os")
            var matches = true
            if (os != null) {
                val osName = os.optString("name", "")
                if (osName.isNotEmpty() && osName != "linux") matches = false
                val arch = os.optString("arch", "")
                if (arch.isNotEmpty()) {
                    val is64 = Cpu.is64
                    if (arch == "x86" && is64) matches = false
                    if (arch == "x86_64" && !is64) matches = false
                }
            }
            val hasFeatures = rule.has("features")
            if (hasFeatures) matches = false
            if (matches) {
                result = rule.optString("action") == "allow"
            }
        }
        return result
    }
}

object Cpu {
    val is64: Boolean by lazy {
        System.getProperty("os.arch")?.contains("64") == true ||
            BuildHelper.abi.contains("64")
    }
}

object BuildHelper {
    val abi: String by lazy {
        android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    }
}

object VersionManifest {
    const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    fun fetch(): JSONObject {
        val text = Downloader.fetchText(MANIFEST_URL)
        return JSONObject(text)
    }

    fun list(force: Boolean = false): JSONArray {
        if (force || !Env.manifestFile.isFile) {
            Env.cacheDir.mkdirs()
            Downloader.download(MANIFEST_URL, Env.manifestFile)
        }
        return JSONObject(Env.manifestFile.readText()).optJSONArray("versions") ?: JSONArray()
    }

    fun urlFor(id: String): String? {
        val arr = list(false)
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("id") == id) return e.optString("url")
        }
        return null
    }

    fun installed(): List<String> {
        val out = mutableListOf<String>()
        Env.versionsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val json = File(dir, dir.name + ".json")
                if (json.isFile) out.add(dir.name)
            }
        }
        return out.sorted()
    }
}
