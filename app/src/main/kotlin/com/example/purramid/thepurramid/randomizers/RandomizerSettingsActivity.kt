// RandomizerSettingsActivity.kt
package com.example.purramid.thepurramid.randomizers

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.purramid.thepurramid.databinding.ActivityRandomizerSettingsBinding
import java.util.UUID
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RandomizerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizerSettingsBinding
    private var randomizerInstanceId: UUID? = null

    companion object {
        const val EXTRA_INSTANCE_ID = "com.example.purramid.SETTINGS_INSTANCE_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout (You'll need to create activity_settings.xml)
        binding = ActivityRandomizerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the instanceId passed from RandomizersActivity
        val instanceIdString = intent.getStringExtra(EXTRA_INSTANCE_ID)
        randomizerInstanceId = instanceIdString?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                // Handle error - invalid ID passed
                finish() // Close settings if ID is invalid
                null
            }
        }

        if (randomizerInstanceId == null) {
            // Handle error - no ID passed
            finish() // Close settings if no ID provided
            return
        }

        // TODO: Set up the Settings UI (Mode selector, List editor btn, toggles)
        // TODO: Create a SettingsViewModel, pass it the randomizerInstanceId
        // TODO: Load the specific settings for this instanceId using the ViewModel/DAO
        // TODO: Implement listeners for UI elements to update settings via ViewModel

        // Example: Display the instance ID temporarily
        binding.textViewSettingsPlaceholder.text = "Settings for Instance:\n$randomizerInstanceId"

        // Example: Close button
        binding.closeSettingsButton.setOnClickListener {
            finish()
        }
    }
    private fun setupListeners() { // Assuming you add this method
        binding.closeSettingsButton.setOnClickListener { finish() }

        binding.buttonListEditor.setOnClickListener {
            val intent = Intent(this, ListEditorActivity::class.java)
            // Optional: Pass instanceId if ListEditor needs it, but likely not directly
            // intent.putExtra(...)
            startActivity(intent)
        }

        // TODO: Setup listeners for Mode selection and Toggles
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // ... (Existing onCreate code) ...
        setupListeners() // Call setupListeners
        // ...
    }
}