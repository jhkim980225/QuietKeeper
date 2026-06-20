package com.quietkeeper.app.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.quietkeeper.app.billing.ProStatus

/**
 * AdMob banner using Google's PUBLIC TEST banner unit id (safe to commit, no
 * account required).
 * TODO(prod): replace [TEST_BANNER_UNIT_ID] with the real banner ad unit id
 * from your AdMob account before production release.
 */
private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

/** Renders an AdMob banner. Caller is responsible for only showing it to free users. */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = TEST_BANNER_UNIT_ID
                try {
                    loadAd(AdRequest.Builder().build())
                } catch (t: Throwable) {
                    // Never let an ad-load failure crash the host screen
                    // (e.g. emulator with no Google account / no network).
                    Log.w("QK.Ads", "AdView.loadAd failed", t)
                }
            }
        },
    )
}

/**
 * Shows [AdBanner] only when the user is NOT Pro. Pro users (real entitlement or
 * debug override) see nothing.
 */
@Composable
fun AdBannerIfFree(modifier: Modifier = Modifier) {
    val isPro by ProStatus.isPro.collectAsStateWithLifecycle()
    if (!isPro) {
        AdBanner(modifier)
    }
}
