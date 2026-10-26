package com.pawil31.parkridekrk.data

import android.content.Context

/** Car park names the driver starred; they are listed first. Kept in shared preferences. */
class Favorites(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    operator fun contains(name: String): Boolean = name in names()

    fun toggle(name: String) {
        val names = names().toMutableSet()
        if (!names.remove(name)) names.add(name)
        prefs.edit().putStringSet(KEY_NAMES, names).apply()
    }

    private fun names(): Set<String> = prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty()

    private companion object {
        const val PREFS_NAME = "favorites"
        const val KEY_NAMES = "names"
    }
}
