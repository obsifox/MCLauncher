package com.mclauncher.core

import org.json.JSONObject
import java.io.File

object Installer {

    interface Progress : Downloader.Progress {
        override fun onStatus(text: String)
    }

    fun install(mc: String, type: Loaders.Type, loaderVer: String?, progress: Progress): InstallResult {
        Env.ensure()
        try {
            val baseId = resolveBaseId(mc, progress)
            val baseJsonFile = ensureBaseJson(mc, progress)
            return when (type) {
                Loaders.Type.VANILLA -> installFromJson(baseJsonFile, progress)
                Loaders.Type.FABRIC -> {
                    val lv = loaderVer ?: Loaders.latestLoaderVersion(mc, Loaders.Type.FABRIC)
                        ?: return InstallResult(false, "fabric meta unavailable")
                    progress.onStatus("fabric $lv")
                    val profile = Loaders.fabricProfile(mc, lv)
                    installFromJson(writeProfile(profile), progress)
                }
                Loaders.Type.QUILT -> {
                    val lv = loaderVer ?: Loaders.latestLoaderVersion(mc, Loaders.Type.QUILT)
                        ?: return InstallResult(false, "quilt meta unavailable")
                    progress.onStatus("quilt $lv")
                    val profile = Loaders.quiltProfile(mc, lv)
                    installFromJson(writeProfile(profile), progress)
                }
                Loaders.Type.OPTIFINE -> {
                    val tier = loaderVer ?: return InstallResult(false, "no optifine tier")
                    val json = Loaders.buildOptiFineJson(mc, tier, baseId)
                    val dir = Env.versionDir(json.getString("id"))
                    dir.mkdirs()
                    val f = File(dir, json.getString("id") + ".json")
                    f.writeText(json.toString(2))
                    val res = installFromJson(f, progress)
                    res
                }
                Loaders.Type.FORGE -> installViaForgeInstaller(mc, loaderVer, baseId, baseJsonFile, Loaders.Type.FORGE, progress)
                Loaders.Type.NEOFORGE -> installViaForgeInstaller(mc, loaderVer, baseId, baseJsonFile, Loaders.Type.NEOFORGE, progress)
            }
        } catch (e: Exception) {
            Logger.log("Installer", "install failed: " + e.message)
            return InstallResult(false, e.message ?: "install failed")
        }
    }

    private fun resolveBaseId(mc: String, progress: Progress): String {
        val dir = Env.versionDir(mc)
        val json = File(dir, "$mc.json")
        if (json.isFile) {
            return runCatching { VersionInfo.parse(json).id }.getOrDefault(mc)
        }
        return mc
    }

    private fun ensureBaseJson(mc: String, progress: Progress): File {
        val dir = Env.versionDir(mc)
        dir.mkdirs()
        val json = File(dir, "$mc.json")
        if (json.isFile && json.length() > 2) return json
        val url = VersionManifest.urlFor(mc) ?: throw IllegalStateException("version $mc not found in manifest")
        progress.onStatus("manifest $mc")
        val tmp = File(dir, "$mc.json.part")
        Downloader.download(url, tmp)
        tmp.renameTo(json)
        return json
    }

    private fun writeProfile(profile: JSONObject): File {
        val id = profile.optString("id") ?: throw IllegalStateException("profile without id")
        val dir = Env.versionDir(id)
        dir.mkdirs()
        val f = File(dir, "$id.json")
        f.writeText(profile.toString(2))
        return f
    }

    fun installFromJson(jsonFile: File, progress: Progress): InstallResult {
        try {
            val info = resolveFull(jsonFile)
            val dir = jsonFile.parentFile ?: return InstallResult(false, "bad version dir")
            val jarName = info.jarPath.ifEmpty { jsonFile.nameWithoutExtension }
            val jarFile = File(dir, "$jarName.jar")

            progress.onStatus("client " + info.id)
            if (info.clientUrl.isNotEmpty()) {
                val cache = File(Env.cacheDir, "client-" + (info.clientSha1 ?: info.id) + ".jar")
                val ok = Downloader.ensure(DownloadTask(info.clientUrl, cache, info.clientSha1, "client"))
                if (!ok) return InstallResult(false, "client download failed")
                if (!Hashing.ok(jarFile, null)) {
                    cache.copyTo(jarFile, overwrite = true)
                }
            }

            progress.onStatus("libraries")
            val tasks = mutableListOf<DownloadTask>()
            for (lib in info.libraries) {
                if (!RuleParser.allowed(lib.rules)) continue
                val artifact = lib.artifact ?: continue
                if (artifact.path.isEmpty() || artifact.url.isEmpty()) continue
                tasks.add(DownloadTask(artifact.url, File(Env.librariesDir, artifact.path), artifact.sha1, artifact.path.substringAfterLast('/')))
            }
            val extra = loaderLibraries(jsonFile)
            tasks.addAll(extra)
            val failed = Downloader.ensureAll(tasks, progress)
            if (failed.isNotEmpty()) {
                return InstallResult(false, "failed: " + failed.take(3).joinToString("; "))
            }

            if (info.assetIndexUrl.isNotEmpty()) {
                progress.onStatus("assets " + info.assetIndexId)
                val err = installAssets(info, progress)
                if (err != null) return InstallResult(false, err)
            }

            val runtimeId = JavaRuntimes.runtimeFor(info.javaMajor)
            progress.onStatus("java " + runtimeId)
            val javaOk = JavaRuntimes.ensure(runtimeId, progress)
            if (!javaOk) Logger.log("Installer", "runtime $runtimeId not ready, user action required")

            val renderer = Renderers.byId(Env.settings.rendererId)
            progress.onStatus("renderer " + renderer.name)
            val renderOk = Renderers.ensure(renderer, progress)
            if (!renderOk) Logger.log("Installer", "renderer ${renderer.id} not ready, user action required")

            progress.onStatus("done " + info.id)
            return InstallResult(true, info.id)
        } catch (e: Exception) {
            Logger.log("Installer", "installFromJson failed: " + e.message)
            return InstallResult(false, e.message ?: "install failed")
        }
    }

