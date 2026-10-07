package com.mclauncher.core

import java.io.File
import java.security.MessageDigest

object Hashing {
    fun sha1(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { ins ->
            val buf = ByteArray(65536)
            var n = ins.read(buf)
            while (n > 0) {
                md.update(buf, 0, n)
                n = ins.read(buf)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun ok(file: File, expected: String?): Boolean {
        if (!file.isFile) return false
        if (expected.isNullOrBlank()) return file.length() > 0
        return runCatching { sha1(file) == expected.lowercase() }.getOrDefault(false)
    }
}
