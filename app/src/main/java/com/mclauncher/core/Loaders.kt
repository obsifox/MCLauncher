package com.mclauncher.core

import org.json.JSONArray
import org.json.JSONObject

object Loaders {

    enum class Type(val key: String) {
        VANILLA("vanilla"),
        FABRIC("fabric"),
        FORGE("forge"),
        NEOFORGE("neoforge"),
        OPTIFINE("optifine"),
        QUILT("quilt");

        companion object {
            fun from(key: String): Type = entries.firstOrNull { it.key == key } ?: VANILLA
        }
    }

    fun latestLoaderVersion(mc: String, type: Type): String? = loaderList(mc, type).firstOrNull()

    fun loaderList(mc: String, type: Type): List<String> {
        return try {
            when (type) {
                Type.VANILLA -> listOf(mc)
                Type.FABRIC -> {
                    val arr = JSONArray(Downloader.fetchText("https://meta.fabricmc.net/v2/versions/loader/$mc"))
                    (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optJSONObject("loader")?.optString("version") }
                }
                Type.QUILT -> {
                    val arr = JSONArray(Downloader.fetchText("https://meta.quiltmc.org/v3/versions/loader/$mc"))
                    (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optJSONObject("loader")?.optString("version") }
                }
                Type.FORGE -> {
                    val o = JSONObject(Downloader.fetchText("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"))
                    val promos = o.optJSONObject("promos") ?: return emptyList()
                    val out = mutableListOf<String>()
                    promos.keys().forEach { k ->
                        val base = k.substringBefore("-")
                        if (base == mc) out.add(k.substringAfter("-"))
                    }
                    out
                }
                Type.NEOFORGE -> {
                    val o = JSONObject(Downloader.fetchText("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"))
                    val arr = o.optJSONArray("versions") ?: return emptyList()
                    (0 until arr.length()).map { arr.getString(it) }.filter { it.startsWith(mcMinorPrefix(mc)) }
                }
                Type.OPTIFINE -> {
                    val arr = JSONArray(Downloader.fetchText("https://bmclapi2.bangbang93.com/optifine/$mc"))
                    (0 until arr.length()).mapNotNull {
                        val e = arr.getJSONObject(it)
                        e.optString("type") + "_" + e.optString("patch")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log("Loaders", "loaderList $type $mc failed: " + e.message)
            emptyList()
        }
    }

    private fun mcMinorPrefix(mc: String): String {
        val parts = mc.split(".")
        return if (parts.size >= 2) parts[0] + "." + parts[1] else mc
    }

    fun fabricProfile(mc: String, loaderVer: String): JSONObject =
        JSONObject(Downloader.fetchText("https://meta.fabricmc.net/v2/versions/loader/$mc/$loaderVer/profile/json"))

    fun quiltProfile(mc: String, loaderVer: String): JSONObject =
        JSONObject(Downloader.fetchText("https://meta.quiltmc.org/v3/versions/loader/$mc/$loaderVer/profile/json"))

    fun forgeInstallerUrl(mc: String, forgeVer: String): String {
        val full = "$mc-$forgeVer"
        return "https://maven.minecraftforge.net/net/minecraftforge/forge/$full/forge-$full-installer.jar"
    }

    fun neoforgeInstallerUrl(neoVer: String): String =
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoVer/neoforge-$neoVer-installer.jar"

    fun optifineFileName(mc: String, tier: String): String {
        val type = tier.substringBefore("_")
        val patch = tier.substringAfter("_", "")
        return "OptiFine_${mc}_$type" + (if (patch.isNotEmpty()) "_$patch" else "")
    }

    fun buildOptiFineJson(mc: String, tier: String, baseId: String): JSONObject {
        val id = "OptiFine_" + optifineFileName(mc, tier).removePrefix("OptiFine_")
        val o = JSONObject()
        o.put("id", id)
        o.put("inheritsFrom", baseId)
        o.put("releaseTime", "2024-01-01T00:00:00+00:00")
        o.put("time", "2024-01-01T00:00:00+00:00")
        o.put("type", "release")
        o.put("mainClass", "")
        val libs = JSONArray()
        val lib = JSONObject()
        lib.put("name", "optifine:OptiFine:" + optifineFileName(mc, tier))
        libs.put(lib)
        o.put("libraries", libs)
        o.put("mclauncherLocalJar", "optifine.jar")
        o.put("mclauncherTweaker", "optifine.OptiFineTweaker")
        return o
    }
}
