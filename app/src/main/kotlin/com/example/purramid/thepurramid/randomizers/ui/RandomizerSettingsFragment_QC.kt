// RandomizerSettingsFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.DiceSumResultType
import com.example.purramid.thepurramid.databinding.FragmentRandomizerSettingsBinding
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.GraphDistributionType
import com.example.purramid.thepurramid.randomizers.GraphPlotType
import com.example.purramid.thepurramid.randomizers.CoinProbabilityMode // New Import
import com.example.purramid.thepurramid.randomizers.PlotType
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.RandomizersHostActivity
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.example.purramid.thepurramid.ui.PurramidPalette // Import PurramidPalette
import com.example.purramid.thepurramid.util.dpToPx
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RandomizerSettingsFragment : Fragment() {

    private var _binding: FragmentRandomizerSettingsBinding? = null
    private val binding get() = _binding!!

    private val args: RandomizerSettingsFragmentArgs by navArgs()
    private val viewModel: RandomizerSettingsViewModel by activityViewModels()

    @Inject
    lateinit var randomizerDao: RandomizerDao
    private var currentInstanceIdForCloning: UUID? = null
    private lateinit var currentSettings: SpinSettingsEntity
    private var isUpdatingProgrammatically = false // Consolidated flag

    companion object {
        private const val MAX_INSTANCES_GENERAL = 7
        private const val MAX_INSTANCES_DICE = 7
        private const val TAG = "SettingsFragment"
    }

    // Adapters for dropdowns
    private lateinit var sumResultsAdapter: ArrayAdapter<String>
    private lateinit var coinProbabilityAdapter: ArrayAdapter<String>
    private lateinit var graphDistributionTypeAdapter: ArrayAdapter<String> // For Dice & Coin
    private lateinit var graphLineStyleAdapter: ArrayAdapter<String>      // For Dice & Coin

    private var selectedCoinColorView: View? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRandomizerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentRandomizerSettingsBinding.bind(view)
        // args = RandomizerSettingsFragmentArgs.fromBundle(requireArguments()) // Fetch instanceId for restart after crash?
        // currentInstanceIdForCloning = try { UUID.fromString(args.instanceId) } catch (e: Exception) { null }

        setupSpinners() // Consolidated spinner setup
        setupCoinColorPalette()
        observeViewModel()
        updateAddAnotherButtonState()
        observeSettingsAndSetupUI()
        setupSpecificListeners()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        selectedCoinColorView = null
    }

    private fun setupSpinners() {
        // Dice Sum Results
        val sumResultTypeNames = DiceSumResultType.values().map {
            getString(when (it) {
                DiceSumResultType.INDIVIDUAL -> R.string.dice_sum_type_individual
                DiceSumResultType.SUM_TYPE -> R.string.dice_sum_type_sum_type
                DiceSumResultType.SUM_TOTAL -> R.string.dice_sum_type_sum_total
            })
        }.toTypedArray()
        sumResultsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sumResultTypeNames)
        binding.autoCompleteTextViewSumResults.setAdapter(sumResultsAdapter)

        // Coin Probability
        val coinProbNames = CoinProbabilityMode.values().map {
            getString(when (it) {
                CoinProbabilityMode.NONE -> R.string.setting_probability_none
                CoinProbabilityMode.TWO_COLUMNS -> R.string.setting_probability_two_columns
                CoinProbabilityMode.GRID_3X3 -> R.string.setting_probability_grid_3x3
                CoinProbabilityMode.GRID_6X6 -> R.string.setting_probability_grid_6x6
                CoinProbabilityMode.GRID_10X10 -> R.string.setting_probability_grid_10x10
                CoinProbabilityMode.GRAPH_DISTRIBUTION -> R.string.setting_probability_graph_distribution
            })
        }.toTypedArray()
        coinProbabilityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, coinProbNames)
        binding.autoCompleteCoinProbability.setAdapter(coinProbabilityAdapter)

        // Graph Distribution Type (shared by Dice and Coin)
        val graphDistTypeNames = GraphDistributionType.values().map {
            getString(when (it) {
                GraphDistributionType.OFF -> R.string.graph_dist_type_off
                GraphDistributionType.MANUAL -> R.string.graph_dist_type_manual
                GraphDistributionType.NORMAL -> R.string.graph_dist_type_normal
                GraphDistributionType.UNIFORM -> R.string.graph_dist_type_uniform
            })
        }.toTypedArray()
        graphDistributionTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, graphDistTypeNames)
        // Assuming you'll have autoCompleteDiceGraphDistributionType and autoCompleteCoinGraphDistributionType
        // binding.autoCompleteDiceGraphDistributionType.setAdapter(graphDistributionTypeAdapter) // For Dice (if you add it)
        binding.autoCompleteCoinGraphDistributionType.setAdapter(graphDistributionTypeAdapter)


        // Graph Plot Type (shared by Dice and Coin)
        val graphPlotTypeNames = GraphPlotType.values().map {
            getString(when (it) {
                GraphPlotType.HISTOGRAM -> R.string.graph_plot_type_histogram
                GraphPlotType.LINE_GRAPH -> R.string.graph_plot_type_line
                GraphPlotType.QQ_PLOT -> R.string.graph_plot_type_qq
            })
        }.toTypedArray()
        graphLineStyleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, graphPlotTypeNames)
        // binding.autoCompleteDiceGraphLineStyle.setAdapter(graphLineStyleAdapter) // For Dice (if you add it)
        binding.autoCompleteCoinGraphLineStyle.setAdapter(graphLineStyleAdapter)
    }

    private fun setupCoinColorPalette() {
        binding.coinColorPalette.removeAllViews()
        val defaultColorInt = context?.let { ContextCompat.getColor(it, R.color.goldenrod) } ?: Color.YELLOW

        PurramidPalette.appStandardPalette.forEachIndexed { index, namedColor ->
            val colorView = View(requireContext()).apply {
                val sizeInDp = 32 // Smaller for settings
                val marginInDp = 4
                val sizeInPx = requireContext().dpToPx(sizeInDp)
                val marginInPx = requireContext().dpToPx(marginInDp)

                layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx).apply {
                    setMargins(marginInPx, marginInPx, marginInPx, marginInPx)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(namedColor.colorInt)
                    // Initial stroke based on whether it's selected (will be updated by observer)
                    val outlineColor = if (Color.luminance(namedColor.colorInt) > 0.5) Color.BLACK else Color.WHITE
                    setStroke(requireContext().dpToPx(1), outlineColor)
                }
                tag = namedColor.colorInt // Store colorInt in tag for easy retrieval
                setOnClickListener {
                    if (isUpdatingProgrammatically) return@setOnClickListener
                    viewModel.updateCoinColor(namedColor.colorInt)
                    // updateCoinColorSelectionUI will be called by the observer
                }
                // Long press for tooltip
                setOnLongClickListener {
                    val tooltipText = namedColor.name
                    val tooltip = Toast.makeText(context, tooltipText, Toast.LENGTH_SHORT)
                    // Position tooltip (this is a basic example, consider TooltipCompat for better positioning)
                    val location = IntArray(2)
                    it.getLocationOnScreen(location)
                    tooltip.setGravity(android.view.Gravity.TOP or android.view.Gravity.START, location[0], location[1] - it.height - 20)
                    tooltip.show()
                    true
                }
            }
            binding.coinColorPalette.addView(colorView)
        }
    }

    private fun updateCoinColorSelectionUI(selectedColorInt: Int) {
        isUpdatingProgrammatically = true
        binding.coinColorPalette.children.forEach { view ->
            val viewColorInt = view.tag as? Int
            if (viewColorInt != null) {
                val drawable = view.background as? GradientDrawable
                if (viewColorInt == selectedColorInt) {
                    drawable?.setStroke(requireContext().dpToPx(3), ContextCompat.getColor(requireContext(), R.color.design_default_color_primary)) // Highlight color
                    selectedCoinColorView = view
                } else {
                    val outlineColor = if (Color.luminance(viewColorInt) > 0.5) Color.BLACK else Color.WHITE
                    drawable?.setStroke(requireContext().dpToPx(1), outlineColor)
                }
            }
        }
        isUpdatingProgrammatically = false
    }

    private fun observeSettingsAndSetupUI() {
        settingsViewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings == null) {
                Log.e("SettingsFragment", "Settings are null!")
                // Consider showing an error state or disabling UI elements
                return@observe
            }
            currentSettings = settings // Cache current settings

            // General mode visibility
            binding.spinSettingsGroup.visibility = if (currentSettings.mode == RandomizerMode.SPIN) View.VISIBLE else View.GONE
            binding.slotsSettingsGroup.visibility = if (currentSettings.mode == RandomizerMode.SLOTS) View.VISIBLE else View.GONE
            binding.diceSettingsGroup.visibility = if (currentSettings.mode == RandomizerMode.DICE) View.VISIBLE else View.GONE
            binding.coinFlipSettingsLayout.visibility = if (currentSettings.mode == RandomizerMode.COIN_FLIP) View.VISIBLE else View.GONE

            // TODO: Confirm UI elements based on currentSettings are populated
            // ... (mode specific switch states, text fields etc.) ...

            // Dice Specific Settings
            if (currentSettings.mode == RandomizerMode.DICE) {
                binding.switchDiceAnimationEnabled.isChecked = currentSettings.isDiceAnimationEnabled
                binding.switchDiceSumResultsEnabled.isChecked = currentSettings.isDiceSumResultsEnabled

                // Graph related settings for Dice
                binding.switchDiceGraphEnabled.isChecked = currentSettings.isDiceGraphEnabled
                val diceGraphOptionsVisibility = if (currentSettings.isDiceGraphEnabled) View.VISIBLE else View.GONE

                binding.diceGraphPlotTypeLayout.visibility = diceGraphOptionsVisibility // Use your new ID
                if (currentSettings.isDiceGraphEnabled) {
                    // Setup for the NEW Dice Graph Plot Type dropdown
                    setupGraphPlotTypeDropdown(
                        binding.diceGraphPlotTypeDropDown, // Pass the AutoCompleteTextView
                        currentSettings.diceGraphPlotType,
                        PlotType.HISTOGRAM // Default if current setting is invalid
                    ) { selectedPlotTypeName ->
                        // Update currentSettings when a new plot type is selected for Dice
                        if (currentSettings.diceGraphPlotType != selectedPlotTypeName) {
                            currentSettings = currentSettings.copy(diceGraphPlotType = selectedPlotTypeName)
                        }
                    }
                }

                binding.diceGraphDistributionTypeLayout.visibility = diceGraphOptionsVisibility
                // setupGraphDistributionTypeDropdown(binding.diceGraphDistributionTypeDropDown, currentSettings.diceGraphDistributionType, ForDice) { ... }

                binding.diceGraphFlipCountLayout.visibility = diceGraphOptionsVisibility
                binding.textFieldDiceGraphFlipCount.setText(currentSettings.diceGraphFlipCount.takeIf { it > 0 }?.toString() ?: "")


                    // Update visibility of dependent graph options
                    val visibility = if (isChecked) View.VISIBLE else View.GONE
                    binding.diceGraphPlotTypeLayout.visibility = visibility
                    binding.diceGraphDistributionTypeLayout.visibility = visibility
                    binding.diceGraphFlipCountLayout.visibility = visibility
                    if (isChecked && binding.diceGraphPlotTypeDropDown.text.isEmpty()) {
                        // If enabling and dropdown is empty, populate/set it
                        setupDiceGraphPlotTypeDropdown(currentSettings.diceGraphPlotType)
                    }
                }

                // Listener for the NEW Dice Graph Plot Type Dropdown
                binding.diceGraphPlotTypeDropDown.setOnItemClickListener { parent, _, position, _ ->
                    val selectedPlotType = PlotType.values()[position]
                    if (currentSettings.diceGraphPlotType != selectedPlotType.name) {
                        currentSettings = currentSettings.copy(diceGraphPlotType = selectedPlotType.name)
                        Log.d(
                            "SettingsFragment",
                            "Dice Graph Plot Type selected: ${selectedPlotType.name}"
                        )
                    }
                }
            }
