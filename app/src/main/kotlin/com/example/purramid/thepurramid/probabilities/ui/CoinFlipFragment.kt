package com.example.purramid.thepurramid.probabilities.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.CoinProbabilityMode
import com.example.purramid.thepurramid.probabilities.data.CoinPosition
import com.example.purramid.thepurramid.probabilities.data.ProbabilitiesPositionState
import com.example.purramid.thepurramid.probabilities.ProbabilitiesHostActivity
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import com.example.purramid.thepurramid.probabilities.animation.CoinAnimationHelper
import com.example.purramid.thepurramid.probabilities.ui.adapter.CoinDisplayAdapter
import com.example.purramid.thepurramid.probabilities.ui.adapter.ProbabilityGridAdapter
import com.example.purramid.thepurramid.probabilities.util.ProbabilitiesLayoutHelper
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinFlipViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType
import com.example.purramid.thepurramid.probabilities.viewmodel.ProbabilitiesPreferencesManager
import com.example.purramid.thepurramid.probabilities.viewmodel.GridCellResult
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinFlipResult
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinFlipSettings
import androidx.core.view.isVisible

@AndroidEntryPoint
class CoinFlipFragment : Fragment() {
    @Inject lateinit var positionManager: ProbabilitiesPositionManager
    @Inject lateinit var preferencesManager: ProbabilitiesPreferencesManager

    private val coinFlipViewModel: CoinFlipViewModel by activityViewModels()
    private var instanceId: Int = 1

    private lateinit var coinDisplayRecyclerView: RecyclerView
    private lateinit var freeFormContainer: FrameLayout
    private lateinit var twoColumnLayout: LinearLayout
    private lateinit var probabilityGridRecyclerView: RecyclerView
    private lateinit var announcementTextView: TextView
    private lateinit var flipButton: Button
    private lateinit var resetButton: Button
    private lateinit var coinPoolButton: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var barChart: BarChart

    private lateinit var coinAdapter: CoinDisplayAdapter
    private var gridAdapter: ProbabilityGridAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coin_flip, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get instance ID from arguments
        instanceId = arguments?.getInt(ProbabilitiesHostActivity.EXTRA_INSTANCE_ID) ?: 1

        // Initialize views
        initializeViews(view)

        // Initialize ViewModel with instance ID
        coinFlipViewModel.initialize(instanceId)

        // Setup button listeners
        setupButtonListeners()

        // Setup adapters
        setupAdapters()

        // Observe ViewModel
        observeViewModel()

