/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.giejay.android.tv.immich.shared.donate

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import timber.log.Timber
import java.lang.IllegalStateException

/**
 * F-Droid flavor stub: Google Play Billing is not available.
 * Displays a message directing users to donate via the GitHub page.
 */
class DonateFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val icon: Drawable =
            requireContext().getDrawable(nl.giejay.android.tv.immich.R.drawable.icon)!!
        return GuidanceStylist.Guidance(
            getString(R.string.donation_title),
            getString(R.string.donation_fdroid_subtitle),
            "",
            icon
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        Toast.makeText(
            ImmichApplication.appContext,
            getString(R.string.donation_fdroid_not_available),
            Toast.LENGTH_LONG
        ).show()
        finalizeFragment()
    }

    private fun finalizeFragment() {
        try {
            findNavController().popBackStack()
        } catch (e: IllegalStateException) {
            Timber.e(e, "Could not close Donate fragment")
        }
    }
}
