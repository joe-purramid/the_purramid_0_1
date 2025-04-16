// MainActivity.kt
package com.example.thepurramid0_1

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

data class AppIntent(val title: String, val icon: Drawable?, val action: (Context) -> Unit)

class MainActivity : AppCompatActivity() {

    private lateinit var appIconButtonContainer: FrameLayout
    private lateinit var appIconImageView: ImageView
    private lateinit var intentsContainer: LinearLayout
    private lateinit var overlayPermissionResultLauncher: ActivityResultLauncher<Intent>
    private var isIntentsVisible = false
    private val allIntents = mutableListOf<AppIntent>()
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    // private val OVERLAY_PERMISSION_REQUEST_CODE = 1234  // This may not be needed any longer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayPermissionResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startClockOverlayService()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Overlay permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                // On older versions, permission is usually granted by default in the manifest
                startClockOverlayService()
            }
        }

        requestOverlayPermission()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + packageName)
            )
            overlayPermissionResultLauncher.launch(intent)
        } else {
            // Permission already granted or not needed
            startClockOverlayService()
        }
    }

        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels

        appIconButtonContainer = findViewById(R.id.appIconButtonContainer)
        appIconImageView = findViewById(R.id.appIconImageView)
        intentsContainer = findViewById(R.id.intentsContainer)

        // Load app icon
        appIconImageView.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.ic_launcher_foreground))

        // Define the intents
        allIntents.apply {

            add(AppIntent("Clock", ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_clock), { startActivity(Intent(this@MainActivity, ClockSettingsActivity::class.java)) }))

            add(AppIntent("Randomizers", ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_random), { startActivity(Intent(this@MainActivity, RandomizersActivity::class.java)) }))

            add(AppIntent("Screen Shade", ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_shade), { startActivity(Intent(this@MainActivity, ScreenShadeActivity::class.java)) }))

            add(AppIntent("Spotlight", ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_spotlight), { startActivity(Intent(this@MainActivity, SpotlightActivity::class.java)) }))

            add(AppIntent("Timers", ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_timer), { startActivity(Intent(this@MainActivity, TimersActivity::class.java)) }))

            add(AppIntent("Traffic Light", ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_traffic_light), { startActivity(Intent(this@MainActivity, TrafficLightActivity::class.java)) }))
        }

        // Initially hide the intents container
        intentsContainer.visibility = View.GONE

        // Set up click listener for the app icon button
        appIconButtonContainer.setOnClickListener {
            toggleIntentsVisibility()
        }

        // Add intents to the container dynamically
        allIntents.forEach { appIntent ->
            val intentView = createIntentView(appIntent)
            intentsContainer.addView(intentView)
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
    }

    private fun startClockOverlayService() {
        val serviceIntent = Intent(this, ClockOverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent) // Consider making it a foreground service
    }

    private fun isPointInsideView(x: Int, y: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return x >= left && x < left + width && y >= top && y < top + height
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