package com.example.timerwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.timerwidget.data.TimerRepository
import com.example.timerwidget.model.TimerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: TimerRepository
    private var timerJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val EXTRA_TIMER_ID = "EXTRA_TIMER_ID"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "TimerServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        repository = TimerRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) startTimer(timerId)
            }
            ACTION_PAUSE -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) pauseTimer(timerId)
            }
            ACTION_RESUME -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) startTimer(timerId)
            }
            ACTION_STOP -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) stopTimer(timerId)
            }
            ACTION_RESET -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) resetTimer(timerId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(timerId: String) {
        // Cancel any existing job to prevent duplicates
        timerJob?.cancel()
        
        // Start Foreground Service immediately
        startForeground(NOTIFICATION_ID, createNotification("Timer Running"))

        timerJob = serviceScope.launch {
            // 1. Update DB State to RUNNING
            repository.updateTimerState(timerId, TimerState.RUNNING)

            // 2. The Ticking Loop
            while (isActive) {
                // Fetch latest state to ensure we have current time
                val timers = repository.timers.first()
                val currentTimer = timers.find { it.id == timerId }

                if (currentTimer == null || currentTimer.state != TimerState.RUNNING) {
                    break // Stop if timer deleted or paused externally
                }

                // Decrement time
                val newTime = currentTimer.currentDurationSec - 1
                repository.updateTimerState(timerId, TimerState.RUNNING, newTime)

                // Update Widget UI directly (Fast)
                updateWidgetUI()

                // Wait 1 second
                delay(1000)
            }
        }
    }

    private fun pauseTimer(timerId: String) {
        timerJob?.cancel()
        serviceScope.launch {
            repository.updateTimerState(timerId, TimerState.PAUSED)
            updateWidgetUI()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun stopTimer(timerId: String) {
        timerJob?.cancel()
        serviceScope.launch {
            // Reset to original duration and IDLE state
            val timers = repository.timers.first()
            val timer = timers.find { it.id == timerId }
            val resetTime = timer?.originalDurationSec ?: 0
            
            repository.updateTimerState(timerId, TimerState.IDLE, resetTime)
            updateWidgetUI()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun resetTimer(timerId: String) {
        timerJob?.cancel()
        serviceScope.launch {
            // Reset to original duration and IDLE state
            val timers = repository.timers.first()
            val timer = timers.find { it.id == timerId }
            val resetTime = timer?.originalDurationSec ?: 0
            
            repository.updateTimerState(timerId, TimerState.IDLE, resetTime)
            updateWidgetUI()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateWidgetUI() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(this, TimerWidgetProvider::class.java))
        
        // Trigger the WidgetProvider's update logic
        val intent = Intent(this, TimerWidgetProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer Widget")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Use standard icon for now
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}