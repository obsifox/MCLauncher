package com.mclauncher.core

import android.os.Environment
import java.io.File

object Env {
    val root: File get() = File(Environment.getExternalStorageDirectory(), ".mclauncher")
    val versionsDir: File get() = File(root, "Version")
    val librariesDir: File get() = File(root, "libraries")
    val assetsDir: File get() = File(root, "assets")
    val runtimesDir: File get() = File(root, "runtimes")
    val renderersDir: File get() = File(root, "renderers")
    val componentsDir: File get() = File(root, "components")
    val logsDir: File get() = File(root, "logs")
    val cacheDir: File get() = File(root, "cache")
    val accountsFile: File get() = File(root, "accounts.json")
    val settingsFile: File get() = File(root, "settings.json")
    val manifestFile: File get() = File(cacheDir, "version_manifest_v2.json")

    fun ensure() {
        listOf(
            root, versionsDir, librariesDir, assetsDir, runtimesDir,
            renderersDir, componentsDir, logsDir, cacheDir
        ).forEach { it.mkdirs() }
    }

    fun versionDir(id: String): File = File(versionsDir, id)

    val settings: Settings by lazy { Settings.load(settingsFile) }
    val accounts: Accounts by lazy { Accounts.load(accountsFile) }
}
