package com.example.purramid.thepurramid.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Utility class for handling photo selection in a way that's compatible with Android 14's
 * Selected Photos Access feature.
 */
object PhotoPickerUtils {
    
    /**
     * For Android 13+ (API 33+), use the Photo Picker which doesn't require permissions
     */
    fun createPhotoPickerLauncher(
        context: androidx.activity.ComponentActivity,
        onPhotoSelected: (Uri?) -> Unit
    ): ActivityResultLauncher<PickVisualMediaRequest> {
        return context.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            onPhotoSelected(uri)
        }
    }
    
    /**
     * For older Android versions, use the traditional document picker
     */
    fun createLegacyImagePickerLauncher(
        context: androidx.activity.ComponentActivity,
        onPhotoSelected: (Uri?) -> Unit
    ): ActivityResultLauncher<String> {
        return context.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            onPhotoSelected(uri)
        }
    }
    
    /**
     * Launch the appropriate picker based on Android version
     */
    fun launchPhotoPicker(
        modernLauncher: ActivityResultLauncher<PickVisualMediaRequest>?,
        legacyLauncher: ActivityResultLauncher<String>?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use Photo Picker for Android 13+
            modernLauncher?.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            // Use legacy picker for older versions
            legacyLauncher?.launch("image/*")
        }
    }
    
    /**
     * Check if we should use the modern photo picker
     */
    fun shouldUsePhotoPicker(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}