package dev.anonymous.transfers_ledger.core

import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatNumerals(text: String, locale: Locale): String {
        if (locale.language != "ar") return text
        val arabicNumerals = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return text.map { char ->
            if (char in '0'..'9') arabicNumerals[char - '0'] else char
        }.joinToString("")
    }

    fun formatMoney(amount: Double, locale: Locale): String {
        return formatNumerals(String.format(Locale.US, "%.2f", amount), locale)
    }

    fun getFullDateTimeArabic(timestamp: Long, locale: Locale): String {
        val date = Date(timestamp)
        return if (locale.language == "ar") {
            val datePart = SimpleDateFormat("EEEE، d/M/yyyy", Locale("ar")).format(date)
            val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
            val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
            val hour12 = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val minute = calendar.get(Calendar.MINUTE)
            val period = if (hour24 < 12) "صباحا" else "مساء"
            formatNumerals("$datePart، الساعة ${String.format(Locale.US, "%02d:%02d", hour12, minute)} $period", locale)
        } else {
            SimpleDateFormat("EEEE, M/d/yyyy, h:mm a", Locale.ENGLISH).format(date)
        }
    }

    fun getDayHeader(timestamp: Long, locale: Locale): String {
        val target = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((today.timeInMillis - target.timeInMillis) / DAY_MS).toInt()
        val text = if (locale.language == "ar") {
            when (diffDays) {
                0 -> "اليوم"
                1 -> "أمس"
                in 2..6 -> SimpleDateFormat("EEEE", Locale("ar")).format(Date(timestamp))
                else -> SimpleDateFormat("EEEE، d/M/yyyy", Locale("ar")).format(Date(timestamp))
            }
        } else {
            when (diffDays) {
                0 -> "Today"
                1 -> "Yesterday"
                in 2..6 -> SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date(timestamp))
                else -> SimpleDateFormat("EEEE, M/d/yyyy", Locale.ENGLISH).format(Date(timestamp))
            }
        }
        return formatNumerals(text, locale)
    }

    fun getLocale(configuration: Configuration): Locale {
        return ConfigurationCompat.getLocales(configuration).get(0) ?: Locale.getDefault()
    }

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
