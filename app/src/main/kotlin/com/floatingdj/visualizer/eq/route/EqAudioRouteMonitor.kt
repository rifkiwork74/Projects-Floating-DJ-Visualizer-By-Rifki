package com.floatingdj.visualizer.eq.route

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

object EqAudioRouteMonitor {
    fun currentOutputs(context: Context): List<String> {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return try {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker internal"
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
                    else -> null
                }
            }.distinct()
        } catch (_: Throwable) { emptyList() }
    }
}
