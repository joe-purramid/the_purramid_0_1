// SlotsMainFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.net.Uri // Import Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView // Import ImageView
import android.widget.LinearLayout // Import LinearLayout
import android.widget.TextView
import android.widget.Toast // For error messages
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide // Import Glide
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.databinding.FragmentSlotsMainBinding // Use Fragment binding
import com.example.purramid.thepurramid.randomizers.SlotsColumnState
import com.example.purramid.thepurramid.randomizers.SpinItemType
import com.example.purramid.thepurramid.randomizers.viewmodel.SlotsResult
import com.example.purramid.thepurramid.randomizers.viewmodel.SlotsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class SlotsMainFragment : Fragment() {

    private var _binding: FragmentSlotsMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SlotsViewModel by viewModels()

    private lateinit var columnViews: List<SlotColumnView>
    private lateinit var listSelectionAdapter: ArrayAdapter<String>
    private var availableLists: List<SpinListEntity> = emptyList()

    // LinearLayout within the announcement overlay to add result views to
    private var announcementResultsContainer: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlotsMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the container within the overlay (assuming it has an ID or is the first child)
        announcementResultsContainer = binding.slotsAnnouncementOverlay.findViewById(R.id.slotsAnnouncementResultsLayout)
        // If you didn't add an ID, you might need to get the child LinearLayout differently:
        // if (binding.slotsAnnouncementOverlay.childCount > 0 && binding.slotsAnnouncementOverlay.getChildAt(0) is LinearLayout) {
        //     announcementResultsContainer = binding.slotsAnnouncementOverlay.getChildAt(0) as LinearLayout
        // }

        initializeColumnViews()
        setupUIListeners()
        observeViewModel()

        listSelectionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        announcementResultsContainer = null // Clear reference
        _binding = null
    }

    private fun initializeColumnViews() {
        columnViews = listOfNotNull(
            binding.slotColumnView1,
            binding.slotColumnView2,
            binding.slotColumnView3,
            binding.slotColumnView4,
            binding.slotColumnView5
        )
    }

    private fun setupUIListeners() {
        binding.slotsCloseButton.setOnClickListener {
            // TODO: Call viewModel.handleManualClose() here once it's implemented
            // viewModel.handleManualClose()
            activity?.finish()
        }
        binding.slotsSettingsButton.setOnClickListener {
            viewModel.instanceId?.let { id ->
                try {
                     // *** Assumes navigation action exists in randomizers_nav_graph.xml ***
                     val action = SlotsMainFragmentDirections.actionSlotsMainFragmentToSettingsFragment(id.toString())
                     findNavController().navigate(action)
                } catch (e: Exception) {
                     Log.e("SlotsMainFragment", "Navigation to Settings failed. Ensure NavGraph action exists.", e)
                     Toast.makeText(context, "Cannot open settings.", Toast.LENGTH_SHORT).show() // Inform user
                }
            } ?: run {
                 Toast.makeText(context, "Cannot open settings: Invalid ID", Toast.LENGTH_SHORT).show()
            }
        }
        binding.slotsSpinButton.setOnClickListener {
            binding.slotsAnnouncementOverlay.isVisible = false
            viewModel.spinAllUnlocked()
        }

        columnViews.forEachIndexed { index, columnView ->
            columnView.setOnTitleClickListener { showListSelectionDialog(index) }
            columnView.setOnLockClickListener { viewModel.toggleLockForColumn(index) }
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        viewModel.settings.observe(lifecycleOwner) { settings ->
            settings?.let {
                 // *** Assumes settings.numSlotsColumns exists ***
                 val numColumns = settings.numSlotsColumns
                 updateColumnCount(numColumns)
            }
        }

        viewModel.columnStates.observe(lifecycleOwner) { states ->
            updateColumnsUI(states)
        }

        viewModel.allSpinLists.observe(lifecycleOwner) { lists ->
            availableLists = lists ?: emptyList()
            listSelectionAdapter.clear()
            listSelectionAdapter.addAll(availableLists.map { it.title })
        }

        viewModel.isSpinning.observe(lifecycleOwner) { spinningMap ->
             columnViews.forEachIndexed { index, columnView ->
                 if (spinningMap[index] == true) {
                     columnView.startSpinAnimation()
                 }
             }
        }

        viewModel.spinResult.observe(lifecycleOwner) { slotsResult ->
            slotsResult?.let {
                 handleSpinResult(it)
                 viewModel.clearSpinResult()
            }
        }

        viewModel.errorEvent.observe(lifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show() // Show error via Toast
                viewModel.clearErrorEvent()
            }
        }
    }

    private fun updateColumnCount(count: Int) {
        columnViews.forEachIndexed { index, columnView ->
            columnView.isVisible = index < count
        }
    }

    private fun updateColumnsUI(states: List<SlotsColumnState>) {
        states.forEach { state ->
            if (state.columnIndex < columnViews.size) {
                val columnView = columnViews[state.columnIndex]
                columnView.setLockedState(state.isLocked)
                val listTitle = availableLists.firstOrNull { it.id == state.selectedListId }?.title
                columnView.setTitle(listTitle)

                // Get data from ViewModel cache and set it on the view
                val currentList = viewModel.getItemsForColumn(state.columnIndex)
                columnView.setData(currentList, state.currentItemId)
            }
        }
    }

    private fun handleSpinResult(result: SlotsResult) {
        // 1. Stop animations and display final items
        result.results.forEach { (state, finalItem) ->
             if (state.columnIndex < columnViews.size) {
                 val columnView = columnViews[state.columnIndex]
                 // Call stopSpinAnimation with the final item ID. The view will handle display.
                 columnView.stopSpinAnimation(finalItem?.id)
             }
        }

        // 2. Show announcement if enabled
        // *** Assumes settings.isAnnounceEnabled exists ***
        val announceEnabled = viewModel.settings.value?.isAnnounceEnabled ?: false
        if (announceEnabled) {
             showAnnouncement(result)
        }
    }

    private fun showAnnouncement(result: SlotsResult) {
         announcementResultsContainer?.let { container ->
             container.removeAllViews() // Clear previous results

             result.results.forEach { (state, item) ->
                 // Create a view (TextView or ImageView) for each result item
                 val viewToAdd: View = createViewForResultItem(item)
                 // Add some layout params if needed (e.g., margins)
                 val params = LinearLayout.LayoutParams(
                     LinearLayout.LayoutParams.WRAP_CONTENT,
                     LinearLayout.LayoutParams.WRAP_CONTENT
                 ).apply {
                     marginEnd = resources.getDimensionPixelSize(R.dimen.small_padding) // Example padding
                 }
                 viewToAdd.layoutParams = params
                 container.addView(viewToAdd)
             }
             binding.slotsAnnouncementOverlay.isVisible = true
             binding.slotsAnnouncementOverlay.setOnClickListener {
                 it.isVisible = false // Dismiss on tap
             }
         } ?: run {
            Log.e("SlotsMainFragment", "Announcement results container not found in overlay layout!")
         }
    }

    /** Creates a TextView or ImageView for a result item */
    private fun createViewForResultItem(item: SpinItemEntity?): View {
        val context = requireContext()
        if (item == null) {
            // Handle null item (e.g., empty list)
            return TextView(context).apply {
                text = "-" // Placeholder for empty
                setTextAppearance(R.style.TextAppearance_AppCompat_Headline5) // Example style
            }
        }

        return when (item.itemType) {
            SpinItemType.IMAGE -> {
                 // *** Assumes you will add ImageView creation logic later ***
                 // For now, show placeholder text even for images
                 TextView(context).apply {
                    text = "[Image]" // Replace with ImageView later
                    setTextAppearance(R.style.TextAppearance_AppCompat_Headline5)
                 }
                /* // Example ImageView creation (add later)
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(100.dpToPx(), 100.dpToPx()) // Example size
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    try {
                        Glide.with(this@SlotsMainFragment)
                            .load(Uri.parse(item.content))
                            .into(this)
                    } catch (e: Exception) {
                        setImageResource(R.drawable.error_placeholder) // Set error drawable
                    }
                }
                */
            }
            SpinItemType.EMOJI -> {
                TextView(context).apply {
                    text = item.emojiList.joinToString(" ")
                    setTextAppearance(R.style.TextAppearance_AppCompat_Headline5)
                }
            }
            SpinItemType.TEXT -> {
                TextView(context).apply {
                    text = item.content
                    setTextAppearance(R.style.TextAppearance_AppCompat_Headline5)
                }
            }
        }
    }


    private fun showListSelectionDialog(columnIndex: Int) {
        if (availableLists.isEmpty()) {
            Toast.makeText(context, "No lists available to select.", Toast.LENGTH_SHORT).show() // TODO: String resource
            return
        }
        val currentListId = viewModel.columnStates.value?.getOrNull(columnIndex)?.selectedListId

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_list_for_column, columnIndex + 1)) // TODO: String resource
            .setSingleChoiceItems(
                availableLists.map { it.title }.toTypedArray(), // Display titles
                availableLists.indexOfFirst { it.id == currentListId } // Pre-select current item
            ) { dialog, which ->
                val selectedList = availableLists[which]
                viewModel.selectListForColumn(columnIndex, selectedList.id)
                dialog.dismiss() // Dismiss on selection
            }
            .setNegativeButton(R.string.cancel, null)
             .setNeutralButton(R.string.list_selection_clear) { dialog, _ -> // TODO: String resource
                 viewModel.selectListForColumn(columnIndex, null)
                 dialog.dismiss()
             }
            .show()
    }
    // Removed findItemData - logic moved to ViewModel/cache access

}
// TODO: Add dpToPx extension function if needed for dynamic ImageView sizing
// fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

// TODO: Add required String resources:
// R.string.select_list_for_column (e.g., "Select List for Column %1$d")
// R.string.list_selection_clear (e.g., "Clear Selection")
// R.string.no_lists_available (e.g., "No lists available to select.")
// R.string.cannot_open_settings (e.g., "Cannot open settings.")
// R.string.settings_navigation_failed (e.g., "Navigation to Settings failed...")

// TODO: Add Navigation action from SlotsMainFragment to Settings Fragment/Activity in nav graph
// Example action ID: action_slotsMainFragment_to_settingsFragment

// TODO: Add R.id.slotsAnnouncementResultsLayout to the LinearLayout inside slotsAnnouncementOverlay in fragment_slots_main.xml