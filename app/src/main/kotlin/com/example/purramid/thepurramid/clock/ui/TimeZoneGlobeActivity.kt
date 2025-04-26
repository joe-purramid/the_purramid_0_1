package com.example.purramid.thepurramid.clock.ui

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.purramid.thepurramid.R // Your base R file
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.utils.Float3 // Using Filament types directly
import dagger.hilt.android.AndroidEntryPoint
import io.github.sceneview.SceneView // Import SceneView
import io.github.sceneview.loaders.ModelLoader // Import ModelLoader
import io.github.sceneview.loaders.MaterialLoader // Import MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node // SceneView Node
import io.github.sceneview.utils.Color as SceneViewColor // Alias Color
import kotlinx.coroutines.CompletableDeferred // Using Deferred for material loading
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.locationtech.jts.geom.Polygon
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.cos
import kotlin.math.sin

// Import ViewModel and State classes (ensure package is correct)
import com.example.purramid.thepurramid.clock.ui.TimeZoneGlobeUiState
import com.example.purramid.thepurramid.clock.ui.TimeZoneGlobeViewModel
import com.example.purramid.thepurramid.clock.ui.TimeZoneOverlayInfo


@AndroidEntryPoint
class TimeZoneGlobeActivity : AppCompatActivity(R.layout.activity_time_zone_globe) { // Use constructor injection for layout

    private val TAG = "TimeZoneGlobeActivity"
    private const val GLOBE_MODEL_ASSET_PATH = "scene.gltf" // Path in app/src/main/assets
    private val EARTH_RADIUS = 0.7f // Adjust scale to match your model's desired size
    private val OVERLAY_RADIUS_FACTOR = 1.005f // Slightly above surface to prevent Z-fighting
    private val ROTATION_FACTOR = 0.15f // Touch rotation sensitivity
    private val BUTTON_ROTATION_AMOUNT_DEGREES = 15f

    // Inject ViewModel
    private val viewModel: TimeZoneGlobeViewModel by viewModels()

    // Views
    private lateinit var sceneView: SceneView
    private lateinit var rotateLeftButton: Button
    private lateinit var rotateRightButton: Button
    private lateinit var resetButton: Button
    private lateinit var cityNorthernTextView: TextView
    private lateinit var citySouthernTextView: TextView
    private lateinit var utcOffsetTextView: TextView

    // Scene Objects
    private lateinit var modelLoader: ModelLoader
    private lateinit var materialLoader: MaterialLoader
    private var globeNode: ModelNode? = null
    private var overlayParentNode: Node? = null // Parent for overlays, attached to globeNode

    // Cache for created overlay materials
    private val materialCache = mutableMapOf<Int, CompletableDeferred<MaterialInstance>>()

    // Touch rotation state
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind Views
        sceneView = findViewById(R.id.sceneView)
        rotateLeftButton = findViewById(R.id.rotateLeftButton)
        rotateRightButton = findViewById(R.id.rotateRightButton)
        resetButton = findViewById(R.id.resetButton)
        cityNorthernTextView = findViewById(R.id.cityNorthernTextView)
        citySouthernTextView = findViewById(R.id.citySouthernTextView)
        utcOffsetTextView = findViewById(R.id.utcOffsetTextView)

        // Initialize SceneView Loaders
        modelLoader = ModelLoader(sceneView.engine, this)
        materialLoader = MaterialLoader(sceneView.engine)

        // Configure SceneView Camera (optional, adjust defaults)
        sceneView.cameraNode.position = Position(x = 0.0f, y = 0.0f, z = 2.5f) // Adjust distance

        // Start loading the globe model
        loadGlobeModel()

        // Setup interaction listeners
        setupTouchListener()
        setupButtonListeners()

