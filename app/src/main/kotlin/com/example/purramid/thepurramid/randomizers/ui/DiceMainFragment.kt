// DiceMainFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentDiceMainBinding
import com.example.purramid.thepurramid.databinding.ItemDieResultBinding
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceRollResults
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import kotlin.random.Random

@AndroidEntryPoint
class DiceMainFragment : Fragment() {

    private var _binding: FragmentDiceMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiceViewModel by viewModels()

    private enum class LastResultType { NONE, POOL, PERCENTILE }
    private var lastResultType = LastResultType.NONE

    private val animationDuration = 1000L // 1 second

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
        binding.diceRollButton.setOnClickListener {
            handleRoll()
        }
        // ... other listeners (close, settings, dice pool, reset) ...
        binding.diceCloseButton.setOnClickListener { activity?.finish() }

        binding.diceSettingsButton.setOnClickListener {
            viewModel.settings.value?.instanceId?.let { navigateToSettings(it) }
                ?: Log.e("DiceMainFragment", "Cannot navigate to settings: Instance ID not available")
        }

        binding.dicePoolButton.setOnClickListener {
            viewModel.settings.value?.instanceId?.let { instanceId ->
                DicePoolDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DicePoolDialogFragment.TAG)
            } ?: Log.e("DiceMainFragment", "Cannot open Dice Pool: Instance ID not available")
        }

        binding.diceResetButton.setOnClickListener {
            Toast.makeText(context, "Reset Graph (TODO)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        viewModel.dicePoolResults.observe(lifecycleOwner) { results ->
            if (viewModel.settings.value?.isDiceAnimationEnabled == false || binding.diceDisplayArea.childCount == 0) {
                // If animation is off, or if this is an update without a roll (e.g. config change), display immediately
                displayDicePoolResults(results)
                if (results != null) lastResultType = LastResultType.POOL
            }
            // If animation is on, results will be displayed by the animation's onEnd listener
        }

        viewModel.percentileResult.observe(lifecycleOwner) { result ->
            if (viewModel.settings.value?.isDiceAnimationEnabled == false || binding.diceDisplayArea.childCount == 0) {
                displayPercentileResult(result)
                if (result != null) lastResultType = LastResultType.PERCENTILE
            }
            // If animation is on, results will be displayed by the animation's onEnd listener
        }
        // ... observe settings and errorEvent ...
        viewModel.settings.observe(lifecycleOwner) { settings ->
            if (settings != null) {
                // Update UI elements based on loaded settings if needed
                // For example, enable/disable roll button based on pool emptiness
                binding.diceRollButton.isEnabled = !(settings.isPercentileDiceEnabled == false &&
                        (viewModel.parseDicePoolConfig(settings.dicePoolConfigJson)?.values?.sum() ?: 0) == 0)
            }
        }

        viewModel.errorEvent.observe(lifecycleOwner) { event ->
            event?.getContentIfNotHandled()?.let { errorMessage ->
                showErrorSnackbar(errorMessage)
            }
        }
    }

    private fun handleRoll() {
        val currentSettings = viewModel.settings.value
        val isAnimationEnabled = currentSettings?.isDiceAnimationEnabled ?: false

        binding.diceDisplayArea.removeAllViews() // Clear previous dice before animation/roll

        if (isAnimationEnabled) {
            // Create placeholder views for animation
            val tempDiceViews = createTemporaryDiceViewsForAnimation(currentSettings)
            tempDiceViews.forEach { binding.diceDisplayArea.addView(it) }

            // Animate these temporary views
            animateDiceViews(tempDiceViews) {
                // Animation ended, now fetch and display actual results
                viewModel.rollDice() // This will trigger LiveData observers
            }
        } else {
            // No animation, roll and display immediately
            viewModel.rollDice()
        }
    }

    /** Creates temporary ImageViews with placeholder/random dice for animation */
    private fun createTemporaryDiceViewsForAnimation(settings: SpinSettingsEntity?): List<View> {
        val tempViews = mutableListOf<View>()
        val inflater = LayoutInflater.from(context)

        if (settings?.isPercentileDiceEnabled == true) {
            // Create two d10 placeholders for percentile
            repeat(2) {
                val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
                // Set a random d10 face or a generic "rolling" SVG
                itemBinding.dieResultImageView.setImageResource(getDieResultDrawable(10, Random.nextInt(1, 11)))
                tempViews.add(itemBinding.root)
            }
        } else {
            val poolConfig = viewModel.parseDicePoolConfig(settings?.dicePoolConfigJson)
            poolConfig?.forEach { (sides, count) ->
                repeat(count) {
                    val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
                    // Set a random face of the correct type or a generic "rolling" SVG
                    itemBinding.dieResultImageView.setImageResource(getDieResultDrawable(sides, Random.nextInt(1, sides + 1)))
                    tempViews.add(itemBinding.root)
                }
            }
        }
        return tempViews
    }


    private fun animateDiceViews(diceViews: List<View>, onAnimationEndAction: () -> Unit) {
        if (diceViews.isEmpty()) {
            onAnimationEndAction()
            return
        }

        var animationsPending = diceViews.size

        diceViews.forEach { dieView ->
            // Simple rotation and fade out/in example
            dieView.alpha = 0f // Start faded out
            val rotation = ObjectAnimator.ofFloat(dieView, "rotation", 0f, 360f * 2) // Two full spins
            rotation.duration = animationDuration
            rotation.interpolator = AccelerateDecelerateInterpolator()

            val fadeIn = ObjectAnimator.ofFloat(dieView, "alpha", 0f, 1f)
            fadeIn.duration = animationDuration / 4 // Fade in quickly

            val fadeOut = ObjectAnimator.ofFloat(dieView, "alpha", 1f, 0f)
            fadeOut.startDelay = animationDuration * 3 / 4 // Start fading out towards the end
            fadeOut.duration = animationDuration / 4

            // For "cycling" effect (more complex, requires rapidly changing SVG sources)
            // This is a conceptual placeholder for the cycling effect.
            // A ValueAnimator could be used to change dieView.setImageResource() at intervals.
            val cycleAnimator = ValueAnimator.ofInt(0, 10).apply { // Cycle 10 times
                duration = animationDuration
                addUpdateListener {
                    // In a real implementation, change the SVG source of dieView (ImageView)
                    // to a different random face. This is tricky with current SVG setup.
                    // For now, this just makes the rotation happen.
                }
            }

            rotation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animationsPending--
                    if (animationsPending == 0) {
                        // All animations finished
                        onAnimationEndAction()
                    }
                }
            })
            // Start rotation and initial fade in. Cycling would be part of this.
            rotation.start()
            fadeIn.start()
            // Fade out is handled by its own startDelay.
            // fadeOut.start() // Not starting fadeOut here, let rotation finish mostly visible
        }
    }


    // --- Helper Functions ---
    @DrawableRes
    private fun getDieResultDrawable(sides: Int, rollValue: Int): Int {
        val usePipsForD6 = viewModel.settings.value?.useDicePips ?: false
        val resourceName = when (sides) {
            4 -> "d4_result_$rollValue"
            6 -> if (usePipsForD6) "d6_result_${rollValue}p" else "d6_result_$rollValue"
            8 -> "d8_result_$rollValue"
            10 -> "d10_result_$rollValue"
            DicePoolDialogFragment.D10_TENS_KEY -> "d10p_result_$rollValue"
            DicePoolDialogFragment.D10_UNITS_KEY -> "d10_result_$rollValue"
            12 -> "d12_result_$rollValue"
            20 -> "d20_result_$rollValue"
            else -> "ic_die_placeholder"
        }
        val resourceId = try {
            context?.resources?.getIdentifier(resourceName, "drawable", requireContext().packageName) ?: 0
        } catch (e: Exception) {
            Log.e("DiceMainFragment", "Error finding resource ID for $resourceName", e)
            0
        }
        return if (resourceId != 0) resourceId else R.drawable.ic_die_placeholder
    }

    // --- Display Logic ---
    private fun displayDicePoolResults(results: DiceRollResults?) {
        // This function is now primarily called AFTER animation, or if animation is off
        // The animation function will handle clearing and adding initial views
        if (viewModel.settings.value?.isDiceAnimationEnabled == true && binding.diceDisplayArea.childCount > 0) {
            // If animation was enabled and views are present, update them instead of removing/re-adding
            updateAnimatedViewsWithResults(results)
            return
        }

        binding.diceDisplayArea.removeAllViews()
        if (results == null) return
        if (results.isEmpty() && lastResultType == LastResultType.POOL) {
            val textView = TextView(requireContext()).apply { text = getString(R.string.dice_no_dice_in_pool) }
            binding.diceDisplayArea.addView(textView)
            return
        }

        val inflater = LayoutInflater.from(context)
        results.entries.sortedBy { it.key }.forEach { (sides, rolls) ->
            rolls.forEach { rollValue ->
                val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
                val drawableResId = getDieResultDrawable(sides, rollValue)
                itemBinding.dieResultImageView.setImageResource(drawableResId)
                // TODO: Apply die color tint
                binding.diceDisplayArea.addView(itemBinding.root)
            }
        }
    }

    private fun displayPercentileResult(result: Int?) {
        // Similar logic as displayDicePoolResults for when to update vs removeAllViews
        if (viewModel.settings.value?.isDiceAnimationEnabled == true && binding.diceDisplayArea.childCount > 0) {
            updateAnimatedViewsWithPercentileResult(result)
            return
        }

        binding.diceDisplayArea.removeAllViews()
        if (result == null) return

        val inflater = LayoutInflater.from(context)
        val tensValue = if (result == 100) 0 else (result / 10) * 10
        val unitsValue = if (result == 100) 0 else result % 10

        val tensBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
        tensBinding.dieResultImageView.setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_TENS_KEY, tensValue))
        binding.diceDisplayArea.addView(tensBinding.root)

        val unitsBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
        unitsBinding.dieResultImageView.setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_UNITS_KEY, unitsValue))
        binding.diceDisplayArea.addView(unitsBinding.root)

        val resultTextView = TextView(requireContext()).apply {
            text = "= $result%"
            textSize = 24f
            val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginStart = 16
            layoutParams = params
        }
        binding.diceDisplayArea.addView(resultTextView)
    }

    /** Updates the already animated views with their final results (Pool) */
    private fun updateAnimatedViewsWithResults(results: DiceRollResults?) {
        if (results == null) {
            binding.diceDisplayArea.removeAllViews() // Or show error/empty
            return
        }
        var viewIndex = 0
        results.entries.sortedBy { it.key }.forEach { (sides, rolls) ->
            rolls.forEach { rollValue ->
                if (viewIndex < binding.diceDisplayArea.childCount) {
                    val itemView = binding.diceDisplayArea.getChildAt(viewIndex)
                    // Assuming itemView is the root of item_die_result.xml (FrameLayout or ImageView directly)
                    val imageView = itemView.findViewById<ImageView>(R.id.dieResultImageView) // Find by ID
                    imageView?.setImageResource(getDieResultDrawable(sides, rollValue))
                    imageView?.alpha = 1f // Ensure it's visible
                    imageView?.rotation = 0f // Reset rotation
                }
                viewIndex++
            }
        }
        // Remove any extra temporary views if the number of dice changed
        while (binding.diceDisplayArea.childCount > viewIndex) {
            binding.diceDisplayArea.removeViewAt(viewIndex)
        }
    }

    /** Updates the already animated views with their final results (Percentile) */
    private fun updateAnimatedViewsWithPercentileResult(result: Int?) {
        if (result == null) {
            binding.diceDisplayArea.removeAllViews()
            return
        }
        // Assuming 2 views for percentile + 1 TextView for result
        if (binding.diceDisplayArea.childCount >= 2) {
            val tensValue = if (result == 100) 0 else (result / 10) * 10
            val unitsValue = if (result == 100) 0 else result % 10

            (binding.diceDisplayArea.getChildAt(0)?.findViewById<ImageView>(R.id.dieResultImageView))?.apply {
                setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_TENS_KEY, tensValue))
                alpha = 1f; rotation = 0f
            }
            (binding.diceDisplayArea.getChildAt(1)?.findViewById<ImageView>(R.id.dieResultImageView))?.apply {
                setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_UNITS_KEY, unitsValue))
                alpha = 1f; rotation = 0f
            }
            if (binding.diceDisplayArea.childCount == 3 && binding.diceDisplayArea.getChildAt(2) is TextView) {
                (binding.diceDisplayArea.getChildAt(2) as TextView).text = "= $result%"
            } else if (binding.diceDisplayArea.childCount == 2) { // Add text view if missing
                val resultTextView = TextView(requireContext()).apply {
                    text = "= $result%"
                    textSize = 24f
                    val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    params.marginStart = 16
                    layoutParams = params
                }
                binding.diceDisplayArea.addView(resultTextView)
            }
        }
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
