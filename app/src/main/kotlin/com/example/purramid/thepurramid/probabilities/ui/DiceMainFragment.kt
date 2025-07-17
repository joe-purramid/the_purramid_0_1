package com.example.purramid.thepurramid.probabilities.ui

import android.R.attr.x
import android.R.attr.y
iimport android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.animation.DiceAnimationHelper
import com.example.purramid.thepurramid.probabilities.data.DicePosition
import com.example.purramid.thepurramid.probabilities.data.ProbabilitiesPositionState
import com.example.purramid.thepurramid.probabilities.DiceSumResultType
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.ProbabilitiesHostActivity
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import com.example.purramid.thepurramid.probabilities.util.ProbabilitiesLayoutHelper
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.DieType
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceSettings
import com.example.purramid.thepurramid.probabilities.viewmodel.DiceResult
import com.example.purramid.thepurramid.probabilities.viewmodel.ProbabilitiesPreferencesManager
import com.example.purramid.thepurramid.util.dpToPx
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import nl.dionsegijn.konfetti.xml.KonfettiView
import javax.inject.Inject
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Button

@AndroidEntryPoint
class DiceMainFragment : Fragment() {
    @Inject lateinit var positionManager: ProbabilitiesPositionManager
    @Inject lateinit var preferencesManager: ProbabilitiesPreferencesManager

    private val diceViewModel: DiceViewModel by activityViewModels()
    private var instanceId: Int = 1

    private lateinit var diceDisplayArea: FlexboxLayout
    private lateinit var announcementTextView: TextView
    private lateinit var konfettiView: KonfettiView
    private lateinit var rollButton: Button
    private lateinit var resetButton: Button
    private lateinit var dicePoolButton: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var barChart: BarChart

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dice_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get instance ID from arguments
        instanceId = arguments?.getInt(ProbabilitiesHostActivity.EXTRA_INSTANCE_ID) ?: 1

        // Initialize views
        initializeViews(view)

        // Initialize ViewModel with instance ID
        diceViewModel.initialize(instanceId)

        // Setup button listeners
        setupButtonListeners()

        // Observe ViewModel
        observeViewModel()

