/*
 * Copyright (C) 2014 The Android Open Source Project
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
package androidx.leanback.widget

import android.graphics.Paint
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RestrictTo
import androidx.leanback.R

/**
 * RowHeaderPresenter provides a default presentation for [HeaderItem] using a
 * [RowHeaderView] and optionally a TextView for description. If a subclass creates its own
 * view, the subclass must also override [.onCreateViewHolder],
 * [.onSelectLevelChanged].
 */
class CustomRowHeaderPresenter
/**
 */ @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX) constructor(private val mLayoutResourceId: Int,
                                                                   private val mAnimateSelect: Boolean) : RowHeaderPresenter() {
    private val mFontMeasurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    /**
     * Returns true if the view visibility is set to [View.GONE] when bound to null.
     */
    /**
     * Optionally sets the view visibility to [View.GONE] when bound to null.
     */

    /**
     * Creates default RowHeaderPresenter using a title view and a description view.
     * @see ViewHolder.ViewHolder
     */
    constructor() : this(R.layout.lb_row_header)

    /**
     */
    constructor(layoutResourceId: Int) : this(layoutResourceId, true)

    /**
     * A ViewHolder for the RowHeaderPresenter.
     */
    class ViewHolder : Presenter.ViewHolder {
        var selectLevel: Float = 0f
        var mOriginalTextColor: Int = 0
        var mUnselectAlpha: Float = 0f
        var mTitleView: RowHeaderView?
        var mDescriptionView: TextView? = null

        /**
         * Creating a new ViewHolder that supports title and description.
         * @param view Root of Views.
         */
        constructor(view: View) : super(view) {
            mTitleView = view.findViewById<View>(R.id.row_header) as RowHeaderView
            mDescriptionView = view.findViewById<View>(R.id.row_header_description) as TextView
            initColors()
        }

        /**
         * Uses a single [RowHeaderView] for creating a new ViewHolder.
         * @param view The single RowHeaderView.
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
        constructor(view: RowHeaderView) : super(view) {
            mTitleView = view
            initColors()
        }

        fun initColors() {
            if (mTitleView != null) {
                mOriginalTextColor = mTitleView!!.currentTextColor
            }

            mUnselectAlpha = view.resources.getFraction(
                R.fraction.lb_browse_header_unselect_alpha, 1, 1)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val root = LayoutInflater.from(parent.context)
            .inflate(mLayoutResourceId, parent, false)

        val viewHolder = ViewHolder(root)
        if (mAnimateSelect) {
            setSelectLevel(viewHolder, 0f)
        }
        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val headerItem = if (item == null) null else (item as Row).headerItem
        val vh = viewHolder as ViewHolder
        if (headerItem == null) {
            if (vh.mTitleView != null) {
                vh.mTitleView!!.text = null
            }
            if (vh.mDescriptionView != null) {
                vh.mDescriptionView!!.text = null
            }

            viewHolder.view.contentDescription = null
            if (isNullItemVisibilityGone) {
                viewHolder.view.visibility = View.GONE
            }
        } else {
            if (vh.mTitleView != null) {
                vh.mTitleView!!.text = headerItem.name + "tejstl"
            }
            if (vh.mDescriptionView != null) {
                if (TextUtils.isEmpty(headerItem.description)) {
                    vh.mDescriptionView!!.visibility = View.GONE
                } else {
                    vh.mDescriptionView!!.visibility = View.VISIBLE
                }
                vh.mDescriptionView!!.text = headerItem.description
            }
            viewHolder.view.contentDescription = headerItem.contentDescription
            viewHolder.view.visibility = View.VISIBLE
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val vh = viewHolder as ViewHolder
        if (vh.mTitleView != null) {
            vh.mTitleView!!.text = null
        }
        if (vh.mDescriptionView != null) {
            vh.mDescriptionView!!.text = null
        }

        if (mAnimateSelect) {
            setSelectLevel(viewHolder, 0f)
        }
    }

    /**
     * Sets the select level.
     */
    fun setSelectLevel(holder: ViewHolder, selectLevel: Float) {
        holder.selectLevel = selectLevel
        onSelectLevelChanged(holder)
    }

    /**
     * Called when the select level changes.  The default implementation sets the alpha on the view.
     */
    protected fun onSelectLevelChanged(holder: ViewHolder) {
        if (mAnimateSelect) {
            holder.view.alpha = (holder.mUnselectAlpha + holder.selectLevel
                    * (1f - holder.mUnselectAlpha))
        }
    }

    /**
     * Returns the space (distance in pixels) below the baseline of the
     * text view, if one exists; otherwise, returns 0.
     */
    fun getSpaceUnderBaseline(holder: ViewHolder): Int {
        var space = holder.view.paddingBottom
        if (holder.view is TextView) {
            space += getFontDescent(holder.view as TextView, mFontMeasurePaint).toInt()
        }
        return space
    }

    companion object {
        protected fun getFontDescent(
            textView: TextView,
            fontMeasurePaint: Paint
        ): Float {
            if (fontMeasurePaint.textSize != textView.textSize) {
                fontMeasurePaint.textSize = textView.textSize
            }
            if (fontMeasurePaint.typeface !== textView.typeface) {
                fontMeasurePaint.setTypeface(textView.typeface)
            }
            return fontMeasurePaint.descent()
        }
    }
}