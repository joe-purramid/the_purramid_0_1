// RandomizerMainFragment.kt
package com.example.purramid.thepurramid.randomizers // Or .randomizers.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color // Import color for placeholder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.accessibility.AccessibilityManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer // Import Observer
import androidx.navigation.fragment.findNavController // Import for navigation
import com.bumptech.glide.Glide // Import Glide
import com.github.jinatonic.confetti.confetto.BitmapConfetto // Or ShapeConfetto
import com.github.jinatonic.confetti.ConfettiManager
import com.github.jinatonic.confetti.ParticleSystem // Correct import from androidx-particles
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.databinding.FragmentRandomizerMainBinding // Use Fragment binding
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

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

    // --- Track active particle systems ---
    private var particleSystems = mutableListOf<ConfettiManager>() // Use ConfettiManager from library

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
            // Navigate using NavController
            // Need to define action and destination in nav graph
            // findNavController().navigate(R.id.action_randomizerMainFragment_to_randomizerSettingsFragment) // Example
             Toast.makeText(requireContext(), "Navigate to Settings (TODO)", Toast.LENGTH_SHORT).show()
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
        if (_binding == null || context == null) return
        val container = binding.fireworksContainer ?: return

        // --- Accessibility Check: Reduced Motion ---
        val am = context?.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val reducedMotionEnabled = am?.isTouchExplorationEnabled ?: false // Approximation, or check API level for specific setting
        if (reducedMotionEnabled) {
            // Optionally show a static indicator instead of animation
            // For now, just skip the animation
            return
        }

        // Make container visible
        container.visibility = View.VISIBLE

        // --- Load Confetti Bitmaps ---
        val confettiDrawables = listOf(
            R.drawable.confetti_piece_red,
            R.drawable.confetti_piece_yellow,
            R.drawable.confetti_piece_green,
            R.drawable.confetti_piece_blue,
            R.drawable.confetti_piece_violet
        )

        val confettiBitmaps = confettiDrawables.mapNotNull { drawableId ->
            // Convert drawable to bitmap
            (ContextCompat.getDrawable(requireContext(), drawableId) as? BitmapDrawable)?.bitmap
            // Or if using simple PNGs:
            // BitmapFactory.decodeResource(resources, drawableId)
        }

        if (confettiBitmaps.isEmpty()) {
            stopCelebration()
            return
        }

        // --- Particle System Setup for Confetti ---
        val numParticles = 150 // More particles for confetti usually
        val durationPerParticle = 4000L // Let them stay longer

        // Create Confetto objects from the loaded bitmaps, cycling through colors
        val confettoList = List(numParticles) { index ->
            BitmapConfetto(confettiBitmaps[index % confettiBitmaps.size])
        }

        // Stop any previous systems
        particleSystems.forEach { it.terminate() }
        particleSystems.clear()

        // Configure the particle system for confetti burst/shower
        val ps = ParticleSystem(confettoList)
            .setSpeedBetween(0.1f, 5f)      // Slower speeds (pixels per physics step)
            .setSpeedVariance(2f)
            // .setEmissionMode(ParticleSystem.EmissionMode.STREAM) // For emit()
            .setEmissionDuration(1000L)     // Emit for 1 second (if using emit, not oneShot)
            .setInitialRotationBetween(0, 360) // Random initial rotation
            .setRotationSpeedVariance(180f)    // Add random spin variance
            .setRotationSpeed(90f)             // Average spin speed
            .setFadeOut(1000L)              // Start fading after 1000ms of particle life
            // Weak gravity, maybe slight sideways variance (wind)
            .addAcceleration(ParticleSystem.GRAVITY_ACCELERATION / 4f, 90f) // Weaker gravity
            .addAccelerationVariance(ParticleSystem.GRAVITY_ACCELERATION / 2f, 0f) // Some variance maybe
            // .addInitializer(AccelerationInitializer(0.0001f, 0.0002f, -30, 30)) // Optional wind effect
            .setParentViewGroup(container)

        // Emit from top edge, falling down (adjust emitter x, y, width, height)
        // Emit 'numParticles / second' for 'emissionDuration'
        // val manager = ps.emit(container, (numParticles / (durationPerParticle/1000.0)).toInt(), durationPerParticle.toInt())

        // OR: One burst from slightly above the center top
        val manager = ps.oneShot(container.width / 2, -30, numParticles)


        particleSystems.add(manager)

        // Schedule stop after 3 seconds (original requirement)
        container.postDelayed({
            stopCelebration()
            // Note: Particles might still be fading out after this if their
            // lifespan + fadeout > 3000ms. stopCelebration just stops emission.
        }, 3000)
    }

    private fun stopCelebration() {
        if (_binding == null) return

        // --- Stop and clear particle systems ---
        particleSystems.forEach { it.terminate() } // Stop emitting and clear
        particleSystems.clear()
        // ---

        // Hide the container
        binding.fireworksContainer.visibility = View.GONE
        // Optional: Clear any placeholder background if set
        binding.fireworksContainer.setBackgroundColor(Color.TRANSPARENT)
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