    private fun loaderLibraries(jsonFile: File): List<DownloadTask> {
        val o = JSONObject(jsonFile.readText())
        val arr = o.optJSONArray("libraries") ?: return emptyList()
        val tasks = mutableListOf<DownloadTask>()
        for (i in 0 until arr.length()) {
            val lib = arr.getJSONObject(i)
            if (!RuleParser.allowed(lib.optJSONArray("rules"))) continue
            val name = lib.optString("name")
            if (name.isEmpty()) continue
            val artifactObj = lib.optJSONObject("downloads")?.optJSONObject("artifact")
            if (artifactObj != null) {
                val path = artifactObj.optString("path")
                val url = artifactObj.optString("url")
                if (path.isNotEmpty() && url.isNotEmpty()) {
                    tasks.add(
                        DownloadTask(url, File(Env.librariesDir, path),
                            artifactObj.optString("sha1", "").takeIf { it.isNotEmpty() },
                            path.substringAfterLast('/'))
                    )
                }
                continue
            }
            val rel = mavenPath(name) ?: continue
            val base = lib.optString("url", "").takeIf { it.isNotEmpty() } ?: repoForGroup(name.substringBefore(':'))
            if (base.isEmpty()) continue
            tasks.add(DownloadTask(base.trimEnd('/') + "/" + rel, File(Env.librariesDir, rel), null, rel.substringAfterLast('/')))
        }
        return tasks
    }

    fun mavenPath(name: String): String? {
        val parts = name.split(":")
        if (parts.size < 3) return null
        val group = parts[0]
        val artifact = parts[1]
        val verPart = parts[2]
        val ver = verPart.substringBefore('@')
        val ext = if (verPart.contains('@')) verPart.substringAfter('@') else "jar"
        var classifier = ""
        var fileVer = ver
        if (ver.contains('-') && ver.substringAfter('-').matches(Regex("(classifier|sources|javadoc).*"))) {
            classifier = "-" + ver.substringAfter('-')
            fileVer = ver.substringBefore('-')
        }
        val extra = if (parts.size > 3) "-" + parts[3] else classifier
        return "${group.replace('.', '/')}/$artifact/$ver/$artifact-$fileVer$extra.$ext"
    }

    private fun repoForGroup(group: String): String = when {
        group.startsWith("net.minecraftforge") -> "https://maven.minecraftforge.net/"
        group.startsWith("net.neoforged") -> "https://maven.neoforged.net/releases/"
        group.startsWith("net.fabricmc") -> "https://maven.fabricmc.net/"
        group.startsWith("org.quiltmc") -> "https://maven.quiltmc.org/repository/release/"
        group.startsWith("cpw.mods") -> "https://maven.minecraftforge.net/"
        group.startsWith("de.oceanlabs.mcp") -> "https://maven.minecraftforge.net/"
        group.startsWith("org.lwjgl") -> "https://libraries.minecraft.net/"
        else -> "https://libraries.minecraft.net/"
    }

