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

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrandedSupportFragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.transition.TransitionHelper
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnChildLaidOutListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.VerticalGridPresenter
import nl.giejay.android.tv.immich.R
import timber.log.Timber

/**
 * A fragment for rendering items in a vertical grids.
 */
open class GridFragment : BrandedSupportFragment(), BrowseSupportFragment.MainFragmentAdapterProvider {
    private var mAdapter: ArrayObjectAdapter = ArrayObjectAdapter()
    private var mGridPresenter: VerticalGridPresenter? = null
    private var mGridViewHolder: VerticalGridPresenter.ViewHolder? = null
    private var mOnItemViewSelectedListener: OnItemViewSelectedListener? = null
    private var mOnItemViewClickedListener: OnItemViewClickedListener? = null
    private var mSceneAfterEntranceTransition: Any? = null
    var progressBar: ProgressBar? = null
    private var mSelectedPosition = -1
    private val mMainFragmentAdapter: BrowseSupportFragment.MainFragmentAdapter<Fragment> =
        object : BrowseSupportFragment.MainFragmentAdapter<Fragment>(this) {
            override fun setEntranceTransitionState(state: Boolean) {
                this@GridFragment.setEntranceTransitionState(state)
            }
        }
    var gridPresenter: VerticalGridPresenter?
        /**
         * Returns the grid presenter.
         */
        get() = mGridPresenter
        /**
         * Sets the grid presenter.
         */
        set(gridPresenter) {
            requireNotNull(gridPresenter) { "Grid presenter may not be null" }
            mGridPresenter = gridPresenter
            mGridPresenter!!.onItemViewSelectedListener = mViewSelectedListener
            if (mOnItemViewClickedListener != null) {
                mGridPresenter!!.onItemViewClickedListener = mOnItemViewClickedListener
            }
        }
    var adapter: ArrayObjectAdapter
        /**
         * Returns the object adapter.
         */
        get() = mAdapter
        /**
         * Sets the object adapter for the fragment.
         */
        set(adapter) {
            mAdapter = adapter
            updateAdapter()
        }
    private val mViewSelectedListener: OnItemViewSelectedListener =
        OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            val position: Int = mGridViewHolder!!.gridView.selectedPosition
            gridOnItemSelected(position)
            if (mOnItemViewSelectedListener != null) {
                mOnItemViewSelectedListener!!.onItemSelected(
                    itemViewHolder, item,
                    rowViewHolder, row
                )
            }
        }
    private val mChildLaidOutListener: OnChildLaidOutListener =
        OnChildLaidOutListener { _, _, position, _ ->
            if (position == 0) {
                showOrHideTitle()
            }
        }

    /**
     * Sets an item selection listener.
     */
    fun setOnItemViewSelectedListener(listener: OnItemViewSelectedListener?) {
        mOnItemViewSelectedListener = listener
    }

    private fun gridOnItemSelected(position: Int) {
        if (position != mSelectedPosition) {
            mSelectedPosition = position
            showOrHideTitle()
        }
    }

    private fun showOrHideTitle() {
        if (mGridViewHolder!!.gridView.findViewHolderForAdapterPosition(mSelectedPosition)
            == null
        ) {
            return
        }
        if (!mGridViewHolder!!.gridView.hasPreviousViewInSameRow(mSelectedPosition)) {
            showTitle(true)
            mMainFragmentAdapter.fragmentHost?.showTitleView(true)
        } else {
            showTitle(false)
            mMainFragmentAdapter.fragmentHost?.showTitleView(false)
        }
    }

    var onItemViewClickedListener: OnItemViewClickedListener?
        /**
         * Returns the item clicked listener.
         */
        get() = mOnItemViewClickedListener
        /**
         * Sets an item clicked listener.
         */
        set(listener) {
            mOnItemViewClickedListener = listener
            if (mGridPresenter != null) {
                mGridPresenter!!.onItemViewClickedListener = mOnItemViewClickedListener
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Timber.i("Loading ${this.javaClass.simpleName}")
        return inflater.inflate(R.layout.grid_fragment, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gridDock: ViewGroup = view.findViewById(R.id.browse_grid_dock)
        progressBar = gridDock.findViewById(R.id.browse_progressbar)
        installTitleView(LayoutInflater.from(requireContext()), gridDock, savedInstanceState)
        mGridViewHolder = mGridPresenter?.onCreateViewHolder(gridDock)
        mGridViewHolder?.view?.apply { gridDock.addView(this) }
        mGridViewHolder?.gridView?.setOnChildLaidOutListener(mChildLaidOutListener)
        mSceneAfterEntranceTransition =
            TransitionHelper.createScene(gridDock) { setEntranceTransitionState(true) }
        mainFragmentAdapter.fragmentHost?.notifyViewCreated(mMainFragmentAdapter)
        updateAdapter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.i("${this.javaClass.simpleName} got destroyed")
        mGridViewHolder = null
    }

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> {
        return mMainFragmentAdapter
    }

    /**
     * Sets the selected item position.
     */
    fun setSelectedPosition(position: Int) {
        mSelectedPosition = position
        if (mGridViewHolder != null && mGridViewHolder!!.gridView.adapter != null) {
            mGridViewHolder!!.gridView.setSelectedPositionSmooth(position)
        }
    }

    private fun updateAdapter() {
        if (mGridViewHolder != null) {
            mGridPresenter?.onBindViewHolder(mGridViewHolder!!, mAdapter)
            if (mSelectedPosition != -1) {
                mGridViewHolder?.gridView?.selectedPosition = mSelectedPosition
            }
        }
    }

    fun setEntranceTransitionState(afterTransition: Boolean) {
        mGridPresenter?.setEntranceTransitionState(mGridViewHolder!!, afterTransition)
    }
}