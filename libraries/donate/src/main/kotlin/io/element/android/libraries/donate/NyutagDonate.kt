/*
 * Нютаг (форк Element X) — просьба о донате: три расходуемые покупки Google Play
 * (`chat.nyutag.messenger.donate.small|medium|large`). Пользы в приложении не дают — это оплата серверов.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.donate

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import timber.log.Timber

private val PRODUCT_IDS = listOf(
    "chat.nyutag.messenger.donate.small",
    "chat.nyutag.messenger.donate.medium",
    "chat.nyutag.messenger.donate.large",
)

/** Состояние окна: товары из Play, покупка, «спасибо». Живёт, пока открыт диалог. */
class NyutagDonationStore(private val activity: Activity?) : PurchasesUpdatedListener {
    var products by mutableStateOf<List<ProductDetails>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var didThank by mutableStateOf(false)
        private set
    var hasError by mutableStateOf(false)
        private set

    private val client: BillingClient? = activity?.let { context ->
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    fun start() {
        val client = client ?: return fail("no activity")
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) return fail("setup ${result.debugMessage}")
                val params = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        PRODUCT_IDS.map {
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(it)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        }
                    )
                    .build()
                client.queryProductDetailsAsync(params) { queryResult, details ->
                    if (queryResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync fail("query ${queryResult.debugMessage}")
                    val list = details.productDetailsList
                        .sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros ?: Long.MAX_VALUE }
                    if (list.isEmpty()) return@queryProductDetailsAsync fail("no products")
                    products = list
                    isLoading = false
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun purchase(product: ProductDetails) {
        val activity = activity ?: return
        val client = client ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            didThank = true
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client?.consumeAsync(params) { _, _ -> }
        }
    }

    fun close() {
        client?.endConnection()
    }

    private fun fail(reason: String) {
        Timber.w("Nyutag donate: $reason")
        hasError = true
        isLoading = false
    }
}

/** Окно «Поддержать Нютаг»: кто мы, на что идут деньги, суммы из Google Play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NyutagDonateDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    LocalContext.current
    val store = remember { NyutagDonationStore(activity) }
    DisposableEffect(store) {
        store.start()
        onDispose { store.close() }
    }
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = ElementTheme.colors.bgCanvasDefault) {
            Column(modifier = Modifier.padding(24.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(CommonStrings.nyutag_donate_title),
                        style = ElementTheme.typography.fontHeadingMdBold,
                        color = ElementTheme.colors.textPrimary,
                    )
                    Text(
                        text = stringResource(if (store.didThank) CommonStrings.nyutag_donate_thanks else CommonStrings.nyutag_donate_body),
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (store.hasError && !store.didThank) {
                        Text(
                            text = stringResource(CommonStrings.nyutag_donate_unavailable),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                if (store.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp),
                    )
                } else if (!store.didThank) {
                    store.products.forEach { product ->
                        val price = product.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
                        Button(
                            text = "${product.name} · $price",
                            onClick = { store.purchase(product) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                    }
                }
                TextButton(
                    text = stringResource(if (store.didThank) CommonStrings.action_close else CommonStrings.action_not_now),
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}
