package dev.anonymous.transfers_ledger.core

import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String {
        val digitsNormalized = normalizeDigits(value)
            .trim()
            .lowercase(Locale.US)
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ٱ", "ا")
            .replace("ى", "ي")
            .replace("ة", "ه")
            .replace(Regex("\\s+"), " ")

        return if (PhoneNumberUtils.isPhoneNumber(digitsNormalized)) {
            PhoneNumberUtils.cleanPhoneNumber(digitsNormalized)
        } else {
            digitsNormalized
        }
    }

    fun normalizeDigits(value: String): String {
        val arabicIndic = "٠١٢٣٤٥٦٧٨٩"
        val easternArabicIndic = "۰۱۲۳۴۵۶۷۸۹"
        return value.map { char ->
            when {
                char in '0'..'9' -> char
                arabicIndic.indexOf(char) >= 0 -> ('0'.code + arabicIndic.indexOf(char)).toChar()
                easternArabicIndic.indexOf(char) >= 0 -> ('0'.code + easternArabicIndic.indexOf(char)).toChar()
                else -> char
            }
        }.joinToString("")
    }
}
