// SpotlightActivity.kt
package com.example.purramid.thepurramid.spotlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivitySpotlightBinding // To host fragment
import com.example.purramid.thepurramid.spotlight.ui.SpotlightSettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SpotlightActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpotlightBinding

    companion object {
        private const val TAG = "SpotlightActivity"
        const val ACTION_SHOW_SPOTLIGHT_SETTINGS = "com.example.purramid.spotlight.ACTION_SHOW_SETTINGS"
        // Use constants from Service for SharedPreferences
        const val PREFS_NAME = SpotlightService.PREFS_NAME_FOR_ACTIVITY
        const val KEY_ACTIVE_COUNT = SpotlightService.KEY_ACTIVE_COUNT_FOR_ACTIVITY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpotlightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate - Intent Action: ${intent.action}")

        // Check if launched to show settings
        if (intent.action == ACTION_SHOW_SPOTLIGHT_SETTINGS) {
            showSettingsFragment()
        } else {
            // Default launch path
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeCount = prefs.getInt(KEY_ACTIVE_COUNT, 0)

            if (activeCount > 0) {
                Log.d(TAG, "Spotlights active ($activeCount), launching settings fragment.")
                showSettingsFragment()
            } else {
                Log.d(TAG, "No active Spotlights, requesting service to add a new one.")
                val serviceIntent = Intent(this, SpotlightService::class.java).apply {
                    action = ACTION_ADD_NEW_SPOTLIGHT_INSTANCE
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                finish() // Finish after telling service to add first instance
            }
        }
    }

    private fun showSettingsFragment() {
        if (supportFragmentManager.findFragmentByTag(SpotlightSettingsFragment.TAG) == null) {
            Log.d(TAG, "Showing Spotlight settings fragment.")
            supportFragmentManager.beginTransaction()
                .replace(R.id.spotlight_fragment_container, SpotlightSettingsFragment.newInstance())
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent - Action: ${intent?.action}")
        if (intent?.action == ACTION_SHOW_SPOTLIGHT_SETTINGS) {
            showSettingsFragment()
        }
    }
}