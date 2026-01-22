package com.example.timerwidget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Simple programmatic UI to avoid creating a layout file for now
        val textView = TextView(this)
        textView.text = "Timer Widget App Installed!"
        textView.textSize = 24f
        setContentView(textView)
    }
}