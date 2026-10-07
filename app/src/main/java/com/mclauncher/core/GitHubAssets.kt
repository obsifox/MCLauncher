package com.mclauncher.core

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object GitHubAssets {

    private fun tokenHeader(): String? = Env.settings.githubToken.takeIf { it.isNotBlank() }

    fun findAsset(repo: String, pattern: Regex): String? {
        return try {
            val arr = JSONArray(fetchReleases("https://api.github.com/repos/$repo/releases?per_page=30"))
            for (r in 0 until arr.length()) {
                val rel = arr.getJSONObject(r)
                val assets = rel.optJSONArray("assets") ?: continue
                for (a in 0 until assets.length()) {
                    val asset = assets.getJSONObject(a)
                    val name = asset.optString("name")
                    if (pattern.containsMatchIn(name)) return asset.optString("browser_download_url")
                }
            }
            null
        } catch (e: Exception) {
            Logger.log("GitHubAssets", "findAsset $repo failed: " + e.message)
            null
        }
    }

    fun latestReleaseAsset(repo: String, pattern: Regex): String? {
        return try {
            val o = org.json.JSONObject(fetchReleases("https://api.github.com/repos/$repo/releases/latest"))
            val assets = o.optJSONArray("assets") ?: return null
            for (a in 0 until assets.length()) {
                val asset = assets.getJSONObject(a)
                if (pattern.containsMatchIn(asset.optString("name"))) return asset.optString("browser_download_url")
            }
            null
        } catch (e: Exception) {
            Logger.log("GitHubAssets", "latestReleaseAsset $repo failed: " + e.message)
            null
        }
    }

    private fun fetchReleases(url: String): String {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 40000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "mclauncher/1.0.0")
            tokenHeader()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.connect()
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn?.disconnect()
        }
    }
}
