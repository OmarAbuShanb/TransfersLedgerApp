package ps.palpay.tracker.core

import android.content.res.Configuration
import androidx.core.os.ConfigurationCompat
import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {

    /**
     * يحول الأرقام في النص إلى أرقام عربية (١٢٣) إذا كانت اللغة عربية
     */
    fun formatNumerals(text: String, locale: Locale): String {
        if (locale.language != "ar") return text
        
        val arabicNumerals = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return text.map { char ->
            if (char in '0'..'9') arabicNumerals[char - '0'] else char
        }.joinToString("")
    }

    fun getRelativeTimeArabic(timestamp: Long, locale: Locale): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        val text = when {
            seconds < 60 -> "الآن"
            minutes == 1L -> "قبل دقيقة"
            minutes == 2L -> "قبل دقيقتين"
            minutes in 3..10 -> "قبل $minutes دقائق"
            minutes < 60 -> "قبل $minutes دقيقة"
            hours == 1L -> "قبل ساعة"
            hours == 2L -> "قبل ساعتين"
            hours in 3..10 -> "قبل $hours ساعات"
            hours < 24 -> "قبل $hours ساعة"
            days == 1L -> "قبل يوم"
            days == 2L -> "قبل يومين"
            days in 3..10 -> "قبل $days أيام"
            days < 30 -> "قبل $days يوماً"
            else -> "منذ فترة"
        }
        
        return formatNumerals(text, locale)
    }

    fun getFullDateTimeArabic(timestamp: Long, locale: Locale): String {
        val date = Date(timestamp)
        
        // نستخدم Locale "ar" للتاريخ ليكون الخميس، 16/7/2026
        val dateSdf = SimpleDateFormat("EEEE، d/M/yyyy", Locale("ar"))
        val datePart = dateSdf.format(date)
        
        // نستخدم Locale "en" للوقت لنحصل على AM/PM بدقة ثم نترجمها يدوياً
        val timeSdf = SimpleDateFormat("hh:mm a", Locale.US)
        val timePart = timeSdf.format(date)
            .replace("AM", "صباحاً")
            .replace("PM", "مساءً")
        
        val fullText = "$datePart، الساعة $timePart"
        return formatNumerals(fullText, locale)
    }
    
    fun getArabicTransactionPlural(count: Int, locale: Locale): String {
        val text = when (count) {
            0 -> "لا يوجد حوالات"
            1 -> "حوالة واحدة"
            2 -> "حوالتان"
            in 3..10 -> "$count حوالات"
            else -> "$count حوالة"
        }
        return formatNumerals(text, locale)
    }

    fun getLocale(configuration: Configuration): Locale {
        return ConfigurationCompat.getLocales(configuration).get(0) ?: Locale.getDefault()
    }
}
