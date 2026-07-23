package nl.giejay.android.tv.immich.shared.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.timeline.TimelineScrubberView
import nl.giejay.android.tv.immich.timeline.TimelineViewModel
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * A vertical grid fragment that includes a chronological scrubber (year/month).
 */
abstract class TimelinedVerticalCardGridFragment<ITEM> : VerticalCardGridFragment<ITEM>() {

    protected var initialJumpDate: LocalDateTime? = null
    protected var scrubberView: TimelineScrubberView? = null

    abstract fun getItemDate(it: ITEM): OffsetDateTime?

    open suspend fun fetchBuckets(apiClient: ApiClient): Either<String, List<TimeBucketSummary>> {
        return Either.Right(emptyList())
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        injectScrubberIntoRail()
        scrubberView?.onCommit = { monthKey, exitToMosaic ->
            commitJumpFromScrubber(monthKey, exitToMosaic)
        }
        fetchInitialBuckets()
    }

    private fun injectScrubberIntoRail() {
        val container = railContainer ?: return
        scrubberView = container.findViewById(R.id.rail_scrubber)
        scrubberView?.apply {
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = true
            elevation = 32f
            nextFocusUpId = R.id.rail_settings_button
        }
    }

    private fun fetchInitialBuckets() {
        ioScope.launch {
            fetchBuckets(apiClient).fold(
                { 
                    Timber.e("Failed to fetch buckets: $it")
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "Failed to fetch timeline: $it", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                { list ->
                    withContext(Dispatchers.Main) {
                        Timber.d("Fetched ${list.size} buckets for fragment ${this@TimelinedVerticalCardGridFragment.javaClass.simpleName}")
                        if (list.isNotEmpty()) {
                            scrubberView?.visibility = View.VISIBLE
                            scrubberView?.setBuckets(list)
                        } else {
                            scrubberView?.visibility = View.GONE
                        }
                    }
                }
            )
        }
    }

    override fun onExitRightFromGrid() {
        if (currentSelectedIndex < COLUMNS) {
            super.onExitRightFromGrid()
        } else if (scrubberView?.visibility == View.VISIBLE) {
            val date = assets.getOrNull(currentSelectedIndex)?.let { getItemDate(it) }
            val monthKey = date?.let { TimelineViewModel.monthBucketKey(it.toLocalDate()) }
            scrubberView?.post {
                scrubberView?.prepareFocusForMonth(monthKey)
                scrubberView?.requestFocus()
            }
        } else {
            super.onExitRightFromGrid()
        }
    }

    override fun updateOnSelected(position: Int) {
        super.updateOnSelected(position)
        updateScrubberFromPosition(position)
    }

    private fun updateScrubberFromPosition(position: Int) {
        if (scrubberView?.visibility != View.VISIBLE || scrubberView?.hasFocus() == true) return
        val date = assets.getOrNull(position)?.let { getItemDate(it) } ?: return
        scrubberView?.setIndicatorMonthKey(TimelineViewModel.monthBucketKey(date.toLocalDate()))
    }

    private fun commitJumpFromScrubber(monthKey: String, exitToMosaic: Boolean) {
        val targetDate = LocalDate.parse(monthKey.take(10))
        
        // Search in loaded assets
        val position = assets.indexOfFirst { 
            getItemDate(it)?.toLocalDate()?.withDayOfMonth(1) == targetDate.withDayOfMonth(1)
        }

        if (position >= 0) {
            manualUpdatePosition(position)
            if (exitToMosaic) {
                view?.findViewById<View>(R.id.grid_frame)?.requestFocus()
            }
        } else {
            // Jump by reloading with filter
            initialJumpDate = targetDate.atStartOfDay()
            clearState()
            progressBar?.visibility = View.VISIBLE
            fetchInitialItems()
            if (exitToMosaic) {
                view?.findViewById<View>(R.id.grid_frame)?.requestFocus()
            }
        }
    }
}
