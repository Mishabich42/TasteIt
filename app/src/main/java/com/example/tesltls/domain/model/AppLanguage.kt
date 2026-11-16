package com.example.tesltls.domain.model

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Підтримувані мови для інтерфейсу та автоматичного перекладу страв через Google Translate.
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val flagEmoji: String,
    val mlKitCode: String
) {
    UKRAINIAN("uk", "Українська", "🇺🇦", TranslateLanguage.UKRAINIAN),
    ENGLISH("en", "English", "🇬🇧", TranslateLanguage.ENGLISH),
    POLISH("pl", "Polski", "🇵🇱", TranslateLanguage.POLISH),
    GERMAN("de", "Deutsch", "🇩🇪", TranslateLanguage.GERMAN),
    SPANISH("es", "Español", "🇪🇸", TranslateLanguage.SPANISH),
    FRENCH("fr", "Français", "🇫🇷", TranslateLanguage.FRENCH),
    ITALIAN("it", "Italiano", "🇮🇹", TranslateLanguage.ITALIAN);

    companion object {
        val DEFAULT = UKRAINIAN

        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT
    }
}
