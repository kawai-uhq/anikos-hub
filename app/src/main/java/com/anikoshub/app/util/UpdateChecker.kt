package com.anikoshub.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/kawai-uhq/anikos-hub/releases/latest"

    /**
     * Returns the download URL of a newer APK if available, otherwise null.
     */
    suspend fun check(currentVersion: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            try {
                if (conn.responseCode !in 200..299) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)

                val latest = json.optString("tag_name")
                    .removePrefix("v")
                    .trim()

                if (latest.isBlank() || !isNewer(latest, currentVersion)) {
                    return@withContext null
                }

                val assets = json.optJSONArray("assets") ?: return@withContext null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").equals("Anikos-Hub.apk", ignoreCase = true)) {
                        return@withContext asset.optString("browser_download_url").ifBlank { null }
                    }
                }
                null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> {
            val nums = v.removePrefix("v")
                .split(".")
                .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            return listOf(
                nums.getOrElse(0) { 0 },
                nums.getOrElse(1) { 0 },
                nums.getOrElse(2) { 0 }
            )
        }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0..2) {
            if (a[i] != b[i]) return a[i] > b[i]
        }
        return false
    }
}
