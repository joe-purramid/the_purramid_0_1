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

class RandomizersActivity : AppCompatActivity() {

    // Use View Binding for layout inflation and view access
    private lateinit var binding: ActivityRandomizersBinding

    // Initialize the ViewModel using the activity-ktx delegate
    private val viewModel: RandomizerViewModel by viewModels()

    // Adapter for the list dropdown
    private lateinit var listDropdownAdapter: ArrayAdapter<String>
    private var listEntities: List<SpinListEntity> = emptyList() // To map displayed title back to ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout using View Binding
        binding = ActivityRandomizersBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun setupUIListeners() {
        // Set up listeners for buttons (Close, Settings, Spin, Marquee)
        binding.closeButton.setOnClickListener {
            // TODO: Maybe save state explicitly before closing? ViewModel handles some via SavedStateHandle.
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

        // Handle the result of a spin
        viewModel.spinResult.observe(this) { resultItem ->
            if (resultItem != null) {
                // A result has been determined
                val spinEnabled = viewModel.spinDialData.value?.settings?.isSpinEnabled ?: true
                if (spinEnabled) {
                    // TODO: Spin was enabled, animation should have finished.
                    // Handle Announce/Celebrate/Sequence logic here based on settings.
                    Toast.makeText(this, "Selected: ${resultItem.content}", Toast.LENGTH_SHORT).show() // Temporary feedback
                } else {
                    // Spin was disabled, result was immediate. UI updated via spinDialData observer.
                     Toast.makeText(this, "Selected (No Spin): ${resultItem.content}", Toast.LENGTH_SHORT).show() // Temporary feedback
                }
                 // Reset spinResult in ViewModel?
                 // viewModel.clearSpinResult()
            } else {
                // spinResult is null, potentially meaning a spin animation should start
                val spinEnabled = viewModel.spinDialData.value?.settings?.isSpinEnabled ?: true
                 if (spinEnabled && ::binding.isInitialized) { // Check binding initialization
                    // TODO: Trigger the spin animation on binding.spinDialView
                    // binding.spinDialView.spin { /* Animation end callback if needed */ }
                 }
            }
        }
    }

    // TODO: Handle saving/restoring state beyond ViewModel (e.g., window position/size if needed)
    // TODO: Implement navigation to/from Settings
    // TODO: Implement Announce/Celebrate/Sequence features based on ViewModel state/settings
}