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
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinFlipViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType
import com.example.purramid.thepurramid.R
import com.google.android.material.snackbar.Snackbar

class CoinPoolDialogFragment : DialogFragment() {
    private val coinFlipViewModel: CoinFlipViewModel by activityViewModels()
    private val pickers = mutableMapOf<CoinType, NumberPicker>()
    private val colorButtons = mutableMapOf<CoinType, Button>()
    private val colors = mutableMapOf<CoinType, Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Add title
        val title = TextView(requireContext()).apply {
            text = "Configure Coin Pool"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        // Load current settings
        val currentSettings = coinFlipViewModel.settings.value

        for (type in CoinType.values()) {
            val currentConfig = currentSettings?.coinConfigs?.find { it.type == type }
            
            // Coin type label
            val label = TextView(requireContext()).apply {
                text = "${type.name} Coins"
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
                        coinFlipViewModel.updateCoinConfig(requireContext(), type, quantity = newVal)
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
                    CoinColorPickerDialogFragment.newInstance().apply {
                        setOnColorSelectedListener { selectedColor ->
                            colors[type] = selectedColor
                            setBackgroundColor(selectedColor)
                            // Update ViewModel immediately
                            try {
                                coinFlipViewModel.updateCoinConfig(requireContext(), type, color = selectedColor)
                            } catch (e: Exception) {
                                showError("Failed to update color: ${e.message}")
                            }
                        }
                    }.show(childFragmentManager, "CoinColorPicker")
                }
            }
            colorButtons[type] = colorButton
            layout.addView(colorButton)

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
        coinFlipViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                coinFlipViewModel.clearError()
            }
        }

        return layout
    }

    private fun saveChanges() {
        try {
            // Validate all inputs
            var hasErrors = false
            for (type in CoinType.values()) {
                val quantity = pickers[type]?.value ?: 0
                if (quantity < 0) {
                    showError("Quantity cannot be negative for ${type.name}")
                    hasErrors = true
                }
            }

            if (!hasErrors) {
                // All changes are already persisted via real-time updates
                showSuccess("Coin pool configuration saved successfully")
                dismiss()
            }
        } catch (e: Exception) {
            showError("Failed to save coin pool configuration: ${e.message}")
        }
    }

    private fun showError(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show()
    }
} 