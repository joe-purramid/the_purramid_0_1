// SpinSettingsEntity.kt
package com.example.purramid.thepurramid.data.db

import java.util.UUID

data class SpinSettingsEntity(
    var mode: RandomizerMode = RandomizerMode.SPIN,
    var currentListId: UUID? = null, // ID of the currently selected list
    var numWedges: Int = 6, // Default number of wedges
    var isSpinEnabled: Boolean = true,
    var isAnnounceEnabled: Boolean = false,
    var isCelebrateEnabled: Boolean = false,
    var isSequenceEnabled: Boolean = false
)