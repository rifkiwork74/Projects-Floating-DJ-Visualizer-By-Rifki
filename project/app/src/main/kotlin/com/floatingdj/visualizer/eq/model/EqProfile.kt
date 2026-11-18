package com.floatingdj.visualizer.eq.model

data class EqProfile(
    val id: String,
    var name: String,
    val centersHz: MutableList<Int> = mutableListOf(),
    val gainDb: MutableList<Float> = mutableListOf(),
    var bassEnabled: Boolean = false,
    var bassStrength: Int = 0,
    var virtualizerEnabled: Boolean = false,
    var virtualizerStrength: Int = 0,
    var limiterEnabled: Boolean = false,
    var preampEnabled: Boolean = false,
    var preampDb: Float = 0f
)
