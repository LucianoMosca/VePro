package com.example.vepro.utils

sealed class TrackingState {
    object Idle : TrackingState()
    object Starting : TrackingState()
    object Tracking : TrackingState()
    data class Error(val message: String) : TrackingState()
}