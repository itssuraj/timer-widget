package com.example.timerwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.timerwidget.data.TimerRepository
import kotlinx.coroutines.launch

// --- Design System Colors (From Blueprint) ---
val BackgroundDark = Color(0xFF121212)
val TextActive = Color(0xFFFEF7FF).copy(alpha = 0.90f)
val TextInactive = Color(0xFFFEF7FF).copy(alpha = 0.40f)
val ButtonActive = Color(0xFFD0BCFF) // Light Purple
val ButtonTextDark = Color(0xFF381E72)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TimerRepository(this)

        lifecycleScope.launch {
            // Initialize preset timers on first launch
            repository.ensureInitialized()
        }

        setContent {
            MaterialTheme {
                // Check if widget is added
                val widgetManager = AppWidgetManager.getInstance(this)
                val widgetIds = widgetManager.getAppWidgetIds(
                    ComponentName(this, TimerWidgetProvider::class.java)
                )
                val hasWidget = widgetIds.isNotEmpty()

                if (hasWidget) {
                    TimerEditorScreen(
                        onTimerStart = { seconds ->
                            lifecycleScope.launch {
                                repository.addTimer(seconds)
                                finish() // Close immediately after adding
                            }
                        }
                    )
                } else {
                    AddWidgetDialog(
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun AddWidgetDialog(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B20)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title
                Text(
                    text = "Add Widget First",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextActive
                )

                // Description
                Text(
                    text = "This is a widget-first app. Please add the Timer Widget to your home screen before creating custom timers.",
                    fontSize = 14.sp,
                    color = TextInactive,
                    lineHeight = 20.sp
                )

                // Steps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepItem(number = "1", text = "Long-press on your home screen")
                    StepItem(number = "2", text = "Tap \"Widgets\"")
                    StepItem(number = "3", text = "Find and select \"Timer Widget\"")
                    StepItem(number = "4", text = "Place it on your home screen")
                }

                Spacer(Modifier.height(8.dp))

                // OK Button
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonActive
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        text = "OK",
                        color = ButtonTextDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun StepItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ButtonActive),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = ButtonTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Step text
        Text(
            text = text,
            fontSize = 14.sp,
            color = TextInactive,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TimerEditorScreen(onTimerStart: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    
    // Convert input "123" -> 1m 23s -> 83 seconds
    val totalSeconds = remember(input) {
        val padded = input.padStart(6, '0')
        val h = padded.substring(0, 2).toInt()
        val m = padded.substring(2, 4).toInt()
        val s = padded.substring(4, 6).toInt()
        (h * 3600) + (m * 60) + s
    }
    
    val isValid = totalSeconds >= 10 // Minimum 10s rule

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- 1. Top Bar (Close) ---
        // (Optional: If design has a close button top-left, add it here. PDF shows X at bottom-right of keypad)
        Spacer(Modifier.height(20.dp))

        // --- 2. Time Display (00h 00m 00s) ---
        TimeDisplay(input)

        // --- 3. Keypad & Action ---
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            KeypadGrid(
                onDigitClick = { digit -> if (input.length < 6) input += digit },
                onDeleteClick = { if (input.isNotEmpty()) input = input.dropLast(1) }
            )
            
            Spacer(Modifier.height(16.dp))

            // Start Button
            Button(
                onClick = { onTimerStart(totalSeconds) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonActive,
                    disabledContainerColor = Color(0xFF2B2930) // Darker gray for disabled
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Timer",
                    color = if (isValid) ButtonTextDark else TextInactive,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
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

    // Helper to determine if a segment is "active" (has non-zero numbers typed)
    // For simplicity in this widget editor, we just light up typed numbers
    // But the design shows 00h 10m 00s where 10 is white, others gray.
    
    // Simple logic: If we have input, highlight relevant parts? 
    // Actually, "Active" design shows white text for valid numbers. 
    // Let's color strict based on input length.
    val len = input.length
    
    val hColor = if (len > 4) TextActive else TextInactive
    val mColor = if (len > 2) TextActive else TextInactive
    val sColor = if (len > 0) TextActive else TextInactive

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = hColor)) { append(hh) }
            withStyle(SpanStyle(color = TextInactive, fontSize = 24.sp)) { append("h ") }
            
            withStyle(SpanStyle(color = mColor)) { append(mm) }
            withStyle(SpanStyle(color = TextInactive, fontSize = 24.sp)) { append("m ") }
            
            withStyle(SpanStyle(color = sColor)) { append(ss) }
            withStyle(SpanStyle(color = TextInactive, fontSize = 24.sp)) { append("s") }
        },
        fontSize = 52.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp
    )
}

@Composable
fun KeypadGrid(onDigitClick: (String) -> Unit, onDeleteClick: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("00", "0", "DEL") // Matches design row 4
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    KeypadButton(key, onDigitClick, onDeleteClick)
                }
            }
        }
    }
}

@Composable
fun KeypadButton(key: String, onDigitClick: (String) -> Unit, onDeleteClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Remove ripple for cleaner look if desired
            ) {
                if (key == "DEL") onDeleteClick() else onDigitClick(key)
            },
        contentAlignment = Alignment.Center
    ) {
        if (key == "DEL") {
            Icon(
                imageVector = Icons.Default.Close, // Using 'X' icon as per visual
                contentDescription = "Delete",
                tint = TextActive,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text(
                text = key,
                color = TextActive,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}