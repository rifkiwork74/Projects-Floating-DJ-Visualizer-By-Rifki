package com.floatingdj.visualizer.eq.data

import android.content.Context
import com.floatingdj.visualizer.eq.model.EqProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class EqPresetRepository(context: Context) {
    private val prefs = context.getSharedPreferences("eq_presets_v1", Context.MODE_PRIVATE)
    fun all(): MutableList<EqProfile> {
        val result = mutableListOf<EqProfile>()
        try {
            val arr = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until arr.length()) result.add(fromJson(arr.getJSONObject(i)))
        } catch (_: Throwable) {}
        if (result.isEmpty()) result.add(EqProfile("flat", "Flat"))
        return result
    }
    /** Seed a small cross-device starter library once without overwriting user presets. */
    fun ensureStarterPresets() {
        val items = all()
        val starter = listOf(
            EqProfile("builtin_flat_5", "Flat · 5 Band", mutableListOf(60, 230, 910, 3600, 14000), mutableListOf(0f, 0f, 0f, 0f, 0f)),
            EqProfile("builtin_bass_5", "Bass · 5 Band", mutableListOf(60, 230, 910, 3600, 14000), mutableListOf(4f, 3f, 1f, 0f, -1f)),
            EqProfile("builtin_vocal_5", "Vocal · 5 Band", mutableListOf(60, 230, 910, 3600, 14000), mutableListOf(-2f, 0f, 2f, 3f, 2f)),
            EqProfile("builtin_treble_5", "Treble · 5 Band", mutableListOf(60, 230, 910, 3600, 14000), mutableListOf(-2f, -1f, 0f, 2f, 4f)),
            EqProfile("builtin_flat_10", "Flat · 10 Band", mutableListOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), mutableListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqProfile("builtin_bass_10", "Bass · 10 Band", mutableListOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), mutableListOf(4f, 4f, 3f, 2f, 0f, -1f, -1f, 0f, 1f, 1f)),
            EqProfile("builtin_vocal_10", "Vocal · 10 Band", mutableListOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), mutableListOf(-2f, -1f, 0f, 2f, 3f, 3f, 2f, 1f, 0f, -1f)),
            EqProfile("builtin_dance_10", "Dance · 10 Band", mutableListOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), mutableListOf(4f, 3f, 1f, 0f, -1f, 2f, 3f, 4f, 2f, 3f)),
            EqProfile("builtin_acoustic_10", "Acoustic · 10 Band", mutableListOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000), mutableListOf(2f, 1f, 0f, 1f, 2f, 2f, 1f, 2f, 2f, 1f))
        )
        var changed = false
        starter.forEach { preset ->
            if (items.none { it.id == preset.id }) {
                items.add(preset)
                changed = true
            }
        }
        if (changed) persist(items)
    }

    fun save(profile: EqProfile) {
        val items = all().filterNot { it.id == profile.id }.toMutableList()
        items.add(profile)
        persist(items)
    }
    fun delete(id: String) = persist(all().filterNot { it.id == id })

    /** Current working state is independent from named presets; slider changes are not lost on Activity close. */
    fun saveCurrentState(profile: EqProfile) {
        prefs.edit().putString("current_state", toJson(profile).toString()).apply()
    }

    fun loadCurrentState(): EqProfile? = try {
        prefs.getString("current_state", null)?.let { raw -> fromJson(JSONObject(raw)) }
    } catch (_: Throwable) { null }

    fun create(name: String, template: EqProfile): EqProfile = template.copy(id = UUID.randomUUID().toString(), name = name)
    fun encode(profile: EqProfile): String = toJson(profile).toString(2)
    fun decode(raw: String): EqProfile {
        val obj = JSONObject(raw)
        require(obj.optInt("schemaVersion", -1) == 1) { "Schema preset tidak didukung" }
        val profile = fromJson(obj)
        require(profile.name.isNotBlank() && profile.centersHz.size == profile.gainDb.size) { "Data preset tidak valid" }
        require(profile.centersHz.size <= 32 && profile.gainDb.all { it in -24f..24f }) { "Nilai preset di luar batas" }
        return profile.copy(id = UUID.randomUUID().toString())
    }
    private fun persist(list: List<EqProfile>) { val a = JSONArray(); list.forEach { a.put(toJson(it)) }; prefs.edit().putString("items", a.toString()).apply() }
    private fun toJson(p: EqProfile) = JSONObject().put("schemaVersion", 1).put("presetId", p.id).put("name", p.name)
        .put("eq", JSONObject().put("centersHz", JSONArray(p.centersHz)).put("gainDb", JSONArray(p.gainDb)))
        .put("preamp", JSONObject().put("enabled", p.preampEnabled).put("gainDb", p.preampDb))
        .put("preampDb", p.preampDb).put("bassBoost", JSONObject().put("enabled", p.bassEnabled).put("strength", p.bassStrength))
        .put("virtualizer", JSONObject().put("enabled", p.virtualizerEnabled).put("strength", p.virtualizerStrength))
        .put("limiter", JSONObject().put("enabled", p.limiterEnabled))
    private fun fromJson(o: JSONObject): EqProfile {
        val eq = o.optJSONObject("eq") ?: JSONObject(); val f = eq.optJSONArray("centersHz") ?: JSONArray(); val g = eq.optJSONArray("gainDb") ?: JSONArray()
        val centers = mutableListOf<Int>(); val gains = mutableListOf<Float>()
        for (i in 0 until minOf(f.length(), g.length())) { centers.add(f.optInt(i)); gains.add(g.optDouble(i).toFloat()) }
        val b = o.optJSONObject("bassBoost") ?: JSONObject(); val v = o.optJSONObject("virtualizer") ?: JSONObject(); val l = o.optJSONObject("limiter") ?: JSONObject()
        val preamp = o.optJSONObject("preamp") ?: JSONObject()
        val preampDb = preamp.optDouble("gainDb", o.optDouble("preampDb", 0.0)).toFloat().coerceIn(-12f, 6f)
        return EqProfile(o.optString("presetId", UUID.randomUUID().toString()), o.optString("name", "Imported"), centers, gains,
            b.optBoolean("enabled"), b.optInt("strength"), v.optBoolean("enabled"), v.optInt("strength"),
            l.optBoolean("enabled", false), preamp.optBoolean("enabled", false), preampDb)
    }
}
