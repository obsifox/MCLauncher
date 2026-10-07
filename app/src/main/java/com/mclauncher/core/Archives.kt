package com.mclauncher.core

import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

object Archives {

    fun extract(archive: File, target: File) {
        val name = archive.name.lowercase()
        target.mkdirs()
        when {
            name.endsWith(".tar.xz") || name.endsWith(".txz") ->
                extractTar(XZInputStream(FileInputStream(archive), 1 shl 20), target)
            name.endsWith(".tar.gz") || name.endsWith(".tgz") ->
                extractTar(GZIPInputStream(FileInputStream(archive), 1 shl 20), target)
            name.endsWith(".tar") -> extractTar(FileInputStream(archive), target)
            name.endsWith(".deb") -> extractDeb(archive, target)
            name.endsWith(".zip") -> extractZip(archive, target)
            else -> throw IllegalArgumentException("unsupported archive: " + archive.name)
        }
    }

    fun extractTar(input: InputStream, target: File) {
        val hdr = ByteArray(512)
        try {
            while (true) {
                if (!readFully(input, hdr)) break
                if (isZeroBlock(hdr)) {
                    if (!readFully(input, ByteArray(512))) break
                    continue
                }
                val name = str(hdr, 0, 100)
                val size = octal(hdr, 124, 12)
                val type = hdr[156].toInt().toChar()
                val prefix = str(hdr, 345, 155)
                var full = if (prefix.isNotEmpty()) "$prefix/$name" else name
                if (name.startsWith("./")) full = full.removePrefix("./")
                val rel = full.trimStart('/').replace("\\", "/")
                if (rel.isEmpty() || rel.contains("../") || rel.contains("/PaxHeader/") || rel.contains("/./")) {
                    consume(input, size)
                    continue
                }
                val out = File(target, rel)
                when (type) {
                    '5' -> out.mkdirs()
                    '2' -> {
                        out.parentFile?.mkdirs()
                        val link = str(hdr, 157, 100)
                        runCatching {
                            if (out.exists()) out.delete()
                            Files.createSymbolicLink(out.toPath(), File(link).toPath())
                        }
                        consume(input, size)
                    }
                    '0', '\u0000', '7', '-' -> {
                        if (full.endsWith("/")) {
                            out.mkdirs()
                            consume(input, size)
                        } else {
                            out.parentFile?.mkdirs()
                            writeFile(input, out, size)
                            if (rel.startsWith("bin/") || rel.startsWith("/bin/")) runCatching { out.setExecutable(true, false) }
                            runCatching {
                                val mode = octal(hdr, 100, 8)
                                if (mode and 0x40L != 0L) out.setExecutable(true, false)
                            }
                        }
                    }
                    'x', 'g', 'L', 'K' -> consume(input, size)
                    else -> consume(input, size)
                }
            }
        } finally {
            input.close()
        }
    }

    private fun writeFile(input: InputStream, out: File, size: Long) {
        java.io.FileOutputStream(out).use { fos ->
            val buf = ByteArray(1 shl 16)
            var left = size
            while (left > 0) {
                val n = input.read(buf, 0, if (left < buf.size) left.toInt() else buf.size)
                if (n <= 0) break
                fos.write(buf, 0, n)
                left -= n
            }
        }
        val padded = (size + 511) / 512 * 512
        consume(input, padded - size)
    }

    fun extractZip(archive: File, target: File) {
        java.util.zip.ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                val rel = e.name.trimStart('/')
                if (rel.isEmpty() || rel.contains("../")) continue
                val out = File(target, rel)
                if (e.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                zip.getInputStream(e).use { ins -> writeFileFull(ins, out) }
                if (rel.startsWith("bin/")) runCatching { out.setExecutable(true, false) }
            }
        }
    }

    private fun writeFileFull(ins: InputStream, out: File) {
        java.io.FileOutputStream(out).use { fos ->
            val buf = ByteArray(1 shl 16)
            var n = ins.read(buf)
            while (n > 0) {
                fos.write(buf, 0, n)
                n = ins.read(buf)
            }
        }
    }

    fun extractDeb(deb: File, target: File) {
        FileInputStream(deb).use { ins ->
            if (readMagic(ins) != "!<arch>\n") throw IllegalArgumentException("not a deb archive")
            var found = false
            while (true) {
                val header = ByteArray(60)
                if (!readFully(ins, header)) break
                val memberName = str(header, 0, 16)
                val memberSize = str(header, 48, 10).trim().toLongOrNull() ?: break
                val cleanName = memberName.trim().removeSuffix("/")
                if (cleanName == "data.tar.xz" || cleanName == "data.tar.gz" || cleanName == "data.tar") {
                    val tmp = File(target.parentFile, cleanName + "." + System.currentTimeMillis())
                    java.io.FileOutputStream(tmp).use { fos ->
                        val buf = ByteArray(1 shl 16)
                        var left = memberSize
                        while (left > 0) {
                            val n = ins.read(buf, 0, if (left < buf.size) left.toInt() else buf.size)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            left -= n
                        }
                    }
                    extract(tmp, target)
                    tmp.delete()
                    found = true
                    break
                } else {
                    consume(ins, memberSize)
                }
            }
            if (!found) throw IllegalStateException("no data.tar in " + deb.name)
        }
    }

    private fun readMagic(ins: InputStream): String {
        val b = ByteArray(8)
        if (!readFully(ins, b)) return ""
        return String(b, 0, 8, Charsets.US_ASCII)
    }

    private fun readFully(ins: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = ins.read(buf, off, buf.size - off)
            if (n <= 0) return false
            off += n
        }
        return true
    }

    private fun consume(ins: InputStream, size: Long) {
        var left = (size + 511) / 512 * 512
        val buf = ByteArray(1 shl 16)
        while (left > 0) {
            val n = ins.read(buf, 0, if (left < buf.size) left.toInt() else buf.size)
            if (n <= 0) break
            left -= n
        }
    }

    private fun isZeroBlock(b: ByteArray): Boolean {
        for (x in b) if (x.toInt() != 0) return false
        return true
    }

    private fun str(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && end < b.size && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8).trim()
    }

    private fun octal(b: ByteArray, off: Int, len: Int): Long {
        val s = str(b, off, len).trim(' ', '\u0000')
        if (s.isEmpty()) return 0
        return runCatching { java.lang.Long.parseLong(s, 8) }.getOrDefault(0)
    }
}
