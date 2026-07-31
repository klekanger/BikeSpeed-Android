package no.bikespeed.app.model

/**
 * User-selectable in-app display language, independent of [MeasurementSystem].
 *
 * Unlike iOS (which needed a manual `.lproj` bundle lookup), Android's per-app locale API
 * (`AppCompatDelegate.setApplicationLocales`) makes every `getString` follow the override —
 * wiring lands with the localization step.
 */
enum class AppLanguage(val storageKey: String, val languageTag: String?) {
    /** Follow the device's language setting. */
    SYSTEM("system", null),
    ENGLISH("english", "en"),
    NORWEGIAN("norwegian", "nb");

    companion object {
        fun fromStorageKey(key: String?): AppLanguage? = entries.firstOrNull { it.storageKey == key }
    }
}
