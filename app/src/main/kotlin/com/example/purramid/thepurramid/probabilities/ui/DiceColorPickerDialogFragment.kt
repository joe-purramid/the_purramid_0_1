package com.example.purramid.thepurramid.probabilities.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceViewModel
import com.google.android.material.card.MaterialCardView

class DiceColorPickerDialogFragment : DialogFragment() {
    private val diceViewModel: DiceViewModel by activityViewModels()
    private var onColorSelectedListener: ((Int) -> Unit)? = null

    companion object {
        const val TAG = "DiceColorPickerDialog"
        
        fun newInstance(): DiceColorPickerDialogFragment {
            return DiceColorPickerDialogFragment()
        }
    }

    fun setOnColorSelectedListener(listener: (Int) -> Unit) {
        onColorSelectedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dice_color_picker_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val colorGrid = view.findViewById<GridLayout>(R.id.colorGrid)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val confirmButton = view.findViewById<Button>(R.id.confirmButton)

        // Define colors
        val colors = listOf(
            0xFFFF0000.toInt(), // Red
            0xFF00FF00.toInt(), // Green
            0xFF0000FF.toInt(), // Blue
            0xFFFFFF00.toInt(), // Yellow
            0xFFFF00FF.toInt(), // Magenta
            0xFF00FFFF.toInt(), // Cyan
            0xFFFF8000.toInt(), // Orange
            0xFF8000FF.toInt(), // Purple
            0xFF008000.toInt(), // Dark Green
            0xFF800000.toInt(), // Dark Red
            0xFF000080.toInt(), // Dark Blue
            0xFF808080.toInt()  // Gray
        )

        // Create color buttons
        colors.forEach { color ->
            val colorButton = MaterialCardView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(80, 80)
                radius = 40f
                setCardBackgroundColor(color)
                setOnClickListener {
                    // Call the callback with the selected color
                    onColorSelectedListener?.invoke(color)
                    dismiss()
                }
            }
            colorGrid.addView(colorButton)
        }

        cancelButton.setOnClickListener { dismiss() }
        confirmButton.setOnClickListener { dismiss() }
    }
} 