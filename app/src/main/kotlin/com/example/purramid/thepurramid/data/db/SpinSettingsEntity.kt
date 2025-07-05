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

    var mode: RandomizerMode = RandomizerMode.SPIN,

    // --- Multi-mode settings ---
    var currentListId: UUID? = null, // ID of the currently selected list
    var isAnnounceEnabled: Boolean = false,
    var isCelebrateEnabled: Boolean = false,
    var backgroundColor: Int = 0xFF000000.toInt(), // Default black background

    // --- Spin Specific ---
    var isSpinEnabled: Boolean = true,
    var isSequenceEnabled: Boolean = false,
    var isSoundEnabled: Boolean = true,
    var isConfettiEnabled: Boolean = false,
    var spinDurationMillis: Long = 2000L,
    var spinMaxItems: Int = 20,
    val currentSpinListId: Long? = null, // Assuming Long is the type of your List ID

    // --- Slots Specific ---
    val numSlotsColumns: Int = 3, // Default to 3 columns
    var slotsColumnStates: List<SlotsColumnState> = emptyList(), // List to hold state for each column
    var isSlotsSoundEnabled: Boolean = true,
    var isSlotsAnnounceResultEnabled: Boolean = false,
    var slotsSpinDuration: Long = 1000L,
    var slotsReelStopVariation: Long = 200L,
    val currentSlotsListId: Long? = null
)