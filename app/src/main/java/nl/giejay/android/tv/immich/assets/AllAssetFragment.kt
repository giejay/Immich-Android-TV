package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_MEDIA_COUNT

class AllAssetFragment : GenericAssetFragment() {
    private var jumpFromDate: LocalDateTime? = null
    private var jumpToDate: LocalDateTime? = null
    private var selectedMonth: YearMonth? = null
    private lateinit var backPressedCallback: OnBackPressedCallback
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault(Locale.Category.FORMAT))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(DateJumpDialogFragment.REQUEST_KEY, this) { _, result ->
            if (result.getBoolean(DateJumpDialogFragment.RESULT_CLEAR, false)) {
                clearDateFilter()
            } else {
                val year = result.getInt(DateJumpDialogFragment.RESULT_YEAR)
                val month = result.getInt(DateJumpDialogFragment.RESULT_MONTH)
                if (year > 0 && month > 0) {
                    applyDateFilter(YearMonth.of(year, month))
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                clearDateFilter()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.listAssets(page,
            pageCount,
            false,
            if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc",
            fromDate = jumpFromDate,
            endDate = jumpToDate,
            contentType = currentFilter)
    }

    override fun onScrollDownThresholdReached() {
        if (findNavController().currentDestination?.id == R.id.allAssetsFragment) {
            findNavController().navigate(R.id.action_allAssetsFragment_to_dateJumpDialogFragment)
        }
    }

    override fun clearState() {
        super.clearState()
    }

    override fun showMediaCount(): Boolean {
        return PreferenceManager.get(SLIDER_SHOW_MEDIA_COUNT)
    }

    private fun applyDateFilter(yearMonth: YearMonth) {
        selectedMonth = yearMonth
        jumpFromDate = yearMonth.atDay(1).atStartOfDay()
        jumpToDate = yearMonth.plusMonths(1).atDay(1).atStartOfDay()
        updateDateFilterIndicator()
        clearState()
        fetchInitialItems()
    }

    private fun clearDateFilter() {
        if (jumpFromDate == null && jumpToDate == null) {
            return
        }
        selectedMonth = null
        jumpFromDate = null
        jumpToDate = null
        updateDateFilterIndicator()
        clearState()
        fetchInitialItems()
    }

    private fun updateDateFilterIndicator() {
        val label = selectedMonth?.let {
            getString(R.string.date_jump_showing_month, it.format(monthFormatter))
        }
        setForcedDateLabel(label)
        backPressedCallback.isEnabled = label != null
    }
}