// RandomizersHostActivity.kt
package com.example.purramid.thepurramid.randomizers

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log // Import Log
import androidx.lifecycle.lifecycleScope // For coroutines
import com.example.purramid.thepurramid.data.db.DEFAULT_SETTINGS_ID
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.databinding.ActivityRandomizersHostBinding
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.UUID // Import UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class RandomizersHostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizersHostBinding
    private var currentInstanceId: UUID? = null // Store the instanceId for this activity instance

    @Inject // Inject DAO here for creating the first default instance
    lateinit var randomizerDao: RandomizerDao

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
        if (instanceIdString == null) {
            // No instanceId passed, means this is likely the FIRST instance being created.
            // Generate a new ID, create default DB entries, and update the intent for ViewModels.
            Log.d(TAG, "instanceId is null in Intent. Assuming first instance, creating defaults.")
            val newInstanceId = UUID.randomUUID()
            currentInstanceId = newInstanceId
            instanceIdString = newInstanceId.toString()

            // Add the new ID to the intent so ViewModels (via SavedStateHandle) can pick it up
            intent.putExtra(EXTRA_INSTANCE_ID, instanceIdString)
            // Re-set the intent for the activity in case it's used later, and for SavedStateHandle
            setIntent(intent)

            lifecycleScope.launch { // Launch coroutine for DB operations
                createDefaultEntriesForNewInstance(newInstanceId)
                // Register *after* DB entries are potentially created
                RandomizerInstanceManager.registerInstance(newInstanceId)
                Log.d(TAG, "Activity created AND DEFAULT ENTRIES ADDED for new instanceId: $newInstanceId")
            }
        } else {
            // instanceIdString is not null, proceed to parse and register
            try {
                currentInstanceId = UUID.fromString(instanceIdString)
                currentInstanceId?.let {
                    RandomizerInstanceManager.registerInstance(it)
                    Log.d(TAG, "Activity created and registered with existing instanceId: $it")
                } ?: run { // Should not happen if instanceIdString was not null
                    Log.e(TAG, "Parsed UUID is null unexpectedly. Finishing.")
                    finish()
                    return
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid UUID format received: $instanceIdString. Finishing activity.", e)
                finish()
                return
            }
        }
        // ViewModels will now pick up the correct instanceId (either passed or newly generated and put into intent)
        // from SavedStateHandle in their init blocks.
    }

    private suspend fun createDefaultEntriesForNewInstance(newInstanceId: UUID) {
        withContext(Dispatchers.IO) {
            try {
                val defaultSettingsEntity = randomizerDao.getDefaultSettings(DEFAULT_SETTINGS_ID)
                val initialSettings = (defaultSettingsEntity?.copy(instanceId = newInstanceId)
                    ?: SpinSettingsEntity(instanceId = newInstanceId)) // Fresh default if no global default

                randomizerDao.saveSettings(initialSettings)
                randomizerDao.saveInstance(RandomizerInstanceEntity(instanceId = newInstanceId))
                Log.d(TAG, "Default DB entries created for new instance $newInstanceId")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating default DB entries for new instance $newInstanceId", e)
                // Consider how to handle this failure. If DB init fails, the instance might be unusable.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentInstanceId?.let {
            RandomizerInstanceManager.unregisterInstance(it)
            Log.d(TAG, "Activity destroyed and unregistered instanceId: $it")
        }
    }
}