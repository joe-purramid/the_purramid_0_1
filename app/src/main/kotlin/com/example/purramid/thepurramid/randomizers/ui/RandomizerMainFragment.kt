// RandomizerMainFragment.kt
package com.example.purramid.thepurramid.randomizers.ui // Or .randomizers.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide // Import Glide
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.databinding.FragmentRandomizerMainBinding // Use Fragment binding
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerViewModel
import com.example.purramid.thepurramid.randomizers.SpinItemType
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit // Import TimeUnit
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter

@AndroidEntryPoint
class RandomizerMainFragment : Fragment() {

    // Use Fragment binding
    private var _binding: FragmentRandomizerMainBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Get ViewModel scoped to this Fragment
    private val viewModel: RandomizerViewModel by viewModels()

    // Adapter and list for dropdown
    private lateinit var listDropdownAdapter: ArrayAdapter<String>
    private var listEntities: List<SpinListEntity> = emptyList()

    // Animation constant
    private val dropdownAnimationDuration = 300L

    // Sequence Views List
    private lateinit var sequenceTextViews: List<TextView>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRandomizerMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize sequence views list
        sequenceTextViews = listOf(
            binding.textSequencePrev4, binding.textSequencePrev3, binding.textSequencePrev2, binding.textSequencePrev1,
            binding.textSequenceCurrent,
            binding.textSequenceNext1, binding.textSequenceNext2, binding.textSequenceNext3, binding.textSequenceNext4
        )

