// MainActivity.kt
package com.example.purramid.thepurramid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint // Keep if needed for touch listener
// import android.content.Context
import android.content.Intent
// import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.example.purramid.thepurramid.randomizers.RandomizerInstanceManager // Import Manager
import com.example.purramid.thepurramid.randomizers.RandomizersActivity
import com.example.purramid.thepurramid.randomizers.RandomizersHostActivity
import com.example.purramid.thepurramid.screen_shade.ScreenShadeActivity
import com.example.purramid.thepurramid.spotlight.SpotlightActivity
import com.example.purramid.thepurramid.timers.TimersActivity
import com.example.purramid.thepurramid.traffic_light.TrafficLightActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch // For coroutines

// --- Define simple Enums for Size Classes (for XML Views context) ---
// Based on Material Design breakpoints: https://m3.material.io/foundations/layout/applying-layout/window-size-classes
enum class WindowWidthSizeClass { COMPACT, MEDIUM, EXPANDED }
enum class WindowHeightSizeClass { COMPACT, MEDIUM, EXPANDED }

data class AppIntent(val title: String, val icon: Drawable?, val action: (Context) -> Unit)

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
        holder.iconImageView.setImageDrawable(currentIntent.iconDrawable)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate layout using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Calculate screen dimensions in pixels
        val displayMetrics = resources.displayMetrics
        screenWidthPx = resources.displayMetrics.widthPixels
        screenHeightPx = resources.displayMetrics.heightPixels

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

        // Define Intents
        allIntents.clear() // Clear if adding dynamically
        allIntents.addAll(
            listOf(
                AppIntent(
                    title = getString(R.string.clock_title),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_clock),
                    action = { startActivity(Intent(this, ClockActivity::class.java)) }
                ),
                AppIntent(
                    title = getString(R.string.randomizers_title),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_random),
                    action = { startActivity(Intent(this, RandomizersHostActivity::class.java)) }
                ),
                AppIntent(
                    title = getString(R.string.screen_shade_title),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_shade),
                    action = { startActivity(Intent(this, ScreenShadeActivity::class.java)) }
                ),
                AppIntent(
                    title = getString(R.string.spotlight_title),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_spotlight),
                    action = { startActivity(Intent(this, SpotlightActivity::class.java)) }
                ),
                AppIntent(
                    title = getString(R.string.timers_title),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_timer),
                    action = { startActivity(Intent(this, TimersActivity::class.java)) }
                ),
                AppIntent(
                    title = getString(R.string.traffic_light_title),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_traffic_light),
                    action = { startActivity(Intent(this, TrafficLightActivity::class.java)) }
                ),
                AppIntent(
                    title = getString(R.string.about),
                    icon = ContextCompat.getDrawable(this, R.drawable.ic_about),
                    action = { startActivity(Intent(this, AboutActivity::class.java)) }
                )
            )
        )

        // Set up the RecyclerView
        binding.intentsRecyclerView.layoutManager = LinearLayoutManager(this)
        val intentAdapter = IntentAdapter(allIntents) { appIntent ->
            animateIntentSelection(appIntent) {
                appIntent.action.invoke()
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
                val rawX = event.rawX
                val rawY = event.rawY

                // Use binding to access views for checking bounds
                if (!isTouchInsideView(rawX, rawY, binding.appIconButtonContainer) &&
                    !isTouchInsideView(rawX, rawY, binding.intentsRecyclerView)
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
    }

    // *** Add Restoration Logic ***
    restoreRandomizerInstances()
}

// --- Restoration Logic ---
private fun restoreRandomizerInstances() {
    lifecycleScope.launch { // Use lifecycleScope for coroutine
        val database = PurramidDatabase.getDatabase(applicationContext)
        // Get instances saved in DB (excluding the default settings record ID)
        val instancesToRestore = database.randomizerDao().getAllNonDefaultInstances()

        if (instancesToRestore.isNotEmpty()) {
            // Re-register these instances with the manager
            instancesToRestore.forEach { instanceEntity ->
                RandomizerInstanceManager.registerInstance(instanceEntity.instanceId)
                // Launch activity for each instance
                launchExistingRandomizer(instanceEntity.instanceId)
            }
        }
        // Else: No instances to restore, normal startup
    }
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
        val scaleDownX = ObjectAnimator.ofFloat(binding.appIconImageView, "scaleX", 1f, 0.8f)
        val scaleDownY = ObjectAnimator.ofFloat(binding.appIconImageView, "scaleY", 1f, 0.8f)
        val fadeIn = ObjectAnimator.ofFloat(binding.intentsRecyclerView, "alpha", 0f, 1f)

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
        val slideUpDistancePx = dpToPx(-30) // Slide up by 30dp (adjust as needed)

        // Access views via binding
        val scaleUpX = ObjectAnimator.ofFloat(binding.appIconImageView, "scaleX", binding.appIconImageView.scaleX, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(binding.appIconImageView, "scaleY", binding.appIconImageView.scaleY, 1f)
        val fadeOut = ObjectAnimator.ofFloat(binding.intentsRecyclerView, "alpha", 1f, 0f)

        // Use the calculated fixed pixel distance for the slide
        val slideUp = ObjectAnimator.ofFloat(binding.intentsRecyclerView, "translationY", 0f, slideUpDistancePx.toFloat())

        AnimatorSet().apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            // Play scale, fade, and slide animations together
            playTogether(scaleUpX, scaleUpY, fadeOut, slideUp)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.intentsRecyclerView.visibility = View.GONE
                    binding.intentsRecyclerView.translationY = 0f
                    isIntentsVisible = false
                }
                override fun onAnimationStart(animation: Animator) {
                    // Optional: Set isIntentsVisible = false here if needed sooner
                }
            })
            start()
        }
    }

    // Ensure dpToPx function definition exists and remains
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}