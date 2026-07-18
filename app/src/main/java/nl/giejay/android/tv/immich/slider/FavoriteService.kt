package nl.giejay.android.tv.immich.slider

import android.widget.Toast
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber

class FavoriteService(
    private val updateFavorite: suspend (String, Boolean) -> Either<String, Unit> = { assetId, isFavorite ->
        ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.hostName,
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        ).updateFavorite(assetId, isFavorite).map { }
    },
    private val showToast: (Int) -> Unit = { toastRes ->
        Toast.makeText(ImmichApplication.appContext, toastRes, Toast.LENGTH_SHORT).show()
    },
    private val logInfo: (String) -> Unit = { message -> Timber.i(message) },
    private val logError: (String) -> Unit = { message -> Timber.e(message) }
) {
    suspend fun toggleFavorite(assetId: String, isFavorite: Boolean) {
        updateFavorite(assetId, isFavorite).fold(
            ifLeft = { error ->
                withContext(Dispatchers.Main) {
                    logError("Failed to toggle favorite for asset $assetId: $error")
                    showToast(if (isFavorite) R.string.favorite_add_failed else R.string.favorite_remove_failed)
                }
            },
            ifRight = {
                withContext(Dispatchers.Main) {
                    logInfo("Successfully toggled favorite for asset $assetId to $isFavorite")
                    showToast(if (isFavorite) R.string.favorite_added else R.string.favorite_removed)
                }
            }
        )
    }
}
