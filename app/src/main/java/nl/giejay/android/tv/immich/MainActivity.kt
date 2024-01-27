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
package nl.giejay.android.tv.immich

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import com.zeuskartik.mediaslider.MediaSliderConfiguration
import com.zeuskartik.mediaslider.SliderItem
import nl.giejay.android.tv.immich.api.ApiUtil
import nl.giejay.android.tv.immich.castconnect.CastHelper
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.db.LocalStorage
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber

/**
 * FragmentActivity that displays the various fragments
 */
class MainActivity : FragmentActivity() {

    private lateinit var navGraph: NavGraph
    private lateinit var navController: NavController
    private lateinit var castHelper: CastHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("Booting main activity")

        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        castHelper = CastHelper(
            { album ->
                LocalStorage.mediaSliderItems = listOf(
                    SliderItem(
                        ApiUtil.getFileUrl("48509ffc-b1fc-49cd-b82e-bcb660ca73f0"),
                        "image",
                        "description"
                    )
                )
                navController.graph = navGraph
                navController.navigate(
                    HomeFragmentDirections.actionSlider(
                        MediaSliderConfiguration(
                            false,
                            false,
                            false,
                            album.albumName,
                            "",
                            "",
                            0,
                            PreferenceManager.sliderInterval()
                        ), album.id
                    )
                )
            },
            application
        )
        if (castHelper.validateAndProcessCastIntent(intent)) {
            return
        }

        loadDeepLinkOrStartingPage(intent.data)
    }

    /**
     * Checks if the app was started with a deep link, loading it if it was
     *
     * If not (or the deep link is invalid), it triggers the normal starting process
     */
    private fun loadDeepLinkOrStartingPage(
        uri: Uri?
    ) {
        if (uri == null) {
            loadStartingPage()
            return
        }
    }

    /**
     * Chooses whether to show the browse screen or the "no Firebase" notice
     */
    private fun loadStartingPage() {
        if (!PreferenceManager.isLoggedId()) {
            Timber.i("Start page is authentication")
            navGraph.setStartDestination(R.id.authFragment)
        } else {
            Timber.i("Start page is home")
            navGraph.setStartDestination(R.id.homeFragment)
        }

        // Set the graph to trigger loading the start destination
        navController.graph = navGraph
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("Main activity got destroyed")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent == null) {
            return
        }

        // Return early if intent is a valid Cast intent and is processed by the Cast SDK.
        if (castHelper.validateAndProcessCastIntent(intent)) {
            return
        }

        // Include logic to process other types of intents here, if any.
    }

//    private fun loadPlaybackFragment(video: Video) {
//        // Set the default graph and go to playback for the loaded Video
//        navController.graph = navGraph
//        navController.navigate(
//            BrowseFragmentDirections.actionBrowseFragmentToPlaybackFragment(
//                video
//            )
//        )
//    }
}
