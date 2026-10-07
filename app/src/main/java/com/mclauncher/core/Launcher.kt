package com.mclauncher.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import net.kdt.pojavlaunch.utils.JREUtils

object Launcher {

    class LaunchConfig(
        val info: VersionInfo,
        val classpath: String,
        val jvmArgs: List<String>,
        val gameArgs: List<String>,
        val gameDir: File,
        val runtimeId: String,
        val mainClass: String
    )

    fun prepare(versionId: String): LaunchConfig {
        val dir = Env.versionDir(versionId)
        val jsonFile = File(dir, "$versionId.json")
        if (!jsonFile.isFile) throw IllegalStateException("version $versionId is not installed")
        val info = Installer.resolveForLaunch(jsonFile)
        if (info.mainClass.isEmpty()) throw IllegalStateException("mainClass missing for $versionId")

        val nativesDir = File(Env.componentsDir, "lib")
        val renderer = Renderers.byId(Env.settings.rendererId)
        val rendererLib = renderer.mainLib()
        if (!nativesDir.isDirectory) throw IllegalStateException("components missing, redownload required")

        val cp = buildClasspath(info, jsonFile)
        val jvmArgs = buildJvmArgs(info, cp, nativesDir, renderer, rendererLib)
        val gameArgs = buildGameArgs(info)
        return LaunchConfig(info, cp, jvmArgs, gameArgs, dir, JavaRuntimes.runtimeFor(info.javaMajor), info.mainClass)
    }

    fun buildClasspath(info: VersionInfo, jsonFile: File): String {
        val entries = mutableListOf<String>()
        val skipGlfw = Regex("org[\\\\/]lwjgl[\\\\/]lwjgl-glfw[\\\\/].*")
        for (lib in info.libraries) {
            if (!RuleParser.allowed(lib.rules)) continue
            val artifact = lib.artifact ?: continue
            if (artifact.path.isEmpty()) continue
            if (skipGlfw.containsMatchIn(artifact.path)) continue
            val f = File(Env.librariesDir, artifact.path)
            if (f.isFile) entries.add(f.absolutePath)
        }
        val glfwJar = File(Env.componentsDir, "components/lwjgl3/lwjgl-glfw-classes.jar")
        if (glfwJar.isFile) entries.add(glfwJar.absolutePath)

        val localJars = mutableListOf<String>()
        try {
            val o = JSONObject(jsonFile.readText())
            if (o.has("mclauncherLocalJar")) {
                val local = o.optString("mclauncherLocalJar")
                val f = File(jsonFile.parentFile, local)
                if (f.isFile) localJars.add(f.absolutePath)
            }
        } catch (_: Exception) {
        }

        val jarName = info.jarPath.ifEmpty { jsonFile.nameWithoutExtension }
        val clientJar = File(jsonFile.parentFile, "$jarName.jar")
        if (clientJar.isFile) entries.add(clientJar.absolutePath)

        return (localJars + entries).joinToString(":")
    }

    private fun buildJvmArgs(
        info: VersionInfo,
        classpath: String,
        nativesDir: File,
        renderer: Renderer,
        rendererLib: File?
    ): List<String> {
        val mem = Env.settings.memoryMb.coerceIn(256, 4096)
        val out = mutableListOf<String>()
        out.add("-Xms${mem}M")
        out.add("-Xmx${mem}M")
        out.add("-Djava.library.path=" + nativesDir.absolutePath)
        out.add("-Dorg.lwjgl.librarypath=" + nativesDir.absolutePath)
        out.add("-Dorg.lwjgl.opengl.libname=" + (rendererLib?.absolutePath ?: ""))
        out.add("-Dorg.lwjgl.freetype.libname=" + File(nativesDir, "libfreetype.so").absolutePath)
        out.add("-Djava.io.tmpdir=" + Env.cacheDir.absolutePath)
        out.add("-Duser.language=" + java.util.Locale.getDefault().language)
        out.add("-Duser.country=" + java.util.Locale.getDefault().country)
        out.add("-XX:ActiveProcessorCount=" + Runtime.getRuntime().availableProcessors())
        Env.settings.jvmArgs.split(" ").filter { it.isNotBlank() }.forEach { out.add(it) }

        info.jvmArgs?.let { arr ->
            appendArgs(arr, out) { token ->
                when (token) {
                    "\${natives_directory}" -> nativesDir.absolutePath
                    "\${library_directory}" -> Env.librariesDir.absolutePath
                    "\${classpath_separator}" -> ":"
                    "\${launcher_name}" -> "mclauncher"
                    "\${launcher_version}" -> "1.0.0"
                    "\${version_name}" -> info.id
                    else -> token
                }
            }
        }
        out.add("-cp")
        out.add(classpath)
        return out
    }

