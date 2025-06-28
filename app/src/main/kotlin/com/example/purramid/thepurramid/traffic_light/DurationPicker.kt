// DurationPicker.kt
package com.example.purramid.thepurramid.traffic_light

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
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
            .setTitle("Set Duration")
            .setView(binding.root)
            .setPositiveButton("Set") { _, _ ->
                val duration = parseDuration()
                onDurationSet?.invoke(duration)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    private fun setupViews() {
        // Initialize with current duration
        val hours = initialDuration / 3600
        val minutes = (initialDuration % 3600) / 60
        val seconds = initialDuration % 60
        
        binding.editTextDuration.setText(String.format("%d%02d%02d", hours, minutes, seconds))
        
        // Add text watcher for formatting
        binding.editTextDuration.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                
                isFormatting = true
                
                val digits = s.toString().replace(Regex("[^0-9]"), "")
                val formatted = when {
                    digits.isEmpty() -> "0:00:00"
                    digits.length <= 2 -> "0:00:${digits.padStart(2, '0')}"
                    digits.length <= 4 -> "0:${digits.substring(0, digits.length - 2).padStart(2, '0')}:${digits.takeLast(2)}"
                    else -> {
                        val hours = digits.substring(0, digits.length - 4).take(1) // Max 9 hours
                        val mins = digits.substring(digits.length - 4, digits.length - 2)
                        val secs = digits.takeLast(2)
                        "$hours:$mins:$secs"
                    }
                }
                
                binding.textFormattedDuration.text = formatted
                
                // Validate duration
                val totalSeconds = parseDuration()
                binding.textDurationError.isVisible = totalSeconds > TimedSequence.MAX_DURATION_SECONDS
                
                isFormatting = false
            }
        })
    }

    private fun parseDuration(): Int {
        val digits = binding.editTextDuration.text.toString().replace(Regex("[^0-9]"), "")
        
        return when {
            digits.isEmpty() -> 0
            digits.length <= 2 -> digits.toInt() // Just seconds
            digits.length <= 4 -> {
                val mins = digits.substring(0, digits.length - 2).toInt()
                val secs = digits.takeLast(2).toInt()
                mins * 60 + secs
            }
            else -> {
                val hours = digits.substring(0, digits.length - 4).take(1).toInt()
                val mins = digits.substring(digits.length - 4, digits.length - 2).toInt()
                val secs = digits.takeLast(2).toInt()
                hours * 3600 + mins * 60 + secs
            }
        }.coerceAtMost(TimedSequence.MAX_DURATION_SECONDS)
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