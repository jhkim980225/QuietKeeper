package com.quietkeeper.app.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily free playback quota for original-audio playback.
 *
 * FREE users get [FREE_DAILY_LIMIT] plays per calendar day; Pro users are
 * unlimited (Pro callers should bypass this entirely). Backed by a dedicated
 * DataStore named "quota" with two keys: a stored date (yyyy-MM-dd) and a
 * play count. A new day resets the count to 0 on the next access.
 */
object PlayQuota {
    const val FREE_DAILY_LIMIT = 5

    private val Context.quotaStore by preferencesDataStore(name = "quota")
    private val DATE_KEY = stringPreferencesKey("quota_date")
    private val COUNT_KEY = intPreferencesKey("quota_count")

    fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Remaining plays today for FREE users (5..0). Resets on a new day. */
    suspend fun remainingToday(context: Context, today: String): Int {
        val store = context.applicationContext.quotaStore
        val prefs = store.data.first()
        val storedDate = prefs[DATE_KEY]
        val count = if (storedDate == today) (prefs[COUNT_KEY] ?: 0) else 0
        if (storedDate != today) {
            // Roll over to the new day so reads stay consistent.
            store.edit {
                it[DATE_KEY] = today
                it[COUNT_KEY] = 0
            }
        }
        return (FREE_DAILY_LIMIT - count).coerceAtLeast(0)
    }

    /**
     * Try to consume one play. Returns true (and increments) when a play is
     * available, false when the daily quota is exhausted. Resets on a new day.
     */
    suspend fun tryConsume(context: Context, today: String): Boolean {
        val store = context.applicationContext.quotaStore
        var allowed = false
        store.edit { prefs ->
            val storedDate = prefs[DATE_KEY]
            val count = if (storedDate == today) (prefs[COUNT_KEY] ?: 0) else 0
            if (count < FREE_DAILY_LIMIT) {
                prefs[DATE_KEY] = today
                prefs[COUNT_KEY] = count + 1
                allowed = true
            } else {
                // Exhausted; still normalize the stored date for this day.
                prefs[DATE_KEY] = today
                prefs[COUNT_KEY] = count
                allowed = false
            }
        }
        return allowed
    }
}
