// SpinSettingsEntity.kt
package com.example.purramid.thepurramid.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.SlotsColumnState
import java.util.UUID

@Entity(tableName = "spin_settings")
data class SpinSettingsEntity(
    @PrimaryKey val instanceId: Int,

    // General settings
    var mode: RandomizerMode = RandomizerMode.SPIN,
    var currentListId: UUID? = null,
    var backgroundColor: Int = 0xFF000000.toInt(),


    // Shared settings
    var isAnnounceEnabled: Boolean = false,
    var isCelebrateEnabled: Boolean = false,

    // --- Spin Specific ---
    var isSpinEnabled: Boolean = true,
    var isSequenceEnabled: Boolean = false,
    var isSoundEnabled: Boolean = true,
    var isConfettiEnabled: Boolean = false,
    var spinDurationMillis: Long = 2000L,
    var spinMaxItems: Int = 20,

    // --- Slots Specific ---
    val slotsColumnCount: Int = 3, // Default to 3 columns
    var slotsColumnStates: List<SlotsColumnState> = emptyList(), // List to hold state for each column
    var isSlotsSoundEnabled: Boolean = true,
    var isSlotsAnnounceEnabled: Boolean = false,
    var slotsSpinDurationMillis: Long = 1000L,
    var slotsReelStopVariationMillis: Long = 200L,
)