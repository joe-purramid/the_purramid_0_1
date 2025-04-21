// SpotlightActivity.kt
package com.example.purramid.thepurramid

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.thepurramid0_1.SpotlightView.Spotlight

class SpotlightActivity : AppCompatActivity() {

    private lateinit var spotlightView: SpotlightView
    private lateinit var btnAdd: ImageView
    private lateinit var btnClose: ImageView
    private lateinit var btnShape: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spotlight) // Make sure you have this layout file

        spotlightView = findViewById(R.id.spotlightView)
        btnAdd = findViewById(R.id.btnAdd)
        btnClose = findViewById(R.id.btnClose)
        btnShape = findViewById(R.id.btnShape)

        // Set OnClickListener for the short press actions
        btnAdd.setOnClickListener {
            if (spotlightView.spotlights.size < 4) {
                val newSpotlight = createNewSpotlight()
                spotlightView.spotlights.add(newSpotlight)
                spotlightView.invalidate()
                updateAddButtonState()
            }
        }

        btnClose.setOnClickListener {
            finish()
        }

        btnShape.setOnClickListener {
            spotlightView.currentShape = when (spotlightView.currentShape) {
                Spotlight.Shape.CIRCLE, Spotlight.Shape.OVAL -> {
                    btnShape.setImageResource(R.drawable.ic_circle)
                    btnShape.contentDescription = getString(R.string.change_shape_square)
                    Spotlight.Shape.SQUARE
                }
                Spotlight.Shape.SQUARE, Spotlight.Shape.RECTANGLE -> {
                    btnShape.setImageResource(R.drawable.ic_square)
                    btnShape.contentDescription = getString(R.string.change_shape_circle)
                    Spotlight.Shape.CIRCLE
                }
            }
            spotlightView.spotlights.forEach { spotlight ->
                when (spotlightView.currentShape) {
                    Spotlight.Shape.CIRCLE -> {
                        spotlight.width = spotlight.radius * 2
                        spotlight.height = spotlight.radius * 2
                        spotlight.size = spotlight.radius * 2 }
                    Spotlight.Shape.SQUARE -> {
                        spotlight.size = spotlight.radius * 2
                        spotlight.width = spotlight.size
                        spotlight.height = spotlight.size }
                    Spotlight.Shape.OVAL -> {
                        spotlight.width = spotlight.radius * 2 * 1.5f
                        spotlight.height = spotlight.radius * 2 / 1.5f }
                    Spotlight.Shape.RECTANGLE -> {
                        spotlight.width = spotlight.radius * 2 * 1.5f
                        spotlight.height = spotlight.radius * 2 / 1.5f
                        spotlight.size = spotlight.width.coerceAtLeast(spotlight.height) }
                }
            }
            spotlightView.invalidate()
        }

        // Set OnLongClickListener for tooltips
        btnAdd.setOnLongClickListener {
            showTooltip(getString(R.string.add_another_spotlight))
            true // Consume the long click event
        }

        btnClose.setOnLongClickListener {
            showTooltip(getString(R.string.close))
            true // Consume the long click event
        }

        btnShape.setOnLongClickListener {
            val tooltipText = if (spotlightView.currentShape == Spotlight.Shape.CIRCLE || spotlightView.currentShape == Spotlight.Shape.OVAL) {
                getString(R.string.change_shape_square)
            } else {
                getString(R.string.change_shape_circle)
            }
            showTooltip(tooltipText)
            true // Consume the long click event
        }

        updateAddButtonState()

    }

    private fun showTooltip(text: String): Boolean {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        return true // Indicate that the long click event was handled
    }

    private fun createNewSpotlight(): Spotlight {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val initialRadius = 100f
        val offsetIncrement = 20f
        val numSpotlights = spotlightView.spotlights.size
        val offsetX = when (numSpotlights) { 1 -> -offsetIncrement; 2 -> offsetIncrement; 3 -> -offsetIncrement * 2; else -> 0f }
        val offsetY = when (numSpotlights) { 1 -> -offsetIncrement; 2 -> -offsetIncrement; 3 -> offsetIncrement * 2; else -> 0f }
        val centerX = screenWidth / 2f + offsetX
        val centerY = screenHeight / 2f + offsetY
        return Spotlight(centerX, centerY, initialRadius, shape = spotlightView.currentShape)
    }

    private fun updateAddButtonState() {
        btnAdd.isEnabled = spotlightView.spotlights.size < 4
    }
}