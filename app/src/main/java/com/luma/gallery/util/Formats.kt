package com.luma.gallery.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
private val timeOnly = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        kb < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", kb / 1024)
        else -> String.format(Locale.getDefault(), "%.2f GB", kb / (1024 * 1024))
    }
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(dayMonthYear)
}

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeOnly)

fun localDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Returns null when the date is today or yesterday so the caller can use a localised label.
 */
fun formatDateHeader(millis: Long): String? {
    val date = localDate(millis)
    val today = LocalDate.now()
    return when {
        date == today || date == today.minusDays(1) -> null
        date.year == today.year -> date.format(dayMonth)
        else -> date.format(dayMonthYear)
    }
}

fun resolutionLabel(width: Int, height: Int): String =
    if (width > 0 && height > 0) "$width × $height" else "—"

fun mimeLabel(mime: String): String = when {
    mime.contains("heic", true) || mime.contains("heif", true) -> "HEIC"
    mime.contains("avif", true) -> "AVIF"
    mime.contains("webp", true) -> "WebP"
    mime.contains("png", true) -> "PNG"
    mime.contains("gif", true) -> "GIF"
    mime.contains("jpeg", true) || mime.contains("jpg", true) -> "JPEG"
    mime.contains("mp4", true) -> "MP4"
    mime.contains("mkv", true) -> "MKV"
    mime.contains("webm", true) -> "WebM"
    mime.contains("3gp", true) -> "3GP"
    mime.contains("avi", true) -> "AVI"
    mime.contains("mov", true) -> "MOV"
    else -> mime.substringAfter('/', mime).uppercase(Locale.getDefault())
}
