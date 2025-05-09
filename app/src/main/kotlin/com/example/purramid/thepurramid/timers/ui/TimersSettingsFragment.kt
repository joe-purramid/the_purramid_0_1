// TimersSettingsFragment.kt
package com.example.purramid.thepurramid.timers.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels // Use activityViewModels to share with TimersService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentTimersSettingsBinding
import com.example.purramid.thepurramid.timers.TimerType
import com.example.purramid.thepurramid.timers.viewmodel.TimersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class TimersSettingsFragment : DialogFragment() {

    private var _binding: FragmentTimersSettingsBinding? = null
    private val binding get() = _binding!!

    // Get the ViewModel scoped to the Service (or a shared parent if this Fragment
    // is launched by an Activity that also manages the Service's ViewModel instance via ID)
    // For simplicity, let's assume it's shared via the hosting Activity/Service context.
    // This requires the TimersService and this fragment to share the same ViewModelStoreOwner
    // or for the ViewModel to be scoped to a NavGraph that both can access.
    // Given TimersService uses its own ViewModelStoreOwner, this settings fragment needs
    // a way to get the correct TimersViewModel instance, usually by passing the timerId.
    // For now, we'll assume TimersActivity will host this and provide the VM.
    // Let's simplify and assume this fragment is hosted by TimersActivity which can give us the VM.
    private val viewModel: TimersViewModel by activityViewModels() // This assumes TimersActivity owns the VM or can provide it.

    private var blockListeners = false // To prevent listener loops

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimersSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle(R.string.timer_settings_title)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.buttonCloseSettings.setOnClickListener {
            // Before dismissing, ensure duration is processed if countdown is selected
            if (viewModel.uiState.value.type == TimerType.COUNTDOWN) {
                saveDurationFromInput()
            }
            dismiss()
        }

        binding.radioGroupTimerType.setOnCheckedChangeListener { _, checkedId ->
            if (blockListeners) return@setOnCheckedChangeListener
            val newType = when (checkedId) {
                R.id.radioStopwatch -> TimerType.STOPWATCH
                R.id.radioCountdown -> TimerType.COUNTDOWN
                else -> viewModel.uiState.value.type // Should not happen
            }
            // If switching to countdown, process current duration input before setting type
            if (newType == TimerType.STOPWATCH && viewModel.uiState.value.type == TimerType.COUNTDOWN) {
                saveDurationFromInput()
            }
            viewModel.setTimerType(newType)
        }

        // Duration input listeners for countdown
        val durationTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (blockListeners || viewModel.uiState.value.type != TimerType.COUNTDOWN) return
                // Live update can be tricky; for now, we save on close/type change
                // Or, you could add a "Set Duration" button
            }
        }
        binding.editTextHours.addTextChangedListener(durationTextWatcher)
        binding.editTextMinutes.addTextChangedListener(durationTextWatcher)
        binding.editTextSeconds.addTextChangedListener(durationTextWatcher)

        // Focus change listener to save duration when focus leaves any duration field
        val durationFocusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !blockListeners && viewModel.uiState.value.type == TimerType.COUNTDOWN) {
                saveDurationFromInput()
            }
        }
        binding.editTextHours.onFocusChangeListener = durationFocusListener
        binding.editTextMinutes.onFocusChangeListener = durationFocusListener
        binding.editTextSeconds.onFocusChangeListener = durationFocusListener


        binding.switchPlaySoundOnEnd.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.setPlaySoundOnEnd(isChecked)
        }

        binding.switchShowCentiseconds.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.setShowCentiseconds(isChecked)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d("TimerSettingsFrag", "Observed state: $state")
                    blockListeners = true // Prevent listeners while updating UI

                    // Update Timer Type RadioGroup
                    val radioIdToCheck = when (state.type) {
                        TimerType.STOPWATCH -> R.id.radioStopwatch
                        TimerType.COUNTDOWN -> R.id.radioCountdown
                    }
                    if (binding.radioGroupTimerType.checkedRadioButtonId != radioIdToCheck) {
                        binding.radioGroupTimerType.check(radioIdToCheck)
                    }

                    // Update Countdown specific UI
                    binding.layoutCountdownSettings.isVisible = state.type == TimerType.COUNTDOWN
                    if (state.type == TimerType.COUNTDOWN) {
                        // Populate duration fields only if not currently focused to avoid disrupting user input
                        if (!binding.editTextHours.hasFocus() && !binding.editTextMinutes.hasFocus() && !binding.editTextSeconds.hasFocus()) {
                            populateDurationFields(state.initialDurationMillis)
                        }
                        binding.switchPlaySoundOnEnd.isChecked = state.playSoundOnEnd
                    }

                    // Update Common Settings
                    binding.switchShowCentiseconds.isChecked = state.showCentiseconds

                    blockListeners = false
                }
            }
        }
    }

    private fun populateDurationFields(totalMillis: Long) {
        val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis) % 60

        if (binding.editTextHours.text.toString() != hours.toString()) {
            binding.editTextHours.setText(hours.toString())
        }
        if (binding.editTextMinutes.text.toString() != minutes.toString()) {
            binding.editTextMinutes.setText(minutes.toString())
        }
        if (binding.editTextSeconds.text.toString() != seconds.toString()) {
            binding.editTextSeconds.setText(seconds.toString())
        }
    }

    private fun saveDurationFromInput() {
        val hours = binding.editTextHours.text.toString().toLongOrNull() ?: 0L
        val minutes = binding.editTextMinutes.text.toString().toLongOrNull() ?: 0L
        val seconds = binding.editTextSeconds.text.toString().toLongOrNull() ?: 0L

        if (hours < 0 || minutes < 0 || seconds < 0 || minutes >= 60 || seconds >= 60) {
            // Basic validation, can be improved
            Log.w("TimerSettingsFrag", "Invalid duration input.")
            // Optionally show a Toast to the user
             android.widget.Toast.makeText(requireContext(), "Invalid duration values", android.widget.Toast.LENGTH_SHORT).show()
            // Re-populate with current VM state to correct invalid input
            populateDurationFields(viewModel.uiState.value.initialDurationMillis)
            return
        }

        val totalMillis = TimeUnit.HOURS.toMillis(hours) +
                          TimeUnit.MINUTES.toMillis(minutes) +
                          TimeUnit.SECONDS.toMillis(seconds)
        viewModel.setInitialDuration(totalMillis)
        Log.d("TimerSettingsFrag", "Saved duration: $totalMillis ms")
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    companion object {
        const val TAG = "TimersSettingsFragment"
        // Factory method if you need to pass timerId to the fragment arguments
        fun newInstance(timerId: Int): TimersSettingsFragment {
            val fragment = TimersSettingsFragment()
            val args = Bundle()
            args.putInt(TimersViewModel.KEY_TIMER_ID, timerId) // Assuming ViewModel uses this key
            fragment.arguments = args
            return fragment
        }
    }
}