package com.example.vepro.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vepro.location.LocationData
import com.example.vepro.location.LocationRepository
import com.example.vepro.utils.LocationAccuracy
import com.example.vepro.utils.Speed
import com.example.vepro.utils.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class SpeedometerViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _currentSpeed = MutableLiveData<Speed>()
    val currentSpeed: LiveData<Speed> = _currentSpeed

    private val _maxSpeed = MutableLiveData<Speed>()
    val maxSpeed: LiveData<Speed> = _maxSpeed

    private val _trackingState = MutableLiveData<TrackingState>(TrackingState.Idle)
    val trackingState: LiveData<TrackingState> = _trackingState

    private val _locationAccuracy = MutableLiveData<LocationAccuracy>()
    val locationAccuracy: LiveData<LocationAccuracy> = _locationAccuracy

    private val _averageSpeed = MutableLiveData<Speed>()
    val averageSpeed: LiveData<Speed> = _averageSpeed

    private val speedHistory = mutableListOf<Float>()
    private var trackingStartTime: Long = 0
    private var totalDistance: Float = 0f

    init {
        _currentSpeed.value = Speed(0f)
        _maxSpeed.value = Speed(0f)
        _averageSpeed.value = Speed(0f)
    }

    fun startTracking() {
        if (_trackingState.value is TrackingState.Tracking) return

        _trackingState.value = TrackingState.Starting
        trackingStartTime = System.currentTimeMillis()
        speedHistory.clear()
        totalDistance = 0f

        viewModelScope.launch {
            try {
                locationRepository.startLocationUpdates()
                    .flowOn(Dispatchers.IO)
                    .collect { location ->
                        handleLocationUpdate(location)
                    }
            } catch (e: Exception) {
                android.util.Log.e("SpeedometerVM", "Exception in location tracking", e)
                android.util.Log.e("SpeedometerVM", "Exception type: ${e::class.java.simpleName}")
                android.util.Log.e("SpeedometerVM", "Exception message: ${e.message}")

                _trackingState.value = TrackingState.Error(
                    when (e) {
                        is SecurityException -> {
                            android.util.Log.e("SpeedometerVM", "Security exception - permission issue")
                            "Location permission required"
                        }
                        is LocationRepository.LocationUnavailableException -> {
                            android.util.Log.e("SpeedometerVM", "Location unavailable exception")
                            "GPS not available"
                        }
                        else -> {
                            android.util.Log.e("SpeedometerVM", "Other exception: ${e::class.java.simpleName}")
                            "Failed to start location tracking: ${e.message}"
                        }
                    }
                )
            }
            //            catch (e: Exception) {
//                _trackingState.value = TrackingState.Error(
//                    when (e) {
//                        is SecurityException -> "Location permission required"
//                        is LocationRepository.LocationUnavailableException -> "GPS not available"
//                        else -> "Failed to start location tracking: ${e.message}"
//                    }
//                )
//            }
        }
    }

    fun stopTracking() {
        locationRepository.stopLocationUpdates()
        _trackingState.value = TrackingState.Idle
        _currentSpeed.value = Speed(0f)
    }

    fun resetError() {
        if (_trackingState.value is TrackingState.Error) {
            _trackingState.value = TrackingState.Idle
        }
    }

    fun resetMaxSpeed() {
        _maxSpeed.value = Speed(0f)
        _averageSpeed.value = Speed(0f)
    }

    private fun handleLocationUpdate(location: LocationData) {
        if (_trackingState.value !is TrackingState.Tracking) {
            _trackingState.value = TrackingState.Tracking
        }

        // Update current speed
        val speed = Speed(location.speed)
        _currentSpeed.value = speed

        // Update max speed
        val currentMax = _maxSpeed.value?.kmh ?: 0f
        if (speed.kmh > currentMax) {
            _maxSpeed.value = speed
        }

        // Update speed history for average calculation
        speedHistory.add(speed.kmh)
        if (speedHistory.size > 50) { // Keep last 50 readings
            speedHistory.removeAt(0)
        }

        // Calculate average speed
        if (speedHistory.isNotEmpty()) {
            val avgKmh = speedHistory.average().toFloat()
            _averageSpeed.value = Speed(avgKmh / 3.6f)
        }

        // Update location accuracy
        _locationAccuracy.value = when {
            location.accuracy <= 5 -> LocationAccuracy.HIGH
            location.accuracy <= 15 -> LocationAccuracy.MEDIUM
            else -> LocationAccuracy.LOW
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}