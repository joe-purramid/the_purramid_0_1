// ScreenShadeActivity.kt
package com.example.purramid.thepurramid.screen_shade

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivityScreenShadeBinding
import com.example.purramid.thepurramid.screen_shade.ui.ScreenShadeSettingsFragment
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScreenShadeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenShadeBinding
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    companion object {
        private const val TAG = "ScreenShadeActivity"
        const val ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE = "com.example.purramid.screen_shade.ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE"
        // Using the constants defined in ScreenShadeService for SharedPreferences
        const val PREFS_NAME = ScreenShadeService.PREFS_NAME_FOR_ACTIVITY
        const val KEY_ACTIVE_COUNT = ScreenShadeService.KEY_ACTIVE_COUNT_FOR_ACTIVITY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenShadeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate - Intent Action: ${intent.action}")

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                Log.d(TAG, "Image selected: $uri, forwarding to service.")
                sendImageUriToService(uri)
            } else {
                Log.d(TAG, "No image selected from picker.")
                sendImageUriToService(null)
            }
            finish() // Finish after image picking attempt
        }

        // Check the action that started this activity
        if (intent.action == ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE) {
            Log.d(TAG, "Launched by service to pick image.")
            openImageChooser()
            // Activity will finish after imagePickerLauncher returns
        } else {
            // Default launch path (e.g., from MainActivity or if no specific action)
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeCount = prefs.getInt(KEY_ACTIVE_COUNT, 0)

            if (activeCount > 0) {
                Log.d(TAG, "Screen Shades active ($activeCount), launching settings fragment.")
                showSettingsFragment()
                // Activity remains open to host the fragment
            } else {
                Log.d(TAG, "No active Screen Shades, requesting service to add a new one.")
                val serviceIntent = Intent(this, ScreenShadeService::class.java).apply {
                    action = ACTION_ADD_NEW_MASK_INSTANCE
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                finish() // Finish after telling service to add the first instance
            }
        }
    }

    private fun openImageChooser() {
        try {
            imagePickerLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open image picker", e)
            Snackbar.make(binding.root, getString(R.string.cannot_open_image_picker), Snackbar.LENGTH_LONG).show()
            finish()
        }
    }

    private fun sendImageUriToService(uri: Uri?) {
        val serviceIntent = Intent(this, ScreenShadeService::class.java).apply {
            action = ACTION_BILLBOARD_IMAGE_SELECTED
            putExtra(EXTRA_IMAGE_URI, uri?.toString()) // Send URI as String
            // The service knows which instance requested it via imageChooserTargetInstanceId
        }
        // Use startService for simple data passing intents that don't require foreground lifecycle
        startService(serviceIntent)
    }

    private fun showSettingsFragment() {
        if (supportFragmentManager.findFragmentByTag(ScreenShadeSettingsFragment.TAG) == null) {
            Log.d(TAG, "Showing Screen Shade settings fragment.")
            supportFragmentManager.beginTransaction()
                .replace(R.id.screen_shade_fragment_container, ScreenShadeSettingsFragment.newInstance())
                .commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent with the new one
        Log.d(TAG, "onNewIntent - Action: ${intent?.action}")
        if (intent?.action == ACTION_LAUNCH_IMAGE_CHOOSER_FROM_SERVICE) {
            openImageChooser()
        } else if (intent?.action != null) {
            // If activity is reordered to front and it's not for image picking,
            // assume it's a generic launch and show settings if masks are active.
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeCount = prefs.getInt(KEY_ACTIVE_COUNT, 0)
            if (activeCount > 0) {
                showSettingsFragment()
            } else {
                // If somehow reordered to front and no masks are active (unlikely scenario if first launch adds one)
                // Default to adding a new mask and finishing.
                val serviceIntent = Intent(this, ScreenShadeService::class.java).apply {
                    action = ACTION_ADD_NEW_MASK_INSTANCE
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                finish()
            }
        }
    }
}