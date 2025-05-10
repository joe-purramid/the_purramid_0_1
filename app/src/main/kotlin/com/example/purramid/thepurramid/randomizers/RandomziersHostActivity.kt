// RandomizersHostActivity.kt
package com.example.purramid.thepurramid.randomizers

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log // Import Log
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivityRandomizersHostBinding
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerViewModel // For KEY_INSTANCE_ID
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID // Import UUID

@AndroidEntryPoint
class RandomizersHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizersHostBinding
    private var currentInstanceId: UUID? = null // Store the instanceId for this activity instance

    companion object {
        private const val TAG = "RandomizersHostActivity"
        // Use the same key that ViewModels expect for SavedStateHandle
        const val EXTRA_INSTANCE_ID = RandomizerViewModel.KEY_INSTANCE_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizersHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve and store the instanceId
        val instanceIdString = intent.getStringExtra(EXTRA_INSTANCE_ID)
        if (instanceIdString != null) {
            try {
                currentInstanceId = UUID.fromString(instanceIdString)
                currentInstanceId?.let {
                    RandomizerInstanceManager.registerInstance(it)
                    Log.d(TAG, "Activity created and registered with instanceId: $it")
                } ?: run {
                    Log.e(TAG, "Failed to parse UUID from string: $instanceIdString. Finishing activity.")
                    // This instance is invalid without a proper ID for its ViewModels.
                    finish()
                    return
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid UUID format received: $instanceIdString. Finishing activity.", e)
                finish()
                return
            }
        } else {
            Log.e(TAG, "instanceId is null in Intent. Finishing activity.")
            // This should ideally not happen if launched correctly.
            // If it can, you might want to generate a new UUID here and save it to intent extras for ViewModels,
            // but that would imply a new instance was intended.
            // For now, let's assume an ID is always passed for existing/new instances.
            finish()
            return
        }

        // Navigation setup is mostly handled by the NavHostFragment and nav graph
        // You might add Toolbar/AppBar setup here later if needed
        // Example:
        // val navController = findNavController(R.id.nav_host_fragment_randomizers)
        // setupActionBarWithNavController(navController)
    }

    // override fun onSupportNavigateUp(): Boolean {
    //     val navController = findNavController(R.id.nav_host_fragment_randomizers)
    //     return navController.navigateUp() || super.onSupportNavigateUp()
    // }

    override fun onDestroy() {
        super.onDestroy()
        currentInstanceId?.let {
            RandomizerInstanceManager.unregisterInstance(it)
            Log.d(TAG, "Activity destroyed and unregistered instanceId: $it")
        }
    }
}