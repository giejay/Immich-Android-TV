package nl.giejay.android.tv.immich.shared.donate

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.navigation.fragment.findNavController
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.shared.guidedstep.GuidedStepUtil.addCheckedAction


class DonateFragment : GuidedStepSupportFragment() {
    private lateinit var donateService: DonateService
    private var products: List<ProductDetails> = emptyList()
    private val mainScope = CoroutineScope(Job() + Dispatchers.Main)

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable =
            requireContext().getDrawable(nl.giejay.android.tv.immich.R.drawable.icon)!!
        return GuidanceStylist.Guidance(
            "Donation",
            "Developing this app takes a lot of effort, if you would like to show your appreciation, you can donate using the following options.",
            "",
            icon
        )
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        donateService = DonateService(activity)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        donateService.setupBilling {
            if (it) {
                donateService.getProducts { products ->
                    if (products.isEmpty()) {
                        showToastAndFinish("You have exhausted your donate options, thanks!")
                        return@getProducts
                    }
                    this.products = products
                    this.products
                        .filter { prod -> prod.oneTimePurchaseOfferDetails?.formattedPrice != null }
                        .sortedBy { prod -> prod.oneTimePurchaseOfferDetails?.priceAmountMicros }
                        .forEachIndexed { index, product ->
                            addCheckedAction(
                                actions,
                                index.toLong(),
                                product.name + " - " + product.oneTimePurchaseOfferDetails!!.formattedPrice,
                                product.description,
                                false,
                                1
                            )
                        }
                    setActions(actions)
                }
            } else {
                showToastAndFinish("Could not initialize donation service, sorry!")
            }
        }
    }

    private fun showToastAndFinish(message: String) {
        mainScope.launch {
            Toast.makeText(
                ImmichApplication.appContext,
                message,
                Toast.LENGTH_SHORT
            ).show()
            finalizeFragment()
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        donateService.launchBilling(
            requireActivity(),
            products.find { it.description == action.description }!!
        )
        finalizeFragment()
    }

    private fun finalizeFragment() {
        findNavController().popBackStack()
    }
}