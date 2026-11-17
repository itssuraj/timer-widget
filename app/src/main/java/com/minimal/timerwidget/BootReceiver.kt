package com.minimal.timerwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minimal.timerwidget.data.TimerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope.launch {
                try {
                    val repository = TimerRepository(context)
                    repository.ensureInitialized()
                    repository.notifyWidgetUpdate()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
