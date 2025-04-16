// MainActivity.kt
package com.example.thepurramid0_1

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

class MainActivity : AppCompatActivity() {

    private lateinit var appIconButtonContainer: FrameLayout
    private lateinit var appIconImageView: ImageView
    private lateinit var intentsContainer: LinearLayout
    // private lateinit var overlayPermissionResultLauncher: ActivityResultLauncher<Intent> // Removed
    private lateinit var intentsContainer: LinearLayout
    private var isIntentsVisible = false
    private val allIntents = mutableListOf<AppIntent>()
    private var screenWidth: Int = 0 // Use Px suffix for pixel dimensions
    private var screenHeight: Int = 0 // Use Px suffix for pixel dimensions
    // private val OVERLAY_PERMISSION_REQUEST_CODE = 1234  // This may not be needed any longer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Calculate screen dimensions in pixels
        val displayMetrics = resources.displayMetrics
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        // Initialize UI elements for the intent launcher
        appIconButtonContainer = findViewById(R.id.appIconButtonContainer)
        appIconImageView = findViewById(R.id.appIconImageView)
        intentsContainer = findViewById(R.id.intentsContainer)
        intentsRecyclerView = findViewById(R.id.intentsRecyclerView) // If using RecyclerView

        // Load app icon
        appIconImageView.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.ic_launcher_foreground))

        // Create the list of app intents
