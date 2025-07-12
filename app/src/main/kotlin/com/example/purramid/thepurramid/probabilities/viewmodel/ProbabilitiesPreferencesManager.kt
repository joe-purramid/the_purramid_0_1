package com.example.purramid.thepurramid.probabilities.viewmodel

import android.content.Context
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProbabilitiesPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("probabilities_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveDiceSettings(instanceId: Int, settings: DiceSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("dice_settings_$instanceId", json).apply()
    }
    
    fun loadDiceSettings(instanceId: Int): DiceSettings? {
        val json = prefs.getString("dice_settings_$instanceId", null) ?: return null
        return try {
            gson.fromJson(json, DiceSettings::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveCoinSettings(instanceId: Int, settings: CoinFlipSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("coin_settings_$instanceId", json).apply()
    }
    
    fun loadCoinSettings(instanceId: Int): CoinFlipSettings? {
        val json = prefs.getString("coin_settings_$instanceId", null) ?: return null
        return try {
            gson.fromJson(json, CoinFlipSettings::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveMode(instanceId: Int, mode: ProbabilitiesMode) {
        prefs.edit().putString("mode_$instanceId", mode.name).apply()
    }
    
    fun loadMode(instanceId: Int): ProbabilitiesMode {
        val modeName = prefs.getString("mode_$instanceId", null)
        return try {
            modeName?.let { ProbabilitiesMode.valueOf(it) } ?: ProbabilitiesMode.DICE
        } catch (e: Exception) {
            ProbabilitiesMode.DICE
        }
    }
    
    fun clearInstance(instanceId: Int) {
        prefs.edit().apply {
            remove("dice_settings_$instanceId")
            remove("coin_settings_$instanceId")
            remove("mode_$instanceId")
            apply()
        }
    }
}