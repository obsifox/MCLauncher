package com.mclauncher.core

import java.io.File

data class Renderer(
    val id: String,
    val name: String,
    val tag: String,
    val libName: String?,
    val needsEgl: Boolean = false,
    val downloadRepo: String? = null,
    val downloadPattern: String? = null,
    val urlKey: String = ""
) {
    fun home(): File {
        val components = File(Env.componentsDir, "renderers/$id")
        if (components.isDirectory && components.listFiles()?.any { it.name.endsWith(".so") } == true) return components
        return File(Env.renderersDir, id)
    }

    fun installed(): Boolean {
        val h = home()
        return h.isDirectory && h.listFiles()?.any { it.name.endsWith(".so") } == true
    }

    fun mainLib(): File? {
        val h = home()
        if (!h.isDirectory) return null
        if (libName != null) {
            val exact = File(h, libName)
            if (exact.isFile) return exact
        }
        val prefer = listOf("libGL.so", "libgl4es_114.so", "libltw.so")
        for (p in prefer) {
            val f = File(h, p)
            if (f.isFile) return f
        }
        return h.listFiles()?.firstOrNull { it.name.startsWith("lib") && it.name.endsWith(".so") }
    }
}

object Renderers {

    val list = listOf(
        Renderer("gl4es", "GL4ES 1.1.5", "stable", "libgl4es_114.so"),
        Renderer("zink", "Zink (Mesa Vulkan)", "stable", null),
        Renderer("angle", "ANGLE (libltw)", "stable", "libltw.so", needsEgl = true),
        Renderer("virgl", "VirGL", "stable", null),
        Renderer("arjx", "Arjx", "alpha", null, downloadRepo = "obsifox/Arjx", downloadPattern = ".+\\.(tar\\.gz|tar\\.xz|tgz|zip)$", urlKey = "renderer-arjx")
    )

    val default: Renderer get() = list.first()

    fun byId(id: String): Renderer = list.firstOrNull { it.id == id } ?: default

    fun ensure(renderer: Renderer, progress: Installer.Progress): Boolean {
        if (renderer.installed()) return true
        val custom = Env.settings.urlOverrides[renderer.urlKey]?.takeIf { it.isNotBlank() }
        val url = custom ?: run {
            val repo = renderer.downloadRepo ?: return false
            val pattern = renderer.downloadPattern ?: return false
            GitHubAssets.findAsset(repo, Regex(pattern))
        } ?: return false
        return try {
            val dir = File(Env.renderersDir, renderer.id)
            dir.parentFile?.mkdirs()
            val archive = File(Env.cacheDir, "renderer-" + renderer.id + "-" + url.substringAfterLast('/'))
            if (!Downloader.ensure(DownloadTask(url, archive, null, "renderer-" + renderer.id))) return false
            Archives.extract(archive, dir)
            normalize(dir)
            archive.delete()
            Logger.log("Renderers", "installed ${renderer.id} to " + dir.absolutePath)
            renderer.installed()
        } catch (e: Exception) {
            Logger.log("Renderers", "install ${renderer.id} failed: " + e.message)
            false
        }
    }

    private fun normalize(dir: File) {
        val children = dir.listFiles() ?: return
        val subdirs = children.filter { it.isDirectory }
        if (subdirs.isNotEmpty() && children.none { it.name.endsWith(".so") }) {
            if (subdirs.size == 1) {
                subdirs[0].listFiles()?.forEach { f ->
                    val target = File(dir, f.name)
                    if (!target.exists()) runCatching { java.nio.file.Files.move(f.toPath(), target.toPath()) }
                }
                subdirs[0].deleteRecursively()
            }
        }
    }
}
