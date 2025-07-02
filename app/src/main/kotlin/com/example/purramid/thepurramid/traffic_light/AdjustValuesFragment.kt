package com.example.purramid.thepurramid.traffic_light

import android.app.Dialog
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentAdjustValuesBinding
import com.example.purramid.thepurramid.databinding.ItemDbRangeEditorBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.DbRange
import com.example.purramid.thepurramid.traffic_light.viewmodel.ResponsiveModeSettings
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AdjustValuesFragment : DialogFragment() {

    private var _binding: FragmentAdjustValuesBinding? = null
    private val binding get() = _binding!!

    private lateinit var greenRangeBinding: ItemDbRangeEditorBinding
    private lateinit var yellowRangeBinding: ItemDbRangeEditorBinding
    private lateinit var redRangeBinding: ItemDbRangeEditorBinding

    private val viewModel: TrafficLightViewModel by activityViewModels()
    private var blockListeners: Boolean = false

    enum class ColorForRange { GREEN, YELLOW, RED }

    companion object {
        const val TAG = "AdjustValuesDialog"
        private const val COLOR_RED_ACTIVE = 0xFFFF0000.toInt()
        private const val COLOR_YELLOW_ACTIVE = 0xFFFFFF00.toInt()
        private const val COLOR_GREEN_ACTIVE = 0xFF00FF00.toInt()

        fun newInstance(): AdjustValuesFragment {
            return AdjustValuesFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdjustValuesBinding.inflate(inflater, container, false)
        greenRangeBinding = ItemDbRangeEditorBinding.bind(binding.includeGreenRange.root)
        yellowRangeBinding = ItemDbRangeEditorBinding.bind(binding.includeYellowRange.root)
        redRangeBinding = ItemDbRangeEditorBinding.bind(binding.includeRedRange.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(R.string.setting_adjust_values)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        // Set color indicators
        greenRangeBinding.imageColorIndicator.apply {
            setImageResource(R.drawable.ic_circle_base)
            setColorFilter(COLOR_GREEN_ACTIVE, PorterDuff.Mode.SRC_IN)
        }
        yellowRangeBinding.imageColorIndicator.apply {
            setImageResource(R.drawable.ic_circle_base)
            setColorFilter(COLOR_YELLOW_ACTIVE, PorterDuff.Mode.SRC_IN)
        }
        redRangeBinding.imageColorIndicator.apply {
            setImageResource(R.drawable.ic_circle_base)
            setColorFilter(COLOR_RED_ACTIVE, PorterDuff.Mode.SRC_IN)
        }

        // Setup text watchers
        setupEditTextListener(greenRangeBinding.editTextMinDb, ColorForRange.GREEN, true)
        setupEditTextListener(greenRangeBinding.editTextMaxDb, ColorForRange.GREEN, false)
        setupEditTextListener(yellowRangeBinding.editTextMinDb, ColorForRange.YELLOW, true)
        setupEditTextListener(yellowRangeBinding.editTextMaxDb, ColorForRange.YELLOW, false)
        setupEditTextListener(redRangeBinding.editTextMinDb, ColorForRange.RED, true)
        setupEditTextListener(redRangeBinding.editTextMaxDb, ColorForRange.RED, false)

        // Dangerous sound alert checkbox
        binding.checkboxDangerousSoundAlert.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.setDangerousSoundAlert(isChecked)
        }

        binding.buttonDangerousSoundInfo.setOnClickListener {
            showDangerousSoundInfoDialog()
        }

        binding.buttonSaveAdjustments.setOnClickListener {
            dismiss()
        }

        binding.buttonCancelAdjustments.setOnClickListener {
            dismiss()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    blockListeners = true
                    updateDbRangeUI(greenRangeBinding, state.responsiveModeSettings.greenRange)
                    updateDbRangeUI(yellowRangeBinding, state.responsiveModeSettings.yellowRange)
                    updateDbRangeUI(redRangeBinding, state.responsiveModeSettings.redRange)
                    binding.checkboxDangerousSoundAlert.isChecked =
                        state.responsiveModeSettings.dangerousSoundAlertEnabled
                    blockListeners = false
                }
            }
        }
    }

    private fun updateDbRangeUI(rangeBinding: ItemDbRangeEditorBinding, dbRange: DbRange) {
        if (dbRange.isNa()) {
            rangeBinding.editTextMinDb.setText(getString(R.string.na_value))
            rangeBinding.editTextMaxDb.setText(getString(R.string.na_value))
            rangeBinding.editTextMinDb.isEnabled = false
            rangeBinding.editTextMaxDb.isEnabled = false
        } else {
            rangeBinding.editTextMinDb.setText(dbRange.minDb?.toString() ?: "")
            rangeBinding.editTextMaxDb.setText(dbRange.maxDb?.toString() ?: "")
            rangeBinding.editTextMinDb.isEnabled = true
            rangeBinding.editTextMaxDb.isEnabled = true
        }
    }

    private fun setupEditTextListener(editText: EditText, color: ColorForRange, isMin: Boolean) {
        editText.doAfterTextChanged { text ->
            if (blockListeners) return@doAfterTextChanged
            if (text.toString() == getString(R.string.na_value)) return@doAfterTextChanged

            val value = text.toString().toIntOrNull()

            // Validate range (0-149 per spec)
            if (value != null && (value < 0 || value > 149)) {
                editText.error = "Value must be between 0 and 149"
                return@doAfterTextChanged
            }

            // Update ViewModel with complex linked logic
            updateLinkedRanges(color, isMin, value)
        }
    }

    private fun updateLinkedRanges(color: ColorForRange, isMin: Boolean, newValue: Int?) {
        val currentSettings = viewModel.uiState.value.responsiveModeSettings
        val newSettings = when (color) {
            ColorForRange.GREEN -> updateGreenRange(currentSettings, isMin, newValue)
            ColorForRange.YELLOW -> updateYellowRange(currentSettings, isMin, newValue)
            ColorForRange.RED -> updateRedRange(currentSettings, isMin, newValue)
        }

        if (newSettings != currentSettings) {
            viewModel.updateResponsiveSettings(newSettings)
        }
    }

    private fun updateGreenRange(
        settings: ResponsiveModeSettings,
        isMin: Boolean,
        newValue: Int?
    ): ResponsiveModeSettings {
        var newGreen = settings.greenRange
        var newYellow = settings.yellowRange
        var newRed = settings.redRange

        if (isMin) {
            // Green min changed - should always be 0
            newGreen = newGreen.copy(minDb = 0)
        } else {
            // Green max changed
            newValue?.let { value ->
                newGreen = newGreen.copy(maxDb = value)

                // Yellow min should be green max + 1
                val yellowMin = value + 1
                if (yellowMin <= 149) {
                    newYellow = newYellow.copy(minDb = yellowMin)

                    // Check if yellow range is now invalid
                    if (newYellow.maxDb != null && yellowMin > newYellow.maxDb!!) {
                        // Yellow range collapsed, make it N/A
                        newYellow = DbRange.NA_RANGE

                        // If red min was based on yellow max, adjust it
                        if (newRed.minDb != null) {
                            newRed = newRed.copy(minDb = yellowMin)
                        }
                    }
                } else {
                    // No room for yellow or red
                    newYellow = DbRange.NA_RANGE
                    newRed = DbRange.NA_RANGE
                }
            }
        }

        return settings.copy(
            greenRange = newGreen,
            yellowRange = newYellow,
            redRange = newRed
        )
    }

    private fun updateYellowRange(
        settings: ResponsiveModeSettings,
        isMin: Boolean,
        newValue: Int?
    ): ResponsiveModeSettings {
        var newGreen = settings.greenRange
        var newYellow = settings.yellowRange
        var newRed = settings.redRange

        if (isMin) {
            // Yellow min changed
            newValue?.let { value ->
                newYellow = newYellow.copy(minDb = value)

                // Green max should be yellow min - 1
                if (value > 0) {
                    newGreen = newGreen.copy(maxDb = value - 1)
                }

                // Check if yellow still has valid range
                if (newYellow.maxDb != null && value > newYellow.maxDb!!) {
                    newYellow = DbRange.NA_RANGE
                }
            }
        } else {
            // Yellow max changed
            newValue?.let { value ->
                newYellow = newYellow.copy(maxDb = value)

                // Red min should be yellow max + 1
                val redMin = value + 1
                if (redMin <= 149) {
                    newRed = newRed.copy(minDb = redMin)

                    // Check if red range is still valid
                    if (newRed.maxDb != null && redMin > newRed.maxDb!!) {
                        newRed = newRed.copy(maxDb = 149)
                    }
                } else {
                    // No room for red
                    newRed = DbRange.NA_RANGE
                }
            }
        }

        return settings.copy(
            greenRange = newGreen,
            yellowRange = newYellow,
            redRange = newRed
        )
    }

    private fun updateRedRange(
        settings: ResponsiveModeSettings,
        isMin: Boolean,
        newValue: Int?
    ): ResponsiveModeSettings {
        var newGreen = settings.greenRange
        var newYellow = settings.yellowRange
        var newRed = settings.redRange

        if (isMin) {
            // Red min changed
            newValue?.let { value ->
                newRed = newRed.copy(minDb = value)

                // Yellow max should be red min - 1
                if (value > 0) {
                    val yellowMax = value - 1
                    newYellow = if (newYellow.minDb != null && yellowMax >= newYellow.minDb!!) {
                        newYellow.copy(maxDb = yellowMax)
                    } else {
                        DbRange.NA_RANGE
                    }

                    // If yellow became N/A, green might need to extend
                    if (newYellow.isNa() && value > 1) {
                        newGreen = newGreen.copy(maxDb = value - 1)
                    }
                }
            }
        } else {
            // Red max changed - should be capped at 149
            newValue?.let { value ->
                newRed = newRed.copy(maxDb = minOf(value, 149))
            }
        }

        return settings.copy(
            greenRange = newGreen,
            yellowRange = newYellow,
            redRange = newRed
        )
    }

    private fun showDangerousSoundInfoDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dangerous_sound_alert_info_title)
            .setMessage(R.string.dangerous_sound_alert_info_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}