    private fun buildGameArgs(info: VersionInfo): List<String> {
        val out = mutableListOf<String>()
        val account = Env.accounts.current()
        val playerName = account?.name ?: "Player"
        val uuid = account?.uuid ?: "00000000-0000-0000-0000-000000000000"
        val virtualDir = File(Env.assetsDir, "virtual/" + info.assetIndexId)

        if (info.minecraftArguments.isNotEmpty()) {
            out.addAll(
                info.minecraftArguments.split(" ").filter { it.isNotBlank() }.map { token ->
                    when (token) {
                        "\${auth_player_name}" -> playerName
                        "\${version_name}" -> info.id
                        "\${game_directory}" -> Env.versionDir(info.id).absolutePath
                        "\${assets_root}" -> Env.assetsDir.absolutePath
                        "\${game_assets}" -> virtualDir.absolutePath
                        "\${assets_index_name}" -> info.assetIndexId
                        "\${auth_uuid}" -> uuid
                        "\${auth_access_token}" -> "0"
                        "\${user_type}" -> "legacy"
                        "\${user_properties}" -> "{}"
                        "\${version_type}" -> info.type
                        "\${resolution_width}" -> "1280"
                        "\${resolution_height}" -> "720"
                        "\${clientid}" -> "mclauncher"
                        "\${auth_xuid}" -> "0"
                        else -> token
                    }
                }
            )
        } else {
            info.gameArgs?.let { arr ->
                appendArgs(arr, out) { token ->
                    when (token) {
                        "\${auth_player_name}" -> playerName
                        "\${version_name}" -> info.id
                        "\${game_directory}" -> Env.versionDir(info.id).absolutePath
                        "\${assets_root}" -> Env.assetsDir.absolutePath
                        "\${game_assets}" -> virtualDir.absolutePath
                        "\${assets_index_name}" -> info.assetIndexId
                        "\${auth_uuid}" -> uuid
                        "\${auth_access_token}" -> "0"
                        "\${user_type}" -> "legacy"
                        "\${user_properties}" -> "{}"
                        "\${version_type}" -> info.type
                        "\${clientid}" -> "mclauncher"
                        "\${auth_xuid}" -> "0"
                        "\${resolution_width}" -> "1280"
                        "\${resolution_height}" -> "720"
                        "\${quickPlayPath}" -> ""
                        "\${quickPlaySingleplayer}" -> ""
                        "\${quickPlayMultiplayer}" -> ""
                        "\${quickPlayRealms}" -> ""
                        else -> token
                    }
                }
            }
        }

        try {
            val jsonFile = File(Env.versionDir(info.id), info.id + ".json")
            if (jsonFile.isFile) {
                val o = JSONObject(jsonFile.readText())
                val tweaker = o.optString("mclauncherTweaker", "")
                if (tweaker.isNotEmpty()) {
                    out.add("--tweakClass")
                    out.add(tweaker)
                }
            }
        } catch (_: Exception) {
        }

        out.add("--width")
        out.add("1280")
        out.add("--height")
        out.add("720")
        return out
    }

    private fun appendArgs(arr: JSONArray, out: MutableList<String>, subst: (String) -> String) {
        for (i in 0 until arr.length()) {
            val e = arr.get(i)
            if (e is String) {
                out.add(subst(e))
            } else if (e is JSONObject) {
                if (!RuleParser.allowed(e.optJSONArray("rules"))) continue
                val value = e.opt("value")
                when (value) {
                    is String -> out.add(subst(value))
                    is JSONArray -> for (j in 0 until value.length()) out.add(subst(value.getString(j)))
                }
            }
        }
    }

    fun applyEnvironment(config: LaunchConfig): Boolean {
        val renderer = Renderers.byId(Env.settings.rendererId)
        val rendererDir = renderer.home()
        val libDir = File(Env.componentsDir, "lib")
        val jvmLd = JavaRuntimes.jvmLibPath(config.runtimeId)
            ?: throw IllegalStateException("java runtime " + config.runtimeId + " missing")

        val ld = listOf(jvmLd, rendererDir.absolutePath, libDir.absolutePath).joinToString(":")
        val home = Env.versionDir(config.info.id).absolutePath

        setenv("JAVA_HOME", JavaRuntimes.javaHome(config.runtimeId)?.absolutePath ?: "")
        setenv("HOME", Env.root.absolutePath)
        setenv("TMPDIR", Env.cacheDir.absolutePath)
        setenv("PATH", (JavaRuntimes.javaHome(config.runtimeId)?.absolutePath ?: "") + "/bin:" + System.getenv("PATH"))
        setenv("POJAV_NATIVEDIR", libDir.absolutePath)
        setenv("LIBGL_MIPMAP", "3")
        setenv("LIBGL_NOERROR", "1")
        setenv("LIBGL_NOINTOVLHACK", "1")
        setenv("LIBGL_NORMALIZE", "1")
        setenv("MESA_GLSL_CACHE_DIR", Env.cacheDir.absolutePath)
        setenv("force_glsl_extensions_warn", "true")
        setenv("allow_higher_compat_version", "true")
        setenv("allow_glsl_extension_directive_midshader", "true")
        setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink")
        setenv("GALLIUM_DRIVER", "zink")
        setenv("VTEST_SOCKET_NAME", File(Env.cacheDir, ".virgl_test").absolutePath)
        setenv("POJAV_RENDERER", renderer.id)
        setenv("POJAV_VSYNC_IN_ZINK", "1")
        setenv("LIBGL_ES", Env.settings.libglEs.ifBlank { "3" })
        if (renderer.needsEgl) setenv("POJAVEXEC_EGL", renderer.libName ?: "libltw.so")
        if (Env.settings.libglEs.isNotBlank()) setenv("LIBGL_ES", Env.settings.libglEs)

        JREUtils.LD_LIBRARY_PATH = ld
        JREUtils.setLdLibraryPath(ld)
        return true
    }

    private fun setenv(key: String, value: String) {
        try {
            NativeBridge.setEnv(key, value)
        } catch (e: Exception) {
            Logger.log("Launcher", "setenv $key failed: " + e.message)
        }
    }
}
