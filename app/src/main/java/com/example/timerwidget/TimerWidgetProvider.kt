package com.example.timerwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
        val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val pendingResult = goAsync()

        updateScope.launch {
            try {
                val repository = TimerRepository(context)
                val timers = repository.timers.first()

                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, timers)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val pendingResult = goAsync()

        updateScope.launch {
            try {
                val repository = TimerRepository(context)
                val timers = repository.timers.first()
                updateWidget(context, appWidgetManager, appWidgetId, timers)
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
        val views = RemoteViews(context.packageName, R.layout.widget_main)

        // Wire up Add Timer button
        val addIntent = Intent(context, MainActivity::class.java)
        views.setOnClickPendingIntent(
            R.id.btn_add_timer,
            PendingIntent.getActivity(
                context, 0, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Check widget dimensions to support compact 1x1 mode
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        // 1x1 widget cell is typically < 85dp; 2x2 or larger is >= 110dp
        val isSmall = (minWidth in 1..85) || (minHeight in 1..85)

        if (timers.isEmpty()) {
            views.setViewVisibility(R.id.text_empty, View.VISIBLE)
            views.setViewVisibility(R.id.primary_timer_container, View.GONE)
            views.setViewVisibility(R.id.secondary_timer_container, View.GONE)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        views.setViewVisibility(R.id.text_empty, View.GONE)

        if (isSmall) {
            // --- 1x1 COMPACT MODE ---
            // Hide header and secondary timer to fit inside a single 1x1 icon cell
            views.setViewVisibility(R.id.header_container, View.GONE)
            views.setViewVisibility(R.id.secondary_timer_container, View.GONE)

            // Display active timer if one is running/paused, otherwise the first preset
            val activeTimer = timers.find {
                it.state == TimerState.RUNNING || it.state == TimerState.PAUSED || it.state == TimerState.OVERRUN
            }
            val timerToShow = activeTimer ?: timers[0]
            bindSlot(
                views = views,
                containerId = R.id.primary_timer_container,
                textId = R.id.primary_timer_text,
                iconId = R.id.primary_timer_icon,
                timer = timerToShow,
                context = context,
                slotIndex = 0,
                isSmall = true
            )
        } else {
            // --- 2x2+ FULL MODE (Permanent Fixed Positions) ---
            // Header is visible
            views.setViewVisibility(R.id.header_container, View.VISIBLE)

            // Slot 1: ALWAYS timers[0] (Fixed position, never shuffles)
            bindSlot(
                views = views,
                containerId = R.id.primary_timer_container,
                textId = R.id.primary_timer_text,
                iconId = R.id.primary_timer_icon,
                timer = timers[0],
                context = context,
                slotIndex = 0,
                isSmall = false
            )

            // Slot 2: ALWAYS timers[1] (if present)
            if (timers.size > 1) {
                bindSlot(
                    views = views,
                    containerId = R.id.secondary_timer_container,
                    textId = R.id.secondary_timer_text,
                    iconId = R.id.secondary_timer_icon,
                    timer = timers[1],
                    context = context,
                    slotIndex = 1,
                    isSmall = false
                )
            } else {
                views.setViewVisibility(R.id.secondary_timer_container, View.GONE)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun bindSlot(
        views: RemoteViews,
        containerId: Int,
        textId: Int,
        iconId: Int,
        timer: TimerItem,
        context: Context,
        slotIndex: Int,
        isSmall: Boolean = false
    ) {
        views.setViewVisibility(containerId, View.VISIBLE)

        if (isSmall) {
            views.setTextViewTextSize(textId, android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
        } else {
            views.setTextViewTextSize(textId, android.util.TypedValue.COMPLEX_UNIT_SP, 24f)
        }

        val baseReqCode = (timer.id.hashCode() * 10) + slotIndex

        when (timer.state) {
            TimerState.RUNNING -> {
                views.setTextViewText(textId, TimeFormatter.format(timer.currentDurationSec))
                views.setTextColor(textId, android.graphics.Color.parseColor("#E6FEF7FF"))
                views.setInt(containerId, "setBackgroundResource", R.drawable.widget_bg_running)

                // Tap container to Pause
                val pauseIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_PAUSE
                    putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                }
                views.setOnClickPendingIntent(
                    containerId,
                    PendingIntent.getForegroundService(
                        context, baseReqCode + 1, pauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                // Reset icon visible only in full mode
                if (isSmall) {
                    views.setViewVisibility(iconId, View.GONE)
                } else {
                    views.setViewVisibility(iconId, View.VISIBLE)
                    views.setImageViewResource(iconId, R.drawable.ic_reset)
                    val resetIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_RESET
                        putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                    }
                    views.setOnClickPendingIntent(
                        iconId,
                        PendingIntent.getForegroundService(
                            context, baseReqCode + 2, resetIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
            TimerState.PAUSED -> {
                views.setTextViewText(textId, TimeFormatter.format(timer.currentDurationSec))
                views.setTextColor(textId, android.graphics.Color.parseColor("#E6FEF7FF"))
                views.setInt(containerId, "setBackgroundResource", R.drawable.widget_bg_idle)

                // Tap container to Resume
                val resumeIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_RESUME
                    putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                }
                views.setOnClickPendingIntent(
                    containerId,
                    PendingIntent.getForegroundService(
                        context, baseReqCode + 1, resumeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                // Reset icon visible only in full mode
                if (isSmall) {
                    views.setViewVisibility(iconId, View.GONE)
                } else {
                    views.setViewVisibility(iconId, View.VISIBLE)
                    views.setImageViewResource(iconId, R.drawable.ic_reset)
                    val resetIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_RESET
                        putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                    }
                    views.setOnClickPendingIntent(
                        iconId,
                        PendingIntent.getForegroundService(
                            context, baseReqCode + 2, resetIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
            TimerState.OVERRUN -> {
                views.setTextViewText(textId, TimeFormatter.format(timer.currentDurationSec))
                views.setTextColor(textId, android.graphics.Color.parseColor("#E6FEF7FF"))
                views.setInt(containerId, "setBackgroundResource", R.drawable.widget_bg_running)

                // Tap container to Pause
                val pauseIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_PAUSE
                    putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                }
                views.setOnClickPendingIntent(
                    containerId,
                    PendingIntent.getForegroundService(
                        context, baseReqCode + 1, pauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )

                // Stop icon visible only in full mode
                if (isSmall) {
                    views.setViewVisibility(iconId, View.GONE)
                } else {
                    views.setViewVisibility(iconId, View.VISIBLE)
                    views.setImageViewResource(iconId, R.drawable.ic_stop)
                    val stopIntent = Intent(context, TimerService::class.java).apply {
                        action = TimerService.ACTION_STOP
                        putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                    }
                    views.setOnClickPendingIntent(
                        iconId,
                        PendingIntent.getForegroundService(
                            context, baseReqCode + 3, stopIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }
            TimerState.IDLE -> {
                views.setTextViewText(textId, TimeFormatter.formatDuration(timer.originalDurationSec))
                views.setTextColor(textId, android.graphics.Color.parseColor("#B3FEF7FF"))
                views.setInt(containerId, "setBackgroundResource", R.drawable.widget_bg_idle)
                views.setViewVisibility(iconId, View.GONE)

                // Tap container to Start
                val startIntent = Intent(context, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
                }
                views.setOnClickPendingIntent(
                    containerId,
                    PendingIntent.getForegroundService(
                        context, baseReqCode, startIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }
}