        setupDropdown()
        setupUIListeners()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clean up binding reference
    }

    // --- Setup Functions (Moved from Activity) ---

    private fun setupDropdown() {
        listDropdownAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf<String>())
        binding.listDropdownListView.adapter = listDropdownAdapter
        binding.listDropdownListView.setOnItemClickListener { _, _, position, _ ->
            if (position < listEntities.size) {
                viewModel.selectList(listEntities[position].id)
            }
        }
    }

    private fun setupUIListeners() {
        binding.closeButton.setOnClickListener {
            // Trigger close logic in ViewModel (includes check for last instance)
            viewModel.handleManualClose()
            // Close the Activity hosting the fragment
            activity?.finish() // Or use NavController popBackStack if appropriate
        }
        binding.settingsButton.setOnClickListener {
            viewModel.instanceId?.let { id ->
                try {
                    val bundle = Bundle().apply {
                        putInt(RandomizerViewModel.KEY_INSTANCE_ID, id)
                    }

                    val navOptions = NavOptions.Builder()
                        .setEnterAnim(R.anim.settings_explosion_enter)
                        .setExitAnim(R.anim.settings_explosion_exit)
                        .setPopEnterAnim(R.anim.settings_explosion_enter)
                        .setPopExitAnim(R.anim.settings_explosion_exit)
                        .build()


                    // Navigate using NavController from NavHostFragment
                    val navController = (requireActivity().supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment_randomizers) as NavHostFragment)
                        .navController
                    navController.navigate(
                        R.id.action_randomizerMainFragment_to_randomizerSettingsFragment,
                        bundle,
                        navOptions
                    )
                } catch (e: Exception) {
                    Log.e("RandomizerMainFragment", "Navigation to Settings failed", e)
                    Snackbar.make(
                        binding.root,
                        getString(R.string.error_cannot_open_settings),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } ?: run {
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_settings_invalid_id),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        binding.spinButton.setOnClickListener {
            clearAnnounceCelebrate()
            viewModel.handleSpinRequest()
        }
        binding.listTitleMarquee.setOnClickListener {
            viewModel.toggleListDropdown()
        }
        binding.buttonSequenceUp.setOnClickListener {
            viewModel.showPreviousSequenceItem()
        }
        binding.buttonSequenceDown.setOnClickListener {
            viewModel.showNextSequenceItem()
        }
    }

    private fun observeViewModel() {
        // Use viewLifecycleOwner for observing LiveData in Fragments
        val lifecycleOwner = viewLifecycleOwner

        viewModel.currentListTitle.observe(lifecycleOwner) { title ->
            binding.listTitleTextView.text = title ?: getString(R.string.select_list)
        }
        viewModel.isDropdownVisible.observe(lifecycleOwner) { isVisible ->
            if (isVisible) {
                animateDropdownOpen()
            } else {
                if (binding.listDropdownCardView.visibility == View.VISIBLE) {
                    animateDropdownClose()
                } else {
                    binding.listDropdownCardView.visibility = View.GONE
                    binding.listTitleCaret.rotation = 0f
                }
            }
        }
        viewModel.displayedListOrder.observe(lifecycleOwner) { sortedLists ->
            listEntities = sortedLists ?: emptyList()
            val listTitles = listEntities.map { it.title }
            listDropdownAdapter.clear()
            listDropdownAdapter.addAll(listTitles)
            listDropdownAdapter.notifyDataSetChanged()
        }
        viewModel.spinDialData.observe(lifecycleOwner) { data ->
            data?.settings?.let { settings ->
                binding.spinDialView.setData(data.items, settings)
                updateSequenceVisibility()
                if (settings.isSequenceEnabled) {
                    binding.announcementTextView.visibility = View.GONE
                    binding.announcementImageView.visibility = View.GONE
                }
            }
        }
        viewModel.spinResult.observe(lifecycleOwner) { resultItem ->
             val settings = viewModel.spinDialData.value?.settings
             val spinEnabled = settings?.isSpinEnabled ?: true

             if (resultItem == null && spinEnabled && settings?.isSequenceEnabled == false) {
                 clearAnnounceCelebrate()
                 binding.spinDialView.spin { resultFromView ->
                     viewModel.setSpinResult(resultFromView)
                 }
             } else if (resultItem != null) {
                 if (settings?.isSequenceEnabled == false) {
                     if (settings.isAnnounceEnabled) {
                         displayAnnouncement(resultItem)
                         if (settings.isCelebrateEnabled) {
                             startCelebration()
                         }
                     } else {
                          clearAnnounceCelebrate()
                     }
                 } else {
                      clearAnnounceCelebrate() // Also clear if sequence IS enabled
                 }
                 viewModel.clearSpinResult()
             }
        }
        viewModel.sequenceList.observe(lifecycleOwner) { sequence ->
            updateSequenceVisibility()
            updateSequenceDisplay()
        }
        viewModel.sequenceIndex.observe(lifecycleOwner) { index ->
            updateSequenceDisplay()
        }
    }

    // --- UI Update/Animation Functions (Moved from Activity, use binding directly) ---

    private fun updateSequenceVisibility() {
        // Requires viewLifecycleOwner checks if called outside observer? No, binding is safe here.
        if (_binding == null) return // Check if binding is null (view destroyed)
        val settings = viewModel.spinDialData.value?.settings
        val sequenceActive = viewModel.sequenceList.value != null
        binding.sequenceDisplayContainer.isVisible = settings?.isSequenceEnabled == true && sequenceActive
    }

    private fun updateSequenceDisplay() {
        if (_binding == null) return // Check if binding is null
        val sequence = viewModel.sequenceList.value
        val index = viewModel.sequenceIndex.value ?: 0

        if (sequence == null || !binding.sequenceDisplayContainer.isVisible) {
            sequenceTextViews.forEach { it.visibility = View.GONE }
            binding.fadeOverlayPrev4.visibility = View.GONE
            binding.fadeOverlayNext4.visibility = View.GONE
            binding.buttonSequenceUp.visibility = View.GONE
            binding.buttonSequenceDown.visibility = View.GONE
            return
        }

        binding.buttonSequenceUp.visibility = View.VISIBLE
        binding.buttonSequenceDown.visibility = View.VISIBLE

        fun getItemContent(idx: Int): String { return sequence.getOrNull(idx)?.content ?: "" }

        binding.textSequenceCurrent.text = getItemContent(index)
        binding.textSequenceCurrent.visibility = View.VISIBLE

        binding.textSequencePrev1.text = getItemContent(index - 1); binding.textSequencePrev1.visibility = if (index > 0) View.VISIBLE else View.GONE
        binding.textSequencePrev2.text = getItemContent(index - 2); binding.textSequencePrev2.visibility = if (index > 1) View.VISIBLE else View.GONE
        binding.textSequencePrev3.text = getItemContent(index - 3); binding.textSequencePrev3.visibility = if (index > 2) View.VISIBLE else View.GONE
        binding.textSequencePrev4.text = getItemContent(index - 4); binding.textSequencePrev4.visibility = if (index > 3) View.VISIBLE else View.GONE

        binding.textSequenceNext1.text = getItemContent(index + 1); binding.textSequenceNext1.visibility = if (index < sequence.size - 1) View.VISIBLE else View.GONE
        binding.textSequenceNext2.text = getItemContent(index + 2); binding.textSequenceNext2.visibility = if (index < sequence.size - 2) View.VISIBLE else View.GONE
        binding.textSequenceNext3.text = getItemContent(index + 3); binding.textSequenceNext3.visibility = if (index < sequence.size - 3) View.VISIBLE else View.GONE
        binding.textSequenceNext4.text = getItemContent(index + 4); binding.textSequenceNext4.visibility = if (index < sequence.size - 4) View.VISIBLE else View.GONE

        binding.fadeOverlayPrev4.isVisible = (index > 4)
        binding.fadeOverlayNext4.isVisible = (index < sequence.size - 5)

        binding.buttonSequenceUp.isEnabled = index > 0
        binding.buttonSequenceDown.isEnabled = index < sequence.size - 1
    }

     private fun displayAnnouncement(item: SpinItemEntity) {
         if (_binding == null) return
         var viewToAnimate: View? = null
         binding.announcementTextView.visibility = View.GONE
         binding.announcementImageView.visibility = View.GONE

         when(item.itemType) {
             SpinItemType.TEXT -> {
                 binding.announcementTextView.text = item.content
                 viewToAnimate = binding.announcementTextView
             }
             SpinItemType.IMAGE -> {
                 Glide.with(this) // Use 'this' (Fragment) or requireContext()
                      .load(item.content)
                      .into(binding.announcementImageView)
                 viewToAnimate = binding.announcementImageView
             }
             SpinItemType.EMOJI -> {
                 binding.announcementTextView.text = item.emojiList.joinToString(" ")
                 viewToAnimate = binding.announcementTextView
             }
         }

         viewToAnimate?.apply {
             scaleX = 0.2f; scaleY = 0.2f; alpha = 0f
             visibility = View.VISIBLE
             AnimatorSet().apply {
                 playTogether(
                     ObjectAnimator.ofFloat(viewToAnimate, View.SCALE_X, 0.2f, 1.0f),
                     ObjectAnimator.ofFloat(viewToAnimate, View.SCALE_Y, 0.2f, 1.0f),
                     ObjectAnimator.ofFloat(viewToAnimate, View.ALPHA, 0f, 1.0f)
                 )
                 duration = 500L
                 interpolator = AccelerateDecelerateInterpolator()
                 start()
             }
         }
     }

    private fun startCelebration() {
        if (_binding == null) return

        // --- Accessibility Check: Reduced Motion ---
        val am = context?.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val reducedMotionEnabled = am?.isTouchExplorationEnabled ?: false
        if (reducedMotionEnabled) {
            return
        }

        // --- Konfetti Configuration ---
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF4363D8.toInt(), 0xFF7F00FF.toInt()),
            position = Position.Relative(0.5, 0.0),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100)
        )

        binding.konfettiView.start(party)

        // Auto-stop after 3 seconds
        binding.konfettiView.postDelayed({
            if (_binding != null) {
                binding.konfettiView.stopGracefully()
            }
        }, 3000)
    }

    private fun stopCelebration() {
        if (_binding == null) return
        binding.konfettiView.stopGracefully()
    }

    private fun clearAnnounceCelebrate() {
        if (_binding == null) return
         binding.announcementTextView.animate().cancel()
         binding.announcementImageView.animate().cancel()
         binding.announcementTextView.visibility = View.GONE
         binding.announcementImageView.visibility = View.GONE
         binding.announcementTextView.text = ""
         if (context != null) Glide.with(requireContext()).clear(binding.announcementImageView) // Use requireContext()

         binding.announcementTextView.alpha = 1f; binding.announcementTextView.scaleX = 1f; binding.announcementTextView.scaleY = 1f
         binding.announcementImageView.alpha = 1f; binding.announcementImageView.scaleX = 1f; binding.announcementImageView.scaleY = 1f
         stopCelebration()
    }

    private fun animateDropdownOpen() {
         if (_binding == null) return
        // ... (implementation remains the same, just use binding directly) ...
         binding.listDropdownCardView.apply {
             visibility = View.VISIBLE; alpha = 0f; translationY = -height.toFloat() / 4
             val alphaAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
             val translationYAnimator = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY, 0f)
             val caretAnimator = ObjectAnimator.ofFloat(binding.listTitleCaret, View.ROTATION, 0f, 180f)
             AnimatorSet().apply {
                 playTogether(alphaAnimator, translationYAnimator, caretAnimator); duration = dropdownAnimationDuration
                 interpolator = AccelerateDecelerateInterpolator(); start()
             }
         }
    }

    private fun animateDropdownClose() {
        if (_binding == null) return
        // ... (implementation remains the same, just use binding directly) ...
         binding.listDropdownCardView.apply {
             val alphaAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 0f)
             val translationYAnimator = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY, -height.toFloat() / 4)
             val caretAnimator = ObjectAnimator.ofFloat(binding.listTitleCaret, View.ROTATION, binding.listTitleCaret.rotation, 0f)
             AnimatorSet().apply {
                 playTogether(alphaAnimator, translationYAnimator, caretAnimator); duration = dropdownAnimationDuration
                 interpolator = AccelerateDecelerateInterpolator()
                 addListener(object : AnimatorListenerAdapter() {
                     override fun onAnimationEnd(animation: Animator) { if (_binding != null) { visibility = View.GONE; translationY = 0f } }
                     override fun onAnimationCancel(animation: Animator) { if (_binding != null) { visibility = View.GONE; translationY = 0f; binding.listTitleCaret.rotation = 0f } }
                 })
                 start()
             }
         }
    }
}