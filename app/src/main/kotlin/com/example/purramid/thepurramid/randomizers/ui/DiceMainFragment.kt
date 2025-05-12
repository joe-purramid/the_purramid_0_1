// DiceMainFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context // For accessibility service
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityManager // For reduced motion check
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible // Import for isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.ui.PurramidPalette
import com.example.purramid.thepurramid.data.db.DEFAULT_EMPTY_JSON_MAP
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity // For settings access
import com.example.purramid.thepurramid.databinding.FragmentDiceMainBinding
import com.example.purramid.thepurramid.databinding.ItemDieResultBinding
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceRollResults
import com.example.purramid.thepurramid.randomizers.viewmodel.DiceViewModel
import com.example.purramid.thepurramid.randomizers.viewmodel.ProcessedDiceResult
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.concurrent.TimeUnit
import java.util.UUID
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

@AndroidEntryPoint
class DiceMainFragment : Fragment() {

    private var _binding: FragmentDiceMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiceViewModel by viewModels()
    private val gson = Gson() // For parsing color config

    private enum class LastResultType { NONE, POOL, PERCENTILE }
    private var lastResultType = LastResultType.NONE
    private val animationDuration = 1000L
    private val announcementDisplayDuration = 3000L

    private val announcementHandler = Handler(Looper.getMainLooper())
    private var announcementRunnable: Runnable? = null

    private var _binding: FragmentDiceMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiceViewModel by viewModels()
    private val gson = Gson()

    private enum class LastResultType { NONE, POOL, PERCENTILE }
    private var lastResultType = LastResultType.NONE
    private val animationDuration = 1000L
    private val announcementDisplayDuration = 3000L

