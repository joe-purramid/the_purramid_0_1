// DiceMainFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes // Import annotation
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.purramid.thepurramid.R // Make sure R is imported
import com.example.purramid.thepurramid.databinding.FragmentDiceMainBinding
import com.example.purramid.thepurramid.databinding.ItemDieResultBinding // Import binding for the item layout
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceRollResults
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class DiceMainFragment : Fragment() {

    private var _binding: FragmentDiceMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiceViewModel by viewModels()

    // Keep track of the last displayed result type to handle clearing correctly
    private enum class LastResultType { NONE, POOL, PERCENTILE }
    private var lastResultType = LastResultType.NONE

    // --- Lifecycle Methods ---
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
        _binding = null
    }


    // --- Setup and Observation ---
    private fun setupListeners() {
        // Roll Button
        binding.diceRollButton.setOnClickListener {
            binding.diceDisplayArea.removeAllViews() // Clear old dice immediately
            viewModel.rollDice()
            // TODO: Trigger dice animation if enabled (part of Step 5.6)
        }

        // Close Button
        binding.diceCloseButton.setOnClickListener {
            activity?.finish() // Or findNavController().popBackStack()
        }

        // Settings Button
        binding.diceSettingsButton.setOnClickListener {
            viewModel.settings.value?.instanceId?.let { navigateToSettings(it) }
                ?: Log.e("DiceMainFragment", "Cannot navigate to settings: Instance ID not available")
        }

        // Dice Pool Button
        binding.dicePoolButton.setOnClickListener {
            viewModel.settings.value?.instanceId?.let { instanceId ->
                DicePoolDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DicePoolDialogFragment.TAG)
            } ?: Log.e("DiceMainFragment", "Cannot open Dice Pool: Instance ID not available")
        }

        // Reset Button
        binding.diceResetButton.setOnClickListener {
            // TODO: (Step 6) Call VM to reset graph data
            Toast.makeText(context, "Reset Graph (TODO)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        viewModel.dicePoolResults.observe(lifecycleOwner) { results ->
            if (lastResultType == LastResultType.POOL || lastResultType == LastResultType.NONE || results != null) {
                displayDicePoolResults(results)
                if (results != null) lastResultType = LastResultType.POOL
            }
        }

        viewModel.percentileResult.observe(lifecycleOwner) { result ->
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


    // --- Helper Functions ---

    /**
     * Returns the drawable resource ID for the specific die face based on sides and result.
     * Handles d6 pips naming convention (d6_result_*p).
     */
    @DrawableRes // Annotation to indicate this returns a drawable resource
    private fun getDieResultDrawable(sides: Int, rollValue: Int): Int {
        // Retrieve the setting for using pips. Default to false if settings are null.
        val usePipsForD6 = viewModel.settings.value?.useDicePips ?: false

        // Construct the resource name based on die type and result
        val resourceName = when (sides) {
            4 -> "d4_result_$rollValue"
            6 -> {
                // Check useDicePips setting for d6
                if (usePipsForD6) {
                    "d6_result_${rollValue}p" // Use pip naming convention
                } else {
                    "d6_result_$rollValue" // Use standard number naming convention
                }
            }
            8 -> "d8_result_$rollValue"
            10 -> "d10_result_$rollValue"
            DicePoolDialogFragment.D10_TENS_KEY -> "d10p_result_$rollValue" // Specific prefix for 00-90
            DicePoolDialogFragment.D10_UNITS_KEY -> "d10_result_$rollValue" // Standard d10 for 0-9
            12 -> "d12_result_$rollValue"
            20 -> "d20_result_$rollValue"
            else -> "ic_die_placeholder" // Fallback resource name
        }

        // Use getResources().getIdentifier to find the drawable ID by name
        // This is less performant than direct R.drawable references but necessary for dynamic names.
        val resourceId = try {
            // Ensure context is not null before accessing resources
            context?.resources?.getIdentifier(resourceName, "drawable", requireContext().packageName) ?: 0
        } catch (e: Exception) {
            Log.e("DiceMainFragment", "Error finding resource ID for $resourceName", e)
            0 // Return 0 if context is somehow unavailable or other error
        }

        // Return the found ID or a placeholder if not found (resourceId will be 0 if not found)
        // Ensure you have ic_die_placeholder.xml in your drawables
        return if (resourceId != 0) resourceId else R.drawable.ic_die_placeholder
    }

    // --- Display Logic ---

    /** Displays the results of a standard dice pool roll */
    private fun displayDicePoolResults(results: DiceRollResults?) {
        // binding.diceDisplayArea.removeAllViews() // Moved to Roll button listener

        if (results == null) return
        if (results.isEmpty() && lastResultType == LastResultType.POOL) { // Only show if this was the intended result type
            // Display "empty pool" message (optional)
            val textView = TextView(requireContext()).apply {
                text = getString(R.string.dice_no_dice_in_pool)
                // Add layout params, padding etc.
            }
            binding.diceDisplayArea.addView(textView)
            return
        }

        val inflater = LayoutInflater.from(context)

        // Sort results by die type (d4, d6, d8...) for grouping
        results.entries.sortedBy { it.key }.forEach { (sides, rolls) ->
            rolls.forEach { rollValue ->
                // Inflate the simplified item layout for each die roll
                val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)

                // Get the specific drawable resource ID using the helper
                val drawableResId = getDieResultDrawable(sides, rollValue)
                itemBinding.dieResultImageView.setImageResource(drawableResId)

                // TODO: Apply die color tint from settings if needed

                // Add the inflated view to the FlexboxLayout
                binding.diceDisplayArea.addView(itemBinding.root)
            }
        }
    }

    /** Displays the result of a percentile roll */
    private fun displayPercentileResult(result: Int?) {
        // binding.diceDisplayArea.removeAllViews() // Moved to Roll button listener

        if (result == null) return

        val inflater = LayoutInflater.from(context)

        // Calculate the contributing dice values
        val tensValue = if (result == 100) 0 else (result / 10) * 10
        val unitsValue = if (result == 100) 0 else result % 10

        // Inflate and set the Tens Die (d10p)
        val tensBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
        val tensDrawableResId = getDieResultDrawable(DicePoolDialogFragment.D10_TENS_KEY, tensValue)
        tensBinding.dieResultImageView.setImageResource(tensDrawableResId)
        // TODO: Apply die color tint from settings
        binding.diceDisplayArea.addView(tensBinding.root)

        // Inflate and set the Units Die (d10)
        val unitsBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
        val unitsDrawableResId = getDieResultDrawable(DicePoolDialogFragment.D10_UNITS_KEY, unitsValue)
        unitsBinding.dieResultImageView.setImageResource(unitsDrawableResId)
        // TODO: Apply die color tint from settings
        binding.diceDisplayArea.addView(unitsBinding.root)

        // Optional: Add a TextView showing the total result as well
        val resultTextView = TextView(requireContext()).apply {
            text = "= $result%"
            textSize = 24f // Adjust size
            val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginStart = 16
            layoutParams = params
        }
        binding.diceDisplayArea.addView(resultTextView)
    }


    // --- Navigation & Error Handling ---
    private fun navigateToSettings(instanceId: UUID) {
        try {
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
