package com.mikado.speakerhub

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val refreshTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            MaterialTheme(
                colorScheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            ) {
                Surface(Modifier.fillMaxWidth()) { HubScreen(refreshTick.intValue) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }
}

@Composable
private fun HubScreen(refreshTick: Int) {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    var caps by remember { mutableStateOf(BtInfo.capabilities(context)) }
    var speakers by remember { mutableStateOf<List<BtSpeaker>>(emptyList()) }
    var prefs by remember { mutableStateOf(store.loadSpeakers()) }
    var scenes by remember { mutableStateOf(store.loadScenes()) }
    var status by remember { mutableStateOf("") }

    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var volume by remember { mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }

    LaunchedEffect(refreshTick, hasPermission) {
        caps = BtInfo.capabilities(context)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
        if (hasPermission) {
            BtInfo.connectedA2dp(context) { connected ->
                speakers = BtInfo.pairedSpeakers(context, connected)
            }
        }
    }

    fun setVolume(fraction: Float) {
        volume = fraction
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * maxVol).toInt(), 0)
    }

    fun updatePref(s: BtSpeaker, change: (SpeakerPref) -> SpeakerPref) {
        val current = prefs[s.address] ?: SpeakerPref(s.address, s.name)
        prefs = prefs + (s.address to change(current))
        store.saveSpeakers(prefs.values)
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("SpeakerHub", style = MaterialTheme.typography.headlineMedium) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This phone", style = MaterialTheme.typography.titleMedium)
                    Text(caps.androidVersion)
                    Text("LE Audio: ${yesNo(caps.leAudio)}")
                    Text("Auracast broadcast: ${yesNo(caps.broadcastSource)}")
                    Text("Auracast assistant: ${yesNo(caps.broadcastAssistant)}")
                    Text(caps.verdict, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { status = "Opened: " + BtInfo.openAuracastSettings(context) }) {
                        Text("Open Auracast settings")
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Master volume", style = MaterialTheme.typography.titleMedium)
                    Slider(value = volume, onValueChange = ::setVolume)
                    Text("Controls the phone's media volume for the active output.")
                }
            }
        }

        item { Text("Speakers", style = MaterialTheme.typography.titleLarge) }

        if (!hasPermission) {
            item {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                    Text("Allow Bluetooth access to list speakers")
                }
            }
        } else if (speakers.isEmpty()) {
            item { Text("No paired audio devices found. Pair speakers in Bluetooth settings first.") }
        }

        items(speakers, key = { it.address }) { s ->
            val pref = prefs[s.address] ?: SpeakerPref(s.address, s.name)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(if (s.connected) "Connected" else "Not connected")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Channel.entries.forEach { ch ->
                            FilterChip(
                                selected = pref.channel == ch,
                                onClick = { updatePref(s) { it.copy(channel = ch) } },
                                label = { Text(ch.label) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Delay ${pref.delayMs} ms", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { updatePref(s) { it.copy(delayMs = (it.delayMs - 10).coerceAtLeast(0)) } }) { Text("-10") }
                        OutlinedButton(onClick = { updatePref(s) { it.copy(delayMs = (it.delayMs + 10).coerceAtMost(500)) } }) { Text("+10") }
                    }
                }
            }
        }

        item { Text("Scenes", style = MaterialTheme.typography.titleLarge) }

        item {
            val connected = speakers.filter { it.connected }
            Button(
                enabled = connected.isNotEmpty(),
                onClick = {
                    val group = connected.map { prefs[it.address] ?: SpeakerPref(it.address, it.name) }
                    val scene = Scene(
                        id = UUID.randomUUID().toString(),
                        name = "Scene ${scenes.size + 1}",
                        volumePercent = (volume * 100).toInt(),
                        speakers = group,
                    )
                    scenes = scenes + scene
                    store.saveScenes(scenes)
                    status = "Saved ${scene.name} with ${group.size} speaker(s)"
                },
            ) { Text("Save connected speakers as scene") }
        }

        items(scenes, key = { it.id }) { scene ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(scene.name, style = MaterialTheme.typography.titleMedium)
                    Text("Volume ${scene.volumePercent}%  ·  " + scene.speakers.joinToString { it.name })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            setVolume(scene.volumePercent / 100f)
                            val newPrefs = prefs.toMutableMap()
                            scene.speakers.forEach { newPrefs[it.address] = it }
                            prefs = newPrefs
                            store.saveSpeakers(prefs.values)
                            val missing = scene.speakers.filter { sp -> speakers.none { it.address == sp.address && it.connected } }
                            status = if (missing.isEmpty()) "${scene.name} applied"
                            else "${scene.name} applied. Connect: " + missing.joinToString { it.name }
                        }) { Text("Apply") }
                        TextButton(onClick = {
                            scenes = scenes.filterNot { it.id == scene.id }
                            store.saveScenes(scenes)
                        }) { Text("Delete") }
                    }
                }
            }
        }

        if (status.isNotEmpty()) item { Text(status, style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun yesNo(v: Boolean) = if (v) "Yes" else "No"