    private val announcementHandler = Handler(Looper.getMainLooper())
    private var announcementRunnable: Runnable? = null

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
        announcementRunnable?.let { announcementHandler.removeCallbacks(it) } // Clear pending hide
        _binding = null
    }

    private fun setupListeners() {
        binding.diceRollButton.setOnClickListener {
            clearAnnouncement() // Clear any previous announcement
            handleRoll()
        }
        binding.diceCloseButton.setOnClickListener {
            viewModel.handleManualClose() // Use the ViewModel's close logic
            activity?.finish()
        }
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
        // Allow tapping announcement to dismiss it
        binding.diceAnnouncementTextView.setOnClickListener {
            clearAnnouncement()
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rawDicePoolResults.observe(lifecycleOwner) { rawResults ->
                        if (viewModel.settings.value?.isDiceAnimationEnabled == false ||
                            (lastResultType == LastResultType.POOL || lastResultType == LastResultType.NONE || rawResults != null)) {
                            if (viewModel.processedDiceResult.value?.isPercentile == false) {
                                displayDicePoolResults(rawResults)
                                if (rawResults != null) lastResultType = LastResultType.POOL
                            }
                        }
                    }
                }
                launch {
                    viewModel.rawPercentileResultComponents.observe(lifecycleOwner) { rawPercentileComponents ->
                        if (viewModel.settings.value?.isDiceAnimationEnabled == false ||
                            (lastResultType == LastResultType.PERCENTILE || lastResultType == LastResultType.NONE || rawPercentileComponents != null)) {
                            if (viewModel.processedDiceResult.value?.isPercentile == true) {
                                displayPercentileResult(viewModel.processedDiceResult.value)
                                if (rawPercentileComponents != null) lastResultType = LastResultType.PERCENTILE
                            }
                        }
                    }
                }
                launch {
                    viewModel.settings.observe(lifecycleOwner) { settings ->
                        if (settings != null) {
                            binding.diceRollButton.isEnabled = !(settings.isPercentileDiceEnabled == false &&
                                    (viewModel.parseDicePoolConfig(settings.dicePoolConfigJson)?.values?.sum()
                                        ?: 0) == 0)
                            // If settings change and announcement is now off, clear it
                            if (!settings.isAnnounceEnabled) {
                                clearAnnouncement()
                            }
                            // If crit celebration is turned off, ensure no confetti is running (more robust)
                            if (!settings.isDiceCritCelebrationEnabled) {
                                binding.konfettiViewDice.stopGracefully()
                            }
                        }
                    }
                }
                launch {
                    viewModel.processedDiceResult.observe(lifecycleOwner) { processedResult ->
                        val currentSettings = viewModel.settings.value
                        if (processedResult != null && currentSettings != null) {
                            if (currentSettings.isAnnounceEnabled) {
                                showAnnouncement(processedResult.announcementString)
                                // *** TRIGGER CRIT CELEBRATION ***
                                if (currentSettings.isDiceCritCelebrationEnabled && processedResult.d20CritsRolled > 0) {
                                    startCritCelebration()
                                }
                            } else {
                                clearAnnouncement()
                            }
                        } else {
                            clearAnnouncement()
                        }
                    }
                }
                launch {
                    viewModel.errorEvent.observe(lifecycleOwner) { event ->
                        event?.getContentIfNotHandled()?.let { errorMessage ->
                            showErrorSnackbar(errorMessage)
                        }
                    }
                }
            }
        }
    }

    private fun handleRoll() {
        val currentSettings = viewModel.settings.value
        val isAnimationEnabled = currentSettings?.isDiceAnimationEnabled ?: false
        clearAnnouncement() // Clear announcement before roll
        binding.diceDisplayArea.removeAllViews()

        if (isAnimationEnabled) {
            val tempDiceViews = createTemporaryDiceViewsForAnimation(currentSettings)
            tempDiceViews.forEach { binding.diceDisplayArea.addView(it) }
            animateDiceViews(tempDiceViews) {
                viewModel.rollDice()
            }
        } else {
            viewModel.rollDice()
        }
    }

    // --- Announcement Logic ---
    private fun showAnnouncement(message: String) {
        announcementRunnable?.let { announcementHandler.removeCallbacks(it) } // Remove previous hide callback

        binding.diceAnnouncementTextView.text = message
        if (!binding.diceAnnouncementTextView.isVisible) {
            binding.diceAnnouncementTextView.alpha = 0f
            binding.diceAnnouncementTextView.visibility = View.VISIBLE
            binding.diceAnnouncementTextView.animate()
                .alpha(1f)
                .setDuration(300) // Short fade-in
                .setListener(null)
        }

        // Auto-hide after a delay
        announcementRunnable = Runnable { clearAnnouncement() }
        announcementHandler.postDelayed(announcementRunnable!!, announcementDisplayDuration)
    }

    private fun clearAnnouncement() {
        announcementRunnable?.let { announcementHandler.removeCallbacks(it) }
        announcementRunnable = null
        if (binding.diceAnnouncementTextView.isVisible) {
            binding.diceAnnouncementTextView.animate()
                .alpha(0f)
                .setDuration(300) // Short fade-out
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (_binding != null) { // Check binding still valid
                            binding.diceAnnouncementTextView.visibility = View.GONE
                            binding.diceAnnouncementTextView.text = "" // Clear text
                        }
                    }
                })
        }
    }

    private fun createTemporaryDiceViewsForAnimation(settings: SpinSettingsEntity?): List<View> {
        val tempViews = mutableListOf<View>()
        val inflater = LayoutInflater.from(context)
        val colorConfig = parseDiceColorConfig(settings?.diceColorConfigJson)

        if (settings?.isPercentileDiceEnabled == true) {
            repeat(2) { index ->
                val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
                val dieTypeKey = if (index == 0) DicePoolDialogFragment.D10_TENS_KEY else DicePoolDialogFragment.D10_UNITS_KEY
                itemBinding.dieResultImageView.setImageResource(getDieResultDrawable(dieTypeKey, Random.nextInt(0, 10))) // Show a random 0-9 face
                applyTint(itemBinding.dieResultImageView, colorConfig[dieTypeKey])
                tempViews.add(itemBinding.root)
            }
        } else {
            val poolConfig = viewModel.parseDicePoolConfig(settings?.dicePoolConfigJson)
            poolConfig?.forEach { (sides, count) ->
                repeat(count) {
                    val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
                    itemBinding.dieResultImageView.setImageResource(getDieResultDrawable(sides, Random.nextInt(1, sides + 1)))
                    applyTint(itemBinding.dieResultImageView, colorConfig[sides])
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
            dieView.alpha = 0f
            val rotation = ObjectAnimator.ofFloat(dieView, "rotation", 0f, 360f * (Random.nextInt(2, 5))) // Random rotations
            rotation.duration = animationDuration
            rotation.interpolator = AccelerateDecelerateInterpolator()
            val fadeIn = ObjectAnimator.ofFloat(dieView, "alpha", 0f, 1f)
            fadeIn.duration = animationDuration / 4
            rotation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animationsPending--
                    if (animationsPending == 0) {
                        onAnimationEndAction()
                    }
                }
            })
            rotation.start()
            fadeIn.start()
        }
    }

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

    private fun displayDicePoolResults(results: DiceRollResults?) {
        if (viewModel.settings.value?.isDiceAnimationEnabled == true && binding.diceDisplayArea.childCount > 0) {
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
        val colorConfig = parseDiceColorConfig(viewModel.settings.value?.diceColorConfigJson)
        results.entries.sortedBy { it.key }.forEach { (sides, rolls) ->
            rolls.forEach { rollValue ->
                val itemBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
                itemBinding.dieResultImageView.setImageResource(getDieResultDrawable(sides, rollValue))
                applyTint(itemBinding.dieResultImageView, colorConfig[sides])
                binding.diceDisplayArea.addView(itemBinding.root)
            }
        }
    }

    private fun displayPercentileResult(processedResult: ProcessedDiceResult?) {
        if (viewModel.settings.value?.isDiceAnimationEnabled == true && binding.diceDisplayArea.childCount > 0 && lastResultType == LastResultType.PERCENTILE) {
            updateAnimatedViewsWithPercentileResult(processedResult?.percentileValue) // Pass the final sum
            return
        }
        binding.diceDisplayArea.removeAllViews()
        if (processedResult?.isPercentile != true || processedResult.percentileValue == null) return

        val finalSum = processedResult.percentileValue
        val componentRolls = processedResult.rawRolls // Should contain D10_TENS_KEY and D10_UNITS_KEY

        val tensValueForDisplay = processedResult.rawRolls?.get(DicePoolDialogFragment.D10_TENS_KEY)?.firstOrNull() ?: 0
        val unitsValueForDisplay = processedResult.rawRolls?.get(DicePoolDialogFragment.D10_UNITS_KEY)?.firstOrNull() ?: 0

        val inflater = LayoutInflater.from(context)
        val colorConfig = parseDiceColorConfig(viewModel.settings.value?.diceColorConfigJson)

        // Tens Die (d10p)
        val tensBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
        tensBinding.dieResultImageView.setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_TENS_KEY, tensValueForDisplay))
        applyTint(tensBinding.dieResultImageView, colorConfig[DicePoolDialogFragment.D10_TENS_KEY])
        binding.diceDisplayArea.addView(tensBinding.root)

        // Units Die (d10)
        val unitsBinding = ItemDieResultBinding.inflate(inflater, binding.diceDisplayArea, false)
        unitsBinding.dieResultImageView.setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_UNITS_KEY, unitsValueForDisplay))
        applyTint(unitsBinding.dieResultImageView, colorConfig[DicePoolDialogFragment.D10_UNITS_KEY])
        binding.diceDisplayArea.addView(unitsBinding.root)

        val resultTextView = TextView(requireContext()).apply {
            text = "= $finalSum%" // Display the final (potentially modified) sum
            textSize = 24f
            val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.marginStart = 16
            layoutParams = params
        }
        binding.diceDisplayArea.addView(resultTextView)
    }

    private fun updateAnimatedViewsWithResults(results: DiceRollResults?) {
        if (results == null) {
            binding.diceDisplayArea.removeAllViews()
            return
        }
        var viewIndex = 0
        val colorConfig = parseDiceColorConfig(viewModel.settings.value?.diceColorConfigJson)
        results.entries.sortedBy { it.key }.forEach { (sides, rolls) ->
            rolls.forEach { rollValue ->
                if (viewIndex < binding.diceDisplayArea.childCount) {
                    val itemView = binding.diceDisplayArea.getChildAt(viewIndex)
                    val imageView = itemView.findViewById<ImageView>(R.id.dieResultImageView)
                    imageView?.setImageResource(getDieResultDrawable(sides, rollValue))
                    applyTint(imageView, colorConfig[sides])
                    imageView?.alpha = 1f
                    imageView?.rotation = 0f
                }
                viewIndex++
            }
        }
        while (binding.diceDisplayArea.childCount > viewIndex) {
            binding.diceDisplayArea.removeViewAt(viewIndex)
        }
    }

    private fun updateAnimatedViewsWithPercentileResult(result: Int?) {
        if (result == null) {
            binding.diceDisplayArea.removeAllViews()
            return
        }
        // Component rolls to update the individual dice SVGs
        val componentRolls = viewModel.processedDiceResult.value?.rawRolls
        val tensValueForDisplay = componentRolls?.get(DicePoolDialogFragment.D10_TENS_KEY)?.firstOrNull() ?: 0
        val unitsValueForDisplay = componentRolls?.get(DicePoolDialogFragment.D10_UNITS_KEY)?.firstOrNull() ?: 0
        val colorConfig = parseDiceColorConfig(viewModel.settings.value?.diceColorConfigJson)

        if (binding.diceDisplayArea.childCount >= 2) {
            (binding.diceDisplayArea.getChildAt(0)?.findViewById<ImageView>(R.id.dieResultImageView))?.apply {
                setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_TENS_KEY, tensValueForDisplay))
                applyTint(this, colorConfig[DicePoolDialogFragment.D10_TENS_KEY])
                alpha = 1f; rotation = 0f
            }
            (binding.diceDisplayArea.getChildAt(1)?.findViewById<ImageView>(R.id.dieResultImageView))?.apply {
                setImageResource(getDieResultDrawable(DicePoolDialogFragment.D10_UNITS_KEY, unitsValueForDisplay))
                applyTint(this, colorConfig[DicePoolDialogFragment.D10_UNITS_KEY])
                alpha = 1f; rotation = 0f
            }
            // Update or add the sum TextView
            var sumTextView = if (binding.diceDisplayArea.childCount == 3 && binding.diceDisplayArea.getChildAt(2) is TextView) {
                binding.diceDisplayArea.getChildAt(2) as TextView
            } else {
                // Remove extra views if any, then add
                while (binding.diceDisplayArea.childCount > 2) {
                    binding.diceDisplayArea.removeViewAt(2)
                }
                TextView(requireContext()).apply {
                    textSize = 24f
                    val params = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    params.marginStart = 16
                    layoutParams = params
                    binding.diceDisplayArea.addView(this)
                }
            }
            sumTextView.text = "= $finalSum%"
        }
    }

    private fun applyTint(imageView: ImageView?, colorInt: Int?) {
        imageView?.let {
            val tintToApply = colorInt ?: PurramidPalette.DEFAULT_DIE_COLOR.colorInt
            if (tintToApply != Color.TRANSPARENT) {
                it.colorFilter = PorterDuffColorFilter(tintToApply, PorterDuff.Mode.SRC_IN)
            } else {
                it.clearColorFilter()
            }
        }
    }

    private fun parseDiceColorConfig(json: String?): Map<Int, Int> {
        if (json.isNullOrEmpty() || json == DEFAULT_EMPTY_JSON_MAP) {
            return emptyMap()
        }
        return try {
            val mapType = object : TypeToken<Map<Int, Int>>() {}.type
            gson.fromJson(json, mapType)
        } catch (e: Exception) {
            Log.e("DiceMainFragment", "Failed to parse dice color config JSON: $json", e)
            emptyMap()
        }
    }

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
    private fun startCritCelebration() {
        if (_binding == null) return // View already destroyed

        // Accessibility Check for reduced motion
        val accessibilityManager = context?.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (accessibilityManager?.isTouchExplorationEnabled == true) { // A proxy for reduced motion preference
            Log.d("DiceMainFragment", "Reduced motion enabled, skipping confetti.")
            return
        }

        // Using the same colors as Spin mode example for now
        val partyColors = listOf(0xFF0000, 0xFFFF00, 0x00FF00, 0x4363D8, 0x7F00FF)
        // Or use your confetti_piece_*.xml colors if preferred, though Konfetti takes Ints.

        binding.konfettiViewDice.start(
            Party(
                speed = 0f,
                maxSpeed = 35f,
                damping = 0.9f,
                spread = 360,
                colors = partyColors,
                emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(150), // Quick burst
                position = Position.Relative(0.5, 0.3) // Emit from near top-center
            )
        )
        // Konfetti usually stops itself based on emitter duration / timeToLive.
        // No explicit stop needed for short bursts unless you want to clear immediately after.
    }
}
