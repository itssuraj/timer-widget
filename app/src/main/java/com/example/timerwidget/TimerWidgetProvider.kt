package com.example.timerwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.timerwidget.data.TimerRepository
import com.example.timerwidget.model.TimerItem
import com.example.timerwidget.model.TimerState
import com.example.timerwidget.util.TimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Create a short-lived scope for this update cycle (prevents coroutine leaks)
        val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val pendingResult = goAsync()

        updateScope.launch {
            try {
                val repository = TimerRepository(context)
                val timers = repository.timers.first() // Fetch snapshot of data

                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, timers)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        timers: List<TimerItem>
    ) {
        // 1. Determine State: Is there any active timer?
        val activeTimer = timers.find { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED || it.state == TimerState.OVERRUN }

        val views = if (activeTimer != null) {
            // --- RENDER RUNNING STATE ---
            RemoteViews(context.packageName, R.layout.widget_running).apply {
                // Set Time Text using TimeFormatter
                setTextViewText(R.id.text_countdown, TimeFormatter.format(activeTimer.currentDurationSec))
                
                // Pause/Resume Button (on the right side next to countdown)
                val toggleAction = if (activeTimer.state == TimerState.RUNNING) {
                    TimerService.ACTION_PAUSE
                } else if (activeTimer.state == TimerState.PAUSED) {
                    TimerService.ACTION_RESUME
                } else {
                    TimerService.ACTION_PAUSE // OVERRUN state can be paused too
                }
                
                val toggleIntent = Intent(context, TimerService::class.java).apply {
                    action = toggleAction
                    putExtra(TimerService.EXTRA_TIMER_ID, activeTimer.id)
                }
                setOnClickPendingIntent(R.id.btn_pause, PendingIntent.getService(
                    context, activeTimer.id.hashCode() + 1, toggleIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Toggle Pause/Resume on Text Click too
                setOnClickPendingIntent(R.id.text_countdown, PendingIntent.getService(
                    context, activeTimer.id.hashCode() + 2, toggleIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
                
                // Add Button -> Opens App (or could be Stop)
                val addIntent = Intent(context, MainActivity::class.java)
                setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Show remaining idle timer below (if there's a second timer that's idle)
                val idleTimer = timers.find { it.state == TimerState.IDLE }
                if (idleTimer != null && idleTimer.id != activeTimer.id) {
                    setViewVisibility(R.id.timer_slot_1, View.VISIBLE)
                    setTextViewText(R.id.timer_slot_1, TimeFormatter.format(idleTimer.originalDurationSec))
                    
                    // Click to Start the idle timer
                    val startIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_TIMER_ID, idleTimer.id)
                    }
                    setOnClickPendingIntent(R.id.timer_slot_1, PendingIntent.getService(
                        context, idleTimer.id.hashCode(), startIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    setViewVisibility(R.id.timer_slot_1, View.GONE)
                }
            }
        } else {
            // --- RENDER IDLE STATE ---
            RemoteViews(context.packageName, R.layout.widget_idle).apply {
                // Add Button -> Opens App
                val addIntent = Intent(context, MainActivity::class.java)
                setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Slot 1 (First Timer)
                if (timers.isNotEmpty()) {
                    val t1 = timers[0]
                    setViewVisibility(R.id.timer_slot_1, View.VISIBLE)
                    setTextViewText(R.id.timer_slot_1, TimeFormatter.format(t1.originalDurationSec))
                    
                    // Click to Start
                    val startIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_TIMER_ID, t1.id)
                    }
                    setOnClickPendingIntent(R.id.timer_slot_1, PendingIntent.getService(
                        context, t1.id.hashCode(), startIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    setViewVisibility(R.id.timer_slot_1, View.GONE)
                }

                // Slot 2 (Second Timer, enforces max 2 shown)
                if (timers.size > 1) {
                    val t2 = timers[1]
                    setViewVisibility(R.id.timer_slot_2, View.VISIBLE)
                    setTextViewText(R.id.timer_slot_2, TimeFormatter.format(t2.originalDurationSec))
                    
                    val startIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_TIMER_ID, t2.id)
                    }
                    setOnClickPendingIntent(R.id.timer_slot_2, PendingIntent.getService(
                        context, t2.id.hashCode(), startIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    setViewVisibility(R.id.timer_slot_2, View.GONE)
                }
                
                // Empty State
                if (timers.isEmpty()) {
                    setViewVisibility(R.id.text_empty, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.text_empty, View.GONE)
                }
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}