allIntents = mutableListOf( // Changed to mutableListOf to allow adding "About"
        AppIntent(
            title = getString(R.string.clock),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_clock),
            action = { context ->
                val clockIntent = Intent(this, ClockActivity::class.java)
                context.startActivity(clockIntent)
            }
        ),
        AppIntent(
            title = getString(R.string.randomizers),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_random),
            action = { context ->
                val randomizersIntent = Intent(this@MainActivity, RandomizersActivity::class.java)
                startActivity(randomizersIntent)
            }
        ),
        AppIntent(
            title = getString(R.string.screenshade),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_shade),
            action = { context ->
                val screenshadeIntent = Intent(this@MainActivity, ScreenShadeActivity::class.java)
                startActivity(screenshadeIntent)
            }
        ),
        AppIntent(
            title = getString(R.string.spotlight),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_spotlight),
            action = { context ->
                val spotlightIntent = Intent(this@MainActivity, SpotlightActivity::class.java)
                startActivity(spotlightIntent)
            }
        ),
        AppIntent(
            title = getString(R.string.timers),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_timer),
            action = { context ->
                val timersIntent = Intent(this@MainActivity, TimersActivity::class.java)
                startActivity(timersIntent)
            }
        ),
        AppIntent(
            title = getString(R.string.trafficlight),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_traffic_light),
            action = { context ->
                val trafficlightIntent = Intent(this@MainActivity, TrafficLightActivity::class.java)
                startActivity(trafficlightIntent)
            }
        ),
        AppIntent(
            title = getString(R.string.about),
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_about),
            action = { context ->
                val aboutIntent = Intent(context, AboutActivity::class.java)
                startActivity(aboutIntent) 
            }
        )
    )


        // Set up the RecyclerView
        intentsRecyclerView.layoutManager = LinearLayoutManager(this)
        val intentAdapter = IntentAdapter(allIntents) { appIntent ->
            // Handle the click action here
            appIntent.action.invoke()
        }
        intentsRecyclerView.adapter = intentAdapter

        // Initially hide the RecyclerView (if needed)
        intentsRecyclerView.visibility = View.GONE

       // Set up click listener for the app icon button to toggle visibility
        appIconButtonContainer.setOnClickListener {
            isIntentsVisible = !isIntentsVisible
            intentsRecyclerView.visibility = if (isIntentsVisible) View.VISIBLE else View.GONE
        }


        // Set up touch listener for handling clicks outside the intents
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0).setOnTouchListener { _, event ->
            if (isIntentsVisible && event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()

                val appIconRect = IntArray(2)
                appIconButtonContainer.getLocationOnScreen(appIconRect)
                val appIconX = appIconRect[0]
                val appIconY = appIconRect[1]
                val appIconWidth = appIconButtonContainer.width
                val appIconHeight = appIconButtonContainer.height

                val intentsRect = IntArray(2)
                intentsContainer.getLocationOnScreen(intentsRect)
                val intentsX = intentsRect[0]
                val intentsY = intentsRect[1]
                val intentsWidth = intentsContainer.width
                val intentsHeight = intentsContainer.height

                if (!isPointInsideView(x, y, appIconX, appIconY, appIconWidth, appIconHeight) &&
                    !isPointInsideView(x, y, intentsX, intentsY, intentsWidth, intentsHeight)) {
                    foldIntents()
                    return@setOnTouchListener true // Consume the touch event
                }
            }
            return@setOnTouchListener false
        }

        // **Large Tablet Specific Adjustments:**
        if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            adjustLayoutForLargeScreen()
            setInitialFreeformWindowSize()
        } else if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
            adjustLayoutForLargeScreen() // You might have a slightly different function for 'large'
        }
    }

    private fun adjustLayoutForLargeScreen() {
        // Example adjustments:
        val layoutManager = intentsRecyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.orientation = RecyclerView.HORIZONTAL // Or GridLayoutManager
        // Adjust padding, margins, font sizes of elements in your layout
        val largeScreenPadding = resources.getDimensionPixelSize(R.dimen.large_screen_padding)
        intentsContainer.setPadding(largeScreenPadding, largeScreenPadding, largeScreenPadding, largeScreenPadding)
        // You might also load a different layout using setContentView based on screen size
        // if (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE) {
        //     setContentView(R.layout.activity_main_xlarge)
        //     // Re-initialize views after layout inflation
        //     appIconButtonContainer = findViewById(R.id.appIconButtonContainer)
        //     // ... and so on
        // }
    }

    private fun setInitialFreeformWindowSize(widthFraction: Float = 0.6f, heightFraction: Float = 0.7f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // Freeform support introduced in Nougat
            try {
                val window = this.window
                val layoutParams = WindowManager.LayoutParams()
                layoutParams.copyFrom(window.attributes)

                val widthFraction: Float
                val heightFraction: Float

                // Roughly categorize resolution
                if (screenWidthPx > 3000) { // Example threshold for 4K width
                    widthFraction = 0.25f
                    heightFraction = 0.3f
                } else { // Assume 2K or lower
                    widthFraction = 0.4f
                    heightFraction = 0.45f
                }

                layoutParams.width = (screenWidthPx * widthFraction).toInt()
                layoutParams.height = (screenHeightPx * heightFraction).toInt()

                window.attributes = layoutParams
            } catch (e: Exception) {
                // Handle potential exceptions (e.g., if the activity is not the top-most)
                e.printStackTrace()
            }
        }
    }

    private fun createIntentView(appIntent: AppIntent): View {
        val intentView = LinearLayout(this)
        intentView.orientation = LinearLayout.HORIZONTAL
        intentView.gravity = android.view.Gravity.CENTER_VERTICAL
        intentView.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        intentView.setBackgroundResource(android.R.drawable.selectable_background) // For touch feedback

        val iconImageView = ImageView(this)
        iconImageView.setImageDrawable(appIntent.icon)
        val iconSize = dpToPx(32)
        val iconLayoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        iconLayoutParams.marginEnd = dpToPx(16)
        iconImageView.layoutParams = iconLayoutParams
        intentView.addView(iconImageView)

        val titleTextView = TextView(this)
        titleTextView.text = appIntent.title
        titleTextView.setTextAppearance(android.R.style.TextAppearance_Medium)
        intentView.addView(titleTextView)

        intentView.setOnClickListener {
            // Animate the selection
            val scaleXAnimator = ObjectAnimator.ofFloat(it, "scaleX", 1f, 1.1f, 1f)
            val scaleYAnimator = ObjectAnimator.ofFloat(it, "scaleY", 1f, 1.1f, 1f)
            val animatorSet = AnimatorSet().apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
                playTogether(scaleXAnimator, scaleYAnimator)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // Launch the intent after the animation
                        appIntent.action(this@MainActivity)
                    }
                })
                start()
            }
        }

        return intentView
    }

    private fun toggleIntentsVisibility() {
        if (isIntentsVisible) {
            foldIntents()
        } else {
            unfoldIntents()
        }
        isIntentsVisible = !isIntentsVisible
    }

    private fun unfoldIntents() {
        intentsContainer.visibility = View.VISIBLE
        val containerHeight = intentsContainer.height // Height will be 0 initially

        // Need to measure the intended height
        intentsContainer.measure(View.MeasureSpec.makeMeasureSpec(intentsContainer.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val targetHeight = intentsContainer.measuredHeight

        // Scale down the app icon
        val scaleDownX = ObjectAnimator.ofFloat(appIconImageView, "scaleX", 1f, 0.8f)
        val scaleDownY = ObjectAnimator.ofFloat(appIconImageView, "scaleY", 1f, 0.8f)

        // Fade in the intents container
        val fadeIn = ObjectAnimator.ofFloat(intentsContainer, "alpha", 0f, 1f)

        // Slide down the intents container
        intentsContainer.translationY = -targetHeight.toFloat()
        val slideDown = ObjectAnimator.ofFloat(intentsContainer, "translationY", -targetHeight.toFloat(), 0f)

        AnimatorSet().apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(scaleDownX, scaleDownY, fadeIn, slideDown)
            start()
        }
    }

    private fun foldIntents() {
        // Scale up the app icon
        val scaleUpX = ObjectAnimator.ofFloat(appIconImageView, "scaleX", 0.8f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(appIconImageView, "scaleY", 0.8f, 1f)

        // Fade out the intents container
        val fadeOut = ObjectAnimator.ofFloat(intentsContainer, "alpha", 1f, 0f)

        // Slide up the intents container
        val slideUp = ObjectAnimator.ofFloat(intentsContainer, "translationY", 0f, -intentsContainer.height.toFloat())

        AnimatorSet().apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(scaleUpX, scaleUpY, fadeOut, slideUp)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    intentsContainer.visibility = View.GONE
                    intentsContainer.translationY = 0f // Reset translation for next unfold
                }
            })
            start()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }
}