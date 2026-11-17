package com.minimal.timerwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.minimal.timerwidget.data.TimerRepository
import com.minimal.timerwidget.model.TimerState
import com.minimal.timerwidget.util.TimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: TimerRepository
    private var timerJob: Job? = null
    private var targetEndTimeMillis: Long = 0L
    private var currentActiveTimerId: String? = null

    private var activeRingtone: Ringtone? = null
    private var activeVibrator: Vibrator? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val ACTION_ADD_MINUTE = "ACTION_ADD_MINUTE"
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
            ACTION_ADD_MINUTE -> {
                val timerId = intent.getStringExtra(EXTRA_TIMER_ID)
                if (timerId != null) addMinute(timerId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(timerId: String) {
        timerJob?.cancel()
        currentActiveTimerId = timerId

        startForeground(
            NOTIFICATION_ID,
            createNotification("Timer Running", timerId, isRunning = true)
        )

        timerJob = serviceScope.launch {
            val initialTimers = repository.timers.first()
            val currentTimer = initialTimers.find { it.id == timerId } ?: return@launch

            repository.updateTimerState(timerId, TimerState.RUNNING)
            targetEndTimeMillis = SystemClock.elapsedRealtime() + (currentTimer.currentDurationSec * 1000L)
            var hasAlerted = currentTimer.currentDurationSec <= 0

            while (isActive) {
                val remainingMillis = targetEndTimeMillis - SystemClock.elapsedRealtime()
                val remainingSec = if (remainingMillis >= 0) {
                    ((remainingMillis + 999) / 1000).toInt()
                } else {
                    (remainingMillis / 1000).toInt()
                }

                if (remainingSec <= 0 && !hasAlerted) {
                    hasAlerted = true
                    triggerCompletionAlert()
                }

                val isOverrun = remainingSec < 0
                val newState = if (isOverrun) TimerState.OVERRUN else TimerState.RUNNING
                repository.updateTimerState(timerId, newState, remainingSec)

                updateWidgetUI()
                val text = if (isOverrun) {
                    "Timer ended (-${TimeFormatter.format(remainingSec)})"
                } else {
                    "Remaining: ${TimeFormatter.format(remainingSec)}"
                }
                updateNotification(text, timerId, isOverrun = isOverrun, isRunning = !isOverrun)

                delay(1000)
            }
        }
    }

    private fun addMinute(timerId: String) {
        targetEndTimeMillis += 60_000L
        stopAlert()
        serviceScope.launch {
            val timers = repository.timers.first()
            val timer = timers.find { it.id == timerId }
            if (timer != null) {
                val newRemaining = timer.currentDurationSec + 60
                val newState = if (newRemaining > 0) TimerState.RUNNING else TimerState.OVERRUN
                repository.updateTimerState(timerId, newState, newRemaining)
                updateWidgetUI()
            }
        }
    }

    private fun pauseTimer(timerId: String) {
        timerJob?.cancel()
        stopAlert()
        serviceScope.launch {
            repository.updateTimerState(timerId, TimerState.PAUSED)
            updateWidgetUI()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopTimer(timerId: String) {
        timerJob?.cancel()
        stopAlert()
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
        stopAlert()
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
            // 1. Audio Alarm using USAGE_ALARM stream
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
            activeRingtone = ringtone

            // 2. Repeating Vibration Pattern
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            activeVibrator = vibrator
            vibrator?.let {
                val pattern = longArrayOf(0, 800, 400, 800, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means loop
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(pattern, 0)
                }
            }
        } catch (ignored: Exception) {
            // Fail gracefully if audio/vibration permission is restricted
        }
    }

    private fun stopAlert() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
            activeVibrator?.cancel()
            activeVibrator = null
        } catch (ignored: Exception) {
        }
    }

    private fun updateNotification(
        content: String,
        timerId: String? = null,
        isOverrun: Boolean = false,
        isRunning: Boolean = false
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(
            NOTIFICATION_ID,
            createNotification(content, timerId, isOverrun = isOverrun, isRunning = isRunning)
        )
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

    private fun createNotification(
        content: String,
        timerId: String? = null,
        isOverrun: Boolean = false,
        isRunning: Boolean = false
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer Widget")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(if (isOverrun) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (timerId != null) {
            if (isOverrun) {
                // Action: Stop Alarm
                val stopIntent = Intent(this, TimerService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_TIMER_ID, timerId)
                }
                val stopPending = PendingIntent.getForegroundService(
                    this, 101, stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_stop, "Stop", stopPending)
            } else if (isRunning) {
                // Action: Pause
                val pauseIntent = Intent(this, TimerService::class.java).apply {
                    action = ACTION_PAUSE
                    putExtra(EXTRA_TIMER_ID, timerId)
                }
                val pausePending = PendingIntent.getForegroundService(
                    this, 102, pauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_stop, "Pause", pausePending)

                // Action: +1 Min
                val addMinIntent = Intent(this, TimerService::class.java).apply {
                    action = ACTION_ADD_MINUTE
                    putExtra(EXTRA_TIMER_ID, timerId)
                }
                val addMinPending = PendingIntent.getForegroundService(
                    this, 103, addMinIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_add, "+1m", addMinPending)

                // Action: Reset
                val resetIntent = Intent(this, TimerService::class.java).apply {
                    action = ACTION_RESET
                    putExtra(EXTRA_TIMER_ID, timerId)
                }
                val resetPending = PendingIntent.getForegroundService(
                    this, 104, resetIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_reset, "Reset", resetPending)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Timer Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active timer status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopAlert()
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
