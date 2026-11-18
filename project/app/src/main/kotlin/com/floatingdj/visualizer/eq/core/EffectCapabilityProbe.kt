package com.floatingdj.visualizer.eq.core

import android.media.audiofx.AudioEffect

/** Read-only inventory. Descriptor presence is not proof that an effect can control global output. */
object EffectCapabilityProbe {
    data class Entry(val name: String, val implementor: String, val typeUuid: String, val implementationUuid: String, val connectMode: String)
    fun scan(): List<Entry> = try {
        AudioEffect.queryEffects()?.map {
            Entry(it.name ?: "Unknown", it.implementor ?: "Unknown", it.type.toString(), it.uuid.toString(), it.connectMode ?: "Unknown")
        } ?: emptyList()
    } catch (_: Throwable) { emptyList() }

    /** Descriptors only: presence never proves that another application's player feeds this effect. */
    fun reverbDescriptors(): List<Entry> = scan().filter {
        it.name.contains("reverb", ignoreCase = true)
    }

    fun reverbPostProcessingDescriptor(): Entry? = reverbDescriptors().firstOrNull {
        it.connectMode.contains("post", ignoreCase = true)
    }

    fun reverbAuxiliaryDescriptor(): Entry? = reverbDescriptors().firstOrNull {
        it.connectMode.contains("aux", ignoreCase = true)
    }
}
