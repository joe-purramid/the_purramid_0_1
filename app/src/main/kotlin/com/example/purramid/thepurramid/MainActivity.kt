// MainActivity.kt
package com.example.purramid.thepurramid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint // Keep if needed for touch listener
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
// import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
// import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope // For launching coroutines
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.window.layout.WindowMetricsCalculator
import com.example.purramid.thepurramid.clock.ClockActivity
import com.example.purramid.thepurramid.databinding.ActivityMainBinding // Import generated binding class
import com.example.purramid.thepurramid.data.db.PurramidDatabase // Import Database
import com.example.purramid.thepurramid.data.db.RandomizerDao // For Randomizer new instance default settings
import com.example.purramid.thepurramid.data.db.DEFAULT_SETTINGS_ID // For Randomizer
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity // For Randomizer
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity // For Randomizer
import com.example.purramid.thepurramid.randomizers.RandomizerInstanceManager // Import Manager
import com.example.purramid.thepurramid.randomizers.RandomizersHostActivity
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerViewModel
import com.example.purramid.thepurramid.screen_shade.ScreenShadeActivity
import com.example.purramid.thepurramid.spotlight.SpotlightActivity
import com.example.purramid.thepurramid.timers.TimersActivity
import com.example.purramid.thepurramid.traffic_light.TrafficLightActivity
import com.example.purramid.thepurramid.util.dpToPx
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers // For DB Ops
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // For switching context

// --- Define simple Enums for Size Classes (for XML Views context) ---
// Based on Material Design breakpoints: https://m3.material.io/foundations/layout/applying-layout/window-size-classes
enum class WindowWidthSizeClass { COMPACT, MEDIUM, EXPANDED }
enum class WindowHeightSizeClass { COMPACT, MEDIUM, EXPANDED }

data class AppIntent(
    val title: String,
    @get:androidx.annotation.DrawableRes val iconResId: Int, // Store Res ID
    val action: (Context) -> Unit,
    val id: String // Unique ID for the intent type
)

class IntentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val iconImageView: ImageView = itemView.findViewById(R.id.intentIconImageView)
    val titleTextView: TextView = itemView.findViewById(R.id.intentTitleTextView)
}

