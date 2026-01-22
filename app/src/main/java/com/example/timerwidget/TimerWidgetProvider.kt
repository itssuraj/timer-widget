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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerWidgetProvider : AppWidgetProvider() {

    // Scope for running async repository calls
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Use goAsync() to keep the BroadcastReceiver alive while we fetch data
        val pendingResult = goAsync()

        scope.launch {
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
        val activeTimer = timers.find { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }

        val views = if (activeTimer != null) {
            // --- RENDER RUNNING STATE ---
            RemoteViews(context.packageName, R.layout.widget_running).apply {
                // Set Time Text
                setTextViewText(R.id.text_countdown, formatTime(activeTimer.currentDurationSec))
                
                // Stop Action
                val stopIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_STOP
                    putExtra(TimerService.EXTRA_TIMER_ID, activeTimer.id)
                }
                setOnClickPendingIntent(R.id.btn_stop, PendingIntent.getService(
                    context, activeTimer.id.hashCode(), stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Toggle Pause/Resume on Text Click
                val toggleAction = if (activeTimer.state == TimerState.RUNNING) TimerService.ACTION_PAUSE else TimerService.ACTION_RESUME
                val toggleIntent = Intent(context, TimerService::class.java).apply {
                    action = toggleAction
                    putExtra(TimerService.EXTRA_TIMER_ID, activeTimer.id)
                }
                setOnClickPendingIntent(R.id.text_countdown, PendingIntent.getService(
                    context, activeTimer.id.hashCode() + 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }
        } else {
            // --- RENDER IDLE STATE ---
            RemoteViews(context.packageName, R.layout.widget_idle).apply {
                // Add Button -> Opens App
                val addIntent = Intent(context, MainActivity::class.java)
                setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Slot 1
                if (timers.isNotEmpty()) {
                    val t1 = timers[0]
                    setViewVisibility(R.id.timer_slot_1, View.VISIBLE)
                    setTextViewText(R.id.timer_slot_1, formatTime(t1.originalDurationSec))
                    
                    // Click to Start
                    val startIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_TIMER_ID, t1.id)
                    }
                    setOnClickPendingIntent(R.id.timer_slot_1, PendingIntent.getService(
                        context, t1.id.hashCode(), startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    setViewVisibility(R.id.timer_slot_1, View.GONE)
                }

                // Slot 2
                if (timers.size > 1) {
                    val t2 = timers[1]
                    setViewVisibility(R.id.timer_slot_2, View.VISIBLE)
                    setTextViewText(R.id.timer_slot_2, formatTime(t2.originalDurationSec))
                    
                    val startIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_TIMER_ID, t2.id)
                    }
                    setOnClickPendingIntent(R.id.timer_slot_2, PendingIntent.getService(
                        context, t2.id.hashCode(), startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    // Simple Helper (or use your TimeFormatter class if accessible)
    private fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return if (m > 0) String.format("%02dm %02ds", m, s) else String.format("%02ds", s)
    }
}