# mclauncher

mclauncher is an Android Minecraft launcher written from scratch in Kotlin with a small C (JNI) bridge. It manages the full lifecycle of installing and running Minecraft: Java Edition on Android, with a portrait menu UI and a landscape in-game activity.

## Features

- Downloads every Minecraft version (from the official Mojang version manifest), from the oldest releases to the latest snapshots
- Loader support: Vanilla, Fabric, Forge, NeoForge, OptiFine, Quilt
- Java runtimes: 8, 17, 21 and 25 layout support with automatic selection based on the version `javaVersion.majorVersion` (jre8/jre21 ship in the components package, jre17 is fetched on demand, jre25 can be provided through a custom URL or by placing it under `runtimes/jre25-<abi>`)
- Renderers: GL4ES, Zink, ANGLE (libltw), VirGL and Arjx (tagged `alpha`, fetched from the obsifox/Arjx releases)
- Incremental download engine: every file is SHA1 verified, corrupted or partial files are deleted and re-downloaded from scratch, healthy files are skipped and never downloaded twice
- Local offline accounts (username only), stored in `accounts.json`
- Test keyboard: an IME (`mclauncher keyboard`) with English and Persian layouts, plus a floating input bar inside the game that sends characters and key events to the game process
- Full storage access (`MANAGE_EXTERNAL_STORAGE`) is requested on first start, all data lives in `/storage/emulated/0/.mclauncher`
- Persian and English UI with RTL support

## Directory layout

```
/storage/emulated/0/.mclauncher/
  Version/<version>/<version>.json
  Version/<version>/<version>.jar
  Version/<version>/optifine.jar
  libraries/
  assets/
  runtimes/
  renderers/
  components/
  cache/
  logs/latest.txt
  accounts.json
  settings.json
```

## Install flow

1. Resolve the version JSON from the Mojang manifest and store it in `Version/<id>`
2. Copy and verify the client jar, re-download on checksum mismatch
3. Ensure every library in the merged JSON (including loader libraries), SHA1 verified, only missing or corrupted files are fetched
4. Ensure the asset index and all asset objects
5. Ensure the Java runtime and the selected renderer
6. Launch: the game runs in-process against the runtime components, with the renderer library injected through `org.lwjgl.opengl.libname` and `LD_LIBRARY_PATH`

Forge and NeoForge are installed by running their official installer with the launcher-provided Java runtime against a staging directory, then the produced version JSON and libraries are moved into the launcher layout.

## Runtime components

The first launch downloads a components package (LWJGL natives, GLFW bridge classes, OpenAL, JRE 8, JRE 21, GL4ES, ANGLE) from the GitHub releases of this repository. The URL can be overridden in the launcher settings.

Renderer sources:

| Renderer | Source | Tag |
|----------|--------|-----|
| GL4ES 1.1.5 | components package | stable |
| Zink | components helpers plus a Mesa/Zink build (custom URL) | stable |
| ANGLE | components package (libltw) | stable |
| VirGL | custom URL | stable |
| Arjx | https://github.com/obsifox/Arjx releases | alpha |

## Building

```
./gradlew assembleRelease
```

Requirements: JDK 17+, Android SDK 34, NDK r26, CMake 3.22. The signing keystore is `keystore/mclauncher.jks` with the alias `mclauncher` (see `app/build.gradle`).

## License

This project is original code written for the mclauncher repository. Runtime components downloaded at first launch keep their own upstream licenses: OpenJDK (GPLv2 with Classpath exception), LWJGL (BSD), GLFW (zlib/libpng), OpenAL-soft (LGPL), GL4ES and Mesa (MIT), ANGLE (BSD), and the PojavLauncher component builds (GPLv3), obtained from their public upstream releases.
