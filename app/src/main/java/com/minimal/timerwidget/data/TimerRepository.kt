package com.minimal.timerwidget.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.minimal.timerwidget.TimerWidgetProvider
import com.minimal.timerwidget.model.TimerItem
import com.minimal.timerwidget.model.TimerState
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

    companion object {
        fun getDefaultTimers(): List<TimerItem> {
            return listOf(
                TimerItem(
                    id = "default_timer_10m",
                    originalDurationSec = 10 * 60, // 10 minutes
                    currentDurationSec = 10 * 60,
                    state = TimerState.IDLE,
                    createdAt = 1000L
                ),
                TimerItem(
                    id = "default_timer_5m",
                    originalDurationSec = 5 * 60, // 5 minutes
                    currentDurationSec = 5 * 60,
                    state = TimerState.IDLE,
                    createdAt = 2000L
                )
            )
        }
    }

    // --- 1. NEW: Expose the list of timers as a Flow (Observable) ---
    val timers: Flow<List<TimerItem>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[TIMERS_KEY]
            if (json == null) {
                getDefaultTimers()
            } else {
                val type = object : TypeToken<MutableList<TimerItem>>() {}.type
                try {
                    val list: List<TimerItem> = gson.fromJson(json, type) ?: emptyList()
                    if (list.isEmpty()) getDefaultTimers() else list
                } catch (e: Exception) {
                    getDefaultTimers()
                }
            }
        }

    // --- Initialize preset timers on first launch ---
    suspend fun ensureInitialized() {
        var didInit = false
        context.dataStore.edit { preferences ->
            val json = preferences[TIMERS_KEY]
            val type = object : TypeToken<MutableList<TimerItem>>() {}.type
            val currentList: List<TimerItem> = if (json != null) {
                try {
                    gson.fromJson(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            if (currentList.isEmpty()) {
                preferences[TIMERS_KEY] = gson.toJson(getDefaultTimers())
                preferences[INITIALIZED_KEY] = "true"
                didInit = true
            }
        }
        if (didInit) {
            notifyWidgetUpdate()
        }
    }

    // --- 2. Add a new timer ---
    suspend fun addTimer(durationSec: Int): String {
        var createdTimerId = ""
        context.dataStore.edit { preferences ->
            val json = preferences[TIMERS_KEY]
            val type = object : TypeToken<MutableList<TimerItem>>() {}.type
            val list: MutableList<TimerItem> = if (json != null) {
                try {
                    gson.fromJson(json, type) ?: getDefaultTimers().toMutableList()
                } catch (e: Exception) {
                    getDefaultTimers().toMutableList()
                }
            } else {
                getDefaultTimers().toMutableList()
            }

            if (list.isEmpty()) {
                list.addAll(getDefaultTimers())
            }

            val id = UUID.randomUUID().toString()
            createdTimerId = id
            val newTimer = TimerItem(
                id = id,
                originalDurationSec = durationSec,
                currentDurationSec = durationSec,
                state = TimerState.IDLE,
                createdAt = System.currentTimeMillis()
            )

            // Logic: Max 2 timers.
            // When full, replace an idle timer first to avoid terminating an active countdown.
            if (list.size >= 2) {
                val idleIndex = list.indexOfFirst { it.state == TimerState.IDLE }
                if (idleIndex != -1) {
                    list.removeAt(idleIndex)
                } else {
                    list.removeAt(0)
                }
            }
            list.add(newTimer)

            preferences[TIMERS_KEY] = gson.toJson(list)
            preferences[INITIALIZED_KEY] = "true"
        }
        
        // Notify the widget to update
        notifyWidgetUpdate()
        return createdTimerId
    }

    // --- 3. Update Timer State (Used by Service to Tick) ---
    suspend fun updateTimerState(timerId: String, newState: TimerState, newTime: Int? = null) {
        context.dataStore.edit { preferences ->
            val json = preferences[TIMERS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TimerItem>>() {}.type
            val list: MutableList<TimerItem> = gson.fromJson(json, type)

            // Ensure only one timer is in RUNNING/OVERRUN state at a time
            if (newState == TimerState.RUNNING) {
                for (item in list) {
                    if (item.id != timerId && (item.state == TimerState.RUNNING || item.state == TimerState.OVERRUN)) {
                        item.state = TimerState.PAUSED
                    }
                }
            }

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
    fun notifyWidgetUpdate() {
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
}