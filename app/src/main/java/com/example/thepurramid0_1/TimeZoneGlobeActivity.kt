// TimeZoneGlobeActivity.kt
package com.example.thepurramid0_1

import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.TimeZone
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.toRadians
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder

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
                            Toast.makeText(this, getString(R.string.error_loading_model, throwable.message), Toast.LENGTH_LONG).show()
                null
                        }
                    }
                    .exceptionally { throwable ->
                        Toast.makeText(this, getString(R.string.error_creating_texture, throwable.message), Toast.LENGTH_LONG).show()
                null
            }
        }
        .exceptionally { throwable ->
            Toast.makeText(this, getString(R.string.error_loading_texture, throwable.message), Toast.LENGTH_LONG).show()
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

    private fun placeGlobe() {
        arFragment?.let { fragment ->
            val anchorNode = AnchorNode(fragment.arSceneView.session?.createAnchor(
                com.google.ar.core.Pose(floatArrayOf(0f, 0f, -1.5f), floatArrayOf(0f, 0f, 0f, 1f))
            ))
            globeNode = TransformableNode(fragment.transformationSystem)
            globeNode?.renderable = globeRenderable
            globeNode?.setParent(anchorNode)
            fragment.arSceneView.scene.addChild(anchorNode)f
            globeNode?.select()
        }
    }

    private fun rotateGlobe(deltaX: Float, deltaY: Float) {
        rotationY += deltaY * 0.1f
        rotationX += deltaX * 0.1f

        globeNode?.let { node ->
            val rotation = Quaternion.axisAngle(Vector3.up(), rotationY)
                .multiply(Quaternion.axisAngle(Vector3.right(), rotationX))
            node.worldRotation = rotation
        }
    }

    private fun resetGlobe() {
        rotationX = 0f
        rotationY = 0f
        globeNode?.worldRotation = Quaternion.identity()
    }

    private fun updateTimeZoneInfo(timeZoneId: String) {
        // In the future, implement logic to:
        // 1. Determine if DST is active for timeZoneId
        // 2. Find populous cities in Northern and Southern hemispheres for timeZoneId
        // 3. Get the UTC offset for timeZoneId

        // For testing, let's just display some placeholder info
        val zone = ZoneId.of(timeZoneId)
        val offset = OffsetDateTime.now(zone).offset
        utcOffsetTextView.text = "UTC${offset.id}"

        // Placeholder city names
        cityNorthernTextView.text = "New York (N)"
        citySouthernTextView.text = "Buenos Aires (S) - Placeholder"
    }

    private suspend fun loadAndParseGeoJson(context: Context, assetFileName: String): GeoJsonFeatureCollection? {
        return try {
            val inputStream = context.assets.open(assetFileName)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString(GeoJsonFeatureCollection.serializer(), jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun processTimeZoneData(geoJson: GeoJsonFeatureCollection?): Map<String, List<List<Pair<Double, Double>>>> {
        val timeZoneBoundaries = mutableMapOf<String, MutableList<List<Pair<Double, Double>>>>()

        geoJson?.features?.forEach { feature ->
            if (feature.geometry.type == "Polygon") {
                val coordinates = feature.geometry.coordinates
                val timeZoneId = feature.properties?.get("tzid") ?: feature.properties?.get("name") // Adjust key

                timeZoneId?.let { tzId ->
                    val polygonBoundaries = coordinates.map { ring ->
                        ring.map { coordinate ->
                            Pair(coordinate[1], coordinate[0]) // [longitude, latitude] to [latitude, longitude]
                        }
                    }
                    timeZoneBoundaries.getOrPut(tzId) { mutableListOf() }.addAll(polygonBoundaries)
                }
            }
        }
        return timeZoneBoundaries
    }

private suspend fun createTimeZoneOverlays(timeZoneBoundaries: Map<String, List<List<Pair<Double, Double>>>>) {
        val colors = listOf(Color.RED, Color.BLUE, Color.rgb(255, 165, 0), Color.GREEN)
        var colorIndex = 0
        val geometryFactory = GeometryFactory()

        for ((timeZoneId, polygonsList) in timeZoneBoundaries) {
            val is30MinuteOffset = TimeZone.getTimeZone(timeZoneId).rawOffset % (3600 * 1000) != 0
            val baseColor = when {
                activeTimeZoneId == timeZoneId -> Color.YELLOW
                is30MinuteOffset -> Color.MAGENTA // Violet
                else -> colors.getOrNull(colorIndex % colors.size) ?: Color.GRAY
            }
            val alphaColor = Color.argb(128, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

            for (polygonBoundary in polygonsList) {
                if (polygonBoundary.size < 3) continue

                val coordinates = polygonBoundary.map { (lat, lon) -> Coordinate(lon, lat) }.toTypedArray()
                val polygon = geometryFactory.createPolygon(coordinates)

                val triangulationBuilder = DelaunayTriangulationBuilder()
                triangulationBuilder.setSites(polygon.exteriorRing.coordinates)
                val triangulation = triangulationBuilder.getTriangles(geometryFactory)

                for (i in 0 until triangulation.numGeometries) {
                    val triangle = triangulation.getGeometryN(i)
                    val triangleCoords = triangle.coordinates

                    val vertices = FloatBuffer.allocate(triangleCoords.size * 3)
                    val indices = IntBuffer.allocate(3)

                    for (j in triangleCoords.indices) {
                        val latRad = toRadians(triangleCoords[j].y)
                        val lonRad = toRadians(triangleCoords[j].x)

                        val x = cos(latRad) * cos(lonRad) * EARTH_RADIUS
                        val y = cos(latRad) * sin(lonRad) * EARTH_RADIUS
                        val z = sin(latRad) * EARTH_RADIUS

                        vertices.put(x.toFloat()).put(y.toFloat()).put(z.toFloat())
                        indices.put(j)
                    }
                    vertices.rewind()
                    indices.rewind()

                    val material = MaterialFactory.makeOpaqueWithColor(alphaColor).await()

                    val vertexArray = VertexArray.builder()
                        .addVertexBuffer(vertices, "a_Position", 3)
                        .setVertexCount(triangleCoords.size)
                        .build()

                    val indexArray = IndexArray.builder()
                        .setBuffer(indices)
                        .setIndexCount(3)
                        .setIndexType(com.google.ar.sceneform.rendering.Index.UInt)
                        .build()

                    val renderable = Renderable.builder()
                        .setGeometry(vertexArray, indexArray, Renderable.PrimitiveType.TRIANGLES)
                        .setMaterial(material)
                        .build()

                    val timeZoneNode = TransformableNode(arFragment?.transformationSystem)
                    timeZoneNode.renderable = renderable
                    timeZoneNode.setParent(globeNode) // Attach to the globe
                    arFragment?.arSceneView?.scene?.addChild(timeZoneNode)
                    timeZoneOverlayNodes.add(timeZoneNode)
                }
            }

            if (!is30MinuteOffset && activeTimeZoneId != timeZoneId) {
                colorIndex++
            }
        }
    }
}