package ps.palpay.tracker.core

object PhoneNumberUtils {
    /**
     * ينظف الرقم من المقدمات الدولية الشائعة ويحوله للتنسيق المحلي الفلسطيني (05x)
     * القواعد المطلوبة:
     * 1. إزالة 0097 أو 97 أو +97
     * 2. إذا بدأ بـ 259 أو 256، تحويل 2 إلى 0 لتصبح 059 أو 056
     * 3. إذا بدأ بـ 59 أو 56 مباشرة، إضافة 0 في البداية
     */
    fun cleanPhoneNumber(input: String): String {
        var cleaned = input.trim().replace(" ", "").replace("-", "")
        
        // إزالة +970, +972, 00970, 00972, 970, 972
        val countryPrefixes = listOf("+970", "+972", "00970", "00972", "970", "972")
        for (prefix in countryPrefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length)
                // بعد إزالة مقدمة الدولة، إذا لم يبدأ بـ 0، نضيف 0
                if (!cleaned.startsWith("0")) {
                    cleaned = "0$cleaned"
                }
                break
            }
        }
        
        // إذا بدأ بـ 259 أو 256 (مقدمة بديلة مستخدمة أحياناً)، نستبدل الـ 2 بـ 0
        if (cleaned.startsWith("259")) {
            cleaned = "059" + cleaned.substring(3)
        } else if (cleaned.startsWith("256")) {
            cleaned = "056" + cleaned.substring(3)
        }
        
        // إذا بدأ بـ 59 أو 56 أو 52 بدون 0، نضع 0
        if ((cleaned.startsWith("59") || cleaned.startsWith("56") || cleaned.startsWith("52")) && !cleaned.startsWith("0")) {
            cleaned = "0$cleaned"
        }
        
        return cleaned
    }

    /**
     * يتحقق إذا كان النص المدخل عبارة عن رقم هاتف (أرقام فقط وطوله مناسب)
     */
    fun isPhoneNumber(input: String): Boolean {
        val cleaned = input.trim().replace(" ", "").replace("-", "").replace("+", "")
        // إذا كان أرقام فقط وطوله بين 7 و 15
        return cleaned.matches(Regex("^[0-9]+$")) && cleaned.length >= 7
    }
}
