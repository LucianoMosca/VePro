package com.example.vepro.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallbacks = mutableSetOf<LocationCallback>()

    class LocationUnavailableException(message: String) : Exception(message)

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(): Flow<LocationData> = callbackFlow {
        if (!hasLocationPermission()) {
            throw SecurityException("Location permission not granted")
        }

        if (!isLocationEnabled()) {
            throw LocationUnavailableException("Location services are disabled")
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(250)
            .setMaxUpdateDelayMillis(750)
            .setWaitForAccurateLocation(false)
            .build()

        /*
        para que se actualice incluso más rapido pero con mayor gasto de bateria:
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 250) // 4x per second
                              .setMinUpdateIntervalMillis(100) // 10x per second max
                              .setMaxUpdateDelayMillis(500)
                              .build()
         */
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                android.util.Log.d("LocationRepo", "onLocationResult: ${result.locations.size} locations")
                result.locations.forEach { location ->
                    android.util.Log.d("LocationRepo", "Location: ${location.latitude}, ${location.longitude}, speed: ${location.speed}")
                    val locationData = LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speed = if (location.hasSpeed()) location.speed else 0f,
                        accuracy = location.accuracy,
                        bearing = if (location.hasBearing()) location.bearing else 0f,
                        timestamp = location.time
                    )
                    trySend(locationData)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                android.util.Log.d("LocationRepo", "onLocationAvailability: ${availability.isLocationAvailable}")
                if (!availability.isLocationAvailable) {
                    android.util.Log.w("LocationRepo", "Location temporarily unavailable - this is normal and expected")
                    // DON'T close the flow immediately - just log it
                    // Location might become available again soon

                    // Only close after a delay or multiple consecutive failures
                    // For now, just log and continue
                }
            }
        }

        locationCallbacks.add(callback)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallbacks.remove(callback)
        }
    }

    fun stopLocationUpdates() {
        locationCallbacks.forEach { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallbacks.clear()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("ServiceCast")
    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}