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
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinFlipViewModel
import com.google.android.material.card.MaterialCardView

class CoinColorPickerDialogFragment : DialogFragment() {
    private val coinFlipViewModel: CoinFlipViewModel by activityViewModels()
    private var onColorSelectedListener: ((Int) -> Unit)? = null

    companion object {
        const val TAG = "CoinColorPickerDialog"
        
        fun newInstance(): CoinColorPickerDialogFragment {
            return CoinColorPickerDialogFragment()
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
        return inflater.inflate(R.layout.fragment_coin_color_picker_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val colorGrid = view.findViewById<GridLayout>(R.id.colorGrid)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val confirmButton = view.findViewById<Button>(R.id.confirmButton)

        // Define colors (more coin-like colors)
        val colors = listOf(
            0xFFDAA520.toInt(), // Goldenrod
            0xFFFFD700.toInt(), // Gold
            0xFFB8860B.toInt(), // DarkGoldenrod
            0xFFFFA500.toInt(), // Orange
            0xFFFF8C00.toInt(), // DarkOrange
            0xFFCD853F.toInt(), // Peru
            0xFFD2691E.toInt(), // Chocolate
            0xFF8B4513.toInt(), // SaddleBrown
            0xFFA0522D.toInt(), // Sienna
            0xFFD2B48C.toInt(), // Tan
            0xFFDEB887.toInt(), // BurlyWood
            0xFFF5DEB3.toInt()  // Wheat
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