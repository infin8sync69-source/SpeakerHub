package com.mikado.speakerhub

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Channel(val label: String) { BOTH("Both"), LEFT("Left"), RIGHT("Right") }

data class SpeakerPref(
    val address: String,
    val name: String,
    val channel: Channel = Channel.BOTH,
    val delayMs: Int = 0,
)

data class Scene(
    val id: String,
    val name: String,
    val volumePercent: Int,
    val speakers: List<SpeakerPref>,
)

/** Persists per-speaker settings and scenes as JSON in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("speakerhub", Context.MODE_PRIVATE)

    fun loadSpeakers(): Map<String, SpeakerPref> {
        val raw = prefs.getString(KEY_SPEAKERS, null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { speakerFromJson(arr.getJSONObject(it)) }
                .associateBy { it.address }
        }.getOrDefault(emptyMap())
    }

    fun saveSpeakers(speakers: Collection<SpeakerPref>) {
        val arr = JSONArray().also { a -> speakers.forEach { a.put(speakerToJson(it)) } }
        prefs.edit().putString(KEY_SPEAKERS, arr.toString()).apply()
    }

    fun loadScenes(): List<Scene> {
        val raw = prefs.getString(KEY_SCENES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sp = o.getJSONArray("speakers")
                Scene(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    volumePercent = o.getInt("volume"),
                    speakers = (0 until sp.length()).map { speakerFromJson(sp.getJSONObject(it)) },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveScenes(scenes: List<Scene>) {
        val arr = JSONArray()
        scenes.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("volume", s.volumePercent)
                    .put("speakers", JSONArray().also { a -> s.speakers.forEach { a.put(speakerToJson(it)) } })
            )
        }
        prefs.edit().putString(KEY_SCENES, arr.toString()).apply()
    }

    private fun speakerToJson(s: SpeakerPref) = JSONObject()
        .put("address", s.address)
        .put("name", s.name)
        .put("channel", s.channel.name)
        .put("delay", s.delayMs)

    private fun speakerFromJson(o: JSONObject) = SpeakerPref(
        address = o.getString("address"),
        name = o.getString("name"),
        channel = runCatching { Channel.valueOf(o.getString("channel")) }.getOrDefault(Channel.BOTH),
        delayMs = o.optInt("delay", 0),
    )

    private companion object {
        const val KEY_SPEAKERS = "speakers"
        const val KEY_SCENES = "scenes"
    }
}
