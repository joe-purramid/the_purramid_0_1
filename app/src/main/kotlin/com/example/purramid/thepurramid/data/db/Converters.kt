// Converters.kt
package com.example.purramid.thepurramid.data.db

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.purramid.thepurramid.probabilities.DiceSumResultType
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.GraphPlotType
import com.example.purramid.thepurramid.probabilities.CoinProbabilityMode
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.SpinItemType
import com.example.purramid.thepurramid.randomizers.SlotsColumnState
import com.example.purramid.thepurramid.spotlight.SpotlightOpening
import com.example.purramid.thepurramid.timers.TimerType
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.MessageData
import com.example.purramid.thepurramid.traffic_light.viewmodel.Orientation
import com.example.purramid.thepurramid.traffic_light.viewmodel.ResponsiveModeSettings
import com.example.purramid.thepurramid.traffic_light.viewmodel.TimedSequence
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightMessages
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightMode
import java.time.LocalTime
import java.util.UUID

/**
 * Type converters to allow Room to reference complex data types.
 */
class Converters {

    private val gson = Gson() // Define Gson instance for use in converters

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
                Log.e("Converters", "Could not convert string to UUID: $uuidString", e)
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
        return value?.let {
            try {
                Gson().toJson(it)
            } catch (e: Exception) {
                Log.e("Converters", "Could not convert List<String> to JSON", e)
                null
            }
        }
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
                Log.e("Converters", "Could not convert JSON to List<String>: $value", e)
                null // Or return emptyList(), depending on desired error handling
            }
        }
    }

    // --- CLOCK ---
    /**
     * Converts a LocalTime to seconds of day (Long) for storage.
     * Returns null if the LocalTime is null.
     */
    @TypeConverter
    fun fromLocalTime(time: LocalTime?): Long? {
        return time?.toSecondOfDay()?.toLong()
    }

    /**
     * Converts seconds of day (Long) back to LocalTime.
     * Returns null if the input is null.
     */
    @TypeConverter
    fun toLocalTime(seconds: Long?): LocalTime? {
        return seconds?.let {
            try {
                LocalTime.ofSecondOfDay(it)
            } catch (e: Exception) {
                Log.e("Converters", "Invalid seconds value for LocalTime: $seconds", e)
                null
            }
        }
    }

    // --- PROBABILITIES ---
    // Converter for DiceSumResultType Enum
    @TypeConverter
    fun fromDiceSumResultType(value: DiceSumResultType?): String? {
        return value?.name ?: DiceSumResultType.INDIVIDUAL.name // Default if null
    }

    @TypeConverter
    fun toDiceSumResultType(value: String?): DiceSumResultType {
        return try {
            value?.let { enumValueOf<DiceSumResultType>(it) } ?: DiceSumResultType.INDIVIDUAL
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid DiceSumResultType string: $value", e)
            DiceSumResultType.INDIVIDUAL // Default on error
        }
    }

    // Converter for GraphDistributionType Enum
    @TypeConverter
    fun fromGraphDistributionType(value: GraphDistributionType?): String? {
        return value?.name ?: GraphDistributionType.OFF.name // Default if null
    }

    @TypeConverter
    fun toGraphDistributionType(value: String?): GraphDistributionType {
        return try {
            value?.let { enumValueOf<GraphDistributionType>(it) } ?: GraphDistributionType.OFF
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid GraphDistributionType string: $value", e)
            GraphDistributionType.OFF // Default on error
        }
    }

    // Converter for GraphPlotType Enum
    @TypeConverter
    fun fromGraphLineStyle(value: GraphPlotType?): String? {
        return value?.name ?: GraphPlotType.HISTOGRAM.name // Default if null
    }

    @TypeConverter
    fun toGraphLineStyle(value: String?): GraphPlotType {
        return try {
            value?.let { enumValueOf<GraphPlotType>(it) } ?: GraphPlotType.HISTOGRAM
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid GraphPlotType string: $value", e)
            GraphPlotType.HISTOGRAM // Default on error
        }
    }

    // --- Converter for CoinProbabilityMode ---
    @TypeConverter
    fun fromCoinProbabilityMode(value: CoinProbabilityMode?): String? {
        return value?.name ?: CoinProbabilityMode.NONE.name // Default if null
    }

    @TypeConverter
    fun toCoinProbabilityMode(value: String?): CoinProbabilityMode {
        return try {
            value?.let { enumValueOf<CoinProbabilityMode>(it) } ?: CoinProbabilityMode.NONE
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid CoinProbabilityMode string: $value", e)
            CoinProbabilityMode.NONE // Default on error
        }
    }

    // --- RANDOMIZERS ---
    @TypeConverter
    fun fromSlotsColumnStateList(value: List<SlotsColumnState>?): String? {
        // Use let for null safety
        return value?.let {
            try {
                Gson().toJson(it)
            } catch (e: Exception) {
                Log.e("Converters", "Could not convert List<SlotsColumnState> to JSON", e)
                null // Return null on serialization error
            }
        }
    }

    /**
     * Converts a JSON String? back to a List<SlotsColumnState>? when reading from the database.
     * Uses Gson for deserialization. Returns null if the string is null/empty or parsing fails.
     */
    @TypeConverter
    fun toSlotsColumnStateList(value: String?): List<SlotsColumnState>? {
        return if (value.isNullOrEmpty()) {
            null // Handle null or empty input string
        } else {
            try {
                // Define the specific generic type for Gson
                val listType = object : TypeToken<List<SlotsColumnState>>() {}.type
                Gson().fromJson(value, listType)
            } catch (e: Exception) {
                // Handle potential JSON parsing errors
                Log.e("Converters", "Could not convert JSON to List<SlotsColumnState>: $value", e)
                null // Return null on parsing error
            }
        }
    }

    // For Map<Int, Int> (Dice Pool, Colors, Modifiers)
    @TypeConverter
    fun fromIntIntMap(value: Map<Int, Int>?): String? {
        return if (value.isNullOrEmpty()) "{}" else Gson().toJson(value)
    }

    @TypeConverter
    fun toIntIntMap(value: String?): Map<Int, Int>? {
        // Handle null/empty input, default to empty map might be safer than null
        if (value.isNullOrEmpty()) {
            return emptyMap() // Return empty map instead of null
        }
        return try {
            val mapType = object : TypeToken<Map<Int, Int>>() {}.type
            Gson().fromJson(value, mapType)
        } catch (e: Exception) {
            Log.e("Converters", "Could not convert JSON to Map<Int, Int>: $value", e)
            emptyMap() // Return empty map on error
        }
    }

    @TypeConverter
    fun fromSpinItemType(value: SpinItemType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toSpinItemType(value: String?): SpinItemType? {
        return try {
            value?.let { SpinItemType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid SpinItemType string: $value", e)
            null
        }
    }

    // Converter for SPIN Enum
    @TypeConverter
    fun fromRandomizerMode(value: RandomizerMode?): String? {
        return value?.name ?: RandomizerMode.SPIN.name // Default if null
    }

    @TypeConverter
    fun toRandomizerMode(value: String?): RandomizerMode {
        return try {
            value?.let { enumValueOf<RandomizerMode>(it) } ?: RandomizerMode.SPIN
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid RandomizerMode string: $value", e)
            RandomizerMode.SPIN // Default on error
        }
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return value?.let { gson.toJson(it) } ?: "[]" // Store empty list as "[]"
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        if (value.isNullOrEmpty()) {
            return emptyList()
        }
        return try {
            val listType = object : TypeToken<List<Long>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("Converters", "Could not convert JSON to List<Long>: $value", e)
            emptyList() // Return empty list on error
        }
    }

    // --- SPOTLIGHT ---
    @TypeConverter
    fun fromSpotlightShape(shape: SpotlightOpening.Shape?): String? {
        return shape?.name
    }

    @TypeConverter
    fun toSpotlightShape(shapeName: String?): SpotlightOpening.Shape? {
        return try {
            shapeName?.let { SpotlightOpening.Shape.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid Spotlight.Shape string: $shapeName", e)
            null // Return null if the string doesn't match an enum constant
        }
    }

    // --- TIMERS ---
    @TypeConverter
    fun fromTimerType(value: TimerType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTimerType(value: String?): TimerType? {
        return try {
            value?.let { TimerType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid TimerType string: $value", e)
            null
        }
    }

    // --- TRAFFIC LIGHT ---
    @TypeConverter
    fun fromTrafficLightMode(mode: TrafficLightMode?): String? {
        return mode?.name ?: TrafficLightMode.MANUAL_CHANGE.name // Default if null
    }

    @TypeConverter
    fun toTrafficLightMode(modeName: String?): TrafficLightMode {
        return try {
            modeName?.let { TrafficLightMode.valueOf(it) } ?: TrafficLightMode.MANUAL_CHANGE
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid TrafficLightMode string: $modeName", e)
            TrafficLightMode.MANUAL_CHANGE // Default on error
        }
    }

    @TypeConverter
    fun fromOrientation(orientation: Orientation?): String? {
        return orientation?.name ?: Orientation.VERTICAL.name // Default if null
    }

    @TypeConverter
    fun toOrientation(orientationName: String?): Orientation {
        return try {
            orientationName?.let { Orientation.valueOf(it) } ?: Orientation.VERTICAL
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid Orientation string: $orientationName", e)
            Orientation.VERTICAL // Default on error
        }
    }

    @TypeConverter
    fun fromLightColor(color: LightColor?): String? {
        return color?.name // Nullable color maps directly to nullable string
    }

    @TypeConverter
    fun toLightColor(colorName: String?): LightColor? {
        return try {
            colorName?.let { LightColor.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Invalid LightColor string: $colorName", e)
            null // Return null if string is invalid or null
        }
    }

    // --- Traffic Light Settings Object Converter ---
    @TypeConverter
    fun fromResponsiveModeSettings(settings: ResponsiveModeSettings?): String? {
        return settings?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toResponsiveModeSettings(settingsJson: String?): ResponsiveModeSettings? {
        if (settingsJson.isNullOrEmpty()) {
            return null // Or return default: ResponsiveModeSettings()
        }
        return try {
            gson.fromJson(settingsJson, ResponsiveModeSettings::class.java)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to parse ResponsiveModeSettings JSON: $settingsJson", e)
            null // Or return default
        }
    }

    // Converter for MessageData
    @TypeConverter
    fun fromMessageData(value: MessageData?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toMessageData(value: String?): MessageData? {
        if (value.isNullOrEmpty()) return null
        return try {
            gson.fromJson(value, MessageData::class.java)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to parse MessageData JSON: $value", e)
            null
        }
    }

    // Converter for TrafficLightMessages
    @TypeConverter
    fun fromTrafficLightMessages(value: TrafficLightMessages?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toTrafficLightMessages(value: String?): TrafficLightMessages? {
        if (value.isNullOrEmpty()) return null
        return try {
            gson.fromJson(value, TrafficLightMessages::class.java)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to parse TrafficLightMessages JSON: $value", e)
            TrafficLightMessages() // Return default instead of null
        }
    }

    // Converter for List<TimedSequence>
    @TypeConverter
    fun fromTimedSequenceList(value: List<TimedSequence>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toTimedSequenceList(value: String?): List<TimedSequence>? {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            val listType = object : TypeToken<List<TimedSequence>>() {}.type
            gson.fromJson(value, listType)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to parse TimedSequence list JSON: $value", e)
            emptyList()
        }
    }

    // Optional: If you decide to store DbRange separately (not needed if part of Settings JSON)
    /*
    @TypeConverter
    fun fromDbRange(range: DbRange?): String? { ... }
    @TypeConverter
    fun toDbRange(rangeJson: String?): DbRange? { ... }
    */
}