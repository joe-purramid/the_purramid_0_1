package com.example.purramid.thepurramid.probabilities.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.animation.DiceAnimationHelper
import com.example.purramid.thepurramid.probabilities.data.DicePosition
import com.example.purramid.thepurramid.probabilities.data.ProbabilitiesPositionState
import com.example.purramid.thepurramid.probabilities.DiceSumResultType
import com.example.purramid.thepurramid.probabilities.ProbabilitiesHostActivity
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import com.example.purramid.thepurramid.probabilities.util.ProbabilitiesLayoutHelper
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.DieType
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.flexbox.FlexboxLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import nl.dionsegijn.konfetti.xml.KonfettiView

@AndroidEntryPoint
class DiceMainFragment : Fragment() {
    @Inject lateinit var positionManager: ProbabilitiesPositionManager
    private val diceViewModel: DiceViewModel by activityViewModels()
    private var instanceId: Int = 1

    private lateinit var diceDisplayArea: FlexboxLayout
    private lateinit var announcementTextView: TextView
    private lateinit var konfettiView: KonfettiView
    private lateinit var rollButton: Button
    private lateinit var resetButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dice_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        diceDisplayArea = view.findViewById(R.id.diceDisplayArea)
        announcementTextView = view.findViewById(R.id.diceAnnouncementTextView)
        konfettiView = view.findViewById(R.id.konfettiViewDice)
        rollButton = view.findViewById(R.id.diceRollButton)
        resetButton = view.findViewById(R.id.diceResetButton)

        val rollButton = view.findViewById<Button>(R.id.dicePoolButton)
        val resetButton = view.findViewById<Button>(R.id.buttonReset)
//        val resultText = view.findViewById<TextView>(R.id.textDiceResult)
        val barChart = view.findViewById<BarChart>(R.id.diceBarChart)

        // Theme-aware colors
        val isDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val barColor = ContextCompat.getColor(requireContext(), if (isDark) R.color.teal_200 else R.color.purple_500)
        val axisTextColor = ContextCompat.getColor(requireContext(), if (isDark) android.R.color.white else android.R.color.darker_gray)
        val valueTextColor = axisTextColor

        // Chart styling
        barChart.setDrawGridBackground(false)
        barChart.setDrawBarShadow(false)
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = true
        barChart.legend.textSize = 12f
        barChart.legend.form = com.github.mikephil.charting.components.Legend.LegendForm.SQUARE
        barChart.legend.textColor = axisTextColor
        barChart.setNoDataText("No data yet. Roll the dice!")
        barChart.setTouchEnabled(false)
        barChart.setScaleEnabled(false)
        barChart.setPinchZoom(false)
        barChart.axisRight.isEnabled = false
        barChart.axisLeft.textColor = axisTextColor
        barChart.xAxis.textColor = axisTextColor
        barChart.axisLeft.setDrawGridLines(false)
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.granularity = 1f
        barChart.xAxis.labelRotationAngle = 0f
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.granularity = 1f
        barChart.setExtraOffsets(0f, 16f, 0f, 16f)

        val updateGraph = {
            val settings = diceViewModel.settings.value
            val result = diceViewModel.result.value
            if (settings?.graphEnabled == true && result != null) {
                barChart.visibility = View.VISIBLE
                val freq = mutableMapOf<Int, Int>()
                for (rolls in result.results.values) {
                    for (roll in rolls) freq[roll] = (freq[roll] ?: 0) + 1
                }
                val sorted = freq.entries.sortedBy { it.key }
                val entries = sorted.mapIndexed { idx, (value, count) ->
                    BarEntry(idx.toFloat(), count.toFloat())
                }
                val dataSet = BarDataSet(entries, "Dice Results")
                dataSet.color = barColor
                dataSet.valueTextColor = valueTextColor
                dataSet.valueTextSize = 14f
                dataSet.valueFormatter = object : ValueFormatter() {
                    override fun getBarLabel(barEntry: BarEntry?): String {
                        return barEntry?.y?.toInt()?.toString() ?: ""
                    }
                }
                val barData = BarData(dataSet)
                barData.barWidth = 0.8f
                barChart.data = barData
                barChart.xAxis.valueFormatter = IndexAxisValueFormatter(sorted.map { it.key.toString() })
                barChart.xAxis.labelCount = sorted.size
                barChart.animateY(700)
                barChart.invalidate()
            } else {
                barChart.visibility = View.GONE
            }
        }

