// MessageData.kt
package com.example.purramid.thepurramid.traffic_light.viewmodel

import android.net.Uri

data class MessageData(
    val text: String = "",
    val emojis: List<String> = emptyList(),
    val imageUri: Uri? = null
) {
    fun isEmpty(): Boolean = text.isEmpty() && emojis.isEmpty() && imageUri == null
    
    fun isValid(): Boolean {
        val charCount = text.length
        val emojiCount = emojis.size
        val hasImage = imageUri != null
        
        return charCount <= MAX_CHARACTERS && 
               emojiCount <= MAX_EMOJIS && 
               (if (hasImage) 1 else 0) <= MAX_IMAGES
    }
    
    companion object {
        const val MAX_CHARACTERS = 27
        const val MAX_EMOJIS = 10
        const val MAX_IMAGES = 1
    }
}

data class TrafficLightMessages(
    val redMessage: MessageData = MessageData(),
    val yellowMessage: MessageData = MessageData(),
    val greenMessage: MessageData = MessageData()
) {
    fun getMessageForColor(color: LightColor): MessageData {
        return when (color) {
            LightColor.RED -> redMessage
            LightColor.YELLOW -> yellowMessage
            LightColor.GREEN -> greenMessage
        }
    }
}