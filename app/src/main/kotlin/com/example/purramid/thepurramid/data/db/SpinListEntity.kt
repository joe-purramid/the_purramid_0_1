// SpinListEntity.kt
package com.example.purramid.thepurramid.data.db

import java.util.UUID

data class SpinListEntity(
    val id: UUID = UUID.randomUUID(), // Unique ID for each list
    var title: String,
    val items: MutableList<SpinItem> = mutableListOf()
)