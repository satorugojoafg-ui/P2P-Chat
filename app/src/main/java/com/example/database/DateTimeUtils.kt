package com.example.database

import com.example.models.UiLanguage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {

    fun parseIsoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis()
        val normalized = iso
            .substringBefore("+")
            .substringBefore("Z")
            .substringBefore(".")
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(normalized)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatTime(millis: Long, language: UiLanguage): String {
        val locale = if (language == UiLanguage.PERSIAN) Locale("fa") else Locale.US
        val sdf = SimpleDateFormat("HH:mm", locale)
        return sdf.format(Date(millis))
    }

    fun formatListTime(millis: Long, language: UiLanguage): String {
        if (millis <= 0L) return ""
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        val locale = if (language == UiLanguage.PERSIAN) Locale("fa") else Locale.US
        return if (sameDay) {
            SimpleDateFormat("HH:mm", locale).format(Date(millis))
        } else {
            SimpleDateFormat("MM/dd", locale).format(Date(millis))
        }
    }

    fun formatLastSeen(iso: String?, language: UiLanguage): String {
        if (iso.isNullOrBlank()) return ""
        val millis = parseIsoToMillis(iso)
        val diffMin = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0L)
        return if (language == UiLanguage.PERSIAN) {
            when {
                diffMin < 1L -> "لحظاتی پیش"
                diffMin < 60L -> "$diffMin دقیقه پیش"
                diffMin < 1440L -> "${diffMin / 60L} ساعت پیش"
                else -> SimpleDateFormat("MM/dd HH:mm", Locale("fa")).format(Date(millis))
            }
        } else {
            when {
                diffMin < 1L -> "just now"
                diffMin < 60L -> "${diffMin}m ago"
                diffMin < 1440L -> "${diffMin / 60L}h ago"
                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(millis))
            }
        }
    }
}
