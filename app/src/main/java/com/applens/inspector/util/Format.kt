package com.applens.inspector.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${bytes} B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

fun formatDuration(ms: Long): String {
    return when {
        ms < 1000 -> "${ms} ms"
        ms < 60_000 -> String.format(Locale.US, "%.1f s", ms / 1000.0)
        else -> "${ms / 60_000} min ${(ms % 60_000) / 1000} s"
    }
}

fun formatCount(value: Int): String = String.format(Locale.US, "%,d", value)
