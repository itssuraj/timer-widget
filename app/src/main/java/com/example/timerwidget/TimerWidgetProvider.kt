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
        // Use a single unified layout for all states
        val views = RemoteViews(context.packageName, R.layout.widget_main)

        // Determine primary and secondary timers
        val activeTimer = timers.find { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED || it.state == TimerState.OVERRUN }
        
        when {
            // Case 1: No timers at all
            timers.isEmpty() -> {
                views.setViewVisibility(R.id.text_empty, View.VISIBLE)
                views.setViewVisibility(R.id.primary_timer_container, View.GONE)
                views.setViewVisibility(R.id.secondary_timer_container, View.GONE)
                
                val addIntent = Intent(context, MainActivity::class.java)
                views.setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }

            // Case 2: One timer (no active/running, so it's idle)
            timers.size == 1 && activeTimer == null -> {
                val timer = timers[0]
                views.setViewVisibility(R.id.text_empty, View.GONE)
                views.setViewVisibility(R.id.primary_timer_container, View.VISIBLE)
                views.setViewVisibility(R.id.secondary_timer_container, View.GONE)

                // Primary timer (idle)
                views.setTextViewText(R.id.primary_timer_text, TimeFormatter.format(timer.originalDurationSec))
                views.setBackgroundResource(R.id.primary_timer_container, R.drawable.widget_bg_idle)
                views.setTextViewTextSize(R.id.primary_timer_text, android.util.TypedValue.COMPLEX_UNIT_SP, 40f)
                views.setViewVisibility(R.id.primary_timer_icon, View.GONE)

                // Click to start
                val startIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                }
                views.setOnClickPendingIntent(R.id.primary_timer_container, PendingIntent.getService(
                    context, timer.id.hashCode(), startIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                val addIntent = Intent(context, MainActivity::class.java)
                views.setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }

            // Case 3: Two idle timers (no active)
            timers.size == 2 && activeTimer == null -> {
                views.setViewVisibility(R.id.text_empty, View.GONE)
                views.setViewVisibility(R.id.primary_timer_container, View.VISIBLE)
                views.setViewVisibility(R.id.secondary_timer_container, View.VISIBLE)

                val timer1 = timers[0]
                val timer2 = timers[1]

                // Primary timer
                views.setTextViewText(R.id.primary_timer_text, TimeFormatter.format(timer1.originalDurationSec))
                views.setBackgroundResource(R.id.primary_timer_container, R.drawable.widget_bg_idle)
                views.setTextViewTextSize(R.id.primary_timer_text, android.util.TypedValue.COMPLEX_UNIT_SP, 40f)
                views.setViewVisibility(R.id.primary_timer_icon, View.GONE)

                val startIntent1 = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_TIMER_ID, timer1.id)
                }
                views.setOnClickPendingIntent(R.id.primary_timer_container, PendingIntent.getService(
                    context, timer1.id.hashCode(), startIntent1, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Secondary timer
                views.setTextViewText(R.id.secondary_timer_text, TimeFormatter.format(timer2.originalDurationSec))

                val startIntent2 = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_TIMER_ID, timer2.id)
                }
                views.setOnClickPendingIntent(R.id.secondary_timer_container, PendingIntent.getService(
                    context, timer2.id.hashCode(), startIntent2, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                val addIntent = Intent(context, MainActivity::class.java)
                views.setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }

            // Case 4: Active timer + idle timer (or only active)
            activeTimer != null -> {
                views.setViewVisibility(R.id.text_empty, View.GONE)
                views.setViewVisibility(R.id.primary_timer_container, View.VISIBLE)

                // Primary timer (active)
                views.setTextViewText(R.id.primary_timer_text, TimeFormatter.format(activeTimer.currentDurationSec))
                views.setTextViewTextSize(R.id.primary_timer_text, android.util.TypedValue.COMPLEX_UNIT_SP, 48f)

                // Set primary background based on state
                val bgRes = if (activeTimer.state == TimerState.PAUSED) {
                    R.drawable.widget_bg_idle
                } else {
                    R.drawable.widget_bg_running
                }
                views.setBackgroundResource(R.id.primary_timer_container, bgRes)

                // Set up toggle action
                val toggleAction = when (activeTimer.state) {
                    TimerState.RUNNING -> TimerService.ACTION_PAUSE
                    TimerState.PAUSED -> TimerService.ACTION_RESUME
                    TimerState.OVERRUN -> TimerService.ACTION_PAUSE
                    else -> TimerService.ACTION_PAUSE
                }

                val toggleIntent = Intent(context, TimerService::class.java).apply {
                    action = toggleAction
                    putExtra(TimerService.EXTRA_TIMER_ID, activeTimer.id)
                }

                views.setOnClickPendingIntent(R.id.primary_timer_container, PendingIntent.getService(
                    context, activeTimer.id.hashCode() + 1, toggleIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))

                // Handle icon (reset/stop)
                if (activeTimer.state == TimerState.OVERRUN) {
                    views.setViewVisibility(R.id.primary_timer_icon, View.VISIBLE)
                    views.setImageViewResource(R.id.primary_timer_icon, R.drawable.ic_stop)
                    
                    val stopIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_STOP
                        putExtra(TimerService.EXTRA_TIMER_ID, activeTimer.id)
                    }
                    views.setOnClickPendingIntent(R.id.primary_timer_icon, PendingIntent.getService(
                        context, activeTimer.id.hashCode() + 2, stopIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    views.setViewVisibility(R.id.primary_timer_icon, View.VISIBLE)
                    views.setImageViewResource(R.id.primary_timer_icon, R.drawable.ic_reset)
                    
                    val resetIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_RESET
                        putExtra(TimerService.EXTRA_TIMER_ID, activeTimer.id)
                    }
                    views.setOnClickPendingIntent(R.id.primary_timer_icon, PendingIntent.getService(
                        context, activeTimer.id.hashCode() + 2, resetIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                }

                // Secondary timer (if there's another one that's idle)
                val secondaryTimer = timers.find { it.id != activeTimer.id && it.state == TimerState.IDLE }
                if (secondaryTimer != null) {
                    views.setViewVisibility(R.id.secondary_timer_container, View.VISIBLE)
                    views.setTextViewText(R.id.secondary_timer_text, TimeFormatter.format(secondaryTimer.originalDurationSec))

                    val startIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_TIMER_ID, secondaryTimer.id)
                    }
                    views.setOnClickPendingIntent(R.id.secondary_timer_container, PendingIntent.getService(
                        context, secondaryTimer.id.hashCode(), startIntent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    views.setViewVisibility(R.id.secondary_timer_container, View.GONE)
                }

                val addIntent = Intent(context, MainActivity::class.java)
                views.setOnClickPendingIntent(R.id.btn_add_timer, PendingIntent.getActivity(
                    context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}