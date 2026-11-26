package com.arete.app.alerts

import android.content.Context
import java.time.LocalDate

/**
 * Guarda en el celular qué avisos se mostraron y en qué día, como "clave|aaaa-mm-dd".
 * Solo claves y fechas; ningún dato del animal más allá de su id interno.
 */
class PreferencesShownAlertStore(context: Context) : ShownAlertStore {
    private val preferences = context.applicationContext.getSharedPreferences("avisos", Context.MODE_PRIVATE)

    override fun shown(): Map<String, LocalDate> =
        preferences.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { entry ->
            val key = entry.substringBeforeLast(SEPARATOR, "")
            val date = runCatching { LocalDate.parse(entry.substringAfterLast(SEPARATOR)) }.getOrNull()
            if (key.isEmpty() || date == null) null else key to date   // una entrada ilegible se ignora
        }.toMap()

    override fun replace(shown: Map<String, LocalDate>) {
        preferences.edit().putStringSet(KEY, shown.map { (key, date) -> "$key$SEPARATOR$date" }.toSet()).apply()
    }

    private companion object {
        const val KEY = "mostrados"
        const val SEPARATOR = "|"
    }
}
