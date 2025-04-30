// Converters.kt
package com.example.purramid.thepurramid.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.purramid.thepurramid.randomizers.SlotsColumnState
import java.util.UUID

/**
 * Type converters to allow Room to reference complex data types.
 */
class Converters {

    /**
     * Converts a UUID? to a String? for storing in the database.
     * Returns null if the UUID is null.
     */
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? {
        return uuid?.toString()
    }

    /**
     * Converts a String? back to a UUID? when reading from the database.
     * Returns null if the String is null or empty.
     */
    @TypeConverter
    fun toUUID(uuidString: String?): UUID? {
        // Check for null or empty string before attempting conversion
        return if (uuidString.isNullOrEmpty()) {
            null
        } else {
            try {
                UUID.fromString(uuidString)
            } catch (e: IllegalArgumentException) {
                // Handle potential malformed UUID strings if necessary,
                // though ideally, only valid UUIDs should be stored.
                // Log.e("Converters", "Could not convert string to UUID: $uuidString", e)
                null // Or throw an exception, depending on desired error handling
            }
        }
    }

    /**
     * Converts a List<String>? (e.g., for emojiList) to a JSON String? for storing.
     * Uses Gson for serialization. Returns null if the list is null.
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Gson().toJson(it) }
    }

    /**
     * Converts a JSON String? back to a List<String>? when reading from the database.
     * Uses Gson for deserialization. Returns null if the string is null or empty.
     */
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return if (value.isNullOrEmpty()) {
            null
        } else {
            try {
                val listType = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(value, listType)
            } catch (e: Exception) {
                // Handle potential JSON parsing errors
                // Log.e("Converters", "Could not convert JSON to List<String>: $value", e)
                null // Or return emptyList(), depending on desired error handling
            }
        }
    }

    @TypeConverter
    fun fromSlotsColumnStateList(value: List<SlotsColumnState>?): String? {
        return value?.let { Gson().toJson(it) }
    }

    @TypeConverter
    fun toSlotsColumnStateList(value: String?): List<SlotsColumnState>? {
        return if (value.isNullOrEmpty()) {
            null // Return null or emptyList() based on preference
        } else {
            try {
                // *** Use TypeToken for List<SlotsColumnState> ***
                val listType = object : TypeToken<List<SlotsColumnState>>() {}.type
                Gson().fromJson(value, listType)
            } catch (e: Exception) {
                // Log.e("Converters", "Could not convert JSON to List<SlotsColumnState>: $value", e)
                null // Or return emptyList()
            }
        }
    }

    // Add other converters here if needed for different data types
    // (e.g., Date, Bitmap, custom objects)
}