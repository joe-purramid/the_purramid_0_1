// DurationPicker.kt
package com.example.purramid.thepurramid.traffic_light

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.DialogDurationPickerBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.TimedSequence
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DurationPickerFragment : DialogFragment() {

    private var _binding: DialogDurationPickerBinding? = null
    private val binding get() = _binding!!

    private var onDurationSet: ((Int) -> Unit)? = null
    private var initialDuration: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogDurationPickerBinding.inflate(LayoutInflater.from(context))

        arguments?.let {
            initialDuration = it.getInt(ARG_DURATION, 0)
        }

        setupViews()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.set_duration))
            .setView(binding.root)
            .setPositiveButton(getString(R.string.set)) { _, _ ->
                val duration = parseDuration()
                onDurationSet?.invoke(duration)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    private fun setupViews() {
        // Initialize with current duration
        val hours = initialDuration / 3600
        val minutes = (initialDuration % 3600) / 60
        val seconds = initialDuration % 60

        // Format as 6 digits for input
        val initialText = String.format("%01d%02d%02d", hours, minutes, seconds)
        binding.editTextDuration.setText(initialText)

        // Add text watcher for formatting
        binding.editTextDuration.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return

                isFormatting = true

                val digits = s.toString().replace(Regex("[^0-9]"), "")
                val paddedDigits = digits.padStart(6, '0').takeLast(6)

                val hours = paddedDigits.substring(0, 1).toIntOrNull() ?: 0
                val minutes = paddedDigits.substring(1, 3).toIntOrNull() ?: 0
                val seconds = paddedDigits.substring(3, 5).toIntOrNull() ?: 0

                val formatted = String.format("%d:%02d:%02d", hours, minutes, seconds)
                binding.textFormattedDuration.text = formatted

                // Validate duration
                val totalSeconds = hours * 3600 + minutes * 60 + seconds
                binding.textDurationError.isVisible = totalSeconds > TimedSequence.MAX_DURATION_SECONDS

                isFormatting = false
            }
        })

        // Trigger initial formatting
        binding.editTextDuration.text?.let {
            binding.editTextDuration.setText(it.toString())
        }
    }

    private fun parseDuration(): Int {
        val digits = binding.editTextDuration.text.toString().replace(Regex("[^0-9]"), "")
        val paddedDigits = digits.padStart(6, '0').takeLast(6)

        val hours = paddedDigits.substring(0, 1).toIntOrNull() ?: 0
        val minutes = paddedDigits.substring(1, 3).toIntOrNull() ?: 0
        val seconds = paddedDigits.substring(3, 5).toIntOrNull() ?: 0

        return (hours * 3600 + minutes * 60 + seconds).coerceAtMost(TimedSequence.MAX_DURATION_SECONDS)
    }

    fun setOnDurationSetListener(listener: (Int) -> Unit) {
        onDurationSet = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DurationPickerDialog"
        private const val ARG_DURATION = "duration"

        fun newInstance(currentDuration: Int, onSet: (Int) -> Unit): DurationPickerFragment {
            return DurationPickerFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DURATION, currentDuration)
                }
                setOnDurationSetListener(onSet)
            }
        }
    }
}