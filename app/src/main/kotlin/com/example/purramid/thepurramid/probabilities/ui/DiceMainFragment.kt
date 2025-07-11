package com.example.purramid.thepurramid.probabilities.ui

import android.graphics.Color
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
import com.example.purramid.thepurramid.probabilities.data.DicePosition
import com.example.purramid.thepurramid.probabilities.data.ProbabilitiesPositionState
import com.example.purramid.thepurramid.probabilities.ProbabilitiesHostActivity
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
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

class DiceMainFragment : Fragment() {
    private val diceViewModel: DiceViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dice_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rollButton = view.findViewById<Button>(R.id.buttonRoll)
        val resetButton = view.findViewById<Button>(R.id.buttonReset)
        val resultText = view.findViewById<TextView>(R.id.textDiceResult)
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

        rollButton?.setOnClickListener { diceViewModel.rollDice() }
        resetButton?.setOnClickListener { diceViewModel.reset() }

        diceViewModel.result.observe(viewLifecycleOwner) { result ->
            resultText?.text = result?.results?.entries?.joinToString("\n") { (type, rolls) ->
                "${type.name}: ${rolls.joinToString(", ")}"
            } ?: ""
            updateGraph()
        }
        diceViewModel.settings.observe(viewLifecycleOwner) { updateGraph() }
        setupDicePositions()
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