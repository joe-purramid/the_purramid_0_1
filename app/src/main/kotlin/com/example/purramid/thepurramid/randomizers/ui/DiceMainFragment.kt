// DiceMainFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // Or use Snackbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentDiceMainBinding
import com.example.purramid.thepurramid.databinding.ItemDieResultBinding
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceRollResults
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class DiceMainFragment : Fragment() {

    private var _binding: FragmentDiceMainBinding? = null
    private val binding get() = _binding!! // Use !! only between onCreateView and onDestroyView

    // Get the ViewModel scoped to this Fragment
    private val viewModel: DiceViewModel by viewModels()

    // Use navArgs to potentially get instanceId if needed for navigating TO settings
    // The ViewModel gets the ID from SavedStateHandle for its own use.
    // private val args: DiceMainFragmentArgs by navArgs() // Assuming args defined in nav graph

    // Keep track of the last displayed result type to handle clearing correctly
    private enum class LastResultType { NONE, POOL, PERCENTILE }
    private var lastResultType = LastResultType.NONE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiceMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    private fun setupListeners() {
        // Roll Button
        binding.diceRollButton.setOnClickListener {
            // TODO: Clear previous results visually before starting animation/roll
            binding.diceDisplayArea.removeAllViews() // Clear old dice immediately
            viewModel.rollDice()
            // TODO: Trigger dice animation if enabled
        }

        // Close Button
        binding.diceCloseButton.setOnClickListener {
            activity?.finish() // Or findNavController().popBackStack() if part of larger flow
        }

        // Settings Button
        binding.diceSettingsButton.setOnClickListener {
             viewModel.settings.value?.instanceId?.let { navigateToSettings(it) }
                 ?: Log.e("DiceMainFragment", "Cannot navigate to settings: Instance ID not available")
        }

        // Dice Pool Button - Mark action as TODO
        binding.dicePoolButton.setOnClickListener {
            viewModel.settings.value?.instanceId?.let { instanceId ->
                DicePoolDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DicePoolDialogFragment.TAG)
            } ?: Log.e("DiceMainFragment", "Cannot open Dice Pool: Instance ID not available")
        }

        // Reset Button - Mark action as TODO and ensure initial visibility is GONE
        binding.diceResetButton.setOnClickListener {
            // TODO: (Step 6) Call VM to reset graph data
            Toast.makeText(context, "Reset Graph (TODO)", Toast.LENGTH_SHORT).show()
        }
        // binding.diceResetButton.visibility = View.GONE // Hide initially
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        viewModel.dicePoolResults.observe(lifecycleOwner) { results ->
            // Only update if the last roll was a pool roll or nothing
            if (lastResultType == LastResultType.POOL || lastResultType == LastResultType.NONE || results != null) {
                displayDicePoolResults(results)
                if (results != null) lastResultType = LastResultType.POOL
            }
        }

        viewModel.percentileResult.observe(lifecycleOwner) { result ->
            // Only update if the last roll was a percentile roll or nothing
            if (lastResultType == LastResultType.PERCENTILE || lastResultType == LastResultType.NONE || result != null) {
                displayPercentileResult(result)
                if (result != null) lastResultType = LastResultType.PERCENTILE
            }
        }

        viewModel.settings.observe(lifecycleOwner) { settings ->
            if (settings != null) {
                // TODO: Update UI elements based on loaded settings if needed
            }
        }

        viewModel.errorEvent.observe(lifecycleOwner) { event ->
            event?.getContentIfNotHandled()?.let { errorMessage ->
                showErrorSnackbar(errorMessage)
            }
        }
    }

    /** Displays the results of a standard dice pool roll */
    private fun displayDicePoolResults(results: DiceRollResults?) {
        // Clear previous results before adding new ones
        binding.diceDisplayArea.removeAllViews()

        if (results == null) {
            return // Don't display anything if null
        }
        if (results.isEmpty()) {
            // Optionally display a message if the pool was empty
            val inflater = LayoutInflater.from(context)
            val textView = TextView(requireContext()).apply { // Create a TextView programmatically
                text = getString(R.string.dice_no_dice_in_pool)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                // Add padding, set appearance etc. if needed
            }
            binding.diceDisplayArea.addView(textView)
            return
        }

        val inflater = LayoutInflater.from(context)
        val usePips = viewModel.settings.value?.useDicePips ?: false // Check setting

        // Sort results by die type (d4, d6, d8...) for grouping
        results.entries.sortedBy { it.key }.forEach { (sides, rolls) ->
            rolls.forEach { rollValue ->
                // Inflate the item layout for each die roll
                val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)

                // Set the die background SVG
                val backgroundResId = getDieBackgroundResource(sides) // Helper function needed
                itemBinding.dieBackgroundImageView.setImageResource(backgroundResId)
                // TODO: Apply die color from settings if implemented

                // Set the result text/pips
                if (sides == 6 && usePips) {
                    itemBinding.dieResultTextView.text = getPipString(rollValue) // Helper needed
                    // Adjust text size/style for pips if necessary
                } else {
                    itemBinding.dieResultTextView.text = rollValue.toString()
                }
                // TODO: Apply text color based on die color/contrast

                // Add the inflated view to the FlexboxLayout
                binding.diceDisplayArea.addView(itemBinding.root)
            }
        }
    }

    /** Displays the result of a percentile roll */
    private fun displayPercentileResult(result: Int?) {
        // Clear previous results before adding new ones
        binding.diceDisplayArea.removeAllViews()

        if (result == null) {
            return // Don't display anything if null
        }

        // Display as simple text for now, could inflate a custom view later
        val inflater = LayoutInflater.from(context)
        val textView = TextView(requireContext()).apply {
            text = "$result%"
            textSize = 32f // Make percentile result prominent
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Add padding etc.
        }
        binding.diceDisplayArea.addView(textView)
    }

    // --- Helper Functions (TODO: Implement these) ---

    /** Returns the drawable resource ID for the die background based on sides */
    private fun getDieBackgroundResource(sides: Int): Int {
        // TODO: Implement logic to return R.drawable.ic_die_d4, R.drawable.ic_die_d6, etc.
        // Need to create these SVG drawables first.
        return when (sides) {
            4 -> R.drawable.ic_die_placeholder // Replace with actual drawables
            6 -> R.drawable.ic_die_placeholder
            8 -> R.drawable.ic_die_placeholder
            10 -> R.drawable.ic_die_placeholder
            12 -> R.drawable.ic_die_placeholder
            20 -> R.drawable.ic_die_placeholder
            else -> R.drawable.ic_die_placeholder // Fallback
        }
    }

    /** Returns a string representation of pips for a d6 roll */
    private fun getPipString(value: Int): String {
        // TODO: Implement logic to return pip characters (e.g., using Unicode dots like •)
        return when (value) {
            1 -> "•"
            2 -> "• •" // Arrange spatially if needed with custom view/font
            3 -> "• • •"
            4 -> "::" // Example using different chars
            5 -> ":•:"
            6 -> ":::"
            else -> value.toString() // Fallback
        }
        // Note: Proper pip layout often requires a custom view or specific font.
    }

    // --- Navigation & Error Handling ---

    private fun navigateToSettings(instanceId: UUID) {
        try {
            // Use the Safe Args generated action class if available
            // Ensure the action and argument name match your nav graph
             val action = DiceMainFragmentDirections.actionDiceMainFragmentToRandomizerSettingsFragment(instanceId.toString())
             findNavController().navigate(action)
        } catch (e: Exception) {
             Log.e("DiceMainFragment", "Navigation to Settings failed.", e)
             showErrorSnackbar(getString(R.string.cannot_open_settings))
        }
    }

    private fun showErrorSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }
}