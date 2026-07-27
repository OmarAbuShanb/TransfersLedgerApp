package dev.anonymous.transfers_ledger.core

object PhoneNumberUtils {
    fun cleanPhoneNumber(input: String): String {
        var cleaned = normalizeDigits(input)
            .trim()
            .replace(" ", "")
            .replace("-", "")

        val countryPrefixes = listOf("+970", "+972", "00970", "00972", "970", "972")
        for (prefix in countryPrefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length)
                if (!cleaned.startsWith("0")) cleaned = "0$cleaned"
                break
            }
        }

        if (cleaned.startsWith("259")) {
            cleaned = "059" + cleaned.substring(3)
        } else if (cleaned.startsWith("256")) {
            cleaned = "056" + cleaned.substring(3)
        }

        if ((cleaned.startsWith("59") || cleaned.startsWith("56") || cleaned.startsWith("52")) && !cleaned.startsWith("0")) {
            cleaned = "0$cleaned"
        }

        return cleaned
    }

    fun isPhoneNumber(input: String): Boolean {
        val cleaned = normalizeDigits(input)
            .trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("+", "")
        return cleaned.matches(Regex("^[0-9]+$")) && cleaned.length >= 7
    }

    private fun normalizeDigits(value: String): String {
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