    private fun installAssets(info: VersionInfo, progress: Progress): String? {
        val idxDir = File(Env.assetsDir, "indexes")
        idxDir.mkdirs()
        val idxFile = File(idxDir, info.assetIndexId + ".json")
        if (!Hashing.ok(idxFile, info.assetIndexSha1)) {
            val ok = Downloader.ensure(DownloadTask(info.assetIndexUrl, idxFile, info.assetIndexSha1, "asset-index"))
            if (!ok) return "asset index download failed"
        }
        val idx = JSONObject(idxFile.readText())
        val objects = idx.optJSONObject("objects") ?: return "asset index invalid"
        val objDir = File(Env.assetsDir, "objects")
        val tasks = mutableListOf<DownloadTask>()
        objects.keys().forEach { key ->
            val e = objects.getJSONObject(key)
            val hash = e.optString("hash")
            if (hash.length < 4) return@forEach
            val prefix = hash.substring(0, 2)
            val target = File(objDir, "$prefix/$hash")
            if (Hashing.ok(target, hash)) return@forEach
            tasks.add(
                DownloadTask(
                    "https://resources.download.minecraft.net/$prefix/$hash", target, hash, key
                )
            )
        }
        val failed = Downloader.ensureAll(tasks, progress)
        if (failed.isNotEmpty()) return "assets failed: " + failed.take(3).joinToString("; ")

        if (idx.optBoolean("virtual") || info.assets == "legacy") {
            val virtualRoot = File(Env.assetsDir, "virtual/" + info.assetIndexId)
            virtualRoot.mkdirs()
            objects.keys().forEach { key ->
                val hash = objects.getJSONObject(key).optString("hash")
                if (hash.length < 4) return@forEach
                val src = File(objDir, "${hash.substring(0, 2)}/$hash")
                val dst = File(virtualRoot, key)
                if (!dst.isFile && src.isFile) {
                    dst.parentFile?.mkdirs()
                    runCatching { java.nio.file.Files.createSymbolicLink(dst.toPath(), src.toPath()) }
                        .onFailure { runCatching { src.copyTo(dst, overwrite = false) } }
                }
            }
        }
        return null
    }

    private fun installViaForgeInstaller(
        mc: String, loaderVer: String?, baseId: String, baseJsonFile: File,
        type: Loaders.Type, progress: Progress
    ): InstallResult {
        val ver = loaderVer ?: return InstallResult(false, "no loader version")
        val full = if (type == Loaders.Type.FORGE) "$mc-$ver" else ver
        val url = if (type == Loaders.Type.FORGE) Loaders.forgeInstallerUrl(mc, ver) else Loaders.neoforgeInstallerUrl(ver)
        val stage = File(Env.cacheDir, "loader-stage-$type-$full")
        stage.deleteRecursively()
        val stageVersions = File(stage, "versions/$baseId")
        stageVersions.mkdirs()
        val jarName = baseId
        val baseJar = File(baseJsonFile.parentFile, "$jarName.jar")
        progress.onStatus("installer download")
        val installer = File(Env.cacheDir, "installer-$type-$full.jar")
        if (!Downloader.ensure(DownloadTask(url, installer, null, "installer"))) {
            return InstallResult(false, "installer download failed")
        }
        progress.onStatus("copy base")
        baseJsonFile.copyTo(File(stageVersions, "$baseId.json"), overwrite = true)
        if (baseJar.isFile) baseJar.copyTo(File(stageVersions, "$baseId.jar"), overwrite = true)

        val major = runCatching { VersionInfo.parse(baseJsonFile).javaMajor }.getOrDefault(8)
        val javaId = if (major >= 17) JavaRuntimes.runtimeFor(major) else "jre17"
        if (!JavaRuntimes.ensure(javaId, progress)) {
            return InstallResult(false, "java $javaId required for installer")
        }

        progress.onStatus("running installer")
        val log = JavaRuntimes.exec(javaId, stage, listOf("-jar", installer.absolutePath, "--installClient", stage.absolutePath))
        Logger.log("Installer", "$type installer output: " + log.takeLast(2000))

        val versionsOut = File(stage, "versions")
        val created = versionsOut.listFiles()?.filter { it.isDirectory && it.name != baseId } ?: emptyList()
        if (created.isEmpty()) {
            return InstallResult(false, "installer did not produce a version (check logs)")
        }
        val newDir = created.first()
        val newId = newDir.name
        val target = Env.versionDir(newId)
        target.deleteRecursively()
        newDir.copyRecursively(target, overwrite = true)
        val libsOut = File(stage, "libraries")
        if (libsOut.isDirectory) {
            libsOut.copyRecursively(Env.librariesDir, overwrite = true)
        }
        stage.deleteRecursively()
        installer.delete()
        progress.onStatus("libraries " + newId)
        val jsonFile = File(target, "$newId.json")
        val res = installFromJson(jsonFile, progress)
        return res
    }

    private fun resolveFull(jsonFile: File): VersionInfo {
        var info = VersionInfo.parse(jsonFile)
        var guard = 0
        while (info.inheritsFrom.isNotEmpty() && guard < 5) {
            val parentFile = File(Env.versionDir(info.inheritsFrom), info.inheritsFrom + ".json")
            if (!parentFile.isFile) {
                ensureBaseJson(info.inheritsFrom, object : Progress {
                    override fun onStatus(text: String) {}
                    override fun onDownload(done: Int, total: Int, tag: String) {}
                })
            }
            if (!parentFile.isFile) break
            info = info.merged(VersionInfo.parse(parentFile))
            guard++
        }
        return info
    }

    fun resolveForLaunch(jsonFile: File): VersionInfo = resolveFull(jsonFile)

    data class InstallResult(val success: Boolean, val message: String)
}
