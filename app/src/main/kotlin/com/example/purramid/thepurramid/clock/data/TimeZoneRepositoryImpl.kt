// TimeZoneRepositoryImpl.kt
package com.example.purramid.thepurramid.clock.data

import android.content.Context
import android.util.Log
// Adjust DB/DI imports
import com.example.purramid.thepurramid.clock.data.CityData
import com.example.purramid.thepurramid.data.db.CityDao
import com.example.purramid.thepurramid.data.db.CityEntity
import com.example.purramid.thepurramid.data.db.TimeZoneBoundaryEntity
import com.example.purramid.thepurramid.data.db.TimeZoneDao
import com.example.purramid.thepurramid.data.db.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.io.geojson.GeoJsonReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


@Singleton
class TimeZoneRepositoryImpl @Inject constructor(
    private val timeZoneDao: TimeZoneDao,
    private val cityDao: CityDao,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher // Inject dispatcher
) : TimeZoneRepository {

    private val TAG = "TimeZoneRepository"
    private val GEOJSON_ASSET_NAME = "time_zones.geojson"
    private val CITIES_CSV_ASSET_PATH = "cities_timezones.csv"
    private val cityDbMutex = Mutex()
    @Volatile private var cityDbPopulated = false // Flag to check if DB is populated

    // Override getTimeZonePolygons (logic remains the same, using ioDispatcher)
    override suspend fun getTimeZonePolygons(): Result<Map<String, List<Polygon>>> = withContext(ioDispatcher) {
        if (cachedPolygons != null) {
            Log.d(TAG, "Returning cached polygons.")
            return@withContext Result.success(cachedPolygons!!)
        }
        // ... rest of Room check, GeoJSON parsing, caching logic ...
         try {
            val count = timeZoneDao.getCount()
            if (count > 0) {
                val entities = timeZoneDao.getAllBoundaries()
                val polygons = entities.associate { entity ->
                     entity.tzid to parseWktToPolygons(entity.polygonWkt)
                }.filterValues { it.isNotEmpty() }
                if (polygons.isNotEmpty()) {
                     Log.d(TAG, "Loaded ${polygons.size} time zones from Room.")
                     cachedPolygons = polygons
                     return@withContext Result.success(polygons)
                }
            }
            // Parse GeoJSON if needed... (rest of the logic)
            Log.d(TAG, "Parsing GeoJSON asset: $GEOJSON_ASSET_NAME")
            // ... (GeoJSON parsing logic) ...
            // ... (Caching logic) ...
            // Return Result.success(parsedPolygons) or Result.failure(e)
             // Placeholder for the rest of the implementation
            Result.failure(NotImplementedError("GeoJSON parsing/caching logic needed here"))

        } catch (e: Exception) {
            Log.e(TAG, "Error getting time zone polygons", e)
            Result.failure(e)
        }
    }

    // --- Implementation for getCitiesForTimeZone using Room ---
    override suspend fun getCitiesForTimeZone(tzId: String): List<CityData> = withContext(ioDispatcher) {
        populateCityDbFromCsvIfNeeded() // Ensure DB has data

        // Query Room database
        val cityEntities = cityDao.getCitiesByTimezone(tzId)

        // Map Entity to Data class for use by ViewModel
        cityEntities.map { entity ->
            CityData(
                name = entity.name,
                country = entity.country,
                latitude = entity.latitude,
                longitude = entity.longitude,
                timezone = entity.timezone
            )
        }
    }

    // --- Logic to populate Room DB from CSV only once ---
    private suspend fun populateCityDbFromCsvIfNeeded() {
        // Quick check without lock first
        if (cityDbPopulated) return

        cityDbMutex.withLock {
            // Double check after lock acquired
            if (cityDbPopulated) return@withLock

            try {
                val count = cityDao.getCount()
                if (count == 0) {

                    // Database is empty, populate from CSV
                    Log.i(TAG, "City database empty. Populating from CSV: $CITIES_CSV_ASSET_PATH")
                    val citiesToInsert = parseCitiesFromCsv()

                    if (citiesToInsert.isNotEmpty()) {
                        cityDao.insertAll(citiesToInsert) // Insert into DB (also inside try)
                        Log.i(TAG, "Successfully inserted ${citiesToInsert.size} cities into Room DB.")
                    } else {
                        Log.w(TAG, "No valid cities found in CSV to insert.")
                    }
                    // Mark as populated only if the whole process succeeded without exceptions
                    cityDbPopulated = true

                } else {
                    // DB already has data from a previous session
                    Log.d(TAG, "City database already populated ($count entries).")
                    cityDbPopulated = true // Mark as populated
                }
            } catch (e: IOException) {
                // Handle errors specifically related to reading the CSV file
                Log.e(TAG, "IOException during city DB population check/process", e)
                // Decide if you want to retry later (don't set cityDbPopulated = true)
            } catch (e: Exception) {
                // Handle other potential errors (e.g., database errors, unexpected parsing issues)
                Log.e(TAG, "Error during city DB population check/process", e)
                // Decide if you want to retry later
            }
        }
    }

    // Helper function to parse the CSV asset (keeps main logic cleaner)
    private fun parseCitiesFromCsv(): List<CityEntity> {
        val cityEntities = mutableListOf<CityEntity>()
        context.assets.open(CITIES_CSV_ASSET_PATH).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLine() // Skip header
                var lineNumber = 1
                reader.forEachLine { line ->
                    try {
                        val tokens = line.split(",").map { it.trim('"', ' ') }
                        if (tokens.size == 6) { // utc_std,city,lat,lng,country,timezone
                            val entity = CityEntity(
                                name = tokens[1],
                                country = tokens[4],
                                latitude = tokens[2].toDouble(),
                                longitude = tokens[3].toDouble(),
                                timezone = tokens[5]
                            )
                            if (entity.timezone.isNotBlank() && entity.name.isNotBlank()) {
                                citiesToInsert.add(entity)
                            } else {
                                Log.w(TAG, "Skipping line $lineNumber: blank city/timezone")
                            }
                        } else {
                            Log.w(TAG, "Skipping line $lineNumber: wrong column count")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping line $lineNumber due to error: ${e.message}")
                    }
                }
            }
        }
    }

     // Helper to parse WKT string back to a list containing one Polygon
    private fun parseWktToPolygons(wkt: String): List<Polygon> {
         // ... (Implementation is the same as before) ...
         return try {
            val geometry = WKTReader().read(wkt)
            if (geometry is Polygon) listOf(geometry) else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WKT: $wkt", e); emptyList()
        }
    }
}