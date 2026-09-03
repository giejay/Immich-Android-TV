package nl.giejay.android.tv.immich.homescreenchannels

import com.google.gson.Gson
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber

private const val PREF_KEY = "home_screen_channel_registry"
private const val MAX_EXCLUDED_ASSET_IDS_PER_CHANNEL = 200

/**
 * Local bookkeeping the TvProvider doesn't give us for free: which asset ids the user explicitly
 * removed a card for, per channel (keyed by that channel's internalProviderId - see
 * [HomeScreenChannelManager]), so a later sync doesn't reinsert them, per the platform's
 * ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED contract. Channel identity itself is NOT tracked
 * here - the TvProvider (queried by internalProviderId) is the single source of truth for that,
 * so a killed process or a stale/restored registry can never orphan or duplicate a channel.
 */
data class ChannelRegistry(
    // channel internalProviderId -> excluded asset ids, ordered oldest-first so eviction is a plain takeLast
    val excludedAssetIdsByChannel: Map<String, List<String>> = emptyMap()
)

object ChannelRegistryStore {
    private val gson = Gson()

    @Synchronized
    fun load(): ChannelRegistry {
        val json = PreferenceManager.sharedPreference.getString(PREF_KEY, null) ?: return ChannelRegistry()
        return try {
            gson.fromJson(json, ChannelRegistry::class.java) ?: ChannelRegistry()
        } catch (e: Exception) {
            Timber.w(e, "Could not parse home screen channel registry, resetting it")
            ChannelRegistry()
        }
    }

    @Synchronized
    private fun save(registry: ChannelRegistry) {
        PreferenceManager.sharedPreference.edit().putString(PREF_KEY, gson.toJson(registry)).apply()
    }

    @Synchronized
    private fun update(mutate: (ChannelRegistry) -> ChannelRegistry) {
        save(mutate(load()))
    }

    fun excludedAssetIds(channelInternalId: String): List<String> =
        load().excludedAssetIdsByChannel[channelInternalId] ?: emptyList()

    fun addExcludedAssetId(channelInternalId: String, assetId: String) {
        update { registry ->
            val current = registry.excludedAssetIdsByChannel[channelInternalId] ?: emptyList()
            val updated = (current + assetId).distinct().takeLast(MAX_EXCLUDED_ASSET_IDS_PER_CHANNEL)
            registry.copy(excludedAssetIdsByChannel = registry.excludedAssetIdsByChannel + (channelInternalId to updated))
        }
    }
}
