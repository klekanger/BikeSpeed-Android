package no.bikespeed.app.fixtures

import android.content.SharedPreferences

/**
 * A [SharedPreferences] that lives entirely in memory — the analogue of the iOS throwaway
 * `UserDefaults` suite. Keeps SettingsStore tests pure JVM (no Robolectric) and each test
 * isolated: one instance per test, nothing on disk, nothing shared.
 *
 * Edits apply immediately on [SharedPreferences.Editor.apply]/commit, which matches how the
 * store uses them. Listeners are supported minimally; no inter-process semantics.
 */
class InMemorySharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values.toMap()

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values.getOrDefault(key, defValues) as Set<String>?

    override fun getString(key: String, defValue: String?): String? =
        values.getOrDefault(key, defValue) as String?

    override fun getInt(key: String, defValue: Int): Int =
        values.getOrDefault(key, defValue) as Int

    override fun getLong(key: String, defValue: Long): Long =
        values.getOrDefault(key, defValue) as Long

    override fun getFloat(key: String, defValue: Float): Float =
        values.getOrDefault(key, defValue) as Float

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values.getOrDefault(key, defValue) as Boolean

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.remove(listener)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals.add(key) }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            val touched = removals + pending.keys
            listeners.forEach { listener ->
                touched.forEach { listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, it) }
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
