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

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import nl.giejay.android.tv.immich.homescreenchannels.HomeScreenChannelManager
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.USER_ID
import timber.log.Timber
import java.util.UUID


/**
 * Initializes libraries, such as Timber, and sets up application wide settings.
 */
class ImmichApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // AppContextInitializer sets this first (before any other provider);
        // kept as a cheap no-op for direct Application construction.
        appContext = this
        PreferenceManager.init(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        var userId = PreferenceManager.get(USER_ID)
        if(userId.isBlank()){
            userId = UUID.randomUUID().toString()
            PreferenceManager.save(USER_ID, userId)
        }

        HomeScreenChannelManager.initSubscriptions()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                HomeScreenChannelManager.scheduleRefresh("app_foreground")
            }
        })
    }

    companion object {
        var appContext: Context? = null
    }

    /** Release builds only log warnings and errors to logcat; no crash reporting backend. */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || (priority == Log.DEBUG && !PreferenceManager.get(DEBUG_MODE))) {
                return
            }
            Log.println(priority, tag ?: "ImmichTV", if (t != null) "$message\n${Log.getStackTraceString(t)}" else message)
        }
    }
}
