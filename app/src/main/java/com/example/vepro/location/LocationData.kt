package com.example.vepro.location

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val accuracy: Float,
    val bearing: Float,
    val timestamp: Long
)