// TODO: Confirm when settings changes are saved for all modes and app intents

            // (TODO: similar setup for Coin Flip settings using its own IDs)
            if (currentSettings.mode == RandomizerMode.COIN_FLIP) {
                // Example for coin flip graph style (which you already have)
                // setupCoinGraphLineStyleDropdown(settings.coinGraphLineStyle)
                // binding.menuCoinGraphLineStyleLayout.visibility = if (settings.isCoinGraphEnabled) View.VISIBLE else View.GONE
            }

            // Update other shared settings like background color, etc.
            // binding.textFieldRandomizerBackgroundColor.setText(currentSettings.backgroundColor)
        }
    }

/**
 * Generic setup for a plot type dropdown.
 * @param autoCompleteView The AutoCompleteTextView to set up.
 * @param currentPlotTypeString The currently saved plot type name (from settings).
 * @param defaultPlotType The default PlotType to use if current is invalid.
 * @param onSelected Callback with the selected PlotType's name.
 */
private fun setupGraphPlotTypeDropdown(
    autoCompleteView: AutoCompleteTextView,
    currentPlotTypeString: String?,
    defaultPlotType: PlotType,
    onSelected: (String) -> Unit
) {
    val plotTypes = PlotType.values()
    val plotTypeDisplayNames = plotTypes.map {
        when (it) {
            PlotType.HISTOGRAM -> getString(R.string.plot_type_histogram)
            PlotType.LINE_GRAPH -> getString(R.string.plot_type_line_graph)
            PlotType.QQ_PLOT -> getString(R.string.plot_type_qq_graph) // You added this string
        }
    }

    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, plotTypeDisplayNames)
    autoCompleteView.setAdapter(adapter)

    val currentPlotType = PlotType.values().find { it.name == currentPlotTypeString } ?: defaultPlotType
    val currentDisplayName = plotTypeDisplayNames[plotTypes.indexOf(currentPlotType)]
    autoCompleteView.setText(currentDisplayName, false)

    // Set listener directly here or ensure it's set in setupSpecificListeners
    autoCompleteView.setOnItemClickListener { parent, _, position, _ ->
        val selectedPlotType = PlotType.values()[position]
        onSelected(selectedPlotType.name)
    }
}

    private fun setupDiceGraphPlotTypeDropdown(currentPlotTypeString: String?) {
        val plotTypes = PlotType.values()
        val plotTypeDisplayNames = plotTypes.map {
            when (it) {
                PlotType.HISTOGRAM -> getString(R.string.plot_type_histogram)
                PlotType.LINE_GRAPH -> getString(R.string.plot_type_line_graph)
                // Add QQ_PLOT when ready
            }
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, plotTypeDisplayNames)
        binding.diceGraphPlotTypeDropDown.setAdapter(adapter) // Use the new ID

        // Determine the current PlotType from the stored string name
        val currentPlotType = PlotType.values().find { it.name == currentPlotTypeString } ?: PlotType.HISTOGRAM
        val currentDisplayName = plotTypeDisplayNames[plotTypes.indexOf(currentPlotType)]
        binding.diceGraphPlotTypeDropDown.setText(currentDisplayName, false)
    }

    private fun setupSpecificListeners() {
        binding.closeSettingsButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // Common button for List Editor - visibility controlled by mode
        val listEditorClickListener = View.OnClickListener {
            try {
                val action = RandomizerSettingsFragmentDirections.actionSettingsToListCreator(null)
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to List Creator failed.", e)
                Snackbar.make(requireView(), "Cannot open List Editor", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.buttonListEditor.setOnClickListener(listEditorClickListener)
        binding.buttonListEditorSlots.setOnClickListener(listEditorClickListener)

        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isUpdatingProgrammatically || !isChecked) return@addOnButtonCheckedListener
            val selectedMode = when (checkedId) {
                R.id.buttonModeSpin -> RandomizerMode.SPIN
                R.id.buttonModeSlots -> RandomizerMode.SLOTS
                R.id.buttonModeDice -> RandomizerMode.DICE
                R.id.buttonModeCoinFlip -> RandomizerMode.COIN_FLIP
                else -> viewModel.settings.value?.mode
            }
            selectedMode?.let { viewModel.updateMode(it) }
            updateAddAnotherButtonState()
        }

        binding.slotsNumColumnsToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isUpdatingProgrammatically || !isChecked) return@addOnButtonCheckedListener
            val numColumns = when (checkedId) {
                R.id.buttonSlotsColumns3 -> 3
                R.id.buttonSlotsColumns5 -> 5
                else -> viewModel.settings.value?.numSlotsColumns ?: 3
            }
            viewModel.updateNumSlotsColumns(numColumns)
        }

        // --- Switches Listeners ---
        val commonSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingProgrammatically) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchIsAnnounceEnabled -> viewModel.updateIsAnnounceEnabled(isChecked)
                R.id.switchIsCelebrateEnabled -> viewModel.updateIsCelebrateEnabled(isChecked) // General celebrate (Spin)
                R.id.switchSpin -> viewModel.updateIsSpinEnabled(isChecked) // Spin specific
                R.id.switchIsSequenceEnabled -> viewModel.updateIsSequenceEnabled(isChecked) // Spin specific
                // Dice
                R.id.switchUseDicePips -> viewModel.updateUseDicePips(isChecked)
                R.id.switchIsPercentileDiceEnabled -> viewModel.updateIsPercentileDiceEnabled(isChecked)
                R.id.switchIsDiceAnimationEnabled -> viewModel.updateIsDiceAnimationEnabled(isChecked)
                R.id.switchIsDiceCritCelebrationEnabled -> viewModel.updateIsDiceCritCelebrationEnabled(isChecked)
                // Coin Flip
                R.id.switchCoinFlipAnimation -> viewModel.updateIsFlipAnimationEnabled(isChecked)
                R.id.switchCoinFreeForm -> viewModel.updateIsCoinFreeFormEnabled(isChecked)
                R.id.switchCoinAnnouncement -> viewModel.updateIsCoinAnnouncementEnabled(isChecked)
            }
        }
        binding.switchIsAnnounceEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsCelebrateEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchSpin.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsSequenceEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchUseDicePips.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsPercentileDiceEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsDiceAnimationEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsDiceCritCelebrationEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchCoinFlipAnimation.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchCoinFreeForm.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchCoinAnnouncement.setOnCheckedChangeListener(commonSwitchListener)


        // --- Buttons for Dialogs ---
        binding.buttonDicePoolConfig.setOnClickListener {
            currentInstanceIdForCloning?.let { instanceId ->
                DicePoolDialogFragment.newInstance(instanceId).show(parentFragmentManager, DicePoolDialogFragment.TAG)
            } ?: Log.e(TAG, "Cannot open Dice Pool Config: Instance ID is null")
        }
        binding.buttonConfigureDiceColors.setOnClickListener {
            currentInstanceIdForCloning?.let { instanceId ->
                DiceColorPickerDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DiceColorPickerDialogFragment.TAG)
            } ?: Log.e(TAG, "Cannot open Dice Color Picker: Instance ID is null")
        }
        binding.buttonConfigureDiceModifiers.setOnClickListener {
            currentInstanceIdForCloning?.let { instanceId ->
                DiceModifiersDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DiceModifiersDialogFragment.TAG)
            } ?: Log.e(TAG, "Cannot open Dice Modifiers: Instance ID is null")
        }

        // --- Dropdown (AutoCompleteTextView) Listeners ---
        binding.autoCompleteTextViewSumResults.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (isUpdatingProgrammatically) return@OnItemClickListener
                val selectedEnum = DiceSumResultType.values()[position]
                viewModel.updateDiceSumResultType(selectedEnum)
            }

        binding.autoCompleteCoinProbability.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (isUpdatingProgrammatically) return@OnItemClickListener
                val selectedEnum = CoinProbabilityMode.values()[position]
                viewModel.updateCoinProbabilityMode(selectedEnum)
            }

        binding.autoCompleteCoinGraphDistributionType.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (isUpdatingProgrammatically) return@OnItemClickListener
                val selectedEnum = GraphDistributionType.values()[position]
                viewModel.updateCoinGraphDistributionType(selectedEnum)
            }
        binding.autoCompleteCoinGraphLineStyle.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (isUpdatingProgrammatically) return@OnItemClickListener
                val selectedEnum = GraphPlotType.values()[position]
                viewModel.updateCoinGraphLineStyle(selectedEnum)
            }
        binding.textFieldCoinGraphFlipCount.doOnTextChanged { text, _, _, _ ->
            if (isUpdatingProgrammatically) return@doOnTextChanged
            val count = text.toString().toIntOrNull() ?: 1000 // Default if parse fails
            viewModel.updateCoinGraphFlipCount(count.coerceIn(1, 10000)) // Example range
        }
        // Add similar listeners for Dice Graph settings if those AutoCompleteTextViews are separate

        binding.buttonAddAnotherRandomizer.setOnClickListener {
            handleAddAnotherInstance()
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner
        viewModel.settings.observe(lifecycleOwner) { settingsEntity ->
            isUpdatingProgrammatically = true // Block listeners

            if (settingsEntity != null) {
                updateModeSelectionUI(settingsEntity.mode)

                // Common settings (visibility might depend on mode further down)
                binding.switchIsAnnounceEnabled.isChecked = settingsEntity.isAnnounceEnabled
                binding.switchIsCelebrateEnabled.isChecked = settingsEntity.isCelebrateEnabled

                // Spin specific
                binding.switchSpin.isChecked = settingsEntity.isSpinEnabled
                binding.switchIsSequenceEnabled.isChecked = settingsEntity.isSequenceEnabled

                // Slots specific
                val slotsButtonToCheck = when (settingsEntity.numSlotsColumns) {
                    5 -> R.id.buttonSlotsColumns5
                    else -> R.id.buttonSlotsColumns3
                }
                if (binding.slotsNumColumnsToggleGroup.checkedButtonId != slotsButtonToCheck) {
                    binding.slotsNumColumnsToggleGroup.check(slotsButtonToCheck)
                }

                // Dice specific
                binding.switchUseDicePips.isChecked = settingsEntity.useDicePips
                binding.switchIsPercentileDiceEnabled.isChecked = settingsEntity.isPercentileDiceEnabled
                binding.switchIsDiceAnimationEnabled.isChecked = settingsEntity.isDiceAnimationEnabled
                binding.switchIsDiceCritCelebrationEnabled.isChecked = settingsEntity.isDiceCritCelebrationEnabled
                setSpinnerSelection(binding.autoCompleteTextViewSumResults, sumResultsAdapter, settingsEntity.diceSumResultType.ordinal)

                // Coin Flip specific
                updateCoinColorSelectionUI(settingsEntity.coinColor)
                binding.switchCoinFlipAnimation.isChecked = settingsEntity.isFlipAnimationEnabled
                binding.switchCoinFreeForm.isChecked = settingsEntity.isCoinFreeFormEnabled
                binding.switchCoinAnnouncement.isChecked = settingsEntity.isCoinAnnouncementEnabled

                val coinProbMode = CoinProbabilityMode.valueOf(settingsEntity.coinProbabilityMode)
                setSpinnerSelection(binding.autoCompleteCoinProbability, coinProbabilityAdapter, coinProbMode.ordinal)

                val coinGraphDistType = GraphDistributionType.valueOf(settingsEntity.coinGraphDistributionType)
                setSpinnerSelection(binding.autoCompleteCoinGraphDistributionType, graphDistributionTypeAdapter, coinGraphDistType.ordinal)

                val coinGraphPlotType = GraphPlotType.valueOf(settingsEntity.coinGraphPlotType)
                setSpinnerSelection(binding.autoCompleteCoinGraphLineStyle, graphLineStyleAdapter, coinGraphPlotType.ordinal)

                if (binding.textFieldCoinGraphFlipCount.text.toString() != settingsEntity.coinGraphFlipCount.toString()) {
                    binding.textFieldCoinGraphFlipCount.setText(settingsEntity.coinGraphFlipCount.toString())
                }

                updateControlEnablementAndVisibility(settingsEntity) // New combined function
                binding.textViewSettingsPlaceholder.visibility = View.GONE
                // enableAllControls(true) // enableAllControls is now part of updateControlEnablementAndVisibility

            } else {
                // Handle null settings (error case)
                binding.textViewSettingsPlaceholder.text = getString(R.string.error_settings_load_failed)
                binding.textViewSettingsPlaceholder.visibility = View.VISIBLE
                enableAllControls(false) // Disable all controls
                binding.modeToggleGroup.clearChecked()
                binding.slotsNumColumnsToggleGroup.clearChecked()
                binding.autoCompleteTextViewSumResults.setText("", false)
                binding.autoCompleteCoinProbability.setText("", false)
                binding.autoCompleteCoinGraphDistributionType.setText("", false)
                binding.autoCompleteCoinGraphLineStyle.setText("", false)
                binding.textFieldCoinGraphFlipCount.setText("")
                updateCoinColorSelectionUI(ContextCompat.getColor(requireContext(), R.color.goldenrod)) // Default color UI
            }
            isUpdatingProgrammatically = false // Re-enable listeners
        }

        viewModel.errorEvent.observe(lifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { resId ->
                if (resId != 0) { // Check if it's a real error or just a clear event
                    val message = getString(resId)
                    if (resId == R.string.error_settings_instance_id_failed) {
                        Log.e(TAG, "Critical Error: $message - Navigating back.")
                        findNavController().popBackStack()
                    } else {
                        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.snackbar_action_ok)) {}
                            .show()
                    }
                }
            }
        }
    }

    private fun <T> setSpinnerSelection(spinner: AutoCompleteTextView, adapter: ArrayAdapter<T>, selectionOrdinal: Int) {
        if (selectionOrdinal >= 0 && selectionOrdinal < adapter.count) {
            val selectionName = adapter.getItem(selectionOrdinal).toString()
            if (spinner.text.toString() != selectionName) {
                spinner.setText(selectionName, false)
            }
        } else {
            spinner.setText("", false) // Clear if invalid ordinal
        }
    }

    private fun enableAllControls(enabled: Boolean) {
        // This is a fallback, more granular control is in updateControlEnablementAndVisibility
        binding.modeToggleGroup.isEnabled = enabled
        binding.buttonListEditor.isEnabled = enabled
        binding.buttonListEditorSlots.isEnabled = enabled
        binding.slotsNumColumnsToggleGroup.isEnabled = enabled
        binding.buttonDicePoolConfig.isEnabled = enabled
        binding.buttonConfigureDiceColors.isEnabled = enabled
        binding.buttonConfigureDiceModifiers.isEnabled = enabled
        // ... and so on for all switches and input fields ...
        binding.switchIsAnnounceEnabled.isEnabled = enabled
        // ... all other switches ...
        binding.autoCompleteTextViewSumResults.isEnabled = enabled
        // ... all coin flip controls ...
        binding.coinColorPalette.children.forEach { it.isEnabled = enabled }
        binding.switchCoinFlipAnimation.isEnabled = enabled
        binding.switchCoinFreeForm.isEnabled = enabled
        binding.switchCoinAnnouncement.isEnabled = enabled
        binding.autoCompleteCoinProbability.isEnabled = enabled
        binding.autoCompleteCoinGraphDistributionType.isEnabled = enabled
        binding.autoCompleteCoinGraphLineStyle.isEnabled = enabled
        binding.textFieldCoinGraphFlipCount.isEnabled = enabled

        binding.buttonAddAnotherRandomizer.isEnabled = enabled
        if (enabled) updateAddAnotherButtonState() // Apply logic if enabling
    }

    private fun updateModeSelectionUI(currentMode: RandomizerMode) {
        isUpdatingProgrammatically = true // Block mode toggle listener temporarily
        val buttonIdToCheck = when (currentMode) {
            RandomizerMode.SPIN -> R.id.buttonModeSpin
            RandomizerMode.SLOTS -> R.id.buttonModeSlots
            RandomizerMode.DICE -> R.id.buttonModeDice
            RandomizerMode.COIN_FLIP -> R.id.buttonModeCoinFlip
        }
        if (binding.modeToggleGroup.checkedButtonId != buttonIdToCheck) {
            binding.modeToggleGroup.check(buttonIdToCheck)
        }
        isUpdatingProgrammatically = false

        // Update visibility of mode-specific sections
        binding.spinSpecificSettingsLayout.isVisible = currentMode == RandomizerMode.SPIN
        binding.slotsSettingsLayout.isVisible = currentMode == RandomizerMode.SLOTS
        binding.diceSettingsLayout.isVisible = currentMode == RandomizerMode.DICE
        binding.coinFlipSettingsLayout.isVisible = currentMode == RandomizerMode.COIN_FLIP

        // List editor button visibility (shared by Spin and Slots)
        binding.buttonListEditor.isVisible = currentMode == RandomizerMode.SPIN
        binding.buttonListEditorSlots.isVisible = currentMode == RandomizerMode.SLOTS


        // Update general "Announce" and "Celebrate" switch visibility based on mode
        val announceVisibleForMode = currentMode in listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS, RandomizerMode.DICE)
        binding.switchIsAnnounceEnabled.isVisible = announceVisibleForMode
        // General "Celebrate" is only for Spin
        binding.switchIsCelebrateEnabled.isVisible = currentMode == RandomizerMode.SPIN

        updateAddAnotherButtonState()
        viewModel.settings.value?.let { updateControlEnablementAndVisibility(it) }
    }

    private fun updateControlEnablementAndVisibility(settings: SpinSettingsEntity) {
        isUpdatingProgrammatically = true // Block listeners during these updates

        val currentMode = settings.mode

        // --- General Announce & Celebrate ---
        // Visibility is already set by updateModeSelectionUI
        // Enablement of general Announce (based on other conflicting settings within its mode)
        when (currentMode) {
            RandomizerMode.SPIN -> {
                binding.switchIsAnnounceEnabled.isEnabled = !settings.isSequenceEnabled
                binding.switchIsCelebrateEnabled.isEnabled = !settings.isSequenceEnabled && settings.isAnnounceEnabled
            }
            RandomizerMode.DICE -> {
                binding.switchIsAnnounceEnabled.isEnabled = settings.graphDistributionType == GraphDistributionType.OFF
            }
            RandomizerMode.SLOTS -> {
                binding.switchIsAnnounceEnabled.isEnabled = true // Slots Announce is independent for now
            }
            RandomizerMode.COIN_FLIP -> {
                // General Announce switch maps to Coin-specific Announce
                binding.switchIsAnnounceEnabled.isChecked = settings.isCoinAnnouncementEnabled
                binding.switchIsAnnounceEnabled.isEnabled = !settings.isCoinFreeFormEnabled &&
                        CoinProbabilityMode.valueOf(settings.coinProbabilityMode) == CoinProbabilityMode.NONE
            }
        }


        // --- Dice Specific (dependent on Dice Announce) ---
        if (currentMode == RandomizerMode.DICE) {
            val diceAnnounceOn = settings.isAnnounceEnabled // General announce maps to dice announce
            binding.menuSumResultsLayout.isVisible = diceAnnounceOn
            binding.buttonConfigureDiceModifiers.isEnabled = diceAnnounceOn
            binding.switchIsDiceCritCelebrationEnabled.isVisible = diceAnnounceOn
            binding.switchIsDiceCritCelebrationEnabled.isEnabled = diceAnnounceOn // Already checks announce in VM, but UI can reflect too
        }

        // --- Coin Flip Specific (dependent on each other) ---
        if (currentMode == RandomizerMode.COIN_FLIP) {
            val coinProbMode = CoinProbabilityMode.valueOf(settings.coinProbabilityMode)

            // Coin Announcement switch
            binding.switchCoinAnnouncement.isEnabled = !settings.isCoinFreeFormEnabled && coinProbMode == CoinProbabilityMode.NONE
            // binding.switchCoinAnnouncement.isChecked = settings.isCoinAnnouncementEnabled // This is set by general Announce observer

            // Coin Free Form switch
            binding.switchCoinFreeForm.isEnabled = !settings.isCoinAnnouncementEnabled && coinProbMode == CoinProbabilityMode.NONE

            // Coin Probability dropdown
            binding.menuCoinProbabilityLayout.isEnabled = !settings.isCoinAnnouncementEnabled && !settings.isCoinFreeFormEnabled

            // Coin Graph sub-settings
            val showCoinGraphSettings = coinProbMode == CoinProbabilityMode.GRAPH_DISTRIBUTION &&
                    !settings.isCoinAnnouncementEnabled && !settings.isCoinFreeFormEnabled
            binding.coinGraphSettingsLayout.isVisible = showCoinGraphSettings
            binding.autoCompleteCoinGraphDistributionType.isEnabled = showCoinGraphSettings
            binding.autoCompleteCoinGraphLineStyle.isEnabled = showCoinGraphSettings
            binding.textFieldCoinGraphFlipCountLayout.isEnabled = showCoinGraphSettings
            binding.textFieldCoinGraphFlipCount.isEnabled = showCoinGraphSettings
        }

        isUpdatingProgrammatically = false
    }


    private fun updateAddAnotherButtonState() {
        val currentMode = viewModel.settings.value?.mode
        lifecycleScope.launch { // Use coroutine for DB access
            val currentInstanceCount = withContext(Dispatchers.IO) {
                randomizerDao.getAllNonDefaultInstances().size
            }
            val limit = if (currentMode == RandomizerMode.DICE) MAX_INSTANCES_DICE else MAX_INSTANCES_GENERAL
            binding.buttonAddAnotherRandomizer.isEnabled = currentInstanceCount < limit
        }
    }

    private fun handleAddAnotherInstance() {
        val currentSettingsEntity = viewModel.settings.value
        if (currentSettingsEntity == null) {
            Snackbar.make(binding.root, R.string.error_settings_not_loaded_cant_save, Snackbar.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val currentInstanceCount = withContext(Dispatchers.IO) {
                randomizerDao.getAllNonDefaultInstances().size
            }
            val limit = if (currentSettingsEntity.mode == RandomizerMode.DICE) MAX_INSTANCES_DICE else MAX_INSTANCES_GENERAL

            if (currentInstanceCount >= limit) {
                val messageResId = if (currentSettingsEntity.mode == RandomizerMode.DICE) {
                    R.string.max_randomizers_dice_reached_snackbar
                } else {
                    R.string.max_randomizers_general_reached_snackbar
                }
                Snackbar.make(binding.root, getString(messageResId, limit), Snackbar.LENGTH_LONG).show()
                return@launch
            }

            val newInstanceId = UUID.randomUUID()
            // Clone current settings for the new instance
            val newSettings = currentSettingsEntity.copy(
                instanceId = newInstanceId,
                // Reset any instance-specific states if necessary, e.g., selected list for slots
                slotsColumnStates = emptyList() // Example: reset slots column states
            )
            val newInstanceEntity = RandomizerInstanceEntity(instanceId = newInstanceId)

            try {
                withContext(Dispatchers.IO) {
                    randomizerDao.saveSettings(newSettings)
                    randomizerDao.saveInstance(newInstanceEntity)
                }
                Log.d(TAG, "Cloned settings and created new instance: $newInstanceId")

                val intent = Intent(requireActivity(), RandomizersHostActivity::class.java).apply {
                    putExtra(RandomizersHostActivity.EXTRA_INSTANCE_ID, newInstanceId.toString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                requireActivity().startActivity(intent)
                updateAddAnotherButtonState() // Refresh button state

            } catch (e: Exception) {
                Log.e(TAG, "Error saving new cloned instance or launching activity", e)
                Snackbar.make(binding.root, "Error creating new randomizer window.", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}