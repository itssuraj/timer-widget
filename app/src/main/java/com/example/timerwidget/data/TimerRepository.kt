package com.example.timerwidget.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timerwidget.TimerWidgetProvider
import com.example.timerwidget.model.TimerItem
import com.example.timerwidget.model.TimerState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

// Single instance of DataStore
private val Context.dataStore by preferencesDataStore(name = "timer_settings")

class TimerRepository(private val context: Context) {
    private val gson = Gson()
    private val TIMERS_KEY = stringPreferencesKey("timers_list")
    private val INITIALIZED_KEY = stringPreferencesKey("initialized")

    // --- 1. NEW: Expose the list of timers as a Flow (Observable) ---
    val timers: Flow<List<TimerItem>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[TIMERS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TimerItem>>() {}.type
            gson.fromJson(json, type)
        }

    // --- Initialize preset timers on first launch ---
    suspend fun ensureInitialized() {
        context.dataStore.edit { preferences ->
            val isInitialized = preferences[INITIALIZED_KEY] == "true"
            if (!isInitialized) {
                val presetTimers = mutableListOf(
                    TimerItem(
                        id = UUID.randomUUID().toString(),
                        originalDurationSec = 10 * 60, // 10 minutes
                        currentDurationSec = 10 * 60,
                        state = TimerState.IDLE,
                        createdAt = System.currentTimeMillis()
                    ),
                    TimerItem(
                        id = UUID.randomUUID().toString(),
                        originalDurationSec = 5 * 60, // 5 minutes
                        currentDurationSec = 5 * 60,
                        state = TimerState.IDLE,
                        createdAt = System.currentTimeMillis()
                    )
                )
                
                preferences[TIMERS_KEY] = gson.toJson(presetTimers)
                preferences[INITIALIZED_KEY] = "true"
            }
        }
    }

    // --- 2. Existing: Add a new timer ---
    suspend fun addTimer(durationSec: Int) {
        context.dataStore.edit { preferences ->
            val json = preferences[TIMERS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TimerItem>>() {}.type
            val list: MutableList<TimerItem> = gson.fromJson(json, type)

            val newTimer = TimerItem(
                id = UUID.randomUUID().toString(),
                originalDurationSec = durationSec,
                currentDurationSec = durationSec,
                state = TimerState.IDLE,
                createdAt = System.currentTimeMillis()
            )

            // Logic: Max 2 timers. Newest replaces oldest (FIFO).
            if (list.size >= 2) list.removeAt(0)
            list.add(newTimer)

            preferences[TIMERS_KEY] = gson.toJson(list)
        }
        
        // Notify the widget to update
        notifyWidgetUpdate()
    }

    // --- 3. NEW: Update Timer State (Used by Service to Tick) ---
    suspend fun updateTimerState(timerId: String, newState: TimerState, newTime: Int? = null) {
        context.dataStore.edit { preferences ->
            val json = preferences[TIMERS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TimerItem>>() {}.type
            val list: MutableList<TimerItem> = gson.fromJson(json, type)

            // Find and modify the specific timer
            val index = list.indexOfFirst { it.id == timerId }
            if (index != -1) {
                val timer = list[index]
                timer.state = newState
                if (newTime != null) {
                    timer.currentDurationSec = newTime
                }
                // Save back to list
                list[index] = timer
                preferences[TIMERS_KEY] = gson.toJson(list)
            }
        }
    }

    // --- Notify widget to update ---
    private fun notifyWidgetUpdate() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            val intent = Intent(context, TimerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }