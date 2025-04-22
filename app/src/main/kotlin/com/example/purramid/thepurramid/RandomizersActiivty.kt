// RandomizersActivity.kt
package com.example.purramid.thepurramid

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter // Needed for the list dropdown
import android.widget.ListView
import android.widget.Toast // For temporary feedback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible // KTX for easy visibility toggling
import com.example.purramid.thepurramid.data.db.SpinListEntity // Import entity if needed for adapter
import com.example.purramid.thepurramid.databinding.ActivityRandomizersBinding // Import View Binding class
import com.example.purramid.thepurramid.managers.RandomizerInstanceManager
import java.util.UUID

class RandomizersActivity : AppCompatActivity() {

    // Use View Binding for layout inflation and view access
    private lateinit var binding: ActivityRandomizersBinding
    private var instanceId: UUID? = null // To hold the ID passed via Intent

    // Initialize the ViewModel using the activity-ktx delegate
    private val viewModel: RandomizerViewModel by viewModels() {
        // Get instanceId from Intent extras if it exists
        instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID)?.let { UUID.fromString(it) }
        RandomizerViewModelFactory.create(application, instanceId)
    }


    // Adapter for the list dropdown
    private lateinit var listDropdownAdapter: ArrayAdapter<String>
    private var listEntities: List<SpinListEntity> = emptyList() // To map displayed title back to ID

    companion object {
        const val EXTRA_INSTANCE_ID = "com.example.purramid.INSTANCE_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register instance with the manager
        // viewModel.instanceId is initialized within the 'by viewModels' block
        // We need access to the definite ID used/created by the viewmodel
        viewModel.viewModelScope.launch { // Access instanceId after VM init potentially
            val idToRegister = viewModel.instanceId // Access the ID from VM
            RandomizerInstanceManager.registerInstance(idToRegister)
        }

        // --- Initial Setup ---
        setupDropdown()
        setupUIListeners()
        observeViewModel()

        // TODO: Add logic for freeform window drag/resize listeners if needed beyond system defaults
        // TODO: Handle multiple instance positioning (offsetting) if launched via "add another"
    }

    private fun setupDropdown() {
        // Initialize the adapter for the dropdown ListView
        listDropdownAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        binding.listDropdownListView.adapter = listDropdownAdapter

        // Handle clicks on items in the dropdown
        binding.listDropdownListView.setOnItemClickListener { parent, view, position, id ->
            if (position < listEntities.size) {
                val selectedListId = listEntities[position].id
                viewModel.selectList(selectedListId)
            }
            // Consider adding an "Add New List..." item later which would navigate elsewhere
        }
    }

    override fun onDestroy() {
        // Unregister instance when Activity is destroyed
        viewModel.viewModelScope.launch { // Access instanceId safely
            val idToUnregister = viewModel.instanceId
            RandomizerInstanceManager.unregisterInstance(idToUnregister)
        }
        super.onDestroy()
    }

    private fun setupUIListeners() {
        // Set up listeners for buttons (Close, Settings, Spin, Marquee)
        binding.closeButton.setOnClickListener {
            // *** Manually closing the window ***
            viewModel.handleManualClose() // Trigger deletion/default save logic
            finish() // Close this instance
        }

        binding.settingsButton.setOnClickListener {
            // TODO: Implement navigation to SettingsActivity/Fragment
            // Need to pass the viewModel.instanceId to the settings screen
            Toast.makeText(this, "Settings Clicked (Not Implemented)", Toast.LENGTH_SHORT).show()
        }

        binding.spinButton.setOnClickListener {
            viewModel.handleSpinRequest() // Delegate logic to ViewModel
            // If spin animation is enabled, the Activity might observe spinResult
            // being null to trigger the animation on the SpinDialView here.
        }

        binding.listTitleMarquee.setOnClickListener {
            viewModel.toggleListDropdown() // Delegate logic to ViewModel
        }

        // Add listener to root to potentially close dropdown when clicking outside
        // binding.root.setOnClickListener { // Be careful not to interfere with other clicks
        //     if (binding.listDropdownCardView.isVisible) {
        //          viewModel.toggleListDropdown()
        //     }
        // }
    }

    private fun observeViewModel() {
        // Observe LiveData from the ViewModel to update the UI

        // Update the marquee title
        viewModel.currentListTitle.observe(this) { title ->
            binding.listTitleTextView.text = title ?: getString(R.string.select_list) // Use string resource
        }

        // Show/Hide the list dropdown
        viewModel.isDropdownVisible.observe(this) { isVisible ->
            // TODO: Add animation for dropdown visibility change
            binding.listDropdownCardView.isVisible = isVisible
            // Update caret icon direction
            val caretDrawable = if (isVisible) R.drawable.ic_caret_up else R.drawable.ic_caret_down
            binding.listTitleCaret.setImageResource(caretDrawable)
        }

        // Update the list of available lists in the dropdown
        viewModel.allSpinLists.observe(this) { lists ->
            listEntities = lists ?: emptyList()
            val listTitles = listEntities.map { it.title }
            listDropdownAdapter.clear()
            listDropdownAdapter.addAll(listTitles)
            // TODO: Add "Add New List..." option to dropdown if desired
            listDropdownAdapter.notifyDataSetChanged()
        }

        // Update the SpinDialView with items and settings
        viewModel.spinDialData.observe(this) { data ->
            if (data != null) {
                // Pass data to the custom view. Assumes SpinDialView has a method like setData.
                 binding.spinDialView.setData(data.items, data.settings) // TODO: Adjust method signature as needed in SpinDialView
            }
        }

        // Observe the result of a spin OR the signal to start spinning
        viewModel.spinResult.observe(this) { resultItem ->
            // Get the current spin enabled state (default to true if settings aren't loaded yet)
            val spinEnabled = viewModel.spinDialData.value?.settings?.isSpinEnabled ?: true

            if (resultItem == null && spinEnabled) {
                // --- Case 1: spinResult is null AND spin is enabled ---
                // This is our signal from the ViewModel to START the animation.
                if (::binding.isInitialized) { // Check binding initialization just in case
                    binding.spinDialView.spin { resultFromView ->
                        // This lambda is the CALLBACK from SpinDialView when its animation finishes.
                        // 'resultFromView' is the SpinItemEntity determined by the SpinDialView.
                        // Now, we tell the ViewModel the final result.
                        viewModel.setSpinResult(resultFromView) // New method needed in ViewModel
                    }
                }
            } else if (resultItem != null) {
                // --- Case 2: spinResult has a value ---
                // This means a result has been finalized, either because:
                //   a) Spin was disabled and the result was immediate.
                //   b) Spin was enabled, the animation finished, and setSpinResult() was called.

                // TODO: Handle Announce/Celebrate/Sequence logic here based on settings and resultItem.
                if (spinEnabled) {
                    // Feedback for when spin was enabled
                    Toast.makeText(this, "Spin Finished! Selected: ${resultItem.content}", Toast.LENGTH_SHORT).show()
                } else {
                    // Feedback for when spin was disabled
                    Toast.makeText(this, "Selected (No Spin): ${resultItem.content}", Toast.LENGTH_SHORT).show()
                }

                // It's good practice to clear the result in the ViewModel now that we've handled it,
                // preventing this block from re-triggering on configuration changes etc.
                viewModel.clearSpinResult() // New method needed in ViewModel

            }
            // --- Case 3: spinResult is null AND spin is DISABLED ---
            // This case shouldn't ideally happen with the current ViewModel logic,
            // as handleSpinRequest should set resultItem directly if spin is disabled.
            // No action needed here, but good to be aware of.
        }
    }

    // TODO: Handle saving/restoring state beyond ViewModel (e.g., window position/size if needed)
    // TODO: Implement navigation to/from Settings
    // TODO: Implement Announce/Celebrate/Sequence features based on ViewModel state/settings
}