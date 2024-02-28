package nl.giejay.android.tv.immich.shared.donate

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import timber.log.Timber

class DonateService(private val context: Context) : PurchasesUpdatedListener {
    private lateinit var mBillingClient: BillingClient
    private var initialized: Boolean = false

    init {
        setupBilling()
    }

    private fun setupBilling() {
        mBillingClient =
            BillingClient.newBuilder(context).setListener(this).enablePendingPurchases().build()
        mBillingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    initialized = true
                } else {
                    Timber.e(
                        billingResult.debugMessage
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                initialized = false
            }

        })
    }

    fun showDonationOptions(activity: Activity) {
        if (initialized) {
            val params: QueryProductDetailsParams.Builder = QueryProductDetailsParams.newBuilder()
            params.setProductList(PRODUCTS.map {
                QueryProductDetailsParams.Product.newBuilder().setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP).build()
            })
            mBillingClient.queryProductDetailsAsync(params.build()) { billingResult, list ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val flowParams: BillingFlowParams = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                            list.map {
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(it).build()
                            }
                        )
                        .build()
                    mBillingClient.launchBillingFlow(activity, flowParams)
                } else {
                    Timber.e(billingResult.debugMessage)
                }
            }
        } else {
            showToast("Sorry, donation services are not initialized yet", Toast.LENGTH_SHORT)
            Timber.e("Sorry, donation services are not initialized yet")
        }
    }

    fun isInitialized(): Boolean {
        return this.initialized;
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, list: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && list != null) {
            for (purchase in list) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams: AcknowledgePurchaseParams =
                            AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.purchaseToken)
                                .build()
                        mBillingClient.acknowledgePurchase(
                            acknowledgePurchaseParams
                        ) { innerResult ->
                            if (innerResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                showToast(
                                    "Thanks for your donation, highly appreciated!",
                                    Toast.LENGTH_SHORT
                                )
                            } else {
                                Timber.e(innerResult.debugMessage)
                                showToast(
                                    "Error while verifying your purchase, please contact the developer.",
                                    Toast.LENGTH_SHORT
                                )
                            }
                        }
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    showToast("Thanks for your donation, highly appreciated!", Toast.LENGTH_SHORT)
                } else if (purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                    showToast("Your donation could not be completed", Toast.LENGTH_SHORT)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            showToast("Donation cancelled", Toast.LENGTH_SHORT)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
            showToast(
                "Could not finalize donation due to error, please contact the developer.",
                Toast.LENGTH_SHORT
            )
        } else {
            showToast(
                "Could not finalize donation due to error, please contact the developer.",
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun showToast(message: String?, length: Int) {
        Toast.makeText(context, message, length).show()
    }

    companion object {
        private const val PRODUCT_ID_1 = "thank_you"
        private const val PRODUCT_ID_2 = "buy_a_coffee"
        private const val PRODUCT_ID_3 = "buy_a_coffee_and_cake"
        private val PRODUCTS = listOf(PRODUCT_ID_1, PRODUCT_ID_2, PRODUCT_ID_3)
    }
}