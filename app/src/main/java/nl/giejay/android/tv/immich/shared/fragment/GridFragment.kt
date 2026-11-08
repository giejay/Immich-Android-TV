/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package nl.giejay.android.tv.immich.shared.fragment


import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.ui.grid.ImmichCardGrid
import nl.giejay.android.tv.immich.ui.immichComposeView
import timber.log.Timber

/**
 * A page that renders [Card]s in a Compose grid.
 *
 * Hosted by [nl.giejay.android.tv.immich.home.HomeFragment] or pushed on the nav graph. The grid,
 * the optional title and the optional background artwork are plain views / Compose; no Leanback.
 */
open class GridFragment(val hideProgressBar: Boolean = false) : Fragment() {
    private var manuallySelectedPosition: Int = -1
    var progressBar: ProgressBar? = null
    private var titleView: TextView? = null
    private var backgroundView: ImageView? = null
    private var pendingBackground: Drawable? = null

    /** Optional heading shown above the grid (album / person name). */
    var title: CharSequence? = null
        set(value) {
            field = value
            titleView?.let {
                it.text = value
                it.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        }

    /** Cards shown in the grid. Mutate on the main thread only; replace items instead of editing them. */
    protected val cards = mutableStateListOf<Card>()

    /** Grid index the user last focused (or -1), used to restore focus after coming back. */
    protected var lastFocusedIndex: Int = -1
        private set

    private var focusIndexRequest by mutableStateOf<Int?>(null)

    protected open val columns: Int = 4
    protected open fun onCardClicked(index: Int, card: Card) {}
    protected open fun onCardFocused(index: Int, card: Card) {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.i("Loading ${this.javaClass.simpleName}")
        return inflater.inflate(R.layout.grid_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gridDock: ViewGroup = view.findViewById(R.id.browse_grid_dock)
        progressBar = gridDock.findViewById(R.id.browse_progressbar)
        titleView = view.findViewById<TextView>(R.id.grid_title).also { tv ->
            tv.text = title
            tv.visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        backgroundView = view.findViewById<ImageView>(R.id.grid_background).also { it.setImageDrawable(pendingBackground) }
        if (hideProgressBar) {
            progressBar?.visibility = View.GONE
        }
        gridDock.addView(
            immichComposeView(requireContext()) {
                ImmichCardGrid(
                    cards = cards,
                    columns = columns,
                    onCardClick = { index, card -> onCardClicked(index, card as Card) },
                    onCardFocused = { index, card ->
                        lastFocusedIndex = index
                        onCardFocused(index, card as Card)
                    },
                    focusIndexRequest = focusIndexRequest,
                    onFocusIndexRequestHandled = { focusIndexRequest = null },
                )
            },
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        // coming back to this page (e.g. from an album or the slider): focus the item the user left
        if (lastFocusedIndex > 0) {
            focusIndexRequest = lastFocusedIndex
        }
    }

    /** Sets (or clears with null) the artwork drawn dimmed behind the grid. */
    protected fun setBackgroundArtwork(drawable: Drawable?) {
        pendingBackground = drawable
        backgroundView?.setImageDrawable(drawable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.i("${this.javaClass.simpleName} got destroyed")
        progressBar = null
        titleView = null
        backgroundView = null
    }

    override fun onResume() {
        super.onResume()
        if (manuallySelectedPosition != -1) {
            updateManualPositionHandler(manuallySelectedPosition)
        }
    }

    protected open fun updateManualPositionHandler(selectedPosition: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            focusIndexRequest = selectedPosition
            manuallySelectedPosition = -1
        }, 100)
    }

    fun manualUpdatePosition(position: Int) {
        manuallySelectedPosition = position
    }
}
