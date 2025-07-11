package com.example.purramid.thepurramid.probabilities.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.DieType
import com.example.purramid.thepurramid.R
import com.google.android.material.snackbar.Snackbar

class DicePoolDialogFragment : DialogFragment() {
    private val diceViewModel: DiceViewModel by activityViewModels()
    private val pickers = mutableMapOf<DieType, NumberPicker>()
    private val colorButtons = mutableMapOf<DieType, Button>()
    private val colors = mutableMapOf<DieType, Int>()
    private val modifierPickers = mutableMapOf<DieType, NumberPicker>()
    private val usePipsSwitches = mutableMapOf<DieType, Button>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Add title
        val title = TextView(requireContext()).apply {
            text = "Configure Dice Pool"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        // Load current settings
        val currentSettings = diceViewModel.settings.value

        for (type in DieType.values()) {
            val currentConfig = currentSettings?.dieConfigs?.find { it.type == type }
            
            // Die type label
            val label = TextView(requireContext()).apply {
                text = "${type.name} Dice"
                textSize = 16f
                setPadding(0, 16, 0, 8)
            }
            layout.addView(label)

            // Quantity picker
            val quantityLabel = TextView(requireContext()).apply {
                text = "Quantity:"
                setPadding(0, 8, 0, 4)
            }
            layout.addView(quantityLabel)
            
            val picker = NumberPicker(requireContext()).apply {
                minValue = 0
                maxValue = 20
                value = currentConfig?.quantity ?: 1
                setOnValueChangedListener { _, _, newVal ->
                    // Update ViewModel immediately for real-time feedback
                    try {
                        diceViewModel.updateDieConfig(requireContext(), type, quantity = newVal)
                    } catch (e: Exception) {
                        showError("Failed to update quantity: ${e.message}")
                    }
                }
            }
            pickers[type] = picker
            layout.addView(picker)

            // Color picker
            val colorLabel = TextView(requireContext()).apply {
                text = "Color:"
                setPadding(0, 8, 0, 4)
            }
            layout.addView(colorLabel)
            
            val currentColor = currentConfig?.color ?: Color.LTGRAY
            colors[type] = currentColor
            val colorButton = Button(requireContext()).apply {
                text = "Pick Color"
                setBackgroundColor(currentColor)
                setOnClickListener {
                    // Open color picker dialog
                    DiceColorPickerDialogFragment.newInstance().apply {
                        setOnColorSelectedListener { selectedColor ->
                            colors[type] = selectedColor
                            setBackgroundColor(selectedColor)
                            // Update ViewModel immediately
                            try {
                                diceViewModel.updateDieConfig(requireContext(), type, color = selectedColor)
                            } catch (e: Exception) {
                                showError("Failed to update color: ${e.message}")
                            }
                        }
                    }.show(childFragmentManager, "DiceColorPicker")
                }
            }
            colorButtons[type] = colorButton
            layout.addView(colorButton)

            // Modifier picker
            val modifierLabel = TextView(requireContext()).apply {
                text = "Modifier:"
                setPadding(0, 8, 0, 4)
            }
            layout.addView(modifierLabel)
            
            val modifierPicker = NumberPicker(requireContext()).apply {
                minValue = -10
                maxValue = 10
                value = currentConfig?.modifier ?: 0
                setOnValueChangedListener { _, _, newVal ->
                    // Update ViewModel immediately
                    try {
                        diceViewModel.updateDieConfig(requireContext(), type, modifier = newVal)
                    } catch (e: Exception) {
                        showError("Failed to update modifier: ${e.message}")
                    }
                }
            }
            modifierPickers[type] = modifierPicker
            layout.addView(modifierPicker)

            // Use pips toggle (for d6 only)
            if (type == DieType.D6) {
                val pipsLabel = TextView(requireContext()).apply {
                    text = "Use Pips:"
                    setPadding(0, 8, 0, 4)
                }
                layout.addView(pipsLabel)
                
                val pipsButton = Button(requireContext()).apply {
                    text = if (currentConfig?.usePips == true) "Pips: ON" else "Pips: OFF"
                    setOnClickListener {
                        val newPipsValue = !(currentConfig?.usePips ?: false)
                        text = if (newPipsValue) "Pips: ON" else "Pips: OFF"
                        try {
                            diceViewModel.updateDieConfig(requireContext(), type, usePips = newPipsValue)
                        } catch (e: Exception) {
                            showError("Failed to update pips setting: ${e.message}")
                        }
                    }
                }
                usePipsSwitches[type] = pipsButton
                layout.addView(pipsButton)
            }

            // Add separator
            val separator = View(requireContext()).apply {
                setBackgroundColor(Color.GRAY)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                setPadding(0, 16, 0, 16)
            }
            layout.addView(separator)
        }

        // Action buttons
        val buttonLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val saveButton = Button(requireContext()).apply { 
            text = "Save & Close"
            setOnClickListener {
                saveChanges()
            }
        }
        
        val cancelButton = Button(requireContext()).apply { 
            text = "Cancel"
            setOnClickListener { 
                dismiss() 
            }
        }

        buttonLayout.addView(saveButton)
        buttonLayout.addView(cancelButton)
        layout.addView(buttonLayout)

        // Observe ViewModel errors
        diceViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                diceViewModel.clearError()
            }
        }

        return layout
    }

    private fun saveChanges() {
        try {
            // Validate all inputs
            var hasErrors = false
            for (type in DieType.values()) {
                val quantity = pickers[type]?.value ?: 0
                if (quantity < 0) {
                    showError("Quantity cannot be negative for ${type.name}")
                    hasErrors = true
                }
            }

            if (!hasErrors) {
                // All changes are already persisted via real-time updates
                showSuccess("Dice pool configuration saved successfully")
                dismiss()
            }
        } catch (e: Exception) {
            showError("Failed to save dice pool configuration: ${e.message}")
        }
    }

    private fun showError(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun onDicePoolChanged() {
        // Clear saved positions to force recalculation
        positionManager.clearPositions(instanceId)

        // The fragment will recalculate on next onViewCreated
        // Or you can trigger immediate recalculation
        parentFragment?.let { parent ->
            if (parent is DiceMainFragment) {
                parent.setupDicePositions()
            }
        }
    }
} 