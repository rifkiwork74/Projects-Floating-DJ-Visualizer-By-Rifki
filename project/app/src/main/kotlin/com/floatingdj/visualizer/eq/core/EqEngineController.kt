package com.floatingdj.visualizer.eq.core

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.os.Build

/**
 * Experimental output-mix controller.
 * Prefer a configurable DynamicsProcessing 10-band stage on API 28+ when the device
 * exposes and grants control of it. Otherwise fall back to the device's native Equalizer.
 * Session 0 coverage is device/ROM dependent and is never treated as proof of every route.
 */
object EqEngineController {
    data class Band(val index: Int, val centerHz: Int, val minMb: Int, val maxMb: Int, val levelMb: Int)
    data class Probe(val name: String, val implementor: String, val uuid: String, val connectMode: String)

    private enum class Backend { NONE, DYNAMICS_PROCESSING_10, NATIVE_EQUALIZER }

    private var backend = Backend.NONE
    private var dynamics: android.media.audiofx.DynamicsProcessing? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    var lastError: String? = null
        private set

    private val tenBandCenters = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    // DynamicsProcessing EQ bands describe upper cut-off edges, not centre-frequency points.
    // Geometric boundaries approximate the requested graphic-EQ centre frequencies.
    private val tenBandCutoffs = floatArrayOf(
        43.8f, 88.1f, 176.8f, 353.6f, 707.1f,
        1414.2f, 2828.4f, 5656.8f, 11313.7f, 20000f
    )

