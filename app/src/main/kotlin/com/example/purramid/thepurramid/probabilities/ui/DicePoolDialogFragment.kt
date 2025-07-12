package com.example.purramid.thepurramid.probabilities.ui

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.DieType
import com.example.purramid.thepurramid.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class DicePoolDialogFragment : DialogFragment() {
    private val diceViewModel: DiceViewModel by activityViewModels()
    private val diceCountViews = mutableMapOf<DieType, EditText>()
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

    class DicePoolDialogFragment : DialogFragment() {
        private val diceViewModel: DiceViewModel by activityViewModels()
        private val diceCountViews = mutableMapOf<DieType, EditText>()

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return activity?.let {
                val builder = MaterialAlertDialogBuilder(it)
                val inflater = requireActivity().layoutInflater
                val view = inflater.inflate(R.layout.dialog_dice_pool, null)

                setupDiceRows(view)

                builder.setView(view)
                    .setPositiveButton(R.string.close) { dialog, _ ->
                        dialog.dismiss()
                    }

                builder.create()
            } ?: throw IllegalStateException("Activity cannot be null")
        }

        private fun setupDiceRows(rootView: View) {
            val settings = diceViewModel.settings.value ?: return

            // Setup each die type row
            setupDieRow(rootView, R.id.dice_row_d4, DieType.D4, "d4")
            setupDieRow(rootView, R.id.dice_row_d6, DieType.D6, "d6")
            setupDieRow(rootView, R.id.dice_row_d8, DieType.D8, "d8")
            setupDieRow(rootView, R.id.dice_row_d10, DieType.D10, "d10")
            setupDieRow(rootView, R.id.dice_row_d12, DieType.D12, "d12")
            setupDieRow(rootView, R.id.dice_row_d20, DieType.D20, "d20")

            // Handle percentile dice visibility
            val percentileTensRow = rootView.findViewById<View>(R.id.dice_row_d10_tens)
            val percentileUnitsRow = rootView.findViewById<View>(R.id.dice_row_d10_units)

            if (settings.usePercentile) {
                percentileTensRow.visibility = View.VISIBLE
                percentileUnitsRow.visibility = View.VISIBLE

                setupPercentileRow(rootView, R.id.dice_row_d10_tens, true)
                setupPercentileRow(rootView, R.id.dice_row_d10_units, false)
            } else {
                percentileTensRow.visibility = View.GONE
                percentileUnitsRow.visibility = View.GONE
            }
        }

        private fun setupDieRow(rootView: View, rowId: Int, dieType: DieType, label: String) {
            val row = rootView.findViewById<View>(rowId)
            val labelView = row.findViewById<TextView>(R.id.diceTypeLabel)
            val countEditText = row.findViewById<EditText>(R.id.diceCountEditText)
            val decrementButton = row.findViewById<ImageButton>(R.id.decrementButton)
            val incrementButton = row.findViewById<ImageButton>(R.id.incrementButton)

            labelView.text = label

            // Set current value
            val currentConfig = diceViewModel.settings.value?.dieConfigs?.find { it.type == dieType }
            countEditText.setText((currentConfig?.quantity ?: 0).toString())

            // Store reference
            diceCountViews[dieType] = countEditText

            // Setup listeners
            decrementButton.setOnClickListener {
                val currentValue = countEditText.text.toString().toIntOrNull() ?: 0
                if (currentValue > 0) {
                    val newValue = currentValue - 1
                    countEditText.setText(newValue.toString())
                    diceViewModel.updateDieConfig(dieType, quantity = newValue)
                }
            }

            incrementButton.setOnClickListener {
                val currentValue = countEditText.text.toString().toIntOrNull() ?: 0
                if (currentValue < 10) {
                    val newValue = currentValue + 1
                    countEditText.setText(newValue.toString())
                    diceViewModel.updateDieConfig(dieType, quantity = newValue)
                }
            }

            countEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val value = s.toString().toIntOrNull() ?: 0
                    if (value in 0..10) {
                        diceViewModel.updateDieConfig(dieType, quantity = value)
                    } else {
                        countEditText.setText("0")
                    }
                }
            })
        }

        private fun setupPercentileRow(rootView: View, rowId: Int, isTens: Boolean) {
            val row = rootView.findViewById<View>(rowId)
            val labelView = row.findViewById<TextView>(R.id.diceTypeLabel)
            val countEditText = row.findViewById<EditText>(R.id.diceCountEditText)
            val decrementButton = row.findViewById<ImageButton>(R.id.decrementButton)
            val incrementButton = row.findViewById<ImageButton>(R.id.incrementButton)

            labelView.text = if (isTens) getString(R.string.dice_label_d10_tens) else getString(R.string.dice_label_d10_units)

            // Percentile dice share the same quantity
            val currentConfig = diceViewModel.settings.value?.dieConfigs?.find { it.type == DieType.PERCENTILE }
            countEditText.setText((currentConfig?.quantity ?: 0).toString())

            // Setup listeners
            decrementButton.setOnClickListener {
                val currentValue = countEditText.text.toString().toIntOrNull() ?: 0
                if (currentValue > 0) {
                    val newValue = currentValue - 1
                    countEditText.setText(newValue.toString())
                    diceViewModel.updateDieConfig(DieType.PERCENTILE, quantity = newValue)
                    // Update the other percentile row
                    updatePercentileRows(rootView, newValue)
                }
            }

            incrementButton.setOnClickListener {
                val currentValue = countEditText.text.toString().toIntOrNull() ?: 0
                if (currentValue < 10) {
                    val newValue = currentValue + 1
                    countEditText.setText(newValue.toString())
                    diceViewModel.updateDieConfig(DieType.PERCENTILE, quantity = newValue)
                    // Update the other percentile row
                    updatePercentileRows(rootView, newValue)
                }
            }

            countEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val value = s.toString().toIntOrNull() ?: 0
                    if (value in 0..10) {
                        diceViewModel.updateDieConfig(DieType.PERCENTILE, quantity = value)
                        updatePercentileRows(rootView, value)
                    } else {
                        countEditText.setText("0")
                    }
                }
            })
        }

        private fun updatePercentileRows(rootView: View, value: Int) {
            // Update both percentile rows to show the same value
            val tensRow = rootView.findViewById<View>(R.id.dice_row_d10_tens)
            val unitsRow = rootView.findViewById<View>(R.id.dice_row_d10_units)

            tensRow.findViewById<EditText>(R.id.diceCountEditText)?.setText(value.toString())
            unitsRow.findViewById<EditText>(R.id.diceCountEditText)?.setText(value.toString())
        }
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