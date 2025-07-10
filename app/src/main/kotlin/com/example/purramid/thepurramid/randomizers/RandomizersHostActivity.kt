package com.example.purramid.thepurramid.randomizers

import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.databinding.ActivityRandomizersHostBinding
import com.example.purramid.thepurramid.instance.InstanceManager
import com.example.purramid.thepurramid.randomizers.data.RandomizerRepository
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerViewModel
import com.example.purramid.thepurramid.ui.FloatingWindowActivity
import com.example.purramid.thepurramid.util.dpToPx
import com.example.purramid.thepurramid.util.isPointInView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RandomizersHostActivity : FloatingWindowActivity() {

    private lateinit var binding: ActivityRandomizersHostBinding
    private lateinit var navController: NavController

    // Use RandomizerSettingsViewModel to observe mode changes
    private val settingsViewModel: RandomizerSettingsViewModel by viewModels()

    @Inject lateinit var randomizerRepository: RandomizerRepository

    companion object {
        private const val TAG = "RandomizersHostActivity"
        const val EXTRA_INSTANCE_ID = RandomizerViewModel.KEY_INSTANCE_ID
        const val EXTRA_CLONE_FROM = "clone_from_instance"
        private const val MIN_WIDTH_DP = 300
        private const val MIN_HEIGHT_DP = 250
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizersHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_randomizers) as NavHostFragment
        navController = navHostFragment.navController

        // Get instance ID from intent or allocate new
        instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, 0)
        if (instanceId == 0) {
            // Allocate a new instanceId
            val newInstanceId = instanceManager.getNextInstanceId(InstanceManager.RANDOMIZERS)
            if (newInstanceId == null) {
                Log.e(TAG, "No available instanceId. Maximum windows reached.")
                finish()
                return
            }
            instanceId = newInstanceId
            intent.putExtra(EXTRA_INSTANCE_ID, newInstanceId)
            setIntent(intent)

            lifecycleScope.launch {
                createDefaultEntriesForNewInstance(newInstanceId)
                Log.d(TAG, "HostActivity created AND DEFAULT DB ENTRIES ADDED for new instanceId: $newInstanceId")
                observeSettingsAndNavigate(newInstanceId)
            }
        } else {
            // Existing instance
            instanceManager.registerExistingInstance(InstanceManager.RANDOMIZERS, instanceId)
            Log.d(TAG, "HostActivity created and registered with existing instanceId: $instanceId")

            // Handle clone settings if requested
            val cloneFromInstance = intent.getIntExtra(EXTRA_CLONE_FROM, -1)
            if (cloneFromInstance != -1) {
                lifecycleScope.launch {
                    cloneSettingsFrom(cloneFromInstance, instanceId)
                }
            }

            observeSettingsAndNavigate(instanceId)
        }
    }

    private suspend fun createDefaultEntriesForNewInstance(newInstanceId: Int) {
        withContext(Dispatchers.IO) {
            try {
                val globalDefaultSettings = randomizerRepository.getDefaultSettings()
                val initialSettingsForInstance = globalDefaultSettings?.copy(
                    instanceId = newInstanceId,
                    mode = RandomizerMode.SPIN
                ) ?: SpinSettingsEntity(
                    instanceId = newInstanceId,
                    mode = RandomizerMode.SPIN
                )
                randomizerRepository.saveSettings(initialSettingsForInstance)

                val instanceEntity = RandomizerInstanceEntity(
                    instanceId = newInstanceId,
                    windowX = window.attributes.x,
                    windowY = window.attributes.y,
                    windowWidth = window.attributes.width,
                    windowHeight = window.attributes.height,
                    isActive = true
                )
                randomizerRepository.saveInstance(instanceEntity)

                Log.d(TAG, "Default DB entries (settings and instance) created for new instance $newInstanceId")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating default DB entries for new instance $newInstanceId", e)
            }
        }
    }

    private suspend fun cloneSettingsFrom(sourceInstanceId: Int, targetInstanceId: Int) {
        withContext(Dispatchers.IO) {
            try {
                val sourceSettings = randomizerRepository.getSettingsForInstance(sourceInstanceId)
                if (sourceSettings != null) {
                    val clonedSettings = sourceSettings.copy(instanceId = targetInstanceId)
                    randomizerRepository.saveSettings(clonedSettings)
                    Log.d(TAG, "Cloned settings from instance $sourceInstanceId to $targetInstanceId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cloning settings", e)
            }
        }
    }

    private fun observeSettingsAndNavigate(instanceId: Int) {
        settingsViewModel.settings.observe(this) { settings ->
            if (settings != null && settings.instanceId == instanceId) {
                Log.d(TAG, "Settings observed in HostActivity for instance ${settings.instanceId}. Mode: ${settings.mode}")
                navigateToModeFragment(settings.mode, instanceId)
            } else {
                Log.w(TAG, "Settings are null or instanceId mismatch for $instanceId. Cannot determine mode.")
                // Fallback or error display if settings can't be loaded
                if (navController.currentDestination?.id != R.id.randomizerMainFragment) {
                    // Optionally navigate to main fragment as default
                    navigateToModeFragment(RandomizerMode.SPIN, instanceId)
                }
            }
        }
    }

    private fun navigateToModeFragment(mode: RandomizerMode, instanceId: Int) {
        val currentDestinationId = navController.currentDestination?.id
        val requiredDestinationId = when (mode) {
            RandomizerMode.SPIN -> R.id.randomizerMainFragment
            RandomizerMode.SLOTS -> R.id.slotsMainFragment
        }
        if (currentDestinationId == requiredDestinationId) {
            Log.d(TAG, "Already on the correct fragment for mode $mode. No navigation needed.")
            return
        }
        Log.d(TAG, "Navigating to mode: $mode for instance: $instanceId")
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(navController.graph.startDestinationId, true)
            .build()
        try {
            val args = Bundle().apply { putInt(RandomizerViewModel.KEY_INSTANCE_ID, instanceId) }
            navController.navigate(requiredDestinationId, args, navOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed for mode $mode: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceManager.releaseInstanceId(InstanceManager.RANDOMIZERS, instanceId)
        Log.d(TAG, "HostActivity destroyed and released instanceId: $instanceId")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called for HostActivity. New Intent Action: ${intent?.action}")
        val newInstanceId = intent?.getIntExtra(EXTRA_INSTANCE_ID, 0) ?: 0
        if (newInstanceId != 0 && newInstanceId != instanceId) {
            instanceManager.releaseInstanceId(InstanceManager.RANDOMIZERS, instanceId)
            instanceId = newInstanceId
            instanceManager.registerExistingInstance(InstanceManager.RANDOMIZERS, newInstanceId)
            setIntent(intent)
            observeSettingsAndNavigate(newInstanceId)
        } else if (newInstanceId != 0) {
            observeSettingsAndNavigate(newInstanceId)
        }
    }

    // FloatingWindowActivity abstract method implementations
    override fun getMinWidth() = dpToPx(MIN_WIDTH_DP)
    override fun getMinHeight() = dpToPx(MIN_HEIGHT_DP)
    override fun getWindowPrefsName() = "randomizers_window_prefs"

    override fun isTouchOnInteractiveElement(event: MotionEvent): Boolean {
        val touchPoint = Point(event.x.toInt(), event.y.toInt())

        // Check if touch is on navigation host fragment (which contains all UI)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_randomizers)
        navHostFragment?.view?.let { view ->
            if (isPointInView(view, touchPoint)) {
                // Touch is within the nav host, now check if it's on specific non-interactive areas
                // For randomizers, we want to allow dragging from empty areas but not from buttons/controls
                // This will be handled by the fragments themselves
                return true
            }
        }

        return false
    }

    override fun isInResizeZone(point: Point): Boolean {
        val decorView = window.decorView
        val edgeThreshold = dpToPx(20)
        return point.x > decorView.width - edgeThreshold ||
                point.y > decorView.height - edgeThreshold
    }

    override fun isInDragZone(point: Point): Boolean {
        // For randomizers, allow dragging from any non-button area
        // The fragments will handle their own touch events for buttons
        return !isTouchOnInteractiveElement(
            MotionEvent.obtain(0, 0, 0, point.x.toFloat(), point.y.toFloat(), 0)
        )
    }

    override fun saveWindowState() {
        if (instanceId <= 0) return

        val bounds = getCurrentWindowBounds()

        // Save to database via repository
        lifecycleScope.launch {
            try {
                val currentInstance = randomizerRepository.getInstanceById(instanceId)
                if (currentInstance != null) {
                    val updatedInstance = currentInstance.copy(
                        windowX = bounds.left,
                        windowY = bounds.top,
                        windowWidth = bounds.width(),
                        windowHeight = bounds.height()
                    )
                    randomizerRepository.saveInstance(updatedInstance)
                    Log.d(TAG, "Window state saved for instance $instanceId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving window state", e)
            }
        }
    }

    // Method to handle Add Another functionality from settings
    fun launchNewRandomizerInstance(cloneFromInstanceId: Int) {
        val newIntent = Intent(this, RandomizersHostActivity::class.java).apply {
            putExtra(EXTRA_CLONE_FROM, cloneFromInstanceId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(newIntent)
    }
}