package com.example.purramid.thepurramid.clock

import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.createTimeZoneOverlays
import com.example.purramid.thepurramid.loadAndParseGeoJson
import com.example.purramid.thepurramid.placeGlobe
import com.example.purramid.thepurramid.processTimeZoneData
import com.example.purramid.thepurramid.resetGlobe
import com.example.purramid.thepurramid.rotateGlobe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimeZoneGlobeActivity : AppCompatActivity() {

    private val EARTH_RADIUS = 0.7f // Should match the scale of your globe
    private val GEOJSON_FILE_NAME = "time_zones.geojson" // Replace with your actual file name
    private val rotationFactor = 0.01f
    private val rotationAmount = 10f

    private var activeTimeZoneId: String? = null // To store the currently selected time zone    private var arFragment: ArFragment? = null
    private var globeRenderable: ModelRenderable? = null
    private var globeNode: TransformableNode? = null
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var rotationX = 0f
    private var rotationY = 0f
    private var timeZoneOverlayNodes: MutableList<TransformableNode> = mutableListOf()

    private lateinit var rotateLeftButton: Button
    private lateinit var rotateRightButton: Button
    private lateinit var resetButton: Button
    private lateinit var cityNorthernTextView: TextView
    private lateinit var citySouthernTextView: TextView
    private lateinit var utcOffsetTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_zone_globe) // Use your layout file

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as? ArFragment

        rotateLeftButton = findViewById(R.id.rotateLeftButton)
        rotateRightButton = findViewById(R.id.rotateRightButton)
        resetButton = findViewById(R.id.resetButton)
        cityNorthernTextView = findViewById(R.id.cityNorthernTextView)
        citySouthernTextView = findViewById(R.id.citySouthernTextView)
        utcOffsetTextView = findViewById(R.id.utcOffsetTextView)

        val modelUri = Uri.parse("scene.gltf") // 3D model file

        // Load and process GeoJSON data
        CoroutineScope(Dispatchers.IO).launch {
            val geoJsonData = loadAndParseGeoJson(this@TimeZoneGlobeActivity, GEOJSON_FILE_NAME)
            val timeZoneBoundaries = processTimeZoneData(geoJsonData)

            // After processing, create the overlay on the main thread
            CoroutineScope(Dispatchers.Main).launch {
                createTimeZoneOverlays(timeZoneBoundaries)
            }
        }

        // Touch input for manual rotation
        arFragment?.arSceneView?.setOnTouchListener { _, motionEvent ->
            globeNode?.let { node ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    lastX = motionEvent.x
                    lastY = motionEvent.y
                    true
                } else if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                    val dx = motionEvent.x - lastX
                    val dy = motionEvent.y - lastY

                    rotationY += dx * rotationFactor
                    rotationX += dy * rotationFactor

                    val rotation = Quaternion.axisAngle(Vector3.up(), rotationY)
                        .multiply(Quaternion.axisAngle(Vector3.right(), rotationX))
                    node.worldRotation = rotation

                    lastX = motionEvent.x
                    lastY = motionEvent.y
                    true
                } else {
                    false
                }
            } ?: false
        }
    }

        // Load the 3D globe model
        private fun loadGlobeModelAndTexture() {
            val displayMetrics = Resources.getSystem().displayMetrics
            val screenWidthPixels = displayMetrics.widthPixels

            val textureUriString = when {
                screenWidthPixels > 3000 -> "earth_texture_10k.png"
                screenWidthPixels > 2000 -> "earth_texture_4k.png"
                screenWidthPixels > 1000 -> "earth_texture_2k.png"
                else -> "earth_texture_1k.png"
            }

        loadGlobeModelAndTexture() // Initialize function

        ModelRenderable.builder()
            .setSource(this, RenderableSource.builder().setSource(this, Uri.parse("scene.gltf"),
                    RenderableSource.SourceType.GLTF)
                .setScale(0.7f) // Adjust scale as needed
                .build())
            .setRegistryId("earth")
            .build()
            .thenAccept { renderable ->
            val textureUri = Uri.parse(textureUriString)
            Texture.builder().setSource(this, textureUri).build()
                .thenAccept { texture ->
                    MaterialFactory.makeOpaqueWithTexture(this, texture)
                        .thenAccept { material ->
                            renderable.material = material
                            placeGlobe()
                        }
                        .exceptionally { throwable ->
                            Toast.makeText(
                                this,
                                getString(R.string.error_loading_model, throwable.message),
                                Toast.LENGTH_LONG
                            ).show()
                null
                        }
                    }
                    .exceptionally { throwable ->
                        Toast.makeText(
                            this,
                            getString(R.string.error_creating_texture, throwable.message),
                            Toast.LENGTH_LONG
                        ).show()
                null
            }
        }
        .exceptionally { throwable ->
            Toast.makeText(
                this,
                getString(R.string.error_loading_texture, throwable.message),
                Toast.LENGTH_LONG
            ).show()
                null
        }
    }

        // Button click listeners for accessible rotation
        rotateLeftButton.setOnClickListener { rotateGlobe(0f, rotationAmount) }
        rotateRightButton.setOnClickListener { rotateGlobe(0f, -rotationAmount) }
        resetButton.setOnClickListener { resetGlobe() }

        // For testing purposes
        // In the future, this will be based on user interaction
        val testTimeZoneId = "America/New_York"
        activeTimeZoneId = testTimeZoneId
        updateTimeZoneInfo(testTimeZoneId)
    }