        // Observe state changes from ViewModel
        observeViewModel()
    }

    private fun loadGlobeModel() {
        lifecycleScope.launch { // Use lifecycleScope for coroutine
            try {
                // Load the glTF model from assets
                val model = modelLoader.loadModel(GLOBE_MODEL_ASSET_PATH)
                if (model == null || model.asset == null) {
                    handleError("Failed to load model: $GLOBE_MODEL_ASSET_PATH")
                    return@launch
                }

                // Create the ModelNode using the first instance from the loaded model asset
                val modelInstance = model.instance ?: model.createInstance("DefaultGlobe") // Ensure instance exists
                globeNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = EARTH_RADIUS // Scale the model
                ).apply {
                    // Apply initial rotation from ViewModel if available
                    viewModel.uiState.value?.let { state ->
                        rotation = state.currentRotation
                    }
                    // Disable built-in transform gestures if using custom touch handling
                    isPositionEditable = false
                    isRotationEditable = false
                    isScaleEditable = false
                }

                // Add the globe node to the scene
                sceneView.addChild(globeNode!!)

                // Create parent node for overlays, attached to the globe node
                overlayParentNode = Node(sceneView.engine).apply {
                    setParent(globeNode)
                }

                Log.d(TAG, "Purramid Globe model loaded and added.")

                // Trigger overlay creation now that the globe exists, if data is ready
                viewModel.uiState.value?.let { state ->
                    if (!state.isLoading && state.timeZoneOverlays.isNotEmpty()) {
                        createOrUpdateOverlays(state.timeZoneOverlays)
                    }
                }

            } catch (e: Exception) {
                handleError("Error loading globe model", e)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            updateUiTexts(state) // Update city/offset TextViews

            // Only update SceneView if the globe has been loaded
            if (globeNode != null && overlayParentNode != null) {
                if (state.isLoading) {
                    // Handle loading state (e.g., show progress, hide overlays)
                    clearOverlays()
                } else if (state.error != null) {
                    // Handle error state (e.g., show error message, hide overlays)
                    handleError(state.error)
                    clearOverlays()
                } else {
                    // Update globe rotation based on ViewModel state
                    globeNode?.rotation = state.currentRotation

                    // Update or create the time zone overlays
                    createOrUpdateOverlays(state.timeZoneOverlays)
                }
            }
        }
    }

    private fun updateUiTexts(state: TimeZoneGlobeUiState) {
        state.activeTimeZoneInfo?.let { info ->
            cityNorthernTextView.text = info.northernCity
            citySouthernTextView.text = info.southernCity
            utcOffsetTextView.text = info.utcOffsetString
        } ?: run {
            cityNorthernTextView.text = ""
            citySouthernTextView.text = ""
            utcOffsetTextView.text = getString(R.string.timezone_placeholder) // Use string resource
        }
    }

    // --- Overlay Creation ---
    private fun createOrUpdateOverlays(overlayInfos: List<TimeZoneOverlayInfo>) {
        if (overlayParentNode == null) {
            Log.w(TAG, "Cannot create overlays, parent node is null.")
            return
        }
        clearOverlays() // Simple strategy: clear all and redraw

        overlayInfos.forEach { info ->
            // Get or create material instance asynchronously
            val materialDeferred = getOrCreateMaterial(info.color)

            // Create renderables for each polygon associated with this time zone info
            info.polygons.forEach { polygon ->
                createPolygonRenderable(polygon, materialDeferred, info.tzid)
            }
        }
    }

    private fun getOrCreateMaterial(colorArgb: Int): CompletableDeferred<MaterialInstance> {
        return materialCache.getOrPut(colorArgb) {
            val deferred = CompletableDeferred<MaterialInstance>()
            lifecycleScope.launch { // Use coroutine to create material off main thread if needed
                try {
                    // SceneView Color utility expects ARGB format
                    val color = SceneViewColor(colorArgb)
                    val material = materialLoader.createColorInstance(
                        color = color,
                        isMetallic = 0.0f,
                        roughness = 1.0f, // Matte appearance
                        reflectance = 0.0f // No reflection
                    )
                    // Configure transparency
                    material.material.blendingMode = MaterialLoader.BlendingMode.TRANSPARENT
                    // Set baseColorFactor *after* creation for transparency
                    material.setParameter("baseColorFactor", color) // Pass RGBA color

                    deferred.complete(material)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create material instance for color $colorArgb", e)
                    deferred.completeExceptionally(e)
                }
            }
            deferred
        }
    }

    private fun clearOverlays() {
        overlayParentNode?.children?.toList()?.forEach {
            // Properly destroy nodes and their associated Filament resources
            it.destroy() // SceneView Node's destroy handles Filament entity removal
        }
        // Optionally clear material cache if memory is a concern
        // materialCache.values.forEach { deferred -> deferred.getCompletedOrNull()?.destroy() }
        // materialCache.clear()
    }

    private fun createPolygonRenderable(
        polygon: Polygon,
        materialDeferred: CompletableDeferred<MaterialInstance>,
        tzId: String
    ) {
        lifecycleScope.launch { // Use coroutine for potential async operations
            try {
                val materialInstance = materialDeferred.await() // Wait for material creation
                val engine = sceneView.engine

                // 1. Triangulate Polygon and get Vertices/Indices
                val geometry = triangulatePolygon(polygon) ?: return@launch // Extract geometry or return
                val (vertices, indices) = geometry

                // 2. Create Filament VertexBuffer
                val vertexBuffer = VertexBuffer.Builder()
                    .bufferCount(1) // Position
                    .vertexCount(vertices.size)
                    .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 0)
                    .build(engine)
                vertexBuffer.setBufferAt(engine, 0, Float3.toFloatBuffer(vertices)) // Use helper

                // 3. Create Filament IndexBuffer
                val indexBuffer = IndexBuffer.Builder()
                    .indexCount(indices.size)
                    .bufferType(IndexBuffer.Builder.IndexType.UINT)
                    .build(engine)
                indexBuffer.setBuffer(engine, IntArray(indices.size) { indices[it] }.toBuffer()) // Use helper

                // Calculate bounding box (optional but good practice)
                val bounds = calculateBounds(vertices)

                // 4. Create Filament Renderable (Entity + Component)
                val renderableEntity = engine.entityManager.create()
                RenderableManager.Builder(1)
                    .boundingBox(bounds)
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer, 0, indices.size)
                    .material(0, materialInstance)
                    .culling(false) // Render overlays even if facing away? Adjust as needed.
                    .build(engine, renderableEntity) // Build for the entity

                // 5. Create SceneView Node and attach Renderable Entity
                val node = Node(engine, renderableEntity).apply {
                    // *** Store time zone ID in the node's tag ***
                    this.tag = tzId
                }
                node.setParent(overlayParentNode) // Attach to the overlay parent

                // Note: Buffers are now associated with the renderable entity.
                // They will be cleaned up when the entity/node is destroyed.

            } catch (e: Exception) {
                Log.e(TAG, "Error creating overlay renderable for $tzId", e)
                // Handle exceptions during material await or geometry creation
                materialDeferred.getCompletedOrNull()?.let {
                    Log.w(TAG, "Material was potentially created but renderable failed.")
                    // Consider destroying the material if it won't be used
                    // it.destroy()
                    // materialCache.remove(materialColorKey) // Remove from cache if destroyed
                }
            }
        }
    }

    // Helper for triangulation (Simple Fan Example - Consider a robust library for complex polygons)
    private fun triangulatePolygon(polygon: Polygon): Pair<List<Float3>, List<Int>>? {
        try {
            if (polygon.exteriorRing.coordinates.size < 4) return null

            val vertices = mutableListOf<Float3>()
            val indices = mutableListOf<Int>()
            val overlayRadius = EARTH_RADIUS * OVERLAY_RADIUS_FACTOR

            // Add centroid vertex
            val centroid = polygon.centroid
            vertices.add(latLonToFloat3(centroid.y, centroid.x, overlayRadius))
            val centroidIndex = 0

            // Add exterior ring vertices and create triangles fanning from centroid
            for (i in 0 until polygon.exteriorRing.coordinates.size - 1) {
                val coord = polygon.exteriorRing.coordinates[i]
                vertices.add(latLonToFloat3(coord.y, coord.x, overlayRadius))
                val currentIndex = i + 1
                val nextIndex = if (i == polygon.exteriorRing.coordinates.size - 2) 1 else currentIndex + 1 // Wrap around
                // Triangle: Centroid, Current, Next
                indices.add(centroidIndex)
                indices.add(currentIndex)
                indices.add(nextIndex)
            }
            return Pair(vertices, indices)
        } catch (e: Exception) {
            Log.e(TAG, "Error during triangulation", e)
            return null
        }
    }

    // Helper to calculate bounding box from vertices
    private fun calculateBounds(vertices: List<Float3>): Box {
        if (vertices.isEmpty()) return Box(0f, 0f, 0f, 1f, 1f, 1f) // Default small box
        var minX = vertices[0].x
        var minY = vertices[0].y
        var minZ = vertices[0].z
        var maxX = vertices[0].x
        var maxY = vertices[0].y
        var maxZ = vertices[0].z

        for (i in 1 until vertices.size) {
            minX = minOf(minX, vertices[i].x)
            minY = minOf(minY, vertices[i].y)
            minZ = minOf(minZ, vertices[i].z)
            maxX = maxOf(maxX, vertices[i].x)
            maxY = maxOf(maxY, vertices[i].y)
            maxZ = maxOf(maxZ, vertices[i].z)
        }
        // Center = (min+max)/2, HalfExtent = (max-min)/2
        return Box(
            (minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f,
            (maxX - minX) / 2f, (maxY - minY) / 2f, (maxZ - minZ) / 2f
        )
    }


    // Helper to convert Lat/Lon to Filament Float3
    private fun latLonToFloat3(lat: Double, lon: Double, radius: Float): Float3 {
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val x = radius * cos(latRad) * cos(lonRad)
        val y = radius * sin(latRad) // Filament Y is up
        val z = radius * cos(latRad) * sin(lonRad) * -1.0 // Adjust Z based on coordinate system convention
        return Float3(x.toFloat(), y.toFloat(), z.toFloat())
    }
    // --- End Overlay Creation ---


    // --- User Interaction ---
    private fun setupTouchListener() {
        // Disable SceneView's built-in manipulator if using fully custom rotation
        sceneView.cameraManipulator.enabled = false

        sceneView.setOnTouchListener { _, event ->
            val currentState = viewModel.uiState.value ?: return@setOnTouchListener false
            if (globeNode == null) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    // Apply rotation delta to the current globe rotation (Euler Angles)
                    val currentRot = globeNode!!.rotation // Use node's current rotation
                    val deltaYaw = -dx * ROTATION_FACTOR * 2 // Yaw around Y - adjust sensitivity multiplier
                    val deltaPitch = -dy * ROTATION_FACTOR * 2 // Pitch around X - adjust sensitivity multiplier

                    // Clamp pitch to avoid flipping upside down (e.g., +/- 89 degrees)
                    val newPitch = (currentRot.x + deltaPitch).coerceIn(-89f, 89f)

                    val newRotation = Rotation(
                        x = newPitch,
                        y = (currentRot.y + deltaYaw) % 360, // Wrap yaw
                        z = currentRot.z // Keep roll fixed
                    )
                    viewModel.updateRotation(newRotation) // Update ViewModel state

                    lastX = event.x; lastY = event.y; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { true } // Consume event
                else -> false
            }
        }
        sceneView.onTap = { motionEvent, hitResult ->
            // Check if the hit was on a node and specifically an overlay node
            val hitNode = hitResult?.node
            if (hitNode != null && hitNode.parent == overlayParentNode && hitNode.tag is String) {
                val tappedTzId = hitNode.tag as String
                Log.d(TAG, "Tapped on time zone: $tappedTzId")
                // Update the ViewModel with the selected time zone
                viewModel.setActiveTimeZone(tappedTzId)
            } else {
                // Optional: Handle tap on globe background or other nodes if needed
                Log.d(TAG, "Tap detected, but not on a tagged overlay node.")
            }
        }
    }

    private fun setupButtonListeners() {
        rotateLeftButton.setOnClickListener { rotateGlobeWithButton(BUTTON_ROTATION_AMOUNT_DEGREES) }
        rotateRightButton.setOnClickListener { rotateGlobeWithButton(-BUTTON_ROTATION_AMOUNT_DEGREES) }
        resetButton.setOnClickListener { resetGlobe() }
    }

    private fun rotateGlobeWithButton(degreesY: Float) {
        viewModel.uiState.value?.let { currentState ->
            val currentRot = globeNode?.rotation ?: Rotation()
            val newRotation = Rotation(currentRot.x, (currentRot.y + degreesY) % 360, currentRot.z)
            viewModel.updateRotation(newRotation)
            // TODO: Implement "Rotate to Next Zone" logic in ViewModel if desired
        }
    }

    private fun resetGlobe() {
        viewModel.updateRotation(Rotation(0f, 0f, 0f)) // Reset rotation state
    }
    // --- End User Interaction ---

    // --- Utility ---
    private fun handleError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        // Potentially update UI state via ViewModel to show error message persistently
    }

    // --- Lifecycle is handled by SceneView when added via XML ---
}


// --- Buffer Utils (Place outside Activity or in a separate file) ---

fun Float3.Companion.toFloatBuffer(list: List<Float3>): FloatBuffer {
    val buffer = java.nio.ByteBuffer.allocateDirect(list.size * 3 * 4) // float3 * 4 bytes/float
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer()
    list.forEach { buffer.put(it.toFloatArray()) }
    buffer.rewind()
    return buffer
}

fun IntArray.toBuffer(): IntBuffer {
    val buffer = java.nio.ByteBuffer.allocateDirect(this.size * 4) // int * 4 bytes/int
        .order(java.nio.ByteOrder.nativeOrder())
        .asIntBuffer()
    buffer.put(this)
    buffer.rewind()
    return buffer
}