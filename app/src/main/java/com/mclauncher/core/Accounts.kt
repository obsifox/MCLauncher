package com.mclauncher.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class Account(val name: String, val uuid: String)

class Accounts private constructor(private val file: File) {
    val list = mutableListOf<Account>()
    var active: String = ""

    fun add(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (list.any { it.name.equals(trimmed, ignoreCase = true) }) {
            active = trimmed
            save()
            return true
        }
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$trimmed".toByteArray()).toString()
        list.add(Account(trimmed, uuid))
        active = trimmed
        save()
        return true
    }

    fun remove(name: String) {
        list.removeAll { it.name == name }
        if (active == name) active = list.firstOrNull()?.name ?: ""
        save()
    }

    fun setActiveAccount(name: String) {
        active = name
        save()
    }

    fun current(): Account? = list.firstOrNull { it.name == active } ?: list.firstOrNull()

    fun save() {
        val arr = JSONArray()
        list.forEach { acc ->
            val o = JSONObject()
            o.put("name", acc.name)
            o.put("uuid", acc.uuid)
            arr.put(o)
        }
        val o = JSONObject()
        o.put("active", active)
        o.put("accounts", arr)
        file.parentFile?.mkdirs()
        file.writeText(o.toString(2))
    }

    companion object {
        fun load(file: File): Accounts {
            val a = Accounts(file)
            if (file.isFile) runCatching {
                val o = JSONObject(file.readText())
                a.active = o.optString("active")
                o.optJSONArray("accounts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val e = arr.getJSONObject(i)
                        a.list.add(Account(e.getString("name"), e.optString("uuid")))
                    }
                }
            }
            return a
        }
    }
}
