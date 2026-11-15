package com.example.timerwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.timerwidget.data.TimerRepository
import com.example.timerwidget.model.TimerState
import com.example.timerwidget.util.TimeFormatter
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
            ACTION_START, ACTION_RESUME -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) startTimer(timerId)
            }
            ACTION_PAUSE -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) pauseTimer(timerId)
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
        // Cancel any existing job
        timerJob?.cancel()
        
        // Start Foreground Service immediately
        startForeground(NOTIFICATION_ID, createNotification("Timer Running"))

        timerJob = serviceScope.launch {
            // Fetch initial timer data
            val initialTimers = repository.timers.first()
            val currentTimer = initialTimers.find { it.id == timerId } ?: return@launch

            // 1. Mark in repository as RUNNING (which also pauses any other timer)
            repository.updateTimerState(timerId, TimerState.RUNNING)

            // 2. Exact wall-clock tracking using elapsedRealtime to prevent coroutine delay drift
            val targetEndTimeMillis = SystemClock.elapsedRealtime() + (currentTimer.currentDurationSec * 1000L)
            var hasAlerted = currentTimer.currentDurationSec <= 0

            while (isActive) {
                val remainingMillis = targetEndTimeMillis - SystemClock.elapsedRealtime()
                val remainingSec = if (remainingMillis >= 0) {
                    ((remainingMillis + 999) / 1000).toInt()
                } else {
                    (remainingMillis / 1000).toInt()
                }

                // Alert user when countdown completes
                if (remainingSec <= 0 && !hasAlerted) {
                    hasAlerted = true
                    triggerCompletionAlert()
                }

                val newState = if (remainingSec < 0) TimerState.OVERRUN else TimerState.RUNNING
                repository.updateTimerState(timerId, newState, remainingSec)

                // Update Widget UI and Notification
                updateWidgetUI()
                updateNotification(if (remainingSec < 0) "Timer ended (-${TimeFormatter.format(remainingSec)})" else "Remaining: ${TimeFormatter.format(remainingSec)}")

                // Sleep for ~1 second
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
            stopSelf()
        }
    }

    private fun stopTimer(timerId: String) {
        timerJob?.cancel()
        serviceScope.launch {
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
            val timers = repository.timers.first()
            val timer = timers.find { it.id == timerId }
            val resetTime = timer?.originalDurationSec ?: 0
            
            repository.updateTimerState(timerId, TimerState.IDLE, resetTime)
            updateWidgetUI()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun triggerCompletionAlert() {
        try {
            // Sound
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone?.play()

            // Vibration
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(600)
                }
            }
        } catch (ignored: Exception) {
            // Fail gracefully if audio/vibration permission is restricted
        }
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun updateWidgetUI() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(this, TimerWidgetProvider::class.java))
        
        if (ids.isNotEmpty()) {
            val intent = Intent(this, TimerWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            sendBroadcast(intent)
        }
    }

    private fun createNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer Widget")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live running countdown timer status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
