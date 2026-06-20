package com.quietkeeper.app.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for whether the user has Pro access.
 *
 * `isPro = billingEntitled || debugPro`, where:
 *  - billingEntitled comes from [BillingManager] (a real owned subscription).
 *  - debugPro is a DataStore-backed override so testers can exercise the Pro
 *    UI on a device that has no configured Play Console product.
 *
 * Call [init] exactly once (from MainActivity) before reading [isPro].
 */
object ProStatus {

    private val Context.dataStore by preferencesDataStore(name = "quietkeeper_pro")
    private val DEBUG_PRO_KEY = booleanPreferencesKey("debug_pro")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Mirrors BillingManager.entitled; updated via onBillingEntitlementChanged.
    private val billingEntitled = MutableStateFlow(false)

    // Last-known debug-pro value, mirrored from DataStore so a non-suspend
    // toggle can flip the flag itself (not the combined isPro).
    private val debugPro = MutableStateFlow(false)

    // Until init() runs, expose a plain false flow so callers never NPE.
    private val fallback = MutableStateFlow(false)

    @Volatile
    private var _isPro: StateFlow<Boolean> = fallback
    /** Combined Pro status. Always safe to read; defaults to false before init(). */
    val isPro: StateFlow<Boolean> get() = _isPro

    @Volatile
    private var initialized = false

    /** Wire the billing + debug flows. Idempotent. */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext

        // Seed from BillingManager's current value in case connect() already ran.
        billingEntitled.value = BillingManager.entitled.value

        val debugProFlow = appContext.dataStore.data.map { prefs ->
            (prefs[DEBUG_PRO_KEY] ?: false).also { debugPro.value = it }
        }

        _isPro = combine(billingEntitled, debugProFlow) { entitled, debug ->
            entitled || debug
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = billingEntitled.value,
        )
    }

    /** Called by BillingManager whenever real entitlement changes. */
    internal fun onBillingEntitlementChanged(value: Boolean) {
        billingEntitled.value = value
    }

    /** Persist the debug Pro override. Recomputes [isPro] reactively via DataStore. */
    suspend fun setDebugPro(context: Context, enabled: Boolean) {
        context.applicationContext.dataStore.edit { prefs ->
            prefs[DEBUG_PRO_KEY] = enabled
        }
    }

    /** Convenience: flip the debug override from a non-suspend caller. */
    fun toggleDebugPro(context: Context) {
        scope.launch {
            setDebugPro(context, !debugPro.value)
        }
    }
}
