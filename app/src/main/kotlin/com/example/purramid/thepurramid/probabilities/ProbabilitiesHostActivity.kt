package com.example.purramid.thepurramid.probabilities

import android.content.Intent
import android.os.Bundle
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
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
class ProbabilitiesHostActivity : FloatingWindowActivity() {
    private lateinit var navController: NavController
    private lateinit var binding: ActivityProbabilitiesHostBinding
    private val settingsViewModel: ProbabilitiesSettingsViewModel by viewModels()

    @Inject lateinit var instanceManager: InstanceManager

    companion object {
        const val EXTRA_INSTANCE_ID = "probabilities_instance_id"
        const val EXTRA_CLONE_FROM = "clone_from_instance"
        private const val MIN_WIDTH_DP = 300
        private const val MIN_HEIGHT_DP = 250
        private const val TAG = "ProbabilitiesHostActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProbabilitiesHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInstanceId()
        setupNavigation()
        restoreWindowState()
    }

    private fun setupInstanceId() {
        instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, 0)
        if (instanceId == 0) {
            instanceId = instanceManager.getNextInstanceId(InstanceManager.PROBABILITIES) ?: run {
                showMaxWindowsError()
                finish()
                return
            }
        }

        // Clone settings if requested
        val cloneFromInstance = intent.getIntExtra(EXTRA_CLONE_FROM, -1)
        if (cloneFromInstance != -1) {
            cloneSettings(cloneFromInstance)
        }
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_probabilities) as NavHostFragment
        navController = navHostFragment.navController

        // Pass instance ID to fragments
        navController.setGraph(R.navigation.probabilities_nav_graph, Bundle().apply {
            putInt(EXTRA_INSTANCE_ID, instanceId)
        })

        // Observe settings to navigate to correct mode
        settingsViewModel.loadSettings(instanceId)
        observeSettingsAndNavigate()
    }

    private fun observeSettingsAndNavigate() {
        settingsViewModel.settings.observe(this) { settings ->
            if (settings != null && settings.instanceId == instanceId) {
                Log.d(TAG, "Settings observed for instance ${settings.instanceId}. Mode: ${settings.mode}")
                navigateToModeFragment(settings.mode)
            }
        }
    }

    private fun navigateToModeFragment(mode: ProbabilitiesMode) {
        val currentDestinationId = navController.currentDestination?.id
        val requiredDestinationId = when (mode) {
            ProbabilitiesMode.DICE -> R.id.diceMainFragment
            ProbabilitiesMode.COIN_FLIP -> R.id.coinFlipFragment
        }

        if (currentDestinationId != requiredDestinationId) {
            Log.d(TAG, "Navigating to mode: $mode for instance: $instanceId")
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(navController.graph.startDestinationId, true)
                .build()

            val args = Bundle().apply {
                putInt(EXTRA_INSTANCE_ID, instanceId)
            }
            navController.navigate(requiredDestinationId, args, navOptions)
        }
    }

    private fun restoreWindowState() {
        val prefs = getSharedPreferences("probabilities_window_states", MODE_PRIVATE)
        val savedX = prefs.getInt("window_${instanceId}_x", -1)
        val savedY = prefs.getInt("window_${instanceId}_y", -1)
        val savedWidth = prefs.getInt("window_${instanceId}_width", -1)
        val savedHeight = prefs.getInt("window_${instanceId}_height", -1)

        if (savedX != -1 && savedY != -1) {
            window.attributes = window.attributes.apply {
                x = savedX
                y = savedY
                if (savedWidth > 0) width = savedWidth
                if (savedHeight > 0) height = savedHeight
            }
        }
    }

    private fun cloneSettings(fromInstanceId: Int) {
        lifecycleScope.launch {
            // Clone dice settings
            val prefs = getSharedPreferences("probabilities_prefs", MODE_PRIVATE)
            val diceJson = prefs.getString("dice_settings_$fromInstanceId", null)
            if (diceJson != null) {
                prefs.edit().putString("dice_settings_$instanceId", diceJson).apply()
            }

            // Clone coin settings
            val coinJson = prefs.getString("coin_settings_$fromInstanceId", null)
            if (coinJson != null) {
                prefs.edit().putString("coin_settings_$instanceId", coinJson).apply()
            }

            // Clone mode
            val mode = prefs.getString("mode_$fromInstanceId", null)
            if (mode != null) {
                prefs.edit().putString("mode_$instanceId", mode).apply()
            }
        }
    }

    private fun showMaxWindowsError() {
        Snackbar.make(
            binding.root,
            getString(R.string.max_probabilities_reached_snackbar, 7),
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun getMinWidth() = dpToPx(MIN_WIDTH_DP)
    override fun getMinHeight() = dpToPx(MIN_HEIGHT_DP)

    override fun isTouchOnInteractiveElement(event: MotionEvent): Boolean {
        val touchPoint = Point(event.x.toInt(), event.y.toInt())

        // Check if touch is on any button or interactive element
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_probabilities)
        navHostFragment?.let { fragment ->
            fragment.childFragmentManager.fragments.forEach { childFragment ->
                childFragment.view?.let { fragmentView ->
                    // Check buttons in the fragment
                    val buttons = listOf(
                        fragmentView.findViewById<View>(R.id.diceRollButton),
                        fragmentView.findViewById<View>(R.id.diceResetButton),
                        fragmentView.findViewById<View>(R.id.dicePoolButton),
                        fragmentView.findViewById<View>(R.id.diceSettingsButton),
                        fragmentView.findViewById<View>(R.id.diceCloseButton),
                        fragmentView.findViewById<View>(R.id.coinFlipActionButton),
                        fragmentView.findViewById<View>(R.id.coinPoolButton),
                        fragmentView.findViewById<View>(R.id.coinFlipSettingsButton),
                        fragmentView.findViewById<View>(R.id.coinFlipCloseButton)
                    )

                    buttons.forEach { button ->
                        button?.let {
                            if (isPointInView(it, touchPoint)) {
                                return true
                            }
                        }
                    }

                    // Check if touch is on dice/coin display areas
                    val displayAreas = listOf(
                        fragmentView.findViewById<View>(R.id.diceDisplayArea),
                        fragmentView.findViewById<View>(R.id.coinDisplayAreaRecyclerView),
                        fragmentView.findViewById<View>(R.id.freeFormDisplayContainer)
                    )

                    displayAreas.forEach { area ->
                        area?.let {
                            if (it.visibility == View.VISIBLE && isPointInView(it, touchPoint)) {
                                return true
                            }
                        }
                    }
                }
            }
        }

        return false
    }

    override fun isInResizeZone(point: Point): Boolean {
        val decorView = window.decorView
        val edgeThreshold = dpToPx(20)

        // Bottom-right corner for resize
        return point.x > decorView.width - edgeThreshold &&
                point.y > decorView.height - edgeThreshold
    }

    override fun isInDragZone(point: Point): Boolean {
        // Allow dragging from any non-interactive area
        return !isTouchOnInteractiveElement(
            MotionEvent.obtain(0, 0, 0, point.x.toFloat(), point.y.toFloat(), 0)
        )
    }

    override fun saveWindowState() {
        val bounds = getCurrentWindowBounds()
        val prefs = getSharedPreferences("probabilities_window_states", MODE_PRIVATE)
        prefs.edit().apply {
            putInt("window_${instanceId}_x", bounds.left)
            putInt("window_${instanceId}_y", bounds.top)
            putInt("window_${instanceId}_width", bounds.width())
            putInt("window_${instanceId}_height", bounds.height())
            apply()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release instance ID
        instanceManager.releaseInstanceId(InstanceManager.PROBABILITIES, instanceId)

        // Check if this was the last instance
        if (instanceManager.getActiveInstanceCount(InstanceManager.PROBABILITIES) == 0) {
            // Optionally clear all probabilities preferences
            Log.d(TAG, "Last Probabilities instance closed")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent called")

        intent?.let {
            val newInstanceId = it.getIntExtra(EXTRA_INSTANCE_ID, 0)
            if (newInstanceId != 0 && newInstanceId != instanceId) {
                // Handle instance change if needed
                instanceId = newInstanceId
                setupNavigation()
            }
        }
    }

    fun openSettings() {
        // Navigate to settings fragment
        val bundle = Bundle().apply {
            putInt("instanceId", instanceId)
        }

        navController.navigate(R.id.probabilitiesSettingsFragment, bundle)

        // Add yellow border per Universal Requirements (7.2.2)
        window.decorView.foreground = ContextCompat.getDrawable(
            this,
            R.drawable.highlight_border
        )
    }

    fun closeSettings() {
        // Remove yellow border
        window.decorView.foreground = null
        navController.popBackStack()
    }

    fun launchNewInstance() {
        val activeCount = instanceManager.getActiveInstanceCount(InstanceManager.PROBABILITIES)
        if (activeCount >= 7) {
            showMaxWindowsError()
            return
        }

        val newInstanceId = instanceManager.getNextInstanceId(InstanceManager.PROBABILITIES)
        if (newInstanceId == null) {
            showMaxWindowsError()
            return
        }

        val currentBounds = getCurrentWindowBounds()
        val intent = Intent(this, ProbabilitiesHostActivity::class.java).apply {
            putExtra(EXTRA_INSTANCE_ID, newInstanceId)
            putExtra(EXTRA_CLONE_FROM, instanceId)

            // Offset new window position
            putExtra("WINDOW_X", currentBounds.left + 50)
            putExtra("WINDOW_Y", currentBounds.top + 50)
            putExtra("WINDOW_WIDTH", currentBounds.width())
            putExtra("WINDOW_HEIGHT", currentBounds.height())

            // Flags for independent window
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }

        startActivity(intent)
    }
}