        // Setup coin positions
        setupCoinPositions()
    }

    private fun initializeViews(view: View) {
        coinDisplayRecyclerView = view.findViewById(R.id.coinDisplayAreaRecyclerView)
        freeFormContainer = view.findViewById(R.id.freeFormDisplayContainer)
        twoColumnLayout = view.findViewById(R.id.probabilityTwoColumnLayout)
        probabilityGridRecyclerView = view.findViewById(R.id.probabilityGridLayout)
        announcementTextView = view.findViewById(R.id.coinFlipAnnouncementTextView)
        flipButton = view.findViewById(R.id.coinFlipActionButton)
        resetButton = view.findViewById(R.id.buttonReset)
        coinPoolButton = view.findViewById(R.id.coinPoolButton)
        settingsButton = view.findViewById(R.id.coinFlipSettingsButton)
        closeButton = view.findViewById(R.id.coinFlipCloseButton)
        barChart = view.findViewById(R.id.coinBarChart)

        // Setup chart
        setupChart()
    }

    private fun setupButtonListeners() {
        flipButton.setOnClickListener {
            val settings = coinFlipViewModel.settings.value
            if (settings?.freeForm == true) {
                // In free form mode, flip button becomes tails button
                toggleAllCoinsToTails()
            } else {
                coinFlipViewModel.flipCoins()
            }
        }

        resetButton?.setOnClickListener {
            coinFlipViewModel.reset()
            resetCoinDisplay()
            gridAdapter?.clearResults()
        }

        coinPoolButton.setOnClickListener {
            showCoinPoolDialog()
        }

        settingsButton.setOnClickListener {
            openSettings()
        }

        closeButton.setOnClickListener {
            activity?.finish()
        }
    }

    private fun setupAdapters() {
        // Setup coin display adapter
        coinAdapter = CoinDisplayAdapter(emptyList())
        coinDisplayRecyclerView.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        coinDisplayRecyclerView.adapter = coinAdapter
    }

    private fun setupChart() {
        // Theme-aware colors
        val isDark = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val barColor = ContextCompat.getColor(
            requireContext(),
            if (isDark) R.color.teal_200 else R.color.teal_700
        )
        val axisTextColor = ContextCompat.getColor(
            requireContext(),
            if (isDark) android.R.color.white else android.R.color.darker_gray
        )

        barChart.apply {
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            description.isEnabled = false
            legend.isEnabled = true
            legend.textSize = 12f
            legend.form = com.github.mikephil.charting.components.Legend.LegendForm.SQUARE
            legend.textColor = axisTextColor
            setNoDataText("No data yet. Flip the coins!")
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
            xAxis.labelCount = 2

            setExtraOffsets(0f, 16f, 0f, 16f)
        }
    }

    private fun observeViewModel() {
        coinFlipViewModel.settings.observe(viewLifecycleOwner) { settings ->
            updateCoinDisplay(settings)
            updateProbabilityDisplay(settings)
            updateFlipButton(settings)
        }

        coinFlipViewModel.result.observe(viewLifecycleOwner) { result ->
            result?.let {
                displayResults(it)
                updateGraph()

                // Update grid if in probability mode
                val settings = coinFlipViewModel.settings.value
                if (settings?.probabilityMode != CoinProbabilityMode.NONE) {
                    updateProbabilityResults(it)
                }
            }
        }

        coinFlipViewModel.error.observe(viewLifecycleOwner) { message ->
            message?.let {
                showError(it)
                coinFlipViewModel.clearError()
            }
        }
    }

    private fun updateCoinDisplay(settings: CoinFlipSettings) {
        // Update adapter with new coin configs
        coinAdapter = CoinDisplayAdapter(settings.coinConfigs.filter { it.quantity > 0 })
        coinDisplayRecyclerView.adapter = coinAdapter

        // Show/hide containers based on mode
        when {
            settings.freeForm -> {
                coinDisplayRecyclerView.visibility = View.GONE
                freeFormContainer.visibility = View.VISIBLE
                setupFreeFormCoins(settings)
            }
            settings.probabilityMode != CoinProbabilityMode.NONE -> {
                coinDisplayRecyclerView.visibility = View.GONE
                freeFormContainer.visibility = View.GONE
            }
            else -> {
                coinDisplayRecyclerView.visibility = View.VISIBLE
                freeFormContainer.visibility = View.GONE
            }
        }
    }

    private fun updateProbabilityDisplay(settings: CoinFlipSettings) {
        // Hide all probability displays first
        twoColumnLayout.visibility = View.GONE
        probabilityGridRecyclerView.visibility = View.GONE

        when (settings.probabilityMode) {
            CoinProbabilityMode.TWO_COLUMNS -> {
                twoColumnLayout.visibility = View.VISIBLE
            }
            CoinProbabilityMode.GRID_3X3 -> {
                setupProbabilityGrid(3)
            }
            CoinProbabilityMode.GRID_6X6 -> {
                setupProbabilityGrid(6)
            }
            CoinProbabilityMode.GRID_10X10 -> {
                setupProbabilityGrid(10)
            }
            else -> {
                // Normal display mode
            }
        }
    }

    private fun setupProbabilityGrid(gridSize: Int) {
        probabilityGridRecyclerView.visibility = View.VISIBLE
        probabilityGridRecyclerView.layoutManager = GridLayoutManager(requireContext(), gridSize)

        val totalCells = gridSize * gridSize
        gridAdapter = ProbabilityGridAdapter(totalCells) { position ->
            // Handle grid cell tap
            flipUpToPosition(position)
        }
        probabilityGridRecyclerView.adapter = gridAdapter
    }

    private fun flipUpToPosition(position: Int) {
        val currentResults = gridAdapter?.getFilledCellCount() ?: 0
        val flipsNeeded = position - currentResults + 1

        if (flipsNeeded > 0) {
            // Perform multiple flips
            val newResults = mutableListOf<GridCellResult>()
            repeat(flipsNeeded) {
                coinFlipViewModel.flipCoins()
                val result = coinFlipViewModel.result.value
                result?.let {
                    var headsCount = 0
                    var tailsCount = 0
                    it.results.values.forEach { flips ->
                        flips.forEach { isHeads ->
                            if (isHeads) headsCount++ else tailsCount++
                        }
                    }
                    newResults.add(GridCellResult(headsCount, tailsCount))
                }
            }

            // Update grid with new results
            gridAdapter?.addResults(newResults)
        }
    }

    private fun updateFlipButton(settings: CoinFlipSettings) {
        if (settings.freeForm) {
            flipButton.text = getString(R.string.tails_action)
        } else {
            flipButton.text = getString(R.string.flip_coins_action)
        }
    }

    private fun setupFreeFormCoins(settings: CoinFlipSettings) {
        freeFormContainer.removeAllViews()

        settings.coinConfigs.forEach { config ->
            repeat(config.quantity) { index ->
                val coinView = createFreeFormCoinView(config.type, config.color, index)
                freeFormContainer.addView(coinView)
                setupFreeFormCoin(coinView, config.type, index)
            }
        }

        restoreCoinPositions()
    }

    private fun createFreeFormCoinView(type: CoinType, color: Int, index: Int): ImageView {
        val imageView = ImageView(requireContext())
        imageView.layoutParams = FrameLayout.LayoutParams(
            dpToPx(60),
            dpToPx(60)
        )

        val drawableRes = getCoinDrawableResource(type, true)
        imageView.setImageResource(drawableRes)
        imageView.setColorFilter(color)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setTag(R.id.coin_type, type)
        imageView.setTag(R.id.coin_index, index)
        imageView.setTag(R.id.coin_is_heads, true)

        return imageView
    }

    private fun setupFreeFormCoin(coinView: View, coinType: CoinType, index: Int) {
        var dX = 0f
        var dY = 0f

        coinView.setOnTouchListener { view, event ->
            if (coinFlipViewModel.settings.value?.freeForm != true) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    view.elevation = 8f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    view.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.elevation = 0f
                    val currentIsHeads = view.getTag(R.id.coin_is_heads) as? Boolean ?: true
                    positionManager.updateCoinPosition(
                        instanceId,
                        coinType,
                        index,
                        view.x,
                        view.y,
                        currentIsHeads
                    )
                    true
                }

                else -> false
            }
        }

        coinView.setOnClickListener { clickedView ->
            if (coinFlipViewModel.settings.value?.freeForm == true) {
                val currentIsHeads = clickedView.getTag(R.id.coin_is_heads) as? Boolean ?: true
                val newIsHeads = !currentIsHeads
                clickedView.setTag(R.id.coin_is_heads, newIsHeads)

                if (clickedView is ImageView) {
                    CoinAnimationHelper.animateCoinFlip(clickedView, newIsHeads) {
                        positionManager.updateCoinPosition(
                            instanceId,
                            coinType,
                            index,
                            clickedView.x,
                            clickedView.y,
                            newIsHeads
                        )
                    }
                }
            }
        }
    }

    private fun toggleAllCoinsToTails() {
        val container = freeFormContainer
        val isTailsMode = flipButton.text == getString(R.string.tails_action)

        for (i in 0 until container.childCount) {
            val coinView = container.getChildAt(i) as? ImageView
            coinView?.let {
                val coinType = it.getTag(R.id.coin_type) as? CoinType
                val index = it.getTag(R.id.coin_index) as? Int ?: 0
                val wasManuallyFlipped = it.getTag(R.id.coin_manually_flipped) as? Boolean ?: false

                if (!wasManuallyFlipped) {
                    it.setTag(R.id.coin_is_heads, !isTailsMode)
                    coinType?.let { type ->
                        val drawableRes = getCoinDrawableResource(type, !isTailsMode)
                        it.setImageResource(drawableRes)
                    }
                }
            }
        }

        // Toggle button text
        flipButton.text = if (isTailsMode) {
            getString(R.string.heads_action)
        } else {
            getString(R.string.tails_action)
        }
    }

    private fun displayResults(result: CoinFlipResult) {
        val settings = coinFlipViewModel.settings.value ?: return

        if (settings.freeForm) {
            // Free form mode doesn't use traditional results
            return
        }

        // Update coin display with results
        coinAdapter.updateResults(result.results)

        if (settings.announce) {
            showAnnouncement(result)
        }

        // Update two-column display if active
        if (settings.probabilityMode == CoinProbabilityMode.TWO_COLUMNS) {
            updateTwoColumnDisplay(result)
        }
    }

    private fun updateTwoColumnDisplay(result: CoinFlipResult) {
        var totalHeads = 0
        var totalTails = 0

        result.results.values.forEach { flips ->
            flips.forEach { isHeads ->
                if (isHeads) totalHeads++ else totalTails++
            }
        }

        view?.findViewById<TextView>(R.id.twoColumnHeadsResultTextView)?.text =
            totalHeads.toString()
        view?.findViewById<TextView>(R.id.twoColumnTailsResultTextView)?.text =
            totalTails.toString()
    }

    private fun updateProbabilityResults(result: CoinFlipResult) {
        var headsCount = 0
        var tailsCount = 0

        result.results.values.forEach { flips ->
            flips.forEach { isHeads ->
                if (isHeads) headsCount++ else tailsCount++
            }
        }

        gridAdapter?.addResult(GridCellResult(headsCount, tailsCount))
    }

    private fun showAnnouncement(result: CoinFlipResult) {
        val totalHeads = result.results.values.sumOf { flips ->
            flips.count { it }
        }
        val totalTails = result.results.values.sumOf { flips ->
            flips.count { !it }
        }

        val announcement = getString(
            R.string.coin_flip_announce_format,
            totalHeads,
            totalTails
        )

        announcementTextView.text = announcement
        announcementTextView.visibility = View.VISIBLE

        announcementTextView.postDelayed({
            announcementTextView.visibility = View.GONE
        }, 3000)
    }

    private fun updateGraph() {
        val settings = coinFlipViewModel.settings.value
        val result = coinFlipViewModel.result.value

        if ((settings?.graphEnabled == true || settings?.probabilityEnabled == true) && result != null) {
            barChart.visibility = View.VISIBLE

            var heads = 0
            var tails = 0

            result.results.values.forEach { flips ->
                flips.forEach { isHeads ->
                    if (isHeads) heads++ else tails++
                }
            }

            val entries = listOf(
                BarEntry(0f, heads.toFloat()),
                BarEntry(1f, tails.toFloat())
            )

            val dataSet = BarDataSet(entries, "Coin Results")
            dataSet.color = ContextCompat.getColor(requireContext(), R.color.teal_700)
            dataSet.valueTextColor = ContextCompat.getColor(requireContext(), android.R.color.black)
            dataSet.valueTextSize = 14f
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String {
                    return barEntry?.y?.toInt()?.toString() ?: ""
                }
            }

            val barData = BarData(dataSet)
            barData.barWidth = 0.8f

            barChart.data = barData
            barChart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Heads", "Tails"))
            barChart.xAxis.labelCount = 2
            barChart.animateY(700)
            barChart.invalidate()
        } else {
            barChart.visibility = View.GONE
        }
    }

    private fun resetCoinDisplay() {
        coinAdapter.updateResults(emptyMap())
        announcementTextView.visibility = View.GONE

        // Reset two-column display
        view?.findViewById<TextView>(R.id.twoColumnHeadsResultTextView)?.text = "0"
        view?.findViewById<TextView>(R.id.twoColumnTailsResultTextView)?.text = "0"

        // Reset free form coins to heads
        for (i in 0 until freeFormContainer.childCount) {
            val coinView = freeFormContainer.getChildAt(i) as? ImageView
            coinView?.let {
                val coinType = it.getTag(R.id.coin_type) as? CoinType
                it.setTag(R.id.coin_is_heads, true)
                it.setTag(R.id.coin_manually_flipped, false)
                coinType?.let { type ->
                    val drawableRes = getCoinDrawableResource(type, true)
                    it.setImageResource(drawableRes)
                }
            }
        }

        // Reset flip button if in free form
        if (coinFlipViewModel.settings.value?.freeForm == true) {
            flipButton.text = getString(R.string.tails_action)
        }
    }

    private fun showCoinPoolDialog() {
        CoinPoolDialogFragment().show(childFragmentManager, "CoinPoolDialog")
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

    private fun setupCoinPositions() {
        val savedPositions = positionManager.loadPositions(instanceId)

        if (savedPositions?.coinPositions?.isNotEmpty() == true) {
            restoreCoinPositions()
        } else {
            val coinDisplayArea = coinDisplayRecyclerView

            coinDisplayArea.post {
                val settings = coinFlipViewModel.settings.value ?: return@post
                val coinGroups = mutableMapOf<CoinType, Int>()

                settings.coinConfigs.forEach { config ->
                    if (config.quantity > 0) {
                        coinGroups[config.type] = config.quantity
                    }
                }

                val defaultPositions = ProbabilitiesLayoutHelper.calculateDefaultCoinPositions(
                    coinDisplayArea.width,
                    coinDisplayArea.height,
                    coinGroups
                )

                applyCoinPositions(defaultPositions)

                val positionState = ProbabilitiesPositionState(
                    instanceId = instanceId,
                    mode = ProbabilitiesMode.COIN_FLIP,
                    coinPositions = defaultPositions
                )
                positionManager.savePositions(instanceId, positionState)
            }
        }
    }

    private fun restoreCoinPositions() {
        val savedPositions = positionManager.loadPositions(instanceId)

        savedPositions?.coinPositions?.forEach { position ->
            val coinView = findCoinViewByTypeAndIndex(position.coinType, position.index)
            coinView?.apply {
                x = position.x
                y = position.y
                rotation = position.rotation
                setTag(R.id.coin_is_heads, position.isHeads)

                if (this is ImageView) {
                    val drawableRes = getCoinDrawableResource(position.coinType, position.isHeads)
                    setImageResource(drawableRes)
                }
            }
        }
    }

    private fun findCoinViewByTypeAndIndex(coinType: CoinType, index: Int): View? {
        // Check free form container first
        if (freeFormContainer.isVisible) {
            for (i in 0 until freeFormContainer.childCount) {
                val coinView = freeFormContainer.getChildAt(i)
                val viewType = coinView.getTag(R.id.coin_type) as? CoinType
                val viewIndex = coinView.getTag(R.id.coin_index) as? Int

                if (viewType == coinType && viewIndex == index) {
                    return coinView
                }
            }
        }

        // Check recycler view
        val adapter = coinDisplayRecyclerView.adapter ?: return null
        var currentIndex = 0

        for (position in 0 until adapter.itemCount) {
            val viewHolder = coinDisplayRecyclerView.findViewHolderForAdapterPosition(position)
            val coinView = viewHolder?.itemView?.findViewById<ImageView>(R.id.coinImageView)
            val viewCoinType = coinView?.getTag(R.id.coin_type) as? CoinType

            if (viewCoinType == coinType) {
                if (currentIndex == index) {
                    return coinView
                }
                currentIndex++
            }
        }

        return null
    }

    private fun applyCoinPositions(positions: List<CoinPosition>) {
        positions.forEach { position ->
            val coinView = findCoinViewByTypeAndIndex(position.coinType, position.index)
            coinView?.apply {
                x = position.x
                y = position.y
                rotation = position.rotation
                setTag(R.id.coin_is_heads, position.isHeads)
            }
        }
    }

    private fun getCoinDrawableResource(coinType: CoinType, isHeads: Boolean): Int {
        return when (coinType) {
            CoinType.B1 -> if (isHeads) R.drawable.b1_coin_flip_heads else R.drawable.b1_coin_flip_tails
            CoinType.B5 -> if (isHeads) R.drawable.b5_coin_flip_heads else R.drawable.b5_coin_flip_tails
            CoinType.B10 -> if (isHeads) R.drawable.b10_coin_flip_heads else R.drawable.b10_coin_flip_tails
            CoinType.B25 -> if (isHeads) R.drawable.b25_coin_flip_heads else R.drawable.b25_coin_flip_tails
            CoinType.MB1 -> if (isHeads) R.drawable.m1_coin_flip_heads else R.drawable.m1_coin_flip_tails
            CoinType.MB2 -> if (isHeads) R.drawable.m2_coin_flip_heads else R.drawable.m2_coin_flip_tails
        }
    }
}