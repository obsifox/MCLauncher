package com.mclauncher.core

import java.io.File

object JavaRuntimes {

    const val JRE8 = "jre8"
    const val JRE17 = "jre17"
    const val JRE21 = "jre21"
    const val JRE25 = "jre25"

    val ids = listOf(JRE8, JRE17, JRE21, JRE25)

    fun abiDirSuffix(): String = "-" + BuildHelper.abi

    fun runtimeFor(javaMajor: Int): String {
        return when {
            javaMajor <= 8 -> JRE8
            javaMajor <= 17 -> JRE17
            javaMajor <= 21 -> JRE21
            else -> if (home(JRE25)?.isDirectory == true) JRE25 else JRE21
        }
    }

    fun home(id: String): File? {
        val fromComponents = File(Env.componentsDir, "runtime/$id")
        if (fromComponents.isDirectory && File(fromComponents, "lib").isDirectory) return fromComponents
        val installed = File(Env.runtimesDir, id + abiDirSuffix())
        if (installed.isDirectory && (File(installed, "lib").isDirectory || File(installed, "bin").isDirectory)) return installed
        val legacy = File(Env.runtimesDir, id)
        if (legacy.isDirectory && File(legacy, "lib").isDirectory) return legacy
        return null
    }

    fun available(): List<String> = ids.filter { home(it) != null }

    fun ensure(id: String, progress: Installer.Progress): Boolean {
        if (home(id) != null) return true
        return when (id) {
            JRE8 -> ensureDownloaded(id, "jre8", Regex("jre8-\\w*(arm64|x86_64)-\\d+-release\\.tar\\.xz"))
            JRE25 -> {
                val custom = Env.settings.urlOverrides["jre25"]
                if (custom.isNullOrBlank()) {
                    Logger.log("JavaRuntimes", "jre25 not installed, no custom URL set")
                    false
                } else {
                    extractFromArchive(id, custom)
                }
            }
            else -> false
        }
    }

    private fun ensureDownloaded(id: String, assetPrefix: String, pattern: Regex): Boolean {
        val url = Env.settings.urlOverrides[id]?.takeIf { it.isNotBlank() }
            ?: GitHubAssets.findAsset("PojavLauncherTeam/android-openjdk-build-multiarch", pattern)
            ?: return false
        return extractFromArchive(id, url)
    }

    private fun extractFromArchive(id: String, url: String): Boolean {
        return try {
            val dir = File(Env.runtimesDir, id + abiDirSuffix())
            dir.parentFile?.mkdirs()
            val archive = File(Env.cacheDir, id + "-" + url.substringAfterLast('/'))
            if (!Downloader.ensure(DownloadTask(url, archive, null, id))) return false
            Archives.extract(archive, dir)
            archive.delete()
            normalizeLayout(dir)
            File(dir, "bin/java")?.setExecutable(true, false)
            Logger.log("JavaRuntimes", "installed $id to " + dir.absolutePath)
            true
        } catch (e: Exception) {
            Logger.log("JavaRuntimes", "install $id failed: " + e.message)
            false
        }
    }

    private fun normalizeLayout(dir: File) {
        val children = dir.listFiles() ?: return
        val dirs = children.filter { it.isDirectory }
        if (dirs.size == 1 && dirs[0].name !in setOf("bin", "lib", "conf", "legal", "include", "man", "release")) {
            val inner = dirs[0]
            inner.listFiles()?.forEach { f ->
                val target = File(dir, f.name)
                if (!target.exists()) runCatching { java.nio.file.Files.move(f.toPath(), target.toPath()) }
            }
            inner.deleteRecursively()
        }
    }

    fun javaHome(id: String): File? = home(id)

    fun jvmLibPath(id: String): String? {
        val h = home(id) ?: return null
        val server = File(h, "lib/server/libjvm.so")
        val client = File(h, "lib/client/libjvm.so")
        val jli = File(h, "lib/jli")
        return when {
            server.isFile -> (h.absolutePath + "/lib:" + h.absolutePath + "/lib/server:" + (if (jli.isDirectory) h.absolutePath + "/lib/jli" else ""))
            client.isFile -> h.absolutePath + "/lib:" + h.absolutePath + "/lib/client"
            else -> h.absolutePath + "/lib"
        }
    }

    fun exec(id: String, workingDir: File, args: List<String>): String {
        val h = home(id) ?: throw IllegalStateException("runtime $id missing")
        val java = File(h, "bin/java")
        java.setExecutable(true, false)
        val ld = jvmLibPath(id) ?: h.absolutePath + "/lib"
        val pb = ProcessBuilder(listOf(java.absolutePath) + args)
        pb.directory(workingDir)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        env["JAVA_HOME"] = h.absolutePath
        env["HOME"] = Env.root.absolutePath
        env["TMPDIR"] = Env.cacheDir.absolutePath
        env["LD_LIBRARY_PATH"] = ld
        env["PATH"] = h.absolutePath + "/bin:" + (env["PATH"] ?: "")
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return out
    }
}
