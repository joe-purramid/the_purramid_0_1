// Qualifiers.kt
package com.example.purramid.thepurramid.data.db 

import javax.inject.Qualifier

// Define the qualifier annotation
@Qualifier
@Retention(AnnotationRetention.BINARY) // Standard retention for qualifiers
annotation class IoDispatcher

// Define other dispatchers here too if needed
// @Qualifier
// @Retention(AnnotationRetention.BINARY)
// annotation class MainDispatcher