        rollButton.setOnClickListener { diceViewModel.rollDice() }
        resetButton.setOnClickListener {
            diceViewModel.reset()
            resetDiceDisplay() }
        dicePoolButton.setOnClickListener { showDicePoolDialog() }
        settingsButton.setOnClickListener { openSettings() }
        closeButton.setOnClickListener { activity?.finish() }

        diceViewModel.result.observe(viewLifecycleOwner) { result ->
            resultText?.text = result?.results?.entries?.joinToString("\n") { (type, rolls) ->
                "${type.name}: ${rolls.joinToString(", ")}"
            } ?: ""
            updateGraph()
        }
        diceViewModel.loadSettings(requireContext(), instanceId)

        observeViewModel()
        setupDicePositions()
    }

    private fun observeViewModel() {
        diceViewModel.settings.observe(viewLifecycleOwner) { settings ->
            updateDiceDisplay(settings)
        }

        diceViewModel.result.observe(viewLifecycleOwner) { result ->
            result?.let {
                displayResults(it)
                resetButton.visibility = View.VISIBLE
            } ?: run {
                resetButton.visibility = View.INVISIBLE
            }
        }

        diceViewModel.rollAnimationTrigger.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                animateDiceRoll()
            }
        }

        diceViewModel.criticalHit.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                showCriticalCelebration()
            }
        }

        diceViewModel.error.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { error ->
                showError(error)
            }
        }
    }

    private fun updateDiceDisplay(settings: DiceSettings) {
        diceDisplayArea.removeAllViews()

        settings.dieConfigs.forEach { config ->
            if (config.quantity > 0) {
                for (i in 0 until config.quantity) {
                    val dieView = createDieView(config.type, config.color, config.usePips)
                    diceDisplayArea.addView(dieView)
                }
            }
        }

        if (settings.usePercentile) {
            val percentileConfig = settings.dieConfigs.find { it.type == DieType.PERCENTILE }
            if (percentileConfig != null && percentileConfig.quantity > 0) {
                for (i in 0 until percentileConfig.quantity) {
                    val tensView = createDieView(DieType.D10, percentileConfig.color, false, true)
                    val onesView = createDieView(DieType.D10, percentileConfig.color, false, false)
                    diceDisplayArea.addView(tensView)
                    diceDisplayArea.addView(onesView)
                }
            }
        }

        setupDicePositions()
    }

    private fun createDieView(type: DieType, color: Int, usePips: Boolean, isTens: Boolean = false): FrameLayout {
        val frameLayout = FrameLayout(requireContext())
        frameLayout.layoutParams = ViewGroup.LayoutParams(64.dpToPx(), 64.dpToPx())

        val imageView = ImageView(requireContext())
        val drawableRes = when (type) {
            DieType.D4 -> R.drawable.d4_result_blank
            DieType.D6 -> if (usePips) R.drawable.d6_pips_blank else R.drawable.d6_result_blank
            DieType.D8 -> R.drawable.d8_result_blank
            DieType.D10 -> R.drawable.d10_result_blank
            DieType.D12 -> R.drawable.d12_result_blank
            DieType.D20 -> R.drawable.d20_result_blank
            DieType.PERCENTILE -> R.drawable.dp_result_blank
        }
        imageView.setImageResource(drawableRes)
        imageView.setColorFilter(color)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        frameLayout.addView(imageView)

        val textView = TextView(requireContext())
        textView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        textView.textSize = 18f
        textView.gravity = android.view.Gravity.CENTER
        val maxValue = when (type) {
            DieType.D4 -> 4
            DieType.D6 -> 6
            DieType.D8 -> 8
            DieType.D10 -> if (isTens) 90 else 10
            DieType.D12 -> 12
            DieType.D20 -> 20
            DieType.PERCENTILE -> 100
        }
        textView.text = maxValue.toString()
        textView.tag = R.id.die_result_text
        frameLayout.addView(textView)

        frameLayout.setTag(R.id.die_type, type)
        frameLayout.setTag(R.id.die_is_tens, isTens)

        return frameLayout
    }

    private fun createDieView(type: DieType, color: Int, usePips: Boolean, isTens: Boolean = false): FrameLayout {
        val frameLayout = FrameLayout(requireContext())
        frameLayout.layoutParams = ViewGroup.LayoutParams(64.dpToPx(), 64.dpToPx())

        val imageView = ImageView(requireContext())
        val drawableRes = when (type) {
            DieType.D4 -> R.drawable.d4_result_blank
            DieType.D6 -> if (usePips) R.drawable.d6_pips_blank else R.drawable.d6_result_blank
            DieType.D8 -> R.drawable.d8_result_blank
            DieType.D10 -> R.drawable.d10_result_blank
            DieType.D12 -> R.drawable.d12_result_blank
            DieType.D20 -> R.drawable.d20_result_blank
            DieType.PERCENTILE -> R.drawable.dp_result_blank
        }
        imageView.setImageResource(drawableRes)
        imageView.setColorFilter(color)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        frameLayout.addView(imageView)

        val textView = TextView(requireContext())
        textView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        textView.textSize = 18f
        textView.gravity = android.view.Gravity.CENTER
        val maxValue = when (type) {
            DieType.D4 -> 4
            DieType.D6 -> 6
            DieType.D8 -> 8
            DieType.D10 -> if (isTens) 90 else 10
            DieType.D12 -> 12
            DieType.D20 -> 20
            DieType.PERCENTILE -> 100
        }
        textView.text = maxValue.toString()
        textView.tag = R.id.die_result_text
        frameLayout.addView(textView)

        frameLayout.setTag(R.id.die_type, type)
        frameLayout.setTag(R.id.die_is_tens, isTens)

        return frameLayout
    }

    private fun displayResults(result: DiceResult) {
        val settings = diceViewModel.settings.value ?: return

        var dieIndex = 0
        result.results.forEach { (type, rolls) ->
            rolls.forEachIndexed { rollIndex, value ->
                if (dieIndex < diceDisplayArea.childCount) {
                    val dieView = diceDisplayArea.getChildAt(dieIndex) as? FrameLayout
                    val textView = dieView?.findViewWithTag<TextView>(R.id.die_result_text)
                    textView?.text = value.toString()
                    dieIndex++
                }
            }
        }

        if (settings.announce) {
            showAnnouncement(result, settings)
        }
    }

    private fun showAnnouncement(result: DiceResult, settings: DiceSettings) {
        val announcement = buildString {
            when (settings.sumResultType) {
                DiceSumResultType.INDIVIDUAL -> {
                    result.results.forEach { (type, rolls) ->
                        val modifier = result.modifiers[type] ?: 0
                        if (rolls.isNotEmpty()) {
                            append("$type: ")
                            append(rolls.joinToString(", ") { roll ->
                                if (modifier != 0) {
                                    "$roll (${roll + modifier})"
                                } else {
                                    roll.toString()
                                }
                            })
                            appendLine()
                        }
                    }
                }
                DiceSumResultType.SUM_TYPE -> {
                    diceViewModel.getSumByType().forEach { (type, sum) ->
                        appendLine("$type Total: $sum")
                    }
                }
                DiceSumResultType.SUM_TOTAL -> {
                    append("Total Sum: ${diceViewModel.getTotalSum()}")
                }
            }
        }

        announcementTextView.text = announcement
        announcementTextView.visibility = View.VISIBLE

        announcementTextView.postDelayed({
            announcementTextView.visibility = View.GONE
        }, 3000)
    }

    private fun animateDiceRoll() {
        val settings = diceViewModel.settings.value ?: return

        for (i in 0 until diceDisplayArea.childCount) {
            val dieView = diceDisplayArea.getChildAt(i)
            if (settings.graphEnabled && settings.graphDistribution == GraphDistributionType.MANUAL) {
                DiceAnimationHelper.animateDiceRollShort(dieView) {}
            } else {
                DiceAnimationHelper.animateDiceRoll(dieView) {}
            }
        }
    }

    private fun showCriticalCelebration() {
        konfettiView.start(
            konfettiView.emitterConfig
                .emitting(100, 3000)
                .spread(360)
                .speed(0f, 10f)
                .position(0.5, 0.5)
        )
    }

    private fun resetDiceDisplay() {
        for (i in 0 until diceDisplayArea.childCount) {
            val dieView = diceDisplayArea.getChildAt(i) as? FrameLayout
            val textView = dieView?.findViewWithTag<TextView>(R.id.die_result_text)
            val type = dieView?.getTag(R.id.die_type) as? DieType
            val isTens = dieView?.getTag(R.id.die_is_tens) as? Boolean ?: false

            val maxValue = when (type) {
                DieType.D4 -> 4
                DieType.D6 -> 6
                DieType.D8 -> 8
                DieType.D10 -> if (isTens) 90 else 10
                DieType.D12 -> 12
                DieType.D20 -> 20
                DieType.PERCENTILE -> 100
                null -> 6
            }
            textView?.text = maxValue.toString()
        }

        announcementTextView.visibility = View.GONE
    }

    private fun showDicePoolDialog() {
        DicePoolDialogFragment().show(childFragmentManager, "DicePoolDialog")
    }

    private fun openSettings() {
        val bundle = Bundle().apply {
            putInt("instanceId", instanceId)
        }

        val settingsFragment = ProbabilitiesSettingsFragment().apply {
            arguments = bundle
        }

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.nav_host_fragment_probabilities, settingsFragment)
            .addToBackStack(null)
            .commit()

        // Highlight the current window with yellow border
        activity?.window?.decorView?.foreground = ContextCompat.getDrawable(
            requireContext(),
            R.drawable.highlight_border
        )
    }

    private fun showError(message: String) {
        view?.let { rootView ->
            com.google.android.material.snackbar.Snackbar.make(
                rootView,
                message,
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.snackbar_action_ok)) {
                // Dismiss action
            }.show()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupDicePositions() {
        val instanceId = arguments?.getInt(ProbabilitiesHostActivity.EXTRA_INSTANCE_ID) ?: 1
        val savedPositions = positionManager.loadPositions(instanceId)

        if (savedPositions?.dicePositions?.isNotEmpty() == true) {
            // Restore saved positions
            restoreDicePositions(savedPositions.dicePositions)
        } else {
            // Calculate and set default positions
            val diceDisplayArea = view?.findViewById<FlexboxLayout>(R.id.diceDisplayArea) ?: return

            diceDisplayArea.post {
                val settings = diceViewModel.settings.value ?: return@post
                val diceGroups = mutableMapOf<DieType, Int>()

                settings.dieConfigs.forEach { config ->
                    if (config.quantity > 0) {
                        diceGroups[config.type] = config.quantity
                    }
                }

                val defaultPositions = ProbabilitiesLayoutHelper.calculateDefaultDicePositions(
                    diceDisplayArea.width,
                    diceDisplayArea.height,
                    diceGroups
                )

                // Apply positions to dice views
                applyDicePositions(defaultPositions)

                // Save the default positions
                val positionState = ProbabilitiesPositionState(
                    instanceId = instanceId,
                    mode = ProbabilitiesMode.DICE,
                    dicePositions = defaultPositions
                )
                positionManager.savePositions(instanceId, positionState)
            }
        }
    }

    private fun applyDicePositions(positions: List<DicePosition>) {
        positions.forEach { position ->
            val dieView = findDieViewByTypeAndIndex(position.dieType, position.index)
            dieView?.apply {
                x = position.x
                y = position.y
                rotation = position.rotation
            }
        }
    }

    private fun findDieViewByTypeAndIndex(dieType: DieType, index: Int): View? {
        val diceDisplayArea = view?.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.diceDisplayArea) ?: return null

        var currentIndex = 0
        for (i in 0 until diceDisplayArea.childCount) {
            val childView = diceDisplayArea.getChildAt(i)
            val viewDieType = childView.getTag(R.id.die_type) as? DieType

            if (viewDieType == dieType) {
                if (currentIndex == index) {
                    return childView
                }
                currentIndex++
            }
        }
        return null
    }

    private fun restoreDicePositions(positions: List<DicePosition>) {
        positions.forEach { position ->
            val dieView = findDieViewByTypeAndIndex(position.dieType, position.index)
            dieView?.apply {
                x = position.x
                y = position.y
                rotation = position.rotation
                position.lastResult?.let { result ->
                    // Update the die to show the last result
                    if (this is FrameLayout) {
                        val textView = findViewById<TextView>(R.id.dieResultTextView)
                        textView?.text = result.toString()
                    }
                }
            }
        }
    }
} 