package com.example.purramid.thepurramid.probabilities

import android.content.Intent
import android.os.Bundle
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivityProbabilitiesHostBinding
import com.example.purramid.thepurramid.instance.InstanceManager
import com.example.purramid.thepurramid.probabilities.viewmodel.ProbabilitiesSettingsViewModel
import com.example.purramid.thepurramid.ui.FloatingWindowActivity
import com.example.purramid.thepurramid.util.dpToPx
import com.example.purramid.thepurramid.util.isPointInView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProbabilitiesHostActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private var currentInstanceId: Int = 0
    private lateinit var binding: ActivityProbabilitiesHostBinding
    private val settingsViewModel: ProbabilitiesSettingsViewModel by viewModels()

    @Inject lateinit var instanceManager: InstanceManager

    companion object {
        const val EXTRA_INSTANCE_ID = "probabilities_instance_id"
        const val EXTRA_CLONE_FROM = "clone_from_instance"
        private const val MIN_WIDTH_DP = 300
        private const val MIN_HEIGHT_DP = 250
        private const val TAG = "ProbabilitiesHostActivity"
        const val EXTRA_INSTANCE_ID = "probabilities_instance_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProbabilitiesHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_probabilities) as NavHostFragment
        navController = navHostFragment.navController

        val instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, 0)
        if (instanceId == 0) {
            // Allocate a new instanceId
            instanceId = instanceManager.getNextInstanceId(InstanceManager.PROBABILITIES) ?: run {
                showMaxWindowsError()
                finish()
                return
            }
        }

        // Clone settings if requested
        val cloneFromInstance = intent.getIntExtra(EXTRA_CLONE_FROM, -1)
        if (cloneFromInstance != -1) {
            settingsViewModel.cloneSettingsFrom(cloneFromInstance, instanceId)
        }

        // Set up navigation and observe mode changes
        setupNavigation()
    }

    private fun showMaxWindowsError() {
        Snackbar.make(
            binding.root,
            getString(R.string.max_probabilities_reached_snackbar),
            Snackbar.LENGTH_LONG
        ).show()
    }
    // TODO: Reconcile this with individual warnings for Dice and Coin Flip that already existr

    private fun observeSettingsAndNavigate(instanceId: Int) {
        settingsViewModel.settings.observe(this) { settings ->
            if (settings != null && settings.instanceId == instanceId) {
                Log.d(TAG, "Settings observed in ProbabilitiesHostActivity for instance ${settings.instanceId}. Mode: ${settings.mode}")
                navigateToModeFragment(settings.mode, instanceId)
            } else {
                Log.w(TAG, "Settings are null or instanceId mismatch for $instanceId. Cannot determine mode.")
            }
        }
    }

    private fun navigateToModeFragment(mode: ProbabilitiesMode, instanceId: Int) {
        val currentDestinationId = navController.currentDestination?.id
        val requiredDestinationId = when (mode) {
            ProbabilitiesMode.DICE -> R.id.diceMainFragment
            ProbabilitiesMode.COIN_FLIP -> R.id.coinFlipFragment
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
            val args = Bundle().apply { putInt(EXTRA_INSTANCE_ID, instanceId) }
            navController.navigate(requiredDestinationId, args, navOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Navigation failed for mode $mode: ${e.message}")
        }
    }

    override fun getMinWidth() = dpToPx(MIN_WIDTH_DP)
    override fun getMinHeight() = dpToPx(MIN_HEIGHT_DP)

    override fun isTouchOnInteractiveElement(event: MotionEvent): Boolean {
        // Check if touch is on buttons, settings, etc.
        val touchPoint = Point(event.x.toInt(), event.y.toInt())
        return isPointInView(binding.navHostFragment, touchPoint)
    }

    override fun isInResizeZone(point: Point): Boolean {
        val decorView = window.decorView
        val edgeThreshold = dpToPx(20)
        return point.x > decorView.width - edgeThreshold ||
                point.y > decorView.height - edgeThreshold
    }

    override fun isInDragZone(point: Point): Boolean {
        // Allow dragging from any non-interactive area
        return !isTouchOnInteractiveElement(MotionEvent.obtain(0, 0, 0, point.x.toFloat(), point.y.toFloat(), 0))
    }

    override fun saveWindowState() {
        val bounds = getCurrentWindowBounds()
        val prefs = getSharedPreferences("probabilities_window_states", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("window_${currentInstanceId}_x", bounds.left)
            putInt("window_${currentInstanceId}_y", bounds.top)
            putInt("window_${currentInstanceId}_width", bounds.width())
            putInt("window_${currentInstanceId}_height", bounds.height())
            apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceManager.releaseInstanceId(InstanceManager.PROBABILITIES, currentInstanceId)
        Log.d(TAG, "ProbabilitiesHostActivity destroyed and released instanceId: $currentInstanceId")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called for ProbabilitiesHostActivity. New Intent Action: ${intent?.action}")
        val newInstanceId = intent?.getIntExtra(EXTRA_INSTANCE_ID, 0) ?: 0
        if (newInstanceId != 0 && newInstanceId != currentInstanceId) {
            instanceManager.releaseInstanceId(InstanceManager.PROBABILITIES, currentInstanceId)
            currentInstanceId = newInstanceId
            instanceManager.registerExistingInstance(InstanceManager.PROBABILITIES, newInstanceId)
            setIntent(intent)
            observeSettingsAndNavigate(newInstanceId)
        } else if (newInstanceId != 0) {
            observeSettingsAndNavigate(newInstanceId)
        }
    }
} 