package com.example.purramid.thepurramid.probabilities.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.instance.InstanceManager
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.CoinProbabilityMode
import com.example.purramid.thepurramid.probabilities.DiceSumResultType
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.GraphPlotType
import com.example.purramid.thepurramid.probabilities.ProbabilitiesHostActivity
import com.example.purramid.thepurramid.probabilities.viewmodel.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject

class ProbabilitiesSettingsFragment : Fragment() {
    private val settingsViewModel: ProbabilitiesSettingsViewModel by activityViewModels()
    private val diceViewModel: DiceViewModel by activityViewModels()
    private val coinFlipViewModel: CoinFlipViewModel by activityViewModels()

    @Inject lateinit var instanceManager: InstanceManager
    private val settingsViewModel: ProbabilitiesSettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_probabilities_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI elements
        val modeToggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.modeToggleGroup)
        val diceSettingsGroup = view.findViewById<LinearLayout>(R.id.diceSettingsGroup)
        val coinSettingsGroup = view.findViewById<LinearLayout>(R.id.coinSettingsGroup)
        val buttonModeDice = view.findViewById<View>(R.id.buttonModeDice)
        val buttonModeCoinFlip = view.findViewById<View>(R.id.buttonModeCoinFlip)

        // Dice settings elements
        val switchCriticalSuccess = view.findViewById<Switch>(R.id.switchCriticalSuccess)
        val switchPercentileDice = view.findViewById<Switch>(R.id.switchPercentileDice)
        val switchDiceAnnounce = view.findViewById<Switch>(R.id.switchDiceAnnounce)
        val switchDiceCriticalCelebration = view.findViewById<Switch>(R.id.switchDiceCriticalCelebration)
        val spinnerDiceSumResultType = view.findViewById<Spinner>(R.id.spinnerDiceSumResultType)
        val switchDiceGraph = view.findViewById<Switch>(R.id.switchDiceGraph)
        val spinnerDiceGraphType = view.findViewById<Spinner>(R.id.spinnerDiceGraphType)
        val spinnerDiceDistribution = view.findViewById<Spinner>(R.id.spinnerDiceDistribution)
        val buttonEditDicePool = view.findViewById<Button>(R.id.buttonEditDicePool)
        val buttonEditDiceColors = view.findViewById<Button>(R.id.buttonEditDiceColors)

        // Coin settings elements
        val switchProbabilityMode = view.findViewById<Switch>(R.id.switchProbabilityMode)
        val spinnerProbabilityType = view.findViewById<Spinner>(R.id.spinnerProbabilityType)
        val switchCoinGraph = view.findViewById<Switch>(R.id.switchCoinGraph)
        val spinnerCoinGraphType = view.findViewById<Spinner>(R.id.spinnerCoinGraphType)
        val spinnerCoinDistribution = view.findViewById<Spinner>(R.id.spinnerCoinDistribution)
        val switchAnnounce = view.findViewById<Switch>(R.id.switchAnnounce)
        val switchFreeForm = view.findViewById<Switch>(R.id.switchFreeForm)
        val buttonEditCoinPool = view.findViewById<Button>(R.id.buttonEditCoinPool)
        val buttonEditCoinColors = view.findViewById<Button>(R.id.buttonEditCoinColors)

        val instanceId = arguments?.getInt("instanceId") ?: 1

        // Populate spinners with enum values
        val graphTypes = GraphPlotType.values().map { it.name.lowercase().replace("_", " ").capitalize() }
        val distributions = GraphDistributionType.values().filter { it != GraphDistributionType.OFF }.map { it.name.lowercase().replace("_", " ").capitalize() }
        val probabilityTypes = CoinProbabilityMode.values().filter { it != CoinProbabilityMode.NONE }.map { it.name.lowercase().replace("_", " ").capitalize() }
        val sumResultTypes = DiceSumResultType.values().map { it.name.lowercase().replace("_", " ").capitalize() }
        
        spinnerDiceGraphType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, graphTypes)
        spinnerDiceDistribution.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, distributions)
        spinnerDiceSumResultType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sumResultTypes)
        spinnerCoinGraphType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, graphTypes)
        spinnerCoinDistribution.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, distributions)
        spinnerProbabilityType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, probabilityTypes)

        // Show/hide settings group based on mode
        settingsViewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings != null) {
                when (settings.mode) {
                    ProbabilitiesMode.DICE -> {
                        diceSettingsGroup.visibility = View.VISIBLE
                        coinSettingsGroup.visibility = View.GONE
                    }
                    ProbabilitiesMode.COIN_FLIP -> {
                        diceSettingsGroup.visibility = View.GONE
                        coinSettingsGroup.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Mode toggle group
        modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.buttonModeDice -> settingsViewModel.updateMode(ProbabilitiesMode.DICE)
                    R.id.buttonModeCoinFlip -> settingsViewModel.updateMode(ProbabilitiesMode.COIN_FLIP)
                }
            }
        }

        // Dice settings listeners with validation and mutual exclusivity
        switchCriticalSuccess.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Critical success only works with d20
                if (isChecked && !validateCriticalSuccessCompatibility()) {
                    showError("Critical success only works with d20 dice")
                    switchCriticalSuccess.isChecked = false
                    return@setOnCheckedChangeListener
                }
                diceViewModel.updateSettings(requireContext(), critEnabled = isChecked)
            } catch (e: Exception) {
                showError("Failed to update critical success setting: ${e.message}")
            }
        }
        
        switchPercentileDice.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Percentile dice and critical success are mutually exclusive
                if (isChecked && switchCriticalSuccess.isChecked) {
                    showError("Percentile dice and critical success cannot be used together")
                    switchPercentileDice.isChecked = false
                    return@setOnCheckedChangeListener
                }
                diceViewModel.updateSettings(requireContext(), usePercentile = isChecked)
            } catch (e: Exception) {
                showError("Failed to update percentile dice setting: ${e.message}")
            }
        }
        
        switchDiceAnnounce.setOnCheckedChangeListener { _, isChecked ->
            try {
                // If Announce is turned off, Critical Celebration should also be turned off
                if (!isChecked && switchDiceCriticalCelebration.isChecked) {
                    switchDiceCriticalCelebration.isChecked = false
                }
                diceViewModel.updateSettings(requireContext(), announce = isChecked)
            } catch (e: Exception) {
                showError("Failed to update dice announce setting: ${e.message}")
            }
        }
        
        switchDiceCriticalCelebration.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Critical Celebration requires Announce to be on (per specifications)
                if (isChecked && !switchDiceAnnounce.isChecked) {
                    switchDiceAnnounce.isChecked = true
                }
                diceViewModel.updateSettings(requireContext(), criticalCelebration = isChecked)
            } catch (e: Exception) {
                showError("Failed to update critical celebration setting: ${e.message}")
            }
        }
        
        spinnerDiceSumResultType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                try {
                    val sumResultType = DiceSumResultType.values()[position]
                    diceViewModel.updateSettings(requireContext(), sumResultType = sumResultType)
                } catch (e: Exception) {
                    showError("Failed to update sum result type: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        switchDiceGraph.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Graph Distribution cannot be active while Announce, Sum Results, or Add Modifiers are toggled on
                if (isChecked) {
                    if (switchDiceAnnounce.isChecked) {
                        switchDiceAnnounce.isChecked = false
                    }
                    // Note: Sum Results and Add Modifiers would need to be turned off here if they were separate toggles
                    // For now, we'll reset the sum result type to INDIVIDUAL
                    spinnerDiceSumResultType.setSelection(0) // INDIVIDUAL
                }
                
                // Enable/disable graph-related spinners based on graph toggle
                spinnerDiceGraphType.isEnabled = isChecked
                spinnerDiceDistribution.isEnabled = isChecked
                
                diceViewModel.updateSettings(requireContext(), graphEnabled = isChecked)
            } catch (e: Exception) {
                showError("Failed to update dice graph setting: ${e.message}")
            }
        }
        
        spinnerDiceGraphType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                try {
                    val graphType = GraphPlotType.values()[position]
                    diceViewModel.updateSettings(requireContext(), graphType = graphType)
                } catch (e: Exception) {
                    showError("Failed to update dice graph type: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        spinnerDiceDistribution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                try {
                    val distribution = GraphDistributionType.values().filter { it != GraphDistributionType.OFF }[position]
                    diceViewModel.updateSettings(requireContext(), graphDistribution = distribution)
                } catch (e: Exception) {
                    showError("Failed to update dice distribution: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        buttonEditDicePool.setOnClickListener {
            try {
                DicePoolDialogFragment().show(childFragmentManager, "DicePoolDialog")
            } catch (e: Exception) {
                showError("Failed to open dice pool dialog: ${e.message}")
            }
        }
        
        buttonEditDiceColors.setOnClickListener {
            try {
                DiceColorPickerDialogFragment.newInstance().show(childFragmentManager, "DiceColorPickerDialog")
            } catch (e: Exception) {
                showError("Failed to open dice color picker: ${e.message}")
            }
        }

        // Coin settings listeners with validation and mutual exclusivity
        switchProbabilityMode.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Probability mode and free form are mutually exclusive
                if (isChecked && switchFreeForm.isChecked) {
                    showError("Probability mode and free form cannot be used together")
                    switchProbabilityMode.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                val probabilityMode = if (isChecked) CoinProbabilityMode.TWO_COLUMNS else CoinProbabilityMode.NONE
                coinFlipViewModel.updateSettings(requireContext(), probabilityMode = probabilityMode)
                
                // Enable/disable probability type spinner based on probability mode
                spinnerProbabilityType.isEnabled = isChecked
            } catch (e: Exception) {
                showError("Failed to update probability mode: ${e.message}")
            }
        }
        
        spinnerProbabilityType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                try {
                    val probabilityMode = CoinProbabilityMode.values().filter { it != CoinProbabilityMode.NONE }[position]
                    coinFlipViewModel.updateSettings(requireContext(), probabilityMode = probabilityMode)
                } catch (e: Exception) {
                    showError("Failed to update probability type: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        switchCoinGraph.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Enable/disable graph-related spinners based on graph toggle
                spinnerCoinGraphType.isEnabled = isChecked
                spinnerCoinDistribution.isEnabled = isChecked
                
                coinFlipViewModel.updateSettings(requireContext(), graphEnabled = isChecked)
            } catch (e: Exception) {
                showError("Failed to update coin graph setting: ${e.message}")
            }
        }
        
        spinnerCoinGraphType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                try {
                    val graphType = GraphPlotType.values()[position]
                    coinFlipViewModel.updateSettings(requireContext(), graphType = graphType)
                } catch (e: Exception) {
                    showError("Failed to update coin graph type: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        spinnerCoinDistribution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                try {
                    val distribution = GraphDistributionType.values().filter { it != GraphDistributionType.OFF }[position]
                    coinFlipViewModel.updateSettings(requireContext(), graphDistribution = distribution)
                } catch (e: Exception) {
                    showError("Failed to update coin distribution: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        switchAnnounce.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Probability mode cannot be active while Announce is toggled on
                if (isChecked && switchProbabilityMode.isChecked) {
                    switchProbabilityMode.isChecked = false
                }
                coinFlipViewModel.updateSettings(requireContext(), announce = isChecked)
            } catch (e: Exception) {
                showError("Failed to update coin announce setting: ${e.message}")
            }
        }
        
        switchFreeForm.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Free form and probability mode are mutually exclusive
                if (isChecked && switchProbabilityMode.isChecked) {
                    showError("Free form and probability mode cannot be used together")
                    switchFreeForm.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                coinFlipViewModel.updateSettings(requireContext(), freeForm = isChecked)
            } catch (e: Exception) {
                showError("Failed to update free form setting: ${e.message}")
            }
        }
        
        buttonEditCoinPool.setOnClickListener {
            try {
                CoinPoolDialogFragment().show(childFragmentManager, "CoinPoolDialog")
            } catch (e: Exception) {
                showError("Failed to open coin pool dialog: ${e.message}")
            }
        }
        
        buttonEditCoinColors.setOnClickListener {
            try {
                CoinColorPickerDialogFragment.newInstance().show(childFragmentManager, "CoinColorPickerDialog")
            } catch (e: Exception) {
                showError("Failed to open coin color picker: ${e.message}")
            }
        }
        
        // Initialize UI state based on current settings
        initializeUIState()
    }
    
    private fun validateCriticalSuccessCompatibility(): Boolean {
        // This would check if d20 dice are configured in the pool
        // For now, return true as a placeholder
        return true
    }
    
    private fun initializeUIState() {
        // Initialize spinners and switches based on current settings
        // This would be called after loading settings from ViewModels
        // For now, this is a placeholder for future implementation
    }
    
    private fun showError(message: String) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }

    private fun onAddAnotherClicked() {
        val currentInstanceId = arguments?.getInt("instanceId") ?: return

        // Check if we can add another window
        val activeCount = instanceManager.getActiveInstanceCount(InstanceManager.PROBABILITIES)
        if (activeCount >= 7) {
            Snackbar.make(
                binding.root,
                getString(R.string.max_probabilities_reached_snackbar),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        // Get next instance ID
        val newInstanceId = instanceManager.getNextInstanceId(InstanceManager.PROBABILITIES)
        if (newInstanceId == null) {
            Snackbar.make(
                binding.root,
                getString(R.string.error_allocating_instance),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        // Launch new window with cloned settings
        val currentActivity = requireActivity() as? ProbabilitiesHostActivity
        val currentBounds = currentActivity?.getCurrentWindowBounds()

        val intent = Intent(requireContext(), ProbabilitiesHostActivity::class.java).apply {
            putExtra(ProbabilitiesHostActivity.EXTRA_INSTANCE_ID, newInstanceId)
            putExtra(ProbabilitiesHostActivity.EXTRA_CLONE_FROM, currentInstanceId)

            // Position new window with offset
            currentBounds?.let {
                putExtra("WINDOW_X", it.left + 50)
                putExtra("WINDOW_Y", it.top + 50)
                putExtra("WINDOW_WIDTH", it.width())
                putExtra("WINDOW_HEIGHT", it.height())
            }

            // Flags for independent window
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }

        startActivity(intent)

        // Optionally close settings after launching
        parentFragmentManager.popBackStack()
    }
} 