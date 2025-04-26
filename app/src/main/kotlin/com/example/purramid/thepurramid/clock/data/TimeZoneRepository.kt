// TimeZoneRepository.kt
package com.example.purramid.thepurramid.clock.data 

import org.locationtech.jts.geom.Polygon

interface TimeZoneRepository {
    suspend fun getTimeZonePolygons(): Result<Map<String, List<Polygon>>>
}