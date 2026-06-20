package com.quietkeeper.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper around Play [BillingClient] for the QuietKeeper Pro subscription.
 *
 * Design constraints for the current phase:
 *  - No real Play Console product is configured yet, so [launchPurchase] is
 *    expected to find NO product details. That is handled gracefully (callback
 *    + log) instead of crashing.
 *  - On an emulator without Play services / Google account, connection simply
 *    fails; [entitled] stays false and nothing crashes.
 *
 * The entitlement [StateFlow] is the only thing the rest of the app reads,
 * via [ProStatus], which combines it with a debug override.
 */
object BillingManager {

    private const val TAG = "QK.Billing"

    /**
     * Product id of the Pro subscription.
     * TODO(prod): create this subscription product in Google Play Console
     * (Monetize > Subscriptions) with this exact id, plus a base plan, before
     * billing can actually complete a purchase.
     */
    const val PRO_SUB_ID = "quietkeeper_pro_monthly"

    private val _entitled = MutableStateFlow(false)
    /** True when an active Pro subscription purchase is owned. Consumed by ProStatus. */
    val entitled: StateFlow<Boolean> = _entitled.asStateFlow()

    /**
     * Optional UI callback invoked when [launchPurchase] cannot proceed because
     * the product is not configured (the expected case right now). The UI shows
     * something like "상품 미설정(개발 중)".
     */
    @Volatile
    var onProductUnavailable: (() -> Unit)? = null

    private var billingClient: BillingClient? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) handlePurchase(purchase)
        } else {
            Log.d(TAG, "Purchases updated, code=${result.responseCode} (${result.debugMessage})")
        }
    }

    /**
     * Start (or restart) the billing connection and refresh entitlement.
     * Safe to call multiple times; no-op crash on failure.
     */
    fun connect(context: Context) {
        if (billingClient?.isReady == true) {
            queryEntitlement()
            return
        }
        val client = try {
            BillingClient.newBuilder(context.applicationContext)
                .setListener(purchasesListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to build BillingClient", t)
            return
        }
        billingClient = client
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Billing connected")
                        queryEntitlement()
                    } else {
                        Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.d(TAG, "Billing service disconnected")
                    // No aggressive reconnect loop; next connect()/launchPurchase() retries.
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "startConnection threw", t)
        }
    }

    /** Query owned SUBS purchases and set entitlement if the Pro sub is active. */
    private fun queryEntitlement() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        try {
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "queryPurchases failed: ${result.responseCode}")
                    return@queryPurchasesAsync
                }
                val owned = purchases.any { purchase ->
                    purchase.products.contains(PRO_SUB_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (owned) {
                    // Make sure any unacknowledged owned purchase gets acknowledged.
                    purchases.filter {
                        it.products.contains(PRO_SUB_ID) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }.forEach { acknowledgeIfNeeded(it) }
                }
                setEntitled(owned)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "queryPurchasesAsync threw", t)
        }
    }

    /**
     * Launch the subscription purchase flow. If the product is not configured in
     * Play Console (the expected case right now), invokes [onProductUnavailable]
     * instead of crashing.
     */
    fun launchPurchase(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "launchPurchase: billing not ready; (re)connecting")
            connect(activity.applicationContext)
            onProductUnavailable?.invoke()
            return
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRO_SUB_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        try {
            client.queryProductDetailsAsync(params) { result, productDetailsList ->
                val details = productDetailsList.firstOrNull()
                val offerToken = details
                    ?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.offerToken
                if (result.responseCode != BillingClient.BillingResponseCode.OK ||
                    details == null || offerToken == null
                ) {
                    // Expected today: no product configured in Play Console.
                    Log.w(
                        TAG,
                        "Pro product '$PRO_SUB_ID' not available " +
                            "(code=${result.responseCode}). Configure it in Play Console."
                    )
                    onProductUnavailable?.invoke()
                    return@queryProductDetailsAsync
                }
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .setOfferToken(offerToken)
                                .build()
                        )
                    )
                    .build()
                val launchResult = client.launchBillingFlow(activity, flowParams)
                Log.d(TAG, "launchBillingFlow code=${launchResult.responseCode}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "queryProductDetailsAsync threw", t)
            onProductUnavailable?.invoke()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRO_SUB_ID)) return
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            acknowledgeIfNeeded(purchase)
            setEntitled(true)
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        try {
            client.acknowledgePurchase(params) { result ->
                Log.d(TAG, "acknowledge code=${result.responseCode}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "acknowledgePurchase threw", t)
        }
    }

    private fun setEntitled(value: Boolean) {
        _entitled.value = value
        ProStatus.onBillingEntitlementChanged(value)
    }
}
