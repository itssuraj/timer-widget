package com.example.timerwidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.timerwidget.model.TimerItem
import com.example.timerwidget.model.TimerState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "timer_settings")

class TimerRepository(private val context: Context) {
    private val gson = Gson()
    private val TIMERS_KEY = stringPreferencesKey("timers_list")

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

            // Logic: Max 2 timers. Newest replaces oldest (Top of list).
            if (list.size >= 2) list.removeAt(0)
            list.add(newTimer)

            preferences[TIMERS_KEY] = gson.toJson(list)
        }
    }
}