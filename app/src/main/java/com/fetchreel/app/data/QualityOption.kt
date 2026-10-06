package com.fetchreel.app.data

data class QualityOption(
    val label: String,
    val formatSpec: String,
    val height: Int? = null,
    val isAudio: Boolean = false
)
