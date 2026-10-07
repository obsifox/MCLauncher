package com.mclauncher.core

import java.io.File

object Components {

    fun abi(): String = BuildHelper.abi

    fun ready(): Boolean {
        val lib: File = File(Env.componentsDir, "lib")
        val exec: File = File(Env.componentsDir, "lib/libpojavexec.so")
        val glfwJar: File = File(Env.componentsDir, "components/lwjgl3/lwjgl-glfw-classes.jar")
        return exec.isFile && glfwJar.isFile && lib.isDirectory
    }

    fun jreBundled(id: String): Boolean {
        return File(Env.componentsDir, "runtime/$id").let { it.isDirectory && File(it, "lib").isDirectory }
    }

    fun ensure(progress: Installer.Progress): Boolean {
        if (ready()) return true
        val custom = Env.settings.urlOverrides["components"]?.takeIf { it.isNotBlank() }
        val fallback = "https://github.com/obsifox/mclauncher/releases/download/components/mclauncher-components-" + abi() + ".tar.gz"
        val url = custom ?: fallback
        return try {
            Env.componentsDir.mkdirs()
            progress.onStatus("components download")
            val archive = File(Env.cacheDir, "mclauncher-components-" + abi() + ".tar.gz")
            if (!Downloader.ensure(DownloadTask(url, archive, null, "components"))) {
                Logger.log("Components", "download failed: " + url)
                return false
            }
            progress.onStatus("components extract")
            Archives.extract(archive, Env.componentsDir)
            normalizeRoot()
            archive.delete()
            Logger.log("Components", "installed to " + Env.componentsDir.absolutePath)
            ready()
        } catch (e: Exception) {
            Logger.log("Components", "install failed: " + e.message)
            false
        }
    }

    private fun normalizeRoot() {
        val inner = File(Env.componentsDir, "mclauncher-components")
        if (inner.isDirectory) {
            inner.listFiles()?.forEach { f: File ->
                val target = File(Env.componentsDir, f.name)
                if (!target.exists()) runCatching { java.nio.file.Files.move(f.toPath(), target.toPath()) }
            }
            inner.deleteRecursively()
        }
    }
}
