// SpinSettingsEntity.kt
package com.example.purramid.thepurramid.data.db

import androidx.room.PrimaryKey
import androidx.room.Entity
import com.example.purramid.thepurramid.randomizers.SlotsColumnState
import com.example.purramid.thepurramid.randomizers.RandomizerMode // Ensure this is imported
import java.util.UUID

@Entity(tableName = "spin_settings")
data class SpinSettingsEntity(
    @PrimaryKey val instanceId: UUID,

    var mode: RandomizerMode = RandomizerMode.SPIN,
    var currentListId: UUID? = null, // ID of the currently selected list
    var isSpinEnabled: Boolean = true,
    var isAnnounceEnabled: Boolean = false,
    var isCelebrateEnabled: Boolean = false,
    var isSequenceEnabled: Boolean = false

    // --- Slots Mode ---
    var numSlotsColumns: Int = 3, // Default to 3 columns
    var slotsColumnStates: List<SlotsColumnState> = emptyList() // List to hold state for each column
)