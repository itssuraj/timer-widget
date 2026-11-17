package com.minimal.timerwidget.model
data class TimerItem(
    val id: String,
    val originalDurationSec: Int,
    var currentDurationSec: Int,
    var state: TimerState,
    val createdAt: Long
)