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
package nl.giejay.android.tv.immich.castconnect

import android.app.Application
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.tv.media.MediaLoadCommandCallback
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import nl.giejay.android.tv.immich.api.model.Album
import timber.log.Timber
import java.util.Date

/**
 * MediaLoadCommandCallback.onLoad() is invoked when the MediaManager detects the intent is a
 * load request, this method receives the load request's data and converts it to a video object.
 * Once converted, the video is played by the local player. The MediaManager is then updated
 * with the MediaLoadRequest and broadcasts the MediaStatus to the connected senders.
 *
 * The load request's data comes from the sender app, such as the mobile or web complimentary apps
 * and thus the data contract should be already defined most likely would be sharing the same data
 * catalogue. The load request data is a media info object and as long as that object is populated
 * with the right fields, this receiver part would remain the same. What varies is the actual data
 * values in the MediaInfo fields.
 */
class CastMediaLoadCommandCallback(
    var onLoaded: (Album, MediaLoadRequestData) -> Unit,
    private val application: Application
) :
    MediaLoadCommandCallback() {

    override fun onLoad(
        senderId: String?,
        mediaLoadRequestData: MediaLoadRequestData
    ): Task<MediaLoadRequestData> {
        return Tasks.call {
//            var videoToPlay = Album("", "sdf", "sdf", "", Date(), Date())
//            if (videoToPlay != null) {
//                onLoaded(videoToPlay, mediaLoadRequestData)
//            } else {
//                Timber.w("Failed to convert cast load request to application-specific video")
//            }

            mediaLoadRequestData
        }
    }

}
