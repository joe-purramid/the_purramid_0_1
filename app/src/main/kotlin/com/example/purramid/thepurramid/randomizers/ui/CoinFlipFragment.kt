// CoinFlipFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.animation.ObjectAnimator // Keep if used for other animations
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
// Removed Button import as it might not be directly used from here now
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager // Added for RecyclerView
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentCoinFlipBinding
import com.example.purramid.thepurramid.randomizers.CoinProbabilityMode
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinFace
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinFlipUiState
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinFlipViewModel
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinInPool
import com.example.purramid.thepurramid.randomizers.viewmodel.CoinType
import com.example.purramid.thepurramid.randomizers.viewmodel.ProbabilityGridCell
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class CoinFlipFragment : Fragment() {

    private var _binding: FragmentCoinFlipBinding? = null
    private val binding get() = _binding!!

    private val coinFlipViewModel: CoinFlipViewModel by activityViewModels()
    private val settingsViewModel: RandomizerSettingsViewModel by activityViewModels()

    private lateinit var probabilityGridAdapter: ProbabilityGridAdapter
    private lateinit var coinAdapter: CoinAdapter // Added adapter for coin pool

    private val coinViewMapForFreeForm = mutableMapOf<UUID, ImageView>() // Renamed for clarity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoinFlipBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCoinAdapter() // Setup coin adapter early
        setupUIListeners()
        setupProbabilityGridRecyclerView()
        observeViewModel()
        coinFlipViewModel.refreshSettings()
    }

    private fun setupCoinAdapter() {
        val initialSettings = coinFlipViewModel.uiState.value.settings
        coinAdapter = CoinAdapter(
            coinColor = initialSettings?.coinColor ?: ContextCompat.getColor(requireContext(), R.color.goldenrod),
            animateFlips = initialSettings?.isFlipAnimationEnabled ?: true,
            getCoinDrawableResFunction = ::getCoinDrawableResource // Pass function reference
        )
        binding.coinDisplayAreaRecyclerView.apply { // Changed ID
            adapter = coinAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            // Add item decoration for spacing if desired
        }
    }

    private fun setupUIListeners() {
        binding.coinFlipCloseButton.setOnClickListener { activity?.finish() }
        binding.coinFlipSettingsButton.setOnClickListener {
            coinFlipViewModel.uiState.value.settings?.instanceId?.let { instanceId ->
                try {
                    val action = CoinFlipFragmentDirections.actionCoinFlipFragmentToRandomizerSettingsFragment(instanceId.toString())
                    findNavController().navigate(action)
                } catch (e: Exception) {
                    Log.e("CoinFlipFragment", "Navigation to Settings failed.", e)
                }
            }
        }
        binding.coinFlipActionButton.setOnClickListener { handleActionButtonClick() }
        binding.manageCoinPoolButton.setOnClickListener {
            coinFlipViewModel.uiState.value.settings?.instanceId?.let {
                CoinPoolDialogFragment.newInstance(it).show(parentFragmentManager, CoinPoolDialogFragment.TAG)
            }
        }
        // Drag listener for Free Form needs to be on a FrameLayout, not RecyclerView.
        // We'll handle this when Free Form is active by potentially overlaying a FrameLayout or using a different container.
        // For now, coinDisplayAreaRecyclerView is for standard mode.
        // binding.coinDisplayAreaRecyclerView.setOnDragListener(coinDragListener) // Remove if coinDisplayArea is now RecyclerView for standard mode
    }

    private fun getCoinDrawableResource(type: CoinType, face: CoinFace): Int {
        // Copied from previous version, ensure R.drawable names match your actual files
        val baseName = when (type) {
            CoinType.BIT_1 -> "ic_1b_coin_flip"
            CoinType.BIT_5 -> "ic_5b_coin_flip"
            CoinType.BIT_10 -> "ic_10b_coin_flip"
            CoinType.BIT_25 -> "ic_25b_coin_flip"
            CoinType.MEATBALL_1 -> "ic_1mb_coin_flip"
            CoinType.MEATBALL_2 -> "ic_2mb_coin_flip"
        }
        val faceName = if (face == CoinFace.HEADS) "_heads" else "_tails"
        val resName = baseName + faceName
        val resId = resources.getIdentifier(resName, "drawable", requireContext().packageName)
        return if (resId != 0) resId else {
            Log.w("CoinFlipFragment", "Drawable not found: $resName. Using generic placeholder.")
            if (face == CoinFace.HEADS) R.drawable.coin_flip_heads else R.drawable.ic_coin_flip_tails
        }
    }


    private fun updateUi(state: CoinFlipUiState) {
        val settings = state.settings
        if (settings == null) {
            binding.coinFlipActionButton.isEnabled = false
            binding.coinFlipTitleTextView.text = getString(R.string.loading_settings)
            return
        }
        binding.coinFlipActionButton.isEnabled = !state.isFlipping && state.coinPool.isNotEmpty()
        binding.coinFlipTitleTextView.text = getString(R.string.randomizer_mode_coin_flip)

        val currentProbMode = CoinProbabilityMode.valueOf(settings.coinProbabilityMode)
        when {
            settings.isCoinFreeFormEnabled -> binding.coinFlipActionButton.text = state.freeFormButtonText
            state.isProbabilityGridFull && currentProbMode.name.startsWith("GRID_") -> {
                binding.coinFlipActionButton.setText(R.string.reset_action)
            }
            else -> binding.coinFlipActionButton.setText(R.string.flip_coins_action)
        }

        // Update adapter properties if settings changed
        coinAdapter.updateCoinAppearanceProperties(settings.coinColor, settings.isFlipAnimationEnabled)

        if (settings.isCoinFreeFormEnabled) {
            binding.coinDisplayAreaRecyclerView.visibility = View.GONE // Hide RecyclerView
            // TODO: Setup a FrameLayout for free form coin views here, make it visible
            // binding.freeFormCoinContainer.visibility = View.VISIBLE
            // updateFreeFormCoinViews(state.coinPool, settings.coinColor)
            Log.d("CoinFlipFragment", "Free Form Mode Active - RecyclerView hidden")
            // Temporarily clear free form map to avoid conflicts
            clearFreeFormCoinViews()
            // For now, if free form is on, we'll just hide the recycler and clear the map
            // The actual free form views need a separate container to be dragged upon.

        } else {
            binding.coinDisplayAreaRecyclerView.visibility = View.VISIBLE
            // TODO: Hide freeFormCoinContainer if you add one
            // binding.freeFormCoinContainer.visibility = View.GONE
            coinAdapter.submitList(state.coinPool)
            Log.d("CoinFlipFragment", "Standard Mode Active - Pool submitted to RecyclerView: ${state.coinPool.size} items")
            clearFreeFormCoinViews() // Ensure free form views are cleared when not in free form mode

            if (state.isFlipping && settings.isFlipAnimationEnabled) {
                state.coinPool.forEach { coinWithResult ->
                    coinAdapter.triggerFlipAnimationForItem(coinWithResult.id, coinWithResult.currentFace)
                }
            }
        }

        // Visibility of Probability Sections
        binding.probabilityTwoColumnLayout.isVisible = currentProbMode == CoinProbabilityMode.TWO_COLUMNS && !settings.isCoinFreeFormEnabled && !settings.isCoinAnnouncementEnabled
        val isGridModeActive = (currentProbMode == CoinProbabilityMode.GRID_3X3 || currentProbMode == CoinProbabilityMode.GRID_6X6 || currentProbMode == CoinProbabilityMode.GRID_10X10)
        binding.probabilityGridLayout.isVisible = isGridModeActive && !settings.isCoinFreeFormEnabled && !settings.isCoinAnnouncementEnabled
        binding.probabilityGraphLayout.isVisible = currentProbMode == CoinProbabilityMode.GRAPH_DISTRIBUTION && !settings.isCoinFreeFormEnabled && !settings.isCoinAnnouncementEnabled

        if (binding.probabilityGridLayout.isVisible) {
            if ((binding.probabilityGridLayout.layoutManager as? GridLayoutManager)?.spanCount != state.probabilityGridColumns && state.probabilityGridColumns > 0) {
                binding.probabilityGridLayout.layoutManager = GridLayoutManager(context, state.probabilityGridColumns)
            }
            probabilityGridAdapter.submitList(state.probabilityGrid)
        }

        // Announcement Text
        val announcementShouldBeVisible = settings.isCoinAnnouncementEnabled && state.lastFlipResult != null && !state.isFlipping && !settings.isCoinFreeFormEnabled && currentProbMode == CoinProbabilityMode.NONE
        if (announcementShouldBeVisible) {
            binding.coinFlipAnnouncementTextView.text = getString(R.string.coin_flip_announce_format, state.lastFlipResult!!.totalHeads, state.lastFlipResult.totalTails)
            binding.coinFlipAnnouncementTextView.visibility = View.VISIBLE
        } else {
            binding.coinFlipAnnouncementTextView.visibility = View.GONE
        }

        state.errorEvent?.getContentIfNotHandled()?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
        }
    }


    // Free form specific logic (will need a separate container than the RecyclerView)
    // For now, these will target a conceptual 'binding.freeFormCoinContainer' which you'd add to XML.
    // Ensure coinDisplayAreaRecyclerView is GONE when free form is active.
    private fun getFreeFormContainer(): FrameLayout {
        // This is a placeholder. You'd ideally have a dedicated FrameLayout in your XML
        // that overlays or replaces the RecyclerView area for free form mode.
        // For this example, we'll attempt to use coinDisplayAreaRecyclerView's parent if it's a FrameLayout,
        // or throw an error. This is NOT ideal for production.
        if (binding.coinDisplayAreaRecyclerView.parent is FrameLayout) {
            return binding.coinDisplayAreaRecyclerView.parent as FrameLayout
        }
        // A better approach:
        // return binding.freeFormCoinContainer // where freeFormCoinContainer is a FrameLayout in your XML
        Log.e("CoinFlipFragment", "Free form requires a dedicated FrameLayout container not yet implemented. Using RecyclerView parent as fallback (not recommended).")
        return binding.coinDisplayAreaRecyclerView.parent as FrameLayout // Risky fallback
    }


    private fun updateFreeFormCoinViews(pool: List<CoinInPool>, colorInt: Int) {
        val container = getFreeFormContainer() // Get the correct container for free form
        if (container == binding.coinDisplayAreaRecyclerView && binding.coinDisplayAreaRecyclerView.isVisible) {
            Log.e("CoinFlipFragment", "Attempting to use RecyclerView for FreeForm. This should be a FrameLayout.")
            return // Avoid drawing on RecyclerView
        }

        val currentCoinIds = pool.map { it.id }.toSet()

        // Remove views for coins no longer in the pool or if container is wrong
        val viewsToRemove = coinViewMapForFreeForm.filterKeys { it !in currentCoinIds }
        viewsToRemove.forEach { (id, view) ->
            (view.parent as? ViewGroup)?.removeView(view) // Remove from its actual parent
            coinViewMapForFreeForm.remove(id)
        }

        // Add/Update views for coins in the pool
        pool.forEach { coin ->
            val coinView = coinViewMapForFreeForm.getOrPut(coin.id) {
                ImageView(requireContext()).apply {
                    val sizePx = requireContext().resources.getDimensionPixelSize(R.dimen.coin_freeform_size)
                    layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                    contentDescription = getString(R.string.coin_image_desc, coin.type.label, coin.currentFace.name.lowercase())
                    setOnLongClickListener { v ->
                        val item = ClipData.Item(coin.id.toString() as CharSequence)
                        val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        val dragData = ClipData(coin.id.toString(), mimeTypes, item)
                        val shadowBuilder = View.DragShadowBuilder(v)
                        v.startDragAndDrop(dragData, shadowBuilder, v, 0)
                        true
                    }
                    container.addView(this) // Add to the correct container
                }
            }
            coinView.setImageResource(getCoinDrawableResource(coin.type, coin.currentFace))
            applyCoinTint(coinView, colorInt)
            coinView.x = coin.xPos
            coinView.y = coin.yPos
            coinView.visibility = View.VISIBLE
        }
    }

    private fun clearFreeFormCoinViews() {
        val container = getFreeFormContainer()
        coinViewMapForFreeForm.forEach { (_, view) -> container.removeView(view) }
        coinViewMapForFreeForm.clear()
    }


    @SuppressLint("NewApi")
    private val coinDragListener = View.OnDragListener { v, event ->
        val draggedView = event.localState as? ImageView ?: return@OnDragListener false
        // val owner = draggedView.parent as? ViewGroup // Not always reliable if view is re-parented

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                draggedView.visibility = View.INVISIBLE
                true
            }
            DragEvent.ACTION_DROP -> {
                val container = v as FrameLayout // This is the coinDisplayArea (or freeFormCoinContainer)
                val x = event.x - (draggedView.width / 2)
                val y = event.y - (draggedView.height / 2)

                val coinIdString = event.clipDescription?.label?.toString()
                if (coinIdString != null) {
                    try {
                        val coinId = UUID.fromString(coinIdString)
                        val newX = x.coerceIn(0f, (container.width - draggedView.width).toFloat())
                        val newY = y.coerceIn(0f, (container.height - draggedView.height).toFloat())

                        // Update ViewModel first
                        coinFlipViewModel.updateCoinPositionInFreeForm(coinId, newX, newY)
                        // The UI should update via the observer, but for immediate feedback:
                        draggedView.x = newX
                        draggedView.y = newY

                    } catch (e: IllegalArgumentException) {
                        Log.e("CoinFlipFragment", "Invalid UUID from drag event: $coinIdString")
                    }
                }
                draggedView.visibility = View.VISIBLE
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                if (!event.result) { // Dropped outside a valid target
                    draggedView.visibility = View.VISIBLE
                }
                true
            }
            else -> true // Consume other drag events like ENTERED, EXITED, LOCATION
        }
    }

    private fun applyCoinTint(imageView: ImageView, colorInt: Int?) {
        val actualColor = colorInt ?: coinFlipViewModel.uiState.value.settings?.coinColor ?: ContextCompat.getColor(requireContext(), R.color.goldenrod)
        if (actualColor != Color.TRANSPARENT) { // Avoid tinting if color is meant to be transparent (use default)
            imageView.colorFilter = PorterDuffColorFilter(actualColor, PorterDuff.Mode.SRC_IN)
        } else {
            imageView.clearColorFilter()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear drag listener from the correct view if it was set
        // If coinDisplayAreaRecyclerView is not the drag target for freeform, this is fine.
        // If you have a separate freeFormCoinContainer, clear its listener.
        (binding.coinDisplayAreaRecyclerView.parent as? ViewGroup)?.setOnDragListener(null) // Example if parent was used.
        clearFreeFormCoinViews()
        _binding = null
    }
}