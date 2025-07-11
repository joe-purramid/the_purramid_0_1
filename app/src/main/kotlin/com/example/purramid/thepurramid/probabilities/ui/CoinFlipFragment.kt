package com.example.purramid.thepurramid.probabilities.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.probabilities.data.CoinPosition
import com.example.purramid.thepurramid.probabilities.data.ProbabilitiesPositionState
import com.example.purramid.thepurramid.probabilities.ProbabilitiesHostActivity
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import com.example.purramid.thepurramid.probabilities.animation.CoinAnimationHelper
import com.example.purramid.thepurramid.probabilities.util.ProbabilitiesLayoutHelper
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinFlipViewModel
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CoinFlipFragment : Fragment() {
    @Inject lateinit var positionManager: ProbabilitiesPositionManager
    private val coinFlipViewModel: CoinFlipViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coin_flip, container, false)
    }

    private fun setupFreeFormCoin(coinView: View, coinType: CoinType, index: Int) {
        var dX = 0f
        var dY = 0f

        coinView.setOnTouchListener { view, event ->
            if (coinFlipViewModel.settings.value?.freeForm != true) return@setOnTouchListener false

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
                CoinAnimationHelper.animateCoinFlip(clickedView as ImageView, newIsHeads) {
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

    private fun restoreCoinPositions() {
        val instanceId = arguments?.getInt(ProbabilitiesHostActivity.EXTRA_INSTANCE_ID) ?: 1
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
        val recyclerView = view?.findViewById<RecyclerView>(R.id.coinDisplayAreaRecyclerView)
        val adapter = recyclerView?.adapter ?: return null

        var currentIndex = 0
        for (position in 0 until adapter.itemCount) {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
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

    private fun getCoinDrawableResource(coinType: CoinType, isHeads: Boolean): Int {
        return when (coinType) {
            CoinType.B1 -> if (isHeads) R.drawable.b1_coin_flip_heads else R.drawable.b1_coin_flip_tails
            CoinType.B5 -> if (isHeads) R.drawable.b5_coin_flip_heads else R.drawable.b5_coin_flip_tails
            CoinType.B10 -> if (isHeads) R.drawable.b10_coin_flip_heads else R.drawable.b10_coin_flip_tails
            CoinType.B25 -> if (isHeads) R.drawable.b25_coin_flip_heads else R.drawable.b25_coin_flip_tails
            CoinType.MB1 -> if (isHeads) R.drawable.mb1_coin_flip_heads else R.drawable.mb1_coin_flip_tails
            CoinType.MB2 -> if (isHeads) R.drawable.mb2_coin_flip_heads else R.drawable.mb2_coin_flip_tails
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val flipButton = view.findViewById<Button>(R.id.buttonFlip)
        val resetButton = view.findViewById<Button>(R.id.buttonReset)
        val resultText = view.findViewById<TextView>(R.id.textCoinResult)
        val barChart = view.findViewById<BarChart>(R.id.coinBarChart)

        // Theme-aware colors
        val isDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val barColor = ContextCompat.getColor(requireContext(), if (isDark) R.color.teal_200 else R.color.teal_700)
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
        barChart.setNoDataText("No data yet. Flip the coins!")
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
        barChart.xAxis.labelCount = 2
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.granularity = 1f
        barChart.setExtraOffsets(0f, 16f, 0f, 16f)

        val updateGraph = {
            val settings = coinFlipViewModel.settings.value
            val result = coinFlipViewModel.result.value
            if ((settings?.graphEnabled == true || settings?.probabilityEnabled == true) && result != null) {
                barChart.visibility = View.VISIBLE
                var heads = 0
                var tails = 0
                for (flips in result.results.values) {
                    for (flip in flips) if (flip) heads++ else tails++
                }
                val entries = listOf(
                    BarEntry(0f, heads.toFloat()),
                    BarEntry(1f, tails.toFloat())
                )
                val dataSet = BarDataSet(entries, "Coin Results")
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
                barChart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Heads", "Tails"))
                barChart.xAxis.labelCount = 2
                barChart.animateY(700)
                barChart.invalidate()
            } else {
                barChart.visibility = View.GONE
            }
        }

        flipButton?.setOnClickListener { coinFlipViewModel.flipCoins() }
        resetButton?.setOnClickListener { coinFlipViewModel.reset() }

        coinFlipViewModel.result.observe(viewLifecycleOwner) { result ->
            resultText?.text = result?.results?.entries?.joinToString("\n") { (type, flips) ->
                "${type.name}: ${flips.joinToString(", ") { if (it) "Heads" else "Tails" }}"
            } ?: ""
            updateGraph()
        }
        coinFlipViewModel.settings.observe(viewLifecycleOwner) { updateGraph() }
        setupCoinPositions()
    }

    private fun setupCoinPositions() {
        val instanceId = arguments?.getInt(ProbabilitiesHostActivity.EXTRA_INSTANCE_ID) ?: 1
        val savedPositions = positionManager.loadPositions(instanceId)

        if (savedPositions?.coinPositions?.isNotEmpty() == true) {
            // Restore saved positions
            restoreCoinPositions() // This method already exists
        } else {
            // Calculate and set default positions
            val coinDisplayArea = view?.findViewById<RecyclerView>(R.id.coinDisplayAreaRecyclerView) ?: return

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

                // Apply positions to coin views
                applyCoinPositions(defaultPositions)

                // Save the default positions
                val positionState = ProbabilitiesPositionState(
                    instanceId = instanceId,
                    mode = ProbabilitiesMode.COIN_FLIP,
                    coinPositions = defaultPositions
                )
                positionManager.savePositions(instanceId, positionState)
            }
        }
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
} 