    @Suppress("DEPRECATION")
    @Synchronized fun activate(): Boolean {
        if (hasControl()) return true
        release()
        lastError = null

        // API 28+: attempt construction even if queryEffects() omits the descriptor.
        // The constructor + hasControl/read-back are the real runtime capability test.
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                // Reserve a real DynamicsProcessing limiter stage. It starts bypassed until
                // the user enables it; this keeps the default sound unchanged.
                val channel = android.media.audiofx.DynamicsProcessing.Channel(
                    0f, true, tenBandCenters.size, false, 0, false, 0, true
                )
                channel.setLimiter(android.media.audiofx.DynamicsProcessing.Limiter(
                    true, false, 0, 5f, 100f, 20f, -1f, 0f
                ))
                val preEq = android.media.audiofx.DynamicsProcessing.Eq(true, true, tenBandCenters.size)
                for (i in tenBandCenters.indices) {
                    preEq.setBand(i, android.media.audiofx.DynamicsProcessing.EqBand(true, tenBandCutoffs[i], 0f))
                }
                channel.setPreEq(preEq)
                val config = android.media.audiofx.DynamicsProcessing.Config.Builder(
                    android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, tenBandCenters.size, false, 0, false, 0, true
                ).setAllChannelsTo(channel).build()
                val dp = android.media.audiofx.DynamicsProcessing(0, 0, config)
                if (dp.hasControl() && dp.channelCount > 0) {
                    dynamics = dp
                    backend = Backend.DYNAMICS_PROCESSING_10
                    dp.setControlStatusListener(object : AudioEffect.OnControlStatusChangeListener {
                        override fun onControlStatusChange(effect: AudioEffect?, controlGranted: Boolean) {
                            if (!controlGranted) lastError = "Kontrol DynamicsProcessing berpindah ke audio effect lain."
                        }
                    })
                } else {
                    lastError = "DynamicsProcessing ditemukan tetapi tidak memberi kontrol pada output session."
                    dp.release()
                }
            } catch (t: Throwable) {
                lastError = "DynamicsProcessing 10-band tidak tersedia pada output session: ${t.javaClass.simpleName}: ${t.message ?: "unsupported"}"
                try { dynamics?.release() } catch (_: Throwable) {}
                dynamics = null
                backend = Backend.NONE
            }
        }

        // Fallback is the actual native band count; never synthesize extra slider controls.
        if (backend == Backend.NONE) {
            try {
                val eq = Equalizer(0, 0)
                if (!eq.hasControl() || eq.numberOfBands <= 0) {
                    if (lastError.isNullOrBlank()) lastError = "Equalizer ditemukan tetapi tidak memberi kontrol pada output session."
                    eq.release()
                    return false
                }
                equalizer = eq
                backend = Backend.NATIVE_EQUALIZER
                eq.setControlStatusListener(object : AudioEffect.OnControlStatusChangeListener {
                    override fun onControlStatusChange(effect: AudioEffect?, controlGranted: Boolean) {
                        if (!controlGranted) lastError = "Kontrol Equalizer berpindah ke effect lain; perubahan tidak lagi dijamin."
                    }
                })
            } catch (t: Throwable) {
                lastError = "${t.javaClass.simpleName}: ${t.message ?: "AudioEffect tidak tersedia"}"
                release()
                return false
            }
        }

        createOptionalEffects()
        return hasControl()
    }

    @Suppress("DEPRECATION")
    private fun createOptionalEffects() {
        try {
            val bass = BassBoost(0, 0)
            if (bass.hasControl()) {
                bass.setControlStatusListener(object : AudioEffect.OnControlStatusChangeListener {
                    override fun onControlStatusChange(effect: AudioEffect?, controlGranted: Boolean) {
                        if (!controlGranted) lastError = "Kontrol Bass Boost berpindah ke effect lain."
                    }
                })
                bassBoost = bass
            } else bass.release()
        } catch (_: Throwable) { bassBoost = null }

        try {
            val virt = Virtualizer(0, 0)
            if (virt.hasControl()) {
                virt.setControlStatusListener(object : AudioEffect.OnControlStatusChangeListener {
                    override fun onControlStatusChange(effect: AudioEffect?, controlGranted: Boolean) {
                        if (!controlGranted) lastError = "Kontrol Virtualizer berpindah ke effect lain."
                    }
                })
                virtualizer = virt
            } else virt.release()
        } catch (_: Throwable) { virtualizer = null }
    }

    @Synchronized fun backendName(): String = when (backend) {
        Backend.DYNAMICS_PROCESSING_10 -> "DYNAMICS PROCESSING · 10-BAND · EXPERIMENTAL OUTPUT SESSION"
        Backend.NATIVE_EQUALIZER -> "LEGACY NATIVE EQUALIZER · ${equalizer?.numberOfBands ?: 0}-BAND · EXPERIMENTAL OUTPUT SESSION"
        else -> "NO ACTIVE EQ ENGINE"
    }

    @Synchronized fun bandCount(): Int = bands().size

    @Synchronized fun isActive(): Boolean = when (backend) {
        Backend.DYNAMICS_PROCESSING_10 -> dynamics?.let { it.hasControl() && it.enabled } == true
        Backend.NATIVE_EQUALIZER -> equalizer?.let { it.hasControl() && it.enabled } == true
        else -> false
    }

    @Synchronized fun hasControl(): Boolean = when (backend) {
        Backend.DYNAMICS_PROCESSING_10 -> dynamics?.hasControl() == true
        Backend.NATIVE_EQUALIZER -> equalizer?.hasControl() == true
        else -> false
    }

    @Synchronized fun bands(): List<Band> {
        return try {
            when (backend) {
                Backend.DYNAMICS_PROCESSING_10 -> {
                    val dp = dynamics ?: return emptyList()
                    if (!dp.hasControl() || dp.channelCount <= 0) return emptyList()
                    tenBandCenters.indices.map { i ->
                        val value = dp.getPreEqBandByChannelIndex(0, i).gain
                        Band(i, tenBandCenters[i], -1500, 1500, (value * 100f).toInt().coerceIn(-1500, 1500))
                    }
                }
                Backend.NATIVE_EQUALIZER -> {
                    val eq = equalizer ?: return emptyList()
                    if (!eq.hasControl()) return emptyList()
                    val range = eq.bandLevelRange
                    (0 until eq.numberOfBands.toInt()).map { i ->
                        Band(i, (eq.getCenterFreq(i.toShort()) / 1000), range[0].toInt(), range[1].toInt(), eq.getBandLevel(i.toShort()).toInt())
                    }
                }
                else -> emptyList()
            }
        } catch (_: Throwable) { emptyList() }
    }

    @Synchronized fun setBand(index: Int, levelMb: Int): Int? {
        return try {
            when (backend) {
                Backend.DYNAMICS_PROCESSING_10 -> {
                    val dp = dynamics ?: return null
                    if (!dp.hasControl() || index !in tenBandCenters.indices) return null
                    val wantedDb = levelMb.coerceIn(-1500, 1500) / 100f
                    for (channel in 0 until dp.channelCount) {
                        val current = dp.getPreEqBandByChannelIndex(channel, index)
                        val updated = android.media.audiofx.DynamicsProcessing.EqBand(current)
                        updated.gain = wantedDb
                        dp.setPreEqBandByChannelIndex(channel, index, updated)
                    }
                    (dp.getPreEqBandByChannelIndex(0, index).gain * 100f).toInt()
                }
                Backend.NATIVE_EQUALIZER -> {
                    val eq = equalizer ?: return null
                    if (!eq.hasControl() || index !in 0 until eq.numberOfBands.toInt()) return null
                    val range = eq.bandLevelRange
                    eq.setBandLevel(index.toShort(), levelMb.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                    eq.getBandLevel(index.toShort()).toInt()
                }
                else -> null
            }
        } catch (_: Throwable) { null }
    }

    @Synchronized fun setEnabled(enabled: Boolean): Boolean = try {
        when (backend) {
            Backend.DYNAMICS_PROCESSING_10 -> dynamics?.let { if (!it.hasControl()) false else { it.enabled = enabled; it.enabled } } ?: false
            Backend.NATIVE_EQUALIZER -> equalizer?.let { if (!it.hasControl()) false else { it.enabled = enabled; it.enabled } } ?: false
            else -> false
        }
    } catch (_: Throwable) { false }

    /** Input-gain stage is the native DynamicsProcessing preamp, available from API 28. */
    @Synchronized fun preampAvailable(): Boolean = try {
        Build.VERSION.SDK_INT >= 28 && backend == Backend.DYNAMICS_PROCESSING_10 &&
            dynamics?.let { it.hasControl() && it.channelCount > 0 } == true
    } catch (_: Throwable) { false }

    @Synchronized fun preampGainDb(): Float = try {
        val dp = dynamics ?: return 0f
        if (!preampAvailable()) 0f else dp.getInputGainByChannelIndex(0).coerceIn(-12f, 6f)
    } catch (_: Throwable) { 0f }

    @Synchronized fun setPreampDb(value: Float): Float? = try {
        val dp = dynamics ?: return null
        if (!preampAvailable()) return null
        val target = value.coerceIn(-12f, 6f)
        dp.setInputGainAllChannelsTo(target)
        dp.getInputGainByChannelIndex(0).coerceIn(-12f, 6f)
    } catch (_: Throwable) { null }

    @Synchronized fun limiterAvailable(): Boolean = try {
        val dp = dynamics ?: return false
        backend == Backend.DYNAMICS_PROCESSING_10 && dp.hasControl() && dp.channelCount > 0 &&
            dp.getLimiterByChannelIndex(0).isInUse
    } catch (_: Throwable) { false }

    @Synchronized fun limiterEnabled(): Boolean = try {
        val dp = dynamics ?: return false
        limiterAvailable() && dp.getLimiterByChannelIndex(0).isEnabled
    } catch (_: Throwable) { false }

    @Synchronized fun setLimiterEnabled(enabled: Boolean): Boolean = try {
        val dp = dynamics ?: return false
        if (backend != Backend.DYNAMICS_PROCESSING_10 || !dp.hasControl() || dp.channelCount <= 0) return false
        for (channel in 0 until dp.channelCount) {
            val current = dp.getLimiterByChannelIndex(channel)
            if (!current.isInUse) return false
            val updated = android.media.audiofx.DynamicsProcessing.Limiter(current)
            updated.setEnabled(enabled)
            dp.setLimiterByChannelIndex(channel, updated)
        }
        limiterEnabled() == enabled
    } catch (_: Throwable) { false }

    @Synchronized fun disableAll() {
        try { dynamics?.enabled = false } catch (_: Throwable) {}
        try { equalizer?.enabled = false } catch (_: Throwable) {}
        try { bassBoost?.enabled = false } catch (_: Throwable) {}
        try { virtualizer?.enabled = false } catch (_: Throwable) {}
    }

    @Synchronized fun setBassStrength(value: Int): Int {
        val fx = bassBoost ?: return -1
        return try { fx.setStrength(value.coerceIn(0, 1000).toShort()); fx.roundedStrength.toInt() } catch (_: Throwable) { -1 }
    }
    @Synchronized fun bassAvailable(): Boolean = bassBoost?.hasControl() == true
    @Synchronized fun bassStrengthSupported(): Boolean = bassBoost?.strengthSupported == true && bassBoost?.hasControl() == true
    @Synchronized fun bassStrength(): Int = try { bassBoost?.roundedStrength?.toInt() ?: -1 } catch (_: Throwable) { -1 }
    @Synchronized fun bassEnabled(): Boolean = bassBoost?.let { it.hasControl() && it.enabled } == true
    @Synchronized fun setBassEnabled(enabled: Boolean): Boolean = try { bassBoost?.let { if (!it.hasControl()) false else { it.enabled = enabled; it.enabled } } ?: false } catch (_: Throwable) { false }

    @Synchronized fun setVirtualizerStrength(value: Int): Int {
        val fx = virtualizer ?: return -1
        return try { fx.setStrength(value.coerceIn(0, 1000).toShort()); fx.roundedStrength.toInt() } catch (_: Throwable) { -1 }
    }
    @Synchronized fun virtualizerAvailable(): Boolean = virtualizer?.hasControl() == true
    @Synchronized fun virtualizerStrengthSupported(): Boolean = virtualizer?.strengthSupported == true && virtualizer?.hasControl() == true
    @Synchronized fun virtualizerStrength(): Int = try { virtualizer?.roundedStrength?.toInt() ?: -1 } catch (_: Throwable) { -1 }
    @Synchronized fun virtualizerEnabled(): Boolean = virtualizer?.let { it.hasControl() && it.enabled } == true
    @Synchronized fun setVirtualizerEnabled(enabled: Boolean): Boolean = try { virtualizer?.let { if (!it.hasControl()) false else { it.enabled = enabled; it.enabled } } ?: false } catch (_: Throwable) { false }

    fun inventory(): List<Probe> = EffectCapabilityProbe.scan().map { Probe(it.name, it.implementor, it.implementationUuid, it.connectMode) }

    @Synchronized fun release() {
        try { dynamics?.enabled = false } catch (_: Throwable) {}
        try { equalizer?.enabled = false } catch (_: Throwable) {}
        try { bassBoost?.enabled = false } catch (_: Throwable) {}
        try { virtualizer?.enabled = false } catch (_: Throwable) {}
        try { dynamics?.release() } catch (_: Throwable) {}
        try { equalizer?.release() } catch (_: Throwable) {}
        try { bassBoost?.release() } catch (_: Throwable) {}
        try { virtualizer?.release() } catch (_: Throwable) {}
        dynamics = null; equalizer = null; bassBoost = null; virtualizer = null; backend = Backend.NONE
    }
}
