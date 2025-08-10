package com.example.vepro

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.vepro.databinding.ActivityMainBinding
import com.example.vepro.location.LocationRepository
import com.example.vepro.utils.LocationAccuracy
import com.example.vepro.utils.Speed
import com.example.vepro.utils.TrackingState
import com.example.vepro.viewmodel.SpeedometerViewModel
import com.example.vepro.viewmodel.SpeedometerViewModelFactory
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speedometerViewModel: SpeedometerViewModel

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MainActivity", "Permission result: $isGranted")
        if (isGranted) {
            android.util.Log.d("MainActivity", "Permission granted by user, starting tracking")
            speedometerViewModel.startTracking()
        } else {
            android.util.Log.d("MainActivity", "Permission denied by user")
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViewModel()
        setupUI()
        observeViewModel()
//        setupNavigation()
    }

    private fun initializeViewModel() {
        val locationRepository = LocationRepository(this)
        val factory = SpeedometerViewModelFactory(locationRepository)
        speedometerViewModel = ViewModelProvider(this, factory)[SpeedometerViewModel::class.java]
    }

    private fun setupUI() {
        binding.startButton.setOnClickListener {
            when (speedometerViewModel.trackingState.value) {
                is TrackingState.Idle -> handleStartTracking()
                is TrackingState.Tracking -> speedometerViewModel.stopTracking()
                is TrackingState.Error -> speedometerViewModel.resetError()
                TrackingState.Starting -> TODO()
            }
        }

        // Reset button setup with error handling
        try {
            binding.resetButton.setOnClickListener {
                android.util.Log.d("Speedometer", "Reset button clicked")
                speedometerViewModel.resetMaxSpeed()
                showResetConfirmation()
            }
            android.util.Log.d("Speedometer", "Reset button listener set successfully")
        } catch (e: Exception) {
            android.util.Log.e("Speedometer", "Error with ViewBinding, trying direct access: ${e.message}")
            // Fallback: direct button access
            val resetBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.resetButton)
            resetBtn?.setOnClickListener {
                android.util.Log.d("Speedometer", "Reset button clicked (direct access)")
                speedometerViewModel.resetMaxSpeed()

                showResetConfirmation()
            }
        }
    }


    private fun showResetConfirmation() {
        // Simple toast to confirm reset worked
        android.widget.Toast.makeText(this, "Max speed reset", android.widget.Toast.LENGTH_SHORT).show()
    }
    private fun observeViewModel() {
        // Observe speed updates
        speedometerViewModel.currentSpeed.observe(this) { speed ->
            binding.speedText.text = getString(R.string.speed_format, speed.kmh)

            // Optional: Update additional UI elements
            updateSpeedIndicator(speed)
        }

        // Observe max speed updates
        speedometerViewModel.maxSpeed.observe(this) { maxSpeed ->
            binding.maxSpeedText.text = getString(R.string.max_speed_format, maxSpeed.kmh)
        }

        // Observe average speed updates
        speedometerViewModel.averageSpeed.observe(this) { avgSpeed ->
            binding.avgSpeedText.text = getString(R.string.speed_format, avgSpeed.kmh)
        }

        // Observe tracking state
        speedometerViewModel.trackingState.observe(this) { state ->
            updateUIForTrackingState(state)
        }

        // Observe location accuracy
        speedometerViewModel.locationAccuracy.observe(this) { accuracy ->
            updateAccuracyIndicator(accuracy)
        }
    }

    private fun handleStartTracking() {
        Log.d("MainActivity", "handleStartTracking called")

        val hasPermission = hasLocationPermission()
        Log.d("MainActivity", "Has location permission: $hasPermission")

        handleTracking(hasPermission)
    }

    private fun handleTracking(hasPermission: Boolean) {
        when {
            hasPermission -> {
                Log.d("MainActivity", "Permission granted, starting tracking")
                speedometerViewModel.startTracking()
            }

            else -> {
                Log.d("MainActivity", "Permission not granted, requesting permission")
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun updateUIForTrackingState(state: TrackingState) {
        when (state) {
            is TrackingState.Idle -> {
                binding.startButton.text = getString(R.string.start_tracking)
                binding.startButton.isEnabled = true
                binding.statusText.text = getString(R.string.ready_to_start)
                binding.progressBar.visibility = View.GONE
            }

            is TrackingState.Tracking -> {
                binding.startButton.text = getString(R.string.stop_tracking)
                binding.startButton.isEnabled = true
                binding.statusText.text = getString(R.string.tracking_active)
                binding.progressBar.visibility = View.GONE
            }

            is TrackingState.Starting -> {
                binding.startButton.isEnabled = false
                binding.statusText.text = getString(R.string.starting_gps)
                binding.progressBar.visibility = View.VISIBLE
            }

            is TrackingState.Error -> {
                binding.startButton.text = getString(R.string.retry)
                binding.startButton.isEnabled = true
                binding.statusText.text = state.message
                binding.progressBar.visibility = View.GONE
                showErrorSnackbar(state.message)
            }
        }
    }

    private fun updateSpeedIndicator(speed: Speed) {
        // Animate speed changes for better UX
        val animator = ValueAnimator.ofFloat(
            binding.speedText.alpha,
            if (speed.kmh > 0) 1.0f else 0.7f
        )
        animator.duration = 100
        animator.addUpdateListener { animation ->
            binding.speedText.alpha = animation.animatedValue as Float
        }
        animator.start()
//
//        // Update max speed if needed
//        if (speed.kmh > (speedometerViewModel.maxSpeed.value?.kmh ?: 0f)) {
//            binding.maxSpeedText.text = getString(R.string.max_speed_format, speed.kmh)
//        }
    }

    private fun updateAccuracyIndicator(accuracy: LocationAccuracy) {
        val (color, text) = when (accuracy) {
            LocationAccuracy.HIGH -> Pair(R.color.accuracy_good, R.string.accuracy_high)
            LocationAccuracy.MEDIUM -> Pair(R.color.accuracy_medium, R.string.accuracy_medium)
            LocationAccuracy.LOW -> Pair(R.color.accuracy_poor, R.string.accuracy_low)
        }

        binding.accuracyIndicator.setColorFilter(ContextCompat.getColor(this, color))
        binding.accuracyText.text = getString(text)
    }

    private fun hasLocationPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        android.util.Log.d("MainActivity", "Checking permission: $hasPermission")
        return hasPermission
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.location_permission_explanation)
            .setPositiveButton(R.string.settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.retry) {
                speedometerViewModel.startTracking()
            }
            .show()
    }

//    private fun setupNavigation() {
//        val navView: BottomNavigationView = binding.navView
//        val navController = findNavController(R.id.nav_host_fragment_activity_main)
//
//        val appBarConfiguration = AppBarConfiguration(
//            setOf(
//                R.id.navigation_home,
//                R.id.navigation_dashboard,
//                R.id.navigation_notifications
//            )
//        )
//
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        navView.setupWithNavController(navController)
//    }

    override fun onDestroy() {
        super.onDestroy()
        speedometerViewModel.stopTracking()
    }
}