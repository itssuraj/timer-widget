package com.example.timerwidget.util
object TimeFormatter {
    fun format(seconds: Int): String {
        val neg = seconds < 0
        val s = kotlin.math.abs(seconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val out = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
        return if (neg) "-$out" else out
    }
}