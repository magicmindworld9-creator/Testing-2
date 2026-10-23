package com.dp.formhelper

import android.content.Context
import org.json.JSONObject

/** One line of saved data: possible label names (normalized) and the value to fill. */
data class Entry(val keys: List<String>, val value: String)

/**
 * Stores named data profiles. Each profile is plain text, one field per line:
 *     label|other label|another name = value
 */
object ProfileStore {
    private const val PREFS = "formhelper"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ACTIVE = "active"
    const val CAPTURED = "Captured"

    /** Lower-case and keep only letters/digits/combining marks (so Telugu and other scripts work). */
    fun norm(s: String): String = buildString {
        for (c in s.lowercase()) {
            val t = Character.getType(c)
            if (c.isLetterOrDigit() ||
                t == Character.NON_SPACING_MARK.toInt() ||
                t == Character.COMBINING_SPACING_MARK.toInt()
            ) append(c)
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(ctx: Context): JSONObject =
        try { JSONObject(prefs(ctx).getString(KEY_PROFILES, "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun store(ctx: Context, o: JSONObject) {
        prefs(ctx).edit().putString(KEY_PROFILES, o.toString()).apply()
    }

    fun names(ctx: Context): List<String> {
        val o = load(ctx)
        val l = ArrayList<String>()
        val it = o.keys()
        while (it.hasNext()) l.add(it.next())
        if (l.isEmpty()) l.add("Default")
        return l.sorted()
    }

    fun text(ctx: Context, name: String): String = load(ctx).optString(name, "")

    fun save(ctx: Context, name: String, text: String) {
        val o = load(ctx)
        o.put(name, text)
        store(ctx, o)
    }

    fun delete(ctx: Context, name: String) {
        val o = load(ctx)
        o.remove(name)
        store(ctx, o)
        if (active(ctx) == name) setActive(ctx, names(ctx).first())
    }

    fun active(ctx: Context): String {
        val a = prefs(ctx).getString(KEY_ACTIVE, null)
        val all = names(ctx)
        return if (a != null && a in all) a else all.first()
    }

    fun setActive(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, name).apply()
    }

    fun parse(text: String): List<Entry> {
        val out = ArrayList<Entry>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val i = line.indexOf('=')
            if (i <= 0) continue
            val keys = line.substring(0, i).split('|').map { norm(it) }.filter { it.isNotEmpty() }
            val value = line.substring(i + 1).trim()
            if (keys.isNotEmpty() && value.isNotEmpty()) out.add(Entry(keys, value))
        }
        return out
    }

    fun entries(ctx: Context, name: String = active(ctx)): List<Entry> = parse(text(ctx, name))

    /** Best entry for a form label: exact match first, then the longest partial match. */
    fun find(entries: List<Entry>, labelNorm: String): Entry? {
        if (labelNorm.isEmpty()) return null
        for (e in entries) if (labelNorm in e.keys) return e
        var best: Entry? = null
        var bestLen = 0
        for (e in entries) for (k in e.keys) {
            if (k.length < 4 || labelNorm.length < 4) continue
            if ((labelNorm.contains(k) || k.contains(labelNorm)) && k.length > bestLen) {
                best = e
                bestLen = k.length
            }
        }
        return best
    }

    /** Adds/updates "label = value" lines in a profile, keeping other lines. */
    fun merge(ctx: Context, name: String, found: Map<String, String>) {
        val lines = LinkedHashMap<String, String>()
        for (raw in text(ctx, name).lines()) {
            val line = raw.trim()
            val i = line.indexOf('=')
            if (line.isEmpty() || line.startsWith("#") || i <= 0) continue
            lines[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        for ((k, v) in found) lines[k] = v
        save(ctx, name, lines.entries.joinToString("\n") { "${it.key} = ${it.value}" })
    }
}
