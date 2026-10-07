package com.mclauncher.core

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DownloadTask(val url: String, val target: File, val sha1: String?, val tag: String)

object Downloader {

    interface Progress {
        fun onDownload(done: Int, total: Int, tag: String)
        fun onStatus(text: String) {}
    }

    fun ensure(task: DownloadTask, retries: Int = 3): Boolean {
        val f = task.target
        if (Hashing.ok(f, task.sha1)) return true
        f.parentFile?.mkdirs()
        if (f.isFile) f.delete()
        var lastErr: Exception? = null
        for (i in 0 until retries) {
            try {
                download(task.url, f)
                if (Hashing.ok(f, task.sha1)) return true
                f.delete()
                lastErr = IllegalStateException("checksum mismatch " + task.url)
                Logger.log("Downloader", "checksum mismatch " + task.tag + " redownloading")
            } catch (e: Exception) {
                lastErr = e
                Logger.log("Downloader", "retry " + (i + 1) + " " + task.tag + " " + e.message)
                f.delete()
                Thread.sleep(1200L * (i + 1))
            }
        }
        Logger.log("Downloader", "failed " + task.tag + " " + task.url + " " + (lastErr?.message ?: ""))
        return false
    }

    fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "mclauncher/1.0.0")
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP " + code + " " + url)
            conn.inputStream.use { ins ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var n = ins.read(buf)
                    while (n > 0) {
                        out.write(buf, 0, n)
                        n = ins.read(buf)
                    }
                }
            }
        } finally {
            conn?.disconnect()
        }
        if (target.isFile) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun ensureAll(tasks: List<DownloadTask>, progress: Progress): List<String> {
        var done = 0
        val total = tasks.size
        val failures = Collections.synchronizedList(mutableListOf<String>())
        if (tasks.isEmpty()) return failures
        val pool = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(tasks.size)
        tasks.forEach { t ->
            pool.submit {
                try {
                    if (!ensure(t)) failures.add(t.tag + " " + t.url)
                } catch (e: Throwable) {
                    failures.add(t.tag + " " + e.message)
                } finally {
                    synchronized(Downloader) {
                        done++
                        progress.onDownload(done, total, t.tag)
                    }
                    latch.countDown()
                }
            }
        }
        latch.await()
        pool.shutdown()
        return failures
    }

    fun fetchText(url: String, timeoutMs: Int = 30000): String {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "mclauncher/1.0.0")
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP " + code + " " + url)
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn?.disconnect()
        }
    }
}
