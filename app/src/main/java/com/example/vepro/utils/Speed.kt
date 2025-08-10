package com.example.vepro.utils

data class Speed(
    val mps: Float,
    val kmh: Float = mps * 3.6f,
    val mph: Float = mps * 2.237f
)