        // Setup dice positions
        setupDicePositions()
    }

    private fun initializeViews(view: View) {
        diceDisplayArea = view.findViewById(R.id.diceDisplayArea)
        announcementTextView = view.findViewById(R.id.diceAnnouncementTextView)
        konfettiView = view.findViewById(R.id.konfettiViewDice)
        rollButton = view.findViewById(R.id.diceRollButton)
        resetButton = view.findViewById(R.id.diceResetButton)
        dicePoolButton = view.findViewById(R.id.dicePoolButton)
        settingsButton = view.findViewById(R.id.diceSettingsButton)
        closeButton = view.findViewById(R.id.diceCloseButton)
        barChart = view.findViewById(R.id.diceBarChart)

        // Setup chart
        setupChart()
    }

    private fun setupButtonListeners() {
        rollButton.setOnClickListener {
            diceViewModel.rollDice()
        }

        resetButton.setOnClickListener {
            diceViewModel.reset()
            resetDiceDisplay()
            resetButton.visibility = View.INVISIBLE
        }

        dicePoolButton.setOnClickListener {
            showDicePoolDialog()
        }

        settingsButton.setOnClickListener {
            openSettings()
        }

        closeButton.setOnClickListener {
            activity?.finish()
        }
    }

    private fun setupChart() {
        // Theme-aware colors
        val isDark = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val barColor = ContextCompat.getColor(
            requireContext(),
            if (isDark) R.color.teal_200 else R.color.purple_500
        )
        val axisTextColor = ContextCompat.getColor(
            requireContext(),
            if (isDark) android.R.color.white else android.R.color.darker_gray
        )

        // Chart styling
        barChart.apply {
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            description.isEnabled = false
            legend.isEnabled = true
            legend.textSize = 12f
            legend.form = com.github.mikephil.charting.components.Legend.LegendForm.SQUARE
            legend.textColor = axisTextColor
            setNoDataText("No data yet. Roll the dice!")
            setTouchEnabled(false)
            setScaleEnabled(false)
            setPinchZoom(false)

            axisRight.isEnabled = false
            axisLeft.textColor = axisTextColor
            axisLeft.setDrawGridLines(false)
            axisLeft.axisMinimum = 0f
            axisLeft.granularity = 1f

            xAxis.textColor = axisTextColor
            xAxis.setDrawGridLines(false)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = 0f

            setExtraOffsets(0f, 16f, 0f, 16f)
        }
    }

    private fun observeViewModel() {
        diceViewModel.settings.observe(viewLifecycleOwner) { settings ->
            updateDiceDisplay(settings)
            updateGraphVisibility(settings)
        }

        diceViewModel.result.observe(viewLifecycleOwner) { result ->
            result?.let {
                displayResults(it)
                resetButton.visibility = View.VISIBLE
                updateGraph()
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

        diceViewModel.error.observe(viewLifecycleOwner) { message ->
            message?.let {
                showError(it)
                diceViewModel.clearError()
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

    private fun createDieView(
        type: DieType,
        color: Int,
        usePips: Boolean,
        isTens: Boolean = false
    ): FrameLayout {
        val frameLayout = FrameLayout(requireContext())
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            dpToPx(64),
            dpToPx(64)
        )

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
        textView.textSize = if (isTens) 14f else 18f
        textView.gravity = android.view.Gravity.CENTER

        val maxValue = when {
            type == DieType.PERCENTILE && isTens -> "00" // Tens die shows 00-90
            type == DieType.PERCENTILE && !isTens -> "0" // Ones die shows 0-9
            else -> {
                when (type) {
                    DieType.D4 -> 4
                    DieType.D6 -> 6
                    DieType.D8 -> 8
                    DieType.D10 -> if (isTens) 90 else 10
                    DieType.D12 -> 12
                    DieType.D20 -> 20
                    DieType.PERCENTILE -> 100
                }
            }
        }
        textView.text = displayText
        textView.tag = R.id.die_result_text
        frameLayout.addView(textView)

        frameLayout.setTag(R.id.die_type, type)
        frameLayout.setTag(R.id.die_is_tens, isTens)
        frameLayout.setTag(R.id.die_is_percentile, type == DieType.PERCENTILE)

        return frameLayout
    }

    private fun createD6WithPips(frameLayout: FrameLayout, value: Int, color: Int) {
        frameLayout.removeAllViews()

        // Background
        val imageView = ImageView(requireContext())
        imageView.setImageResource(R.drawable.d6_pips_blank)
        imageView.setColorFilter(color)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        frameLayout.addView(imageView)

        // Add pips based on value
        val pipContainer = FrameLayout(requireContext())
        pipContainer.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        when (value) {
            1 -> addPip(pipContainer, 0.5f, 0.5f) // Center
            2 -> {
                addPip(pipContainer, 0.25f, 0.25f) // Top left
                addPip(pipContainer, 0.75f, 0.75f) // Bottom right
            }
            3 -> {
                addPip(pipContainer, 0.25f, 0.25f) // Top left
                addPip(pipContainer, 0.5f, 0.5f)   // Center
                addPip(pipContainer, 0.75f, 0.75f) // Bottom right
            }
            4 -> {
                addPip(pipContainer, 0.25f, 0.25f) // Top left
                addPip(pipContainer, 0.75f, 0.25f) // Top right
                addPip(pipContainer, 0.25f, 0.75f) // Bottom left
                addPip(pipContainer, 0.75f, 0.75f) // Bottom right
            }
            5 -> {
                addPip(pipContainer, 0.25f, 0.25f) // Top left
                addPip(pipContainer, 0.75f, 0.25f) // Top right
                addPip(pipContainer, 0.5f, 0.5f)   // Center
                addPip(pipContainer, 0.25f, 0.75f) // Bottom left
                addPip(pipContainer, 0.75f, 0.75f) // Bottom right
            }
            6 -> {
                addPip(pipContainer, 0.25f, 0.25f) // Top left
                addPip(pipContainer, 0.75f, 0.25f) // Top right
                addPip(pipContainer, 0.25f, 0.5f)  // Middle left
                addPip(pipContainer, 0.75f, 0.5f)  // Middle right
                addPip(pipContainer, 0.25f, 0.75f) // Bottom left
                addPip(pipContainer, 0.75f, 0.75f) // Bottom right
            }
        }

        frameLayout.addView(pipContainer)
    }

    private fun addPip(container: FrameLayout, xRatio: Float, yRatio: Float) {
        val pip = View(requireContext())
        val pipSize = dpToPx(8)
        val params = FrameLayout.LayoutParams(pipSize, pipSize)

        container.post {
            params.leftMargin = (container.width * xRatio - pipSize / 2).toInt()
            params.topMargin = (container.height * yRatio - pipSize / 2).toInt()
            pip.layoutParams = params
        }

        pip.background = ContextCompat.getDrawable(requireContext(), R.drawable.pip_circle)
        container.addView(pip)
    }

    private fun displayResults(result: DiceResult) {
        val settings = diceViewModel.settings.value ?: return

        var dieIndex = 0
        result.results.forEach { (type, rolls) ->
            if (type == DieType.D6 && settings.dieConfigs.find { it.type == DieType.D6 }?.usePips == true) {
                // Display pips for d6
                rolls.forEach { value ->
                    if (dieIndex < diceDisplayArea.childCount) {
                        val dieView = diceDisplayArea.getChildAt(dieIndex) as? FrameLayout
                        val color = settings.dieConfigs.find { it.type == DieType.D6 }?.color ?: Color.WHITE
                        dieView?.let { createD6WithPips(it, value, color) }
                        dieIndex++
                    }
                }
            } else if (type == DieType.PERCENTILE && settings.usePercentile) {
                // For percentile dice, we need to display on two separate d10s
                rolls.forEach { percentileResult ->
                    if (dieIndex + 1 < diceDisplayArea.childCount) {
                        val tensValue = (percentileResult / 10) * 10
                        val onesValue = percentileResult % 10

                        // Special case for 100
                        val tensDisplay = if (percentileResult == 100) "00" else tensValue.toString().padStart(2, '0')
                        val onesDisplay = if (percentileResult == 100) "0" else onesValue.toString()

                        // Update tens die
                        val tensDieView = diceDisplayArea.getChildAt(dieIndex) as? FrameLayout
                        val tensTextView = tensDieView?.findViewWithTag<TextView>(R.id.die_result_text)
                        tensTextView?.text = tensDisplay

                        // Update ones die
                        val onesDieView = diceDisplayArea.getChildAt(dieIndex + 1) as? FrameLayout
                        val onesTextView = onesDieView?.findViewWithTag<TextView>(R.id.die_result_text)
                        onesTextView?.text = onesDisplay

                        dieIndex += 2 // Skip both dice
                    }
                }
            } else {
                // Regular dice
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
        // Create confetti presets for a dramatic celebration
        konfettiView.build()
            .addColors(
                Color.parseColor("#FFD700"), // Gold
                Color.parseColor("#FF0000"), // Red
                Color.parseColor("#00FF00"), // Green
                Color.parseColor("#0000FF"), // Blue
                Color.parseColor("#FF00FF"), // Magenta
                Color.parseColor("#00FFFF"), // Cyan
                Color.parseColor("#FFA500")  // Orange
            )
            .setDirection(0.0, 359.0)
            .setSpeed(4f, 7f)
            .setFadeOutEnabled(true)
            .setTimeToLive(3000L)
            .addShapes(
                nl.dionsegijn.konfetti.core.models.Shape.Square,
                nl.dionsegijn.konfetti.core.models.Shape.Circle
            )
            .addSizes(
                nl.dionsegijn.konfetti.core.models.Size(8),
                nl.dionsegijn.konfetti.core.models.Size(12),
                nl.dionsegijn.konfetti.core.models.Size(16)
            )
            .setPosition(-50f, konfettiView.width + 50f, -50f, -50f)
            .streamFor(200, 2000L)

        // Also animate the d20 that rolled the natural 20
        findD20WithNat20()?.let { dieView ->
            DiceAnimationHelper.animateCriticalHit(dieView)
        }
    }

    private fun findD20WithNat20(): View? {
        for (i in 0 until diceDisplayArea.childCount) {
            val dieView = diceDisplayArea.getChildAt(i) as? FrameLayout
            val dieType = dieView?.getTag(R.id.die_type) as? DieType
            val textView = dieView?.findViewWithTag<TextView>(R.id.die_result_text)

            if (dieType == DieType.D20 && textView?.text == "20") {
                return dieView
            }
        }
        return null
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

    private fun updateGraph() {
        val settings = diceViewModel.settings.value
        val result = diceViewModel.result.value

        if (settings?.graphEnabled == true && result != null) {
            barChart.visibility = View.VISIBLE

            val freq = mutableMapOf<Int, Int>()
            for (rolls in result.results.values) {
                for (roll in rolls) {
                    freq[roll] = (freq[roll] ?: 0) + 1
                }
            }

            val sorted = freq.entries.sortedBy { it.key }
            val entries = sorted.mapIndexed { idx, (value, count) ->
                BarEntry(idx.toFloat(), count.toFloat())
            }

            val dataSet = BarDataSet(entries, "Dice Results")
            dataSet.color = ContextCompat.getColor(
                requireContext(),
                R.color.purple_500
            )
            dataSet.valueTextColor = ContextCompat.getColor(
                requireContext(),
                android.R.color.black
            )
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

    private fun updateGraphVisibility(settings: DiceSettings) {
        if (settings.graphEnabled) {
            updateGraph()
        } else {
            barChart.visibility = View.GONE
        }
    }

    private fun showDicePoolDialog() {
        DicePoolDialogFragment().show(childFragmentManager, "DicePoolDialog")
    }

    private fun openSettings() {
        (activity as? ProbabilitiesHostActivity)?.openSettings()
    }

    private fun showError(message: String) {
        view?.let { rootView ->
            Snackbar.make(
                rootView,
                message,
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.snackbar_action_ok)) {
                // Dismiss action
            }.show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupDicePositions() {
        val savedPositions = positionManager.loadPositions(instanceId)

        if (savedPositions?.dicePositions?.isNotEmpty() == true) {
            restoreDicePositions(savedPositions.dicePositions)
        } else {
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

                applyDicePositions(defaultPositions)

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
                    if (this is FrameLayout) {
                        val textView = findViewById<TextView>(R.id.die_result_text)
                        textView?.text = result.toString()
                    }
                }
            }
        }
    }
}