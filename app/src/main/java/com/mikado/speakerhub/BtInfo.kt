package com.mikado.speakerhub

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** What this phone can do for multi-speaker audio. */
data class Capabilities(
    val androidVersion: String,
    val bluetoothOn: Boolean,
    val leAudio: Boolean,
    val broadcastSource: Boolean,
    val broadcastAssistant: Boolean,
) {
    val verdict: String
        get() = when {
            !bluetoothOn -> "Bluetooth is off."
            broadcastSource -> "Auracast ready: this phone can broadcast to any number of Auracast speakers."
            leAudio -> "LE Audio only: this phone can't broadcast. Use a speaker party link or multi-phone sync."
            else -> "Classic Bluetooth only: one active speaker at a time. Use a speaker party link or multi-phone sync."
        }
}

data class BtSpeaker(val address: String, val name: String, val connected: Boolean)

object BtInfo {
    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun capabilities(context: Context): Capabilities {
        val a = adapter(context)
        fun supported(code: Int) = code == BluetoothStatusCodes.FEATURE_SUPPORTED
        return Capabilities(
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            bluetoothOn = a?.isEnabled == true,
            leAudio = a != null && supported(a.isLeAudioSupported),
            broadcastSource = a != null && supported(a.isLeAudioBroadcastSourceSupported),
            broadcastAssistant = a != null && supported(a.isLeAudioBroadcastAssistantSupported),
        )
    }

    /**
     * Paired audio devices, plus whether each is currently connected over A2DP.
     * Requires BLUETOOTH_CONNECT; the caller must check it first.
     */
    @SuppressLint("MissingPermission")
    fun pairedSpeakers(context: Context, connectedAddresses: Set<String>): List<BtSpeaker> {
        val a = adapter(context) ?: return emptyList()
        return a.bondedDevices
            .filter { d ->
                val major = d.bluetoothClass?.majorDeviceClass
                major == BluetoothClass.Device.Major.AUDIO_VIDEO
            }
            .map { BtSpeaker(it.address, it.name ?: it.address, it.address in connectedAddresses) }
            .sortedBy { it.name.lowercase() }
    }

    /** Asynchronously reads the addresses currently connected over A2DP. */
    @SuppressLint("MissingPermission")
    fun connectedA2dp(context: Context, onResult: (Set<String>) -> Unit) {
        val a = adapter(context)
        if (a == null) {
            onResult(emptySet())
            return
        }
        a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val result = proxy.connectedDevices.map { it.address }.toSet()
                a.closeProfileProxy(profile, proxy)
                onResult(result)
            }

            override fun onServiceDisconnected(profile: Int) = Unit
        }, BluetoothProfile.A2DP)
    }

    /**
     * Opens the system's audio-sharing (Auracast) screen if the OEM exposes one, otherwise
     * the regular Bluetooth settings. Returns a short description of what was opened.
     */
    fun openAuracastSettings(context: Context): String {
        val candidates = listOf(
            Intent("com.android.settings.BLUETOOTH_AUDIO_SHARING_SETTINGS") to "Audio sharing settings",
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS) to "Bluetooth settings",
        )
        for ((intent, label) in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return label
            } catch (_: Exception) {
                // Not exposed on this device; try the next one.
            }
        }
        return "Could not open settings"
    }
}