class IntentAdapter(private val intents: List<AppIntent>, private val onItemClick: (AppIntent) -> Unit) :
    RecyclerView.Adapter<IntentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntentViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_intent, parent, false)
        return IntentViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: IntentViewHolder, position: Int) {
        val currentIntent = intents[position]
        holder.iconImageView.setImageResource(currentIntent.iconResId)
        holder.titleTextView.text = currentIntent.title
        holder.itemView.setOnClickListener {
            onItemClick(currentIntent)
        }
    }

    override fun getItemCount() = intents.size
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isIntentsVisible = false
    private val allIntents = mutableListOf<AppIntent>()
    // Keep screen pixel dimensions if needed for other things
    private var screenWidthPx: Int = 0
    private var screenHeightPx: Int = 0

    companion object {
        private const val TAG = "MainActivity"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate layout using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Calculate screen dimensions in pixels
        val displayMetrics = resources.displayMetrics
        screenWidthPx = displayMetrics.widthPixels
        screenHeightPx = displayMetrics.heightPixels

        // --- Calculate Window Size Classes ---
        val wmc = WindowMetricsCalculator.getOrCreate()
        // Use computeMaximumWindowMetrics for a more stable calculation,
        // or computeCurrentWindowMetrics if you need it to react instantly to multi-window changes
        val currentWindowMetrics = wmc.computeCurrentWindowMetrics(this)
        val widthDp = currentWindowMetrics.bounds.width() / displayMetrics.density
        val heightDp = currentWindowMetrics.bounds.height() / displayMetrics.density

        val widthSizeClass = when {
            widthDp < 600f -> WindowWidthSizeClass.COMPACT
            widthDp < 840f -> WindowWidthSizeClass.MEDIUM
            else -> WindowWidthSizeClass.EXPANDED
        }

        val heightSizeClass = when {
            heightDp < 480f -> WindowHeightSizeClass.COMPACT
            heightDp < 900f -> WindowHeightSizeClass.MEDIUM
            else -> WindowHeightSizeClass.EXPANDED
        }
        // Log calculated classes (optional for debugging)
        // Log.d("MainActivity", "Width: ${widthDp}dp ($widthSizeClass), Height: ${heightDp}dp ($heightSizeClass)")

        // Access views via binding object
        // Load app icon
        binding.appIconImageView.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.ic_launcher_foreground))

        defineAppIntents() // Call method to setup intents

        // Set up the RecyclerView
        binding.intentsRecyclerView.layoutManager = LinearLayoutManager(this)
        val intentAdapter = IntentAdapter(allIntents) { appIntent ->
            animateIntentSelection(appIntent) {
                appIntent.action.invoke(this@MainActivity)
                if (isIntentsVisible) {
                    hideIntentsAnimated()
                }
            }
        }
        binding.intentsRecyclerView.adapter = intentAdapter

        // Set up click listener for the app icon button
        binding.appIconButtonContainer.setOnClickListener {
            toggleIntentsVisibility()
        }

        // Set up touch listener for handling clicks outside the intents
        binding.root.setOnTouchListener { _, event -> // Attach listener to the root view from binding
            if (isIntentsVisible && event.action == MotionEvent.ACTION_DOWN) {
                if (!isTouchInsideView(event.rawX, event.rawY, binding.appIconButtonContainer) &&
                    !isTouchInsideView(event.rawX, event.rawY, binding.intentsRecyclerView)
                ) {
                    hideIntentsAnimated()
                    return@setOnTouchListener true
                }
            }
            return@setOnTouchListener false
        }

        // Apply Layout Adaptations based on Size Classes
        adaptLayoutToSizeClasses(widthSizeClass, heightSizeClass)
        // Set initial freeform window size
        setInitialFreeformWindowSize()
        // *** Add Restoration Logic ***
        restoreRandomizerInstances()
    }

    // Define Intents
    private fun defineAppIntents() {
        allIntents.clear() // Clear if adding dynamically
        allIntents.addAll(
            listOf(
                AppIntent(
                    title = getString(R.string.clock_title),
                    iconResId = R.drawable.ic_clock,
                    id = "clock",
                    action = { context ->
                        // ClockActivity handles logic for new vs settings
                        val intent = Intent(context, ClockActivity::class.java)
                        context.startActivity(intent)
                    }
                ),
                AppIntent(
                    title = getString(R.string.randomizers_title),
                    iconResId = R.drawable.ic_random,
                    id = "randomizers",
                    action = { _ ->
                        val activeRandomizerCount = RandomizerInstanceManager.getActiveInstanceCount()
                        if (activeRandomizerCount > 0) {
                            Log.d(TAG, "Randomizers active ($activeRandomizerCount), reordering to front.")
                            val intent = Intent(this@MainActivity, RandomizersHostActivity::class.java).apply {
                                // No specific instanceId needed here for REORDER_TO_FRONT to bring an existing task forward.
                                // The system will pick one of the tasks running RandomizersHostActivity.
                                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            startActivity(intent) // No special ActivityOptions needed for reordering
                        } else {
                            Log.d(TAG, "No active Randomizers, launching new offset instance.")
                            // Call the function that handles new UUID, offset bounds, and ActivityOptions
                            launchNewRandomizerInstance()
                        }
                    }
                ),

                AppIntent(
                    title = getString(R.string.screen_shade_title),
                    iconResId = R.drawable.ic_shade,
                    id = "screen_shade",
                    action = { context ->
                        // ScreenShadeActivity handles logic for new vs settings
                        val intent = Intent(context, ScreenShadeActivity::class.java)
                        context.startActivity(intent)
                    }
                ),
                AppIntent(
                    title = getString(R.string.spotlight_title),
                    iconResId = R.drawable.ic_spotlight, // Ensure you have ic_spotlight
                    id = "spotlight",
                    action = { context ->
                        // SpotlightActivity handles logic for new vs settings
                        val intent = Intent(context, SpotlightActivity::class.java)
                        context.startActivity(intent)
                    }
                ),
                AppIntent(
                    title = getString(R.string.timers_title),
                    iconResId = R.drawable.ic_timer,
                    id = "timers",
                    action = { context ->
                        // TimersActivity handles logic for new vs settings
                        val intent = Intent(context, TimersActivity::class.java)
                        context.startActivity(intent)
                    }
                ),
                AppIntent(
                    title = getString(R.string.traffic_light_title),
                    iconResId = R.drawable.ic_traffic_light,
                    id = "traffic_light",
                    action = { context ->
                        // TrafficLightActivity handles logic for new vs settings
                        val intent = Intent(context, TrafficLightActivity::class.java)
                        context.startActivity(intent)
                    }
                ),
                AppIntent(
                    title = getString(R.string.about),
                    iconResId = R.drawable.ic_about, // Ensure you have ic_about
                    id = "about",
                    action = { context ->
                        val intent = Intent(context, AboutActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // Standard behavior for About
                        context.startActivity(intent)
                    }
                )
            )
        )
    }

    private fun launchNewRandomizerInstance() {
        val instanceCount = RandomizerInstanceManager.getActiveInstanceCount()
        Log.d(TAG, "Current Randomizer Instance Count: $instanceCount for new launch") // Use your existing TAG

        val GdpToPx = resources.displayMetrics.density

        val baseOffsetDp = 50f
        val cascadeStepDp = 40f
        val offsetX = (baseOffsetDp * GdpToPx).toInt() + (instanceCount * (cascadeStepDp * GdpToPx).toInt())
        val offsetY = (baseOffsetDp * GdpToPx).toInt() + (instanceCount * (cascadeStepDp * GdpToPx).toInt())

        val defaultWidthDp = 800f
        val defaultHeightDp = 600f
        val windowWidth = (defaultWidthDp * GdpToPx).toInt()
        val windowHeight = (defaultHeightDp * GdpToPx).toInt()

        if (offsetX + windowWidth > screenWidthPx) { /* Adjust offsetX */ }
        if (offsetY + windowHeight > screenHeightPx) { /* Adjust offsetY */ }

        val newBounds = Rect(offsetX, offsetY, offsetX + windowWidth, offsetY + windowHeight)
        Log.d(TAG, "Calculated bounds for new Randomizer window: $newBounds")

        val newInstanceId = UUID.randomUUID()
        Log.d(TAG, "New Randomizer Instance ID: $newInstanceId")

        val intent = Intent(this, RandomizersHostActivity::class.java).apply {
            putExtra(RandomizersHostActivity.EXTRA_INSTANCE_ID, newInstanceId.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activityOptions = ActivityOptions.makeBasic()
        activityOptions.launchBounds = newBounds

        try {
            startActivity(intent, activityOptions.toBundle())
            Log.i(TAG, "Successfully started new RandomizersHostActivity instance with custom bounds.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting RandomizersHostActivity with custom bounds: ${e.localizedMessage}", e)
            // Consider showing a Toast to the user here
        }
    }

    private fun launchExistingRandomizer(instanceId: UUID) {
        Log.d(TAG, "Intending to launch existing Randomizer: $instanceId (restore logic)")
        val intent = Intent(this, RandomizersHostActivity::class.java).apply {
            putExtra(RandomizersHostActivity.EXTRA_INSTANCE_ID, instanceId.toString())
            // Important flags to try and bring an existing task to the foreground or create new if needed
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(intent)
    }

    // Helper Function for Touch Handling
    private fun isTouchInsideView(rawX: Float, rawY: Float, view: View): Boolean {
        if (!view.isShown) {
            return false
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewRect = Rect(
            location[0], location[1],
            location[0] + view.width, location[1] + view.height
        )
        return viewRect.contains(rawX.toInt(), rawY.toInt())
    }

    // Intent Selection Animation
    private fun animateIntentSelection(appIntent: AppIntent, onEndAction: () -> Unit) {
        // Animate the RecyclerView via binding
        val animatorSet = AnimatorSet().apply {
            play(ObjectAnimator.ofFloat(binding.intentsRecyclerView, "alpha", 1f, 0.8f, 1f))
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEndAction()
                }
            })
            start()
        }
    }

    // Visibility Toggle Function
    private fun toggleIntentsVisibility() {
        if (isIntentsVisible) {
            hideIntentsAnimated()
        } else {
            showIntentsAnimated()
        }
    }

    private fun showIntentsAnimated() {
        if (isIntentsVisible) return

        binding.intentsRecyclerView.visibility = View.VISIBLE
        binding.intentsRecyclerView.alpha = 0f

        // Access views via binding
        val scaleDownX = ObjectAnimator.ofFloat(binding.appIconImageView, View.SCALE_X, 1f, 0.8f)
        val scaleDownY = ObjectAnimator.ofFloat(binding.appIconImageView, View.SCALE_Y, 1f, 0.8f)
        val fadeIn = ObjectAnimator.ofFloat(binding.intentsRecyclerView, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(scaleDownX, scaleDownY, fadeIn)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    isIntentsVisible = true
                }
            })
            start()
        }
    }

    // Adapted Hide Animation
    private fun hideIntentsAnimated() {
        if (!isIntentsVisible) return

        // Define slide distance in DP and convert to pixels using the helper function
        val slideUpDistancePx = this.dpToPx(-30) // Slide up by 30dp (adjust as needed)

        // Access views via binding
        val scaleUpX = ObjectAnimator.ofFloat(binding.appIconImageView, View.SCALE_X, binding.appIconImageView.scaleX, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(binding.appIconImageView, View.SCALE_Y, binding.appIconImageView.scaleY, 1f)
        val fadeOut = ObjectAnimator.ofFloat(binding.intentsRecyclerView, View.ALPHA, 1f, 0f)
        val slideUp = ObjectAnimator.ofFloat(binding.intentsRecyclerView, View.TRANSLATION_Y, 0f, slideUpDistancePx.toFloat())

        AnimatorSet().apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            // Play scale, fade, and slide animations together
            playTogether(scaleUpX, scaleUpY, fadeOut, slideUp)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (_binding != null) { // Check binding
                        binding.intentsRecyclerView.visibility = View.GONE
                        binding.intentsRecyclerView.translationY = 0f
                    }
                    isIntentsVisible = false
                }
                override fun onAnimationStart(animation: Animator) {
                    if (_binding != null) {
                        binding.intentsRecyclerView.visibility = View.GONE
                        binding.intentsRecyclerView.translationY = 0f
                        binding.listTitleCaret.rotation = 0f
                    }
                    isIntentsVisible = false
                }
            })
            start()
        }
    }

    // Utility and Other Functions
    private fun adaptLayoutToSizeClasses(widthSizeClass: WindowWidthSizeClass, heightSizeClass: WindowHeightSizeClass) {
        val layoutManager = binding.intentsRecyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.orientation =
            if (widthSizeClass == WindowWidthSizeClass.EXPANDED || widthSizeClass == WindowWidthSizeClass.MEDIUM) {
                // Use horizontal scroll on Medium/Expanded width
                RecyclerView.HORIZONTAL
            } else {
                // Use vertical scroll on Compact width
                RecyclerView.VERTICAL
            }

        // Adjust padding, margins, font sizes etc. based on size classes
        val paddingSize = if (widthSizeClass == WindowWidthSizeClass.EXPANDED) {
            resources.getDimensionPixelSize(R.dimen.large_screen_padding) // Use larger padding
        } else {
            resources.getDimensionPixelSize(R.dimen.default_padding) // Use default padding
        }
        binding.intentsRecyclerView.setPadding(paddingSize, paddingSize, paddingSize, paddingSize)
    }

    private fun setInitialFreeformWindowSize(widthFraction: Float = 0.6f, heightFraction: Float = 0.7f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // Freeform support introduced in Nougat
            try {
                val window = this.window
                val layoutParams = WindowManager.LayoutParams()
                layoutParams.copyFrom(window.attributes)
                val calculatedWidthFraction = if (screenWidthPx > 3000) 0.25f else 0.4f
                val calculatedHeightFraction = if (screenWidthPx > 3000) 0.3f else 0.45f
                layoutParams.width = (screenWidthPx * calculatedWidthFraction).toInt()
                layoutParams.height = (screenHeightPx * calculatedHeightFraction).toInt()
                window.attributes = layoutParams
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// TODO Address how services are handling multiple instances of [clock, timers, etc.] compared to
// the individual free form windows of Randomizers (which is an activity and not a service

// TODO: [MULTI-INSTANCE RE-ARCHITECTURE] Revisit the multi-instance behavior for all app intents
// (Clock, Spotlight, Timers, ScreenShade, TrafficLight). The current vision is to allow
// up to four distinct, draggable, and individually configurable instances for each of these,
// similar to how Randomizers are intended to work.
// This will require:
// 1. Modifying each respective Activity (e.g., ClockActivity, SpotlightActivity) to:
//    a. Become a persistent, windowed UI host (not finish after starting a service).
//    b. Accept a unique instance ID (e.g., a UUID string).
//    c. Manage its state (settings, position, UI state) based on this instance ID.
// 2. Implementing a robust instance management system (perhaps a generic version of
//    RandomizerInstanceManager or a per-type manager) for MainActivity to accurately
//    track active Activity windows of each type.
// 3. Updating the `launchMultiInstanceActivity` function in MainActivity to use this
//    robust tracking for its "count < MAX_INSTANCES_PER_TYPE" logic and for
//    reliably bringing a specific existing instance to the front if the max is reached.
// This work is deferred until after the current Coin Flip development phase.
