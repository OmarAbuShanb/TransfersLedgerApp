# تثبيت نسخة الريليز 64-بت فقط

الهدف هو تعديل إعدادات المشروع لحصر البناء على معماريات 64-بت فقط (`arm64-v8a` للهواتف و `x86_64` للمحاكيات)، ثم توضيح كيفية تثبيت النسخة الموقعة (Release) على الهاتف.

## التغييرات المقترحة

### [تطبيق TransfersLedgerApp](file:///C:/Users/Administrator/AndroidStudioProjects/TransfersLedgerApp/app/build.gradle.kts)

#### [تعديل] [build.gradle.kts](file:///C:/Users/Administrator/AndroidStudioProjects/TransfersLedgerApp/app/build.gradle.kts)
إضافة `abiFilters` داخل `defaultConfig` لتحديد المعماريات المدعومة بـ 64-بت فقط.

```kotlin
android {
    // ...
    defaultConfig {
        // ...
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }
}
```

## خطة التحقق

### التحقق اليدوي
1.  توصيل الهاتف وتفعيل وضع تصحيح أخطاء USB.
2.  تشغيل أمر التثبيت: `./gradlew installRelease`.
3.  التأكد من أن التطبيق يعمل بشكل صحيح على الهاتف.
4.  (اختياري) فحص ملف الـ APK الناتج للتأكد من وجود مجلدات `arm64-v8a` و `x86_64` فقط في مجلد `lib`.
