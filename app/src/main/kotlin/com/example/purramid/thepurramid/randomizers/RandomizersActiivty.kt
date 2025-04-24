// RandomizersActivity.kt
package com.example.purramid.thepurramid.randomizers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.View
import android.widget.ArrayAdapter // For the list dropdown
import android.widget.TextView
import android.widget.Toast // For temporary feedback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.databinding.ActivityRandomizersBinding
import com.example.purramid.thepurramid.managers.RandomizerInstanceManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RandomizersActivity : AppCompatActivity() {

    // Use View Binding for layout inflation and view access
    private lateinit var binding: ActivityRandomizersBinding
    private val viewModel: RandomizerViewModel by viewModels()

    // --- Animation Constants ---
    private val dropdownAnimationDuration = 300L // milliseconds

    // Adapter for the list dropdown
    private lateinit var listDropdownAdapter: ArrayAdapter<String>
    private var listEntities: List<SpinListEntity> = emptyList() // To map displayed title back to ID

    companion object {
        const val EXTRA_INSTANCE_ID = "com.example.purramid.INSTANCE_ID"
    }

    // --- Animation Constants ---
    private val dropdownAnimationDuration = 300L

    // --- Views for Sequence Display ---
    // Group views for easier show/hide/update
    private lateinit var sequenceTextViews: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize sequence views list
        sequenceTextViews = listOf(
            // Update current item text
            binding.textSequenceCurrent.text = getItemContent(index)
            binding.textSequenceCurrent.visibility = View.VISIBLE

                    // Update previous items (up to 4)
            binding.textSequencePrev1.text = getItemContent(index - 1)
            binding.textSequencePrev1.visibility = if (index > 0) View.VISIBLE else View.GONE
            binding.textSequencePrev2.text = getItemContent(index - 2)
            binding.textSequencePrev2.visibility = if (index > 1) View.VISIBLE else View.GONE
            binding.textSequencePrev3.text = getItemContent(index - 3)
            binding.textSequencePrev3.visibility = if (index > 2) View.VISIBLE else View.GONE
            binding.textSequencePrev4.text = getItemContent(index - 4)
            binding.textSequencePrev4.visibility = if (index > 3) View.VISIBLE else View.GONE

            // Update next items (up to 4)
            binding.textSequenceNext1.text = getItemContent(index + 1)
            binding.textSequenceNext1.visibility = if (index < sequence.size - 1) View.VISIBLE else View.GONE
            binding.textSequenceNext2.text = getItemContent(index + 2)
            binding.textSequenceNext2.visibility = if (index < sequence.size - 2) View.VISIBLE else View.GONE
            binding.textSequenceNext3.text = getItemContent(index + 3)
            binding.textSequenceNext3.visibility = if (index < sequence.size - 3) View.VISIBLE else View.GONE
            binding.textSequenceNext4.text = getItemContent(index + 4)
            binding.textSequenceNext4.visibility = if (index < sequence.size - 4) View.VISIBLE else View.GONE

            // Update button enabled state
            binding.buttonSequenceUp.isEnabled = index > 0
            binding.buttonSequenceDown.isEnabled = index < sequence.size - 1
        )

        // Register instance
        RandomizerInstanceManager.registerInstance(viewModel.instanceId)

        // --- Initial Setup ---
        setupDropdown()
        setupUIListeners()
        observeViewModel()

        // TODO: Add logic for freeform window drag/resize listeners if needed beyond system defaults
        // TODO: Handle multiple instance positioning (offsetting) if launched via "add another"
    }

    override fun onDestroy() {
        RandomizerInstanceManager.unregisterInstance(viewModel.instanceId)
        super.onDestroy()
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
        }
    }

    private fun setupUIListeners() {
        // Set up listeners for buttons (Close, Settings, Spin, Marquee)
        binding.closeButton.setOnClickListener {
            viewModel.handleManualClose() // Trigger deletion/default save logic
            finish()
        }

        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, RandomizerSettingsActivity::class.java) // Use correct Settings activity name
            intent.putExtra(RandomizerSettingsActivity.EXTRA_INSTANCE_ID, viewModel.instanceId.toString())
            startActivity(intent)
        }

        binding.spinButton.setOnClickListener {
            // Clear previous announce/celebrate when starting new spin
            clearAnnounceCelebrate()
            viewModel.handleSpinRequest()
        }

        binding.listTitleMarquee.setOnClickListener {
            viewModel.toggleListDropdown() // Delegate logic to ViewModel
        }

        // *** Sequence Button Listeners ***
        binding.buttonSequenceUp.setOnClickListener {
            viewModel.showPreviousSequenceItem()
        }
        binding.buttonSequenceDown.setOnClickListener {
            viewModel.showNextSequenceItem()
        }
    }

    private fun observeViewModel() {
        // Observe LiveData from the ViewModel to update the UI
        // Update the marquee title
        viewModel.currentListTitle.observe(this) { title ->
            binding.listTitleTextView.text = title ?: getString(R.string.select_list) // Use string resource
        }

        // Show/Hide the list dropdown
        viewModel.isDropdownVisible.observe(this) { isVisible ->
            if (isVisible) {
                animateDropdownOpen()
            } else {
                // Only animate close if it's currently somewhat visible
                if (binding.listDropdownCardView.visibility == View.VISIBLE) {
                    animateDropdownClose()
                } else {
                    // Ensure it's hidden if already hidden (e.g., initial state)
                    binding.listDropdownCardView.visibility = View.GONE
                    // Ensure caret is reset if closed instantly
                    binding.listTitleCaret.rotation = 0f
                }
            }
        }

        // Update the list of available lists in the dropdown
        viewModel.displayedListOrder.observe(this) { sortedLists -> // OBSERVE NEW LIVEDATA
            listEntities = sortedLists ?: emptyList()
            val listTitles = listEntities.map { it.title } // Get titles from sorted list
            listDropdownAdapter.clear()
            listDropdownAdapter.addAll(listTitles)
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
            val settings = viewModel.spinDialData.value?.settings
            val spinEnabled = settings?.isSpinEnabled ?: true // Needed for context below

            if (resultItem != null) {
                // --- A result is available ---

                // Check if Sequence mode is OFF (Announce/Celebrate only run if Sequence is OFF)
                if (settings?.isSequenceEnabled == false) {
                    // Check if Announce is ON
                    if (settings.isAnnounceEnabled) {
                        displayAnnouncement(resultItem) // Call helper to show/animate announcement

                        // Check if Celebrate is ON (only possible if Announce is ON)
                        if (settings.isCelebrateEnabled) {
                            startCelebration() // Call helper to show/animate fireworks
                        }
                    } else {
                        // Announce is OFF, ensure UI is hidden
                         clearAnnounceCelebrate()
                    }
                } else {
                     // Sequence mode is ON, ensure Announce/Celebrate UI is hidden
                     clearAnnounceCelebrate()
                }

                // Clear the result signal in the ViewModel
                viewModel.clearSpinResult()

            } else {
                 // Result is null - potentially start spin animation (if enabled and NOT sequence)
                 if (spinEnabled && settings?.isSequenceEnabled == false) {
                    if (::binding.isInitialized) {
                        // Clear previous announce/celebrate before starting animation
                        clearAnnounceCelebrate()
                        binding.spinDialView.spin { resultFromView ->
                            viewModel.setSpinResult(resultFromView)
                        }
                    }
                }
                 // If spin disabled or sequence on, result is set directly by VM, handled by resultItem != null block
            }
        }
        
        // --- Sequence Observers ---
        viewModel.sequenceList.observe(this) { sequence ->
            updateSequenceVisibility() // Update visibility when list changes
            updateSequenceDisplay() // Update text views
        }

        viewModel.sequenceIndex.observe(this) { index ->
            updateSequenceDisplay() // Update text views when index changes
        }

    }

    // --- NEW: Helper Functions for Announce/Celebrate ---

    /** Displays the announcement UI for the given item */
    private fun displayAnnouncement(item: SpinItemEntity) {
        // TODO: Handle displaying Image/Emoji content appropriately
        binding.announcementTextView.text = item.content // Display text for now
        binding.announcementTextView.visibility = View.VISIBLE

        // --- Animation ---
        binding.announcementTextView.apply {
            // 1. Set initial state (small and transparent)
            scaleX = 0.2f // Start small
            scaleY = 0.2f // Start small
            alpha = 0f
            visibility = View.VISIBLE // Make it visible before animating

            // 2. Create animators for scale and fade
            val scaleXAnimator = ObjectAnimator.ofFloat(this, View.SCALE_X, 0.2f, 1.0f)
            val scaleYAnimator = ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.2f, 1.0f)
            val alphaAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1.0f)

            // 3. Combine animators in a set
            AnimatorSet().apply {
                playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
                duration = 500L // Adjust duration as needed (milliseconds)
                // Optional: Add an interpolator for effect (e.g., bounce)
                // interpolator = android.view.animation.OvershootInterpolator(1.5f)
                interpolator = AccelerateDecelerateInterpolator() // Standard smooth start/end
                start() // Run the animation
            }
        }
    }

    /** Starts the celebration (fireworks) animation */
    private fun startCelebration() {
        binding.fireworksContainer.visibility = View.VISIBLE
        // TODO: Implement actual fireworks animation (e.g., using a library or custom drawable animation)
        // Placeholder: Just show a color temporarily
        binding.fireworksContainer.setBackgroundColor(getColor(R.color.purple_200)) // Example color

        // Stop celebration after 3 seconds
        binding.fireworksContainer.postDelayed({
            stopCelebration()
        }, 3000) // 3 seconds
    }

    /** Hides the celebration UI */
    private fun stopCelebration() {
         // TODO: Stop actual fireworks animation
         binding.fireworksContainer.setBackgroundColor(Color.TRANSPARENT) // Reset placeholder
         binding.fireworksContainer.visibility = View.GONE
    }

    /** Hides both Announcement and Celebration UI */
    private fun clearAnnounceCelebrate() {
         binding.announcementTextView.animate().cancel() // Cancel any ongoing animation
         binding.announcementTextView.visibility = View.GONE
         binding.announcementTextView.text = ""
         // Reset properties modified by animation
         binding.announcementTextView.alpha = 1f
         binding.announcementTextView.scaleX = 1f
         binding.announcementTextView.scaleY = 1f
         stopCelebration()
    }

    /** Updates the TextViews in the sequence display based on current list and index */
    private fun updateSequenceDisplay() {
        val sequence = viewModel.sequenceList.value
        val index = viewModel.sequenceIndex.value ?: 0

        // Check if sequence is active and container is visible
        if (sequence == null || !binding.sequenceDisplayContainer.isVisible) {
            // Ensure all sequence text views and overlays are hidden if sequence not active
            sequenceTextViews.forEach { it.visibility = View.GONE }
            binding.fadeOverlayPrev4.visibility = View.GONE // Also hide overlays
            binding.fadeOverlayNext4.visibility = View.GONE
            // Also hide buttons if container isn't visible (optional, could be handled by container visibility)
            binding.buttonSequenceUp.visibility = View.GONE
            binding.buttonSequenceDown.visibility = View.GONE
            return
        }

        // Make sure buttons are visible if container is visible
        binding.buttonSequenceUp.visibility = View.VISIBLE
        binding.buttonSequenceDown.visibility = View.VISIBLE

        // Helper to safely get item text or empty string
        fun getItemContent(idx: Int): String {
            // Only return content if the item actually exists in the list
            return sequence.getOrNull(idx)?.content ?: ""
        }

        // --- Update Text and Visibility for each TextView ---

        // Current item
        binding.textSequenceCurrent.text = getItemContent(index)
        binding.textSequenceCurrent.visibility = View.VISIBLE // Always visible if sequence is active

        // Previous items
        binding.textSequencePrev1.text = getItemContent(index - 1)
        binding.textSequencePrev1.visibility = if (index > 0) View.VISIBLE else View.GONE
        binding.textSequencePrev2.text = getItemContent(index - 2)
        binding.textSequencePrev2.visibility = if (index > 1) View.VISIBLE else View.GONE
        binding.textSequencePrev3.text = getItemContent(index - 3)
        binding.textSequencePrev3.visibility = if (index > 2) View.VISIBLE else View.GONE
        binding.textSequencePrev4.text = getItemContent(index - 4)
        binding.textSequencePrev4.visibility = if (index > 3) View.VISIBLE else View.GONE

        // Next items
        binding.textSequenceNext1.text = getItemContent(index + 1)
        binding.textSequenceNext1.visibility = if (index < sequence.size - 1) View.VISIBLE else View.GONE
        binding.textSequenceNext2.text = getItemContent(index + 2)
        binding.textSequenceNext2.visibility = if (index < sequence.size - 2) View.VISIBLE else View.GONE
        binding.textSequenceNext3.text = getItemContent(index + 3)
        binding.textSequenceNext3.visibility = if (index < sequence.size - 3) View.VISIBLE else View.GONE
        binding.textSequenceNext4.text = getItemContent(index + 4)
        binding.textSequenceNext4.visibility = if (index < sequence.size - 4) View.VISIBLE else View.GONE


        // --- ADDED: Control Fade Overlay Visibility ---
        // Show top fade only if textSequencePrev4 is visible AND there's at least one more item *before* it (index > 4)
        binding.fadeOverlayPrev4.isVisible = (index > 4)

        // Show bottom fade only if textSequenceNext4 is visible AND there's at least one more item *after* it (index < sequence.size - 5)
        binding.fadeOverlayNext4.isVisible = (index < sequence.size - 5)
        // --- End of Added Code ---


        // Update button enabled state
        binding.buttonSequenceUp.isEnabled = index > 0
        binding.buttonSequenceDown.isEnabled = index < sequence.size - 1
    }

    /** Shows or hides the sequence display container based on settings and state */
    private fun updateSequenceVisibility() {
        val settings = viewModel.spinDialData.value?.settings
        val sequenceActive = viewModel.sequenceList.value != null

        binding.sequenceDisplayContainer.isVisible = settings?.isSequenceEnabled == true && sequenceActive
    }

    private fun animateDropdownOpen() {
        binding.listDropdownCardView.apply {
            // Prepare for animation
            visibility = View.VISIBLE
            alpha = 0f
            translationY = -height.toFloat() / 4 // Start slightly above final position

            // Create animators
            val alphaAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
            val translationYAnimator = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY, 0f)

            // Caret Rotation Animator (0 to 180 degrees)
            val caretAnimator = ObjectAnimator.ofFloat(binding.listTitleCaret, View.ROTATION, 0f, 180f)

            // Combine and run
            AnimatorSet().apply {
                playTogether(alphaAnimator, translationYAnimator, caretAnimator)
                duration = dropdownAnimationDuration
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun animateDropdownClose() {
        binding.listDropdownCardView.apply {
            // Create animators
            val alphaAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 0f)
            val translationYAnimator = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY, -height.toFloat() / 4)

            // Animate from current rotation back to 0 in case animation was interrupted
            val caretAnimator = ObjectAnimator.ofFloat(binding.listTitleCaret, View.ROTATION, binding.listTitleCaret.rotation, 0f)

            // Combine and run
            AnimatorSet().apply {
                playTogether(alphaAnimator, translationYAnimator, caretAnimator)
                duration = dropdownAnimationDuration
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Hide view after animation completes
                        visibility = View.GONE
                        // Reset translationY for next open animation
                        translationY = 0f
                        // Optional: Ensure rotation is exactly 0 if animation glitches
                        binding.listTitleCaret.rotation = 0f
                    }
                    // Reset rotation if animation is cancelled
                    override fun onAnimationCancel(animation: Animator) {
                        visibility = View.GONE
                        translationY = 0f
                        binding.listTitleCaret.rotation = 0f
                    }
                })
                start()
            }
        }
    }
    
    // TODO: Handle saving/restoring state beyond ViewModel (e.g., window position/size if needed)
    // TODO: Implement navigation to/from Settings
    // TODO: Implement Announce/Celebrate/Sequence features based on ViewModel state/settings
}
