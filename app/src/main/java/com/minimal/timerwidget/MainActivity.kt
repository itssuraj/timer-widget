package com.minimal.timerwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.minimal.timerwidget.data.TimerRepository
import kotlinx.coroutines.launch

// --- Design System Colors (From Figma) ---
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF161618)
val KeypadButtonDark = Color(0xFF202429)
val TextActive = Color(0xFFFEF7FF).copy(alpha = 0.90f)
val TextInactive = Color(0xFFFEF7FF).copy(alpha = 0.70f)
val SurfaceWhite = Color(0xFFFEF7FF)

class MainActivity : ComponentActivity() {

    private fun pinWidget(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val myProvider = ComponentName(context, TimerWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(myProvider, null, null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val repository = TimerRepository(this)

        lifecycleScope.launch {
            // Initialize preset timers on first launch
            repository.ensureInitialized()
        }

        // Request notification permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MaterialTheme {
                val widgetManager = AppWidgetManager.getInstance(this)
                val widgetIds = widgetManager.getAppWidgetIds(
                    ComponentName(this, TimerWidgetProvider::class.java)
                )
                val hasWidget = widgetIds.isNotEmpty()
                var showAddDialog by rememberSaveable { mutableStateOf(!hasWidget) }

                if (showAddDialog) {
                    AddWidgetDialog(
                        onPinWidget = {
                            pinWidget(this)
                        },
                        onOpenInApp = {
                            showAddDialog = false
                        }
                    )
                } else {
                    TimerEditorScreen(
                        hasWidget = hasWidget,
                        onPinWidget = { pinWidget(this) },
                        onTimerStart = { seconds ->
                            lifecycleScope.launch {
                                val newTimerId = repository.addTimer(seconds)
                                val startIntent = Intent(this@MainActivity, TimerService::class.java).apply {
                                    action = TimerService.ACTION_START
                                    putExtra(TimerService.EXTRA_TIMER_ID, newTimerId)
                                }
                                startForegroundService(startIntent)
                                if (hasWidget) {
                                    finish()
                                }
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun AddWidgetDialog(
    onPinWidget: () -> Unit,
    onOpenInApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark
            )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Timer Widget",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextActive
                )

                // Description
                Text(
                    text = "A minimalist dot-matrix countdown timer built for your home screen and day-to-day focus.",
                    fontSize = 14.sp,
                    color = TextInactive,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(4.dp))

                // Pin Widget Button (1-Tap!)
                Button(
                    onClick = onPinWidget,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceWhite
                    ),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = "✨ Add Widget to Home Screen",
                        color = BackgroundDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // In-App Timer Button
                OutlinedButton(
                    onClick = onOpenInApp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextActive
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "Use In-App Timer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Helpful subtitle
                Text(
                    text = "Or touch & hold your home screen > Widgets > Timer Widget",
                    fontSize = 12.sp,
                    color = TextInactive.copy(alpha = 0.6f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun TimerEditorScreen(
    hasWidget: Boolean,
    onPinWidget: () -> Unit,
    onTimerStart: (Int) -> Unit,
    onClose: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    // Convert input digits e.g. "123" -> 1m 23s -> 83 seconds
    val totalSeconds = remember(input) {
        val padded = input.padStart(6, '0')
        val h = padded.substring(0, 2).toInt()
        val m = padded.substring(2, 4).toInt()
        val s = padded.substring(4, 6).toInt()
        (h * 3600) + (m * 60) + s
    }

    val isValid = totalSeconds >= 10 // Minimum 10 seconds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- 1. Top App Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Timer",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
                color = TextInactive
            )

            if (!hasWidget) {
                Surface(
                    onClick = onPinWidget,
                    shape = RoundedCornerShape(16.dp),
                    color = KeypadButtonDark
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("✨", fontSize = 12.sp)
                        Text(
                            "Pin Widget",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextActive
                        )
                    }
                }
            }
        }

        // --- 2. Time Display (00h 00m 00s) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            TimeDisplay(input)
        }

        // --- 3. Keypad & Actions Grid ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rows 1-3: Number keypad (1-9)
            val digitRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )

            digitRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { digit ->
                        DigitButton(
                            text = digit,
                            onClick = {
                                if (input.length < 6) input += digit
                            }
                        )
                    }
                }
            }

            // Row 4: "00", "0", and Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DigitButton(
                    text = "00",
                    onClick = {
                        if (input.length <= 4 && input.isNotEmpty()) input += "00"
                    }
                )
                DigitButton(
                    text = "0",
                    onClick = {
                        if (input.length < 6 && input.isNotEmpty()) input += "0"
                    }
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(KeypadButtonDark)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (input.isNotEmpty()) input = input.dropLast(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_backspace),
                        contentDescription = "Delete",
                        tint = if (input.isNotEmpty()) TextActive else TextInactive,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Row 5: Action Row (Close, Play, Spacer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Close Button (45dp circle centered in 80dp cell)
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                            .background(KeypadButtonDark)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClose()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = TextActive,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Column 2: Play / Start Button (80dp circle)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isValid) SurfaceWhite else KeypadButtonDark)
                        .clickable(
                            enabled = isValid,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTimerStart(totalSeconds)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = "Start Timer",
                        tint = if (isValid) BackgroundDark else TextInactive,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Column 3: Placeholder spacer to maintain alignment
                Spacer(modifier = Modifier.size(80.dp))
            }
        }
    }
}

@Composable
fun TimeDisplay(input: String) {
    val padded = input.padStart(6, '0')
    val hh = padded.substring(0, 2)
    val mm = padded.substring(2, 4)
    val ss = padded.substring(4, 6)

    // Highlight segments based on typed input
    val len = input.length
    val hColor = if (len > 4) TextActive else TextInactive
    val mColor = if (len > 2) TextActive else TextInactive
    val sColor = if (len > 0) TextActive else TextInactive

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = hColor)) { append(hh) }
            withStyle(SpanStyle(color = hColor, fontSize = 25.sp)) { append("h ") }

            withStyle(SpanStyle(color = mColor)) { append(mm) }
            withStyle(SpanStyle(color = mColor, fontSize = 25.sp)) { append("m ") }

            withStyle(SpanStyle(color = sColor)) { append(ss) }
            withStyle(SpanStyle(color = sColor, fontSize = 25.sp)) { append("s") }
        },
        fontSize = 50.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 1.sp
    )
}

@Composable
fun DigitButton(
    text: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(KeypadButtonDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextActive,
            fontSize = 32.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
