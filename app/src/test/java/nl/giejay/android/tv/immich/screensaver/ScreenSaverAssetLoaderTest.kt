package nl.giejay.android.tv.immich.screensaver

import android.content.Context
import android.content.SharedPreferences
import arrow.core.Either
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.Pref
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.android.tv.immich.slider.FavoriteService
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.plugin.MediaRemoteControlsKeyEventPlugin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ScreenSaverAssetLoaderTest {
    private val mainDispatcher = StandardTestDispatcher()
    private val preferenceValues = mutableMapOf<String, Any?>()

    private lateinit var appContext: Context
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)

        appContext = mock()
        whenever(appContext.getString(any())).thenAnswer { "res-${it.getArgument<Int>(0)}" }
        ImmichApplication.appContext = appContext

        sharedPreferences = mock()
        whenever(sharedPreferences.getString(any(), anyOrNull())).thenAnswer {
            preferenceValues[it.getArgument<String>(0)] as? String ?: it.getArgument(1)
        }
        whenever(sharedPreferences.getBoolean(any(), any())).thenAnswer {
            preferenceValues[it.getArgument<String>(0)] as? Boolean ?: it.getArgument(1)
        }
        whenever(sharedPreferences.getInt(any(), any())).thenAnswer {
            preferenceValues[it.getArgument<String>(0)] as? Int ?: it.getArgument(1)
        }
        whenever(sharedPreferences.getStringSet(any(), anyOrNull())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            (preferenceValues[it.getArgument<String>(0)] as? Set<String> ?: it.getArgument(1)) as Set<String>?
        }
        whenever(sharedPreferences.contains(any())).thenAnswer {
            preferenceValues.containsKey(it.getArgument<String>(0))
        }
        whenever(sharedPreferences.all).thenAnswer {
            preferenceValues.filterValues { it != null }
        }

        val sharedPreferenceField = PreferenceManager.javaClass.getDeclaredField("sharedPreference")
        sharedPreferenceField.isAccessible = true
        sharedPreferenceField.set(PreferenceManager, sharedPreferences)

        @Suppress("UNCHECKED_CAST")
        val liveContext = PreferenceManager.javaClass.getDeclaredField("liveContext").apply {
            isAccessible = true
        }.get(PreferenceManager) as MutableMap<String, Any?>
        liveContext.clear()
        preferenceValues.clear()
    }

    @After
    fun tearDown() {
        @Suppress("UNCHECKED_CAST")
        val liveContext = PreferenceManager.javaClass.getDeclaredField("liveContext").apply {
            isAccessible = true
        }.get(PreferenceManager) as MutableMap<String, Any?>
        liveContext.clear()
        preferenceValues.clear()
        ImmichApplication.appContext = null
        Dispatchers.resetMain()
    }

    @Test
    fun `start falls back to full album fetch when random album search returns no assets`() = runTest {
        setPreference(HOST_NAME, "https://example.com")
        setPreference(API_KEY, "api-key")
        setPreference(SCREENSAVER_TYPE, ScreenSaverType.ALBUMS)
        setPreference(SCREENSAVER_ALBUMS, setOf("shared-album"))
        setPreference(EXCLUDE_ASSETS_IN_ALBUM, emptySet())

        val asset = asset("shared-asset")
        val apiClient = mock<ApiClient>()
        whenever(
            apiClient.listAssets(
                page = any(),
                pageCount = any(),
                random = eq(true),
                order = any(),
                personIds = any(),
                fromDate = anyOrNull(),
                endDate = anyOrNull(),
                contentType = eq(ContentType.IMAGE),
                albumIds = eq(listOf("shared-album"))
            )
        ).thenReturn(Either.Right(AssetResponse(emptyList(), true)))
        whenever(
            apiClient.listAssetsFromAlbum(
                albumIds = eq(listOf("shared-album")),
                contentType = eq(ContentType.IMAGE),
                pageCount = eq(1000)
            )
        ).thenReturn(Either.Right(AssetResponse(listOf(asset), false)))

        val host = mock<ScreenSaverAssetLoader.Host>()
        val favoriteService = mock<FavoriteService>()
        val loader = ScreenSaverAssetLoader(
            scope = this,
            apiClient = apiClient,
            host = host,
            favoriteService = favoriteService
        )

        mockConstruction(MediaRemoteControlsKeyEventPlugin::class.java).use {
            loader.start().join()
            mainDispatcher.scheduler.advanceUntilIdle()

            verify(apiClient, times(1)).listAssets(
                eq(1),
                eq(50),
                eq(true),
                any(),
                any(),
                isNull(),
                isNull(),
                eq(ContentType.IMAGE),
                eq(listOf("shared-album"))
            )
            verify(apiClient, times(1)).listAssetsFromAlbum(
                eq(listOf("shared-album")),
                eq(ContentType.IMAGE),
                eq(1000)
            )

            val configurationCaptor = argumentCaptor<MediaSliderConfiguration>()
            verify(host).onScreenSaverConfigurationReady(configurationCaptor.capture())
            verify(host, never()).showScreenSaverMessage(any(), any())
            verify(host, never()).exitScreenSaver()

            val actual = configurationCaptor.firstValue
            val expected = buildScreenSaverConfiguration(listOf(asset), actual.loadMore, this@runTest, favoriteService)

            assertThat(actual.startPosition).isEqualTo(expected.startPosition)
            assertThat(actual.interval).isEqualTo(expected.interval)
            assertThat(actual.isOnlyUseThumbnails).isEqualTo(expected.isOnlyUseThumbnails)
            assertThat(actual.isVideoSoundEnable).isEqualTo(expected.isVideoSoundEnable)
            assertThat(actual.animationSpeedMillis).isEqualTo(expected.animationSpeedMillis)
            assertThat(actual.maxCutOffHeight).isEqualTo(expected.maxCutOffHeight)
            assertThat(actual.maxCutOffWidth).isEqualTo(expected.maxCutOffWidth)
            assertThat(actual.glideTransformation).isEqualTo(expected.glideTransformation)
            assertThat(actual.enableSlideAnimation).isEqualTo(expected.enableSlideAnimation)
            assertThat(actual.gradiantOverlay).isEqualTo(expected.gradiantOverlay)
            assertThat(actual.metaDataConfig).containsExactlyElementsIn(expected.metaDataConfig)
            assertThat(actual.zoomAndScrollPanorama).isEqualTo(expected.zoomAndScrollPanorama)
            assertThat(actual.zoomEffectPercent).isEqualTo(expected.zoomEffectPercent)
            assertThat(actual.panEffectPercent).isEqualTo(expected.panEffectPercent)
            assertThat(actual.useLargeVideoBuffer).isEqualTo(expected.useLargeVideoBuffer)
            assertThat(actual.dpadSeeksInVideo).isEqualTo(expected.dpadSeeksInVideo)
            assertThat(actual.items).containsExactlyElementsIn(expected.items)
            assertThat(actual.items.single().ids()).containsExactly(asset.id)
            assertThat(actual.loadMore).isNull()
            assertThat(actual.controllerPlugins).hasSize(expected.controllerPlugins.size)
            assertThat(actual.viewPlugins).hasSize(expected.viewPlugins.size)
            assertThat(actual.keyEventPlugins).hasSize(expected.keyEventPlugins.size)
        }
    }

    private fun <T> setPreference(pref: Pref<T, *, *>, value: T) {
        @Suppress("UNCHECKED_CAST")
        val liveContext = PreferenceManager.javaClass.getDeclaredField("liveContext").apply {
            isAccessible = true
        }.get(PreferenceManager) as MutableMap<String, Any?>
        liveContext[pref.key()] = value
        preferenceValues[pref.key()] = pref.toPrefValue(value as Any) ?: value
    }

    private fun asset(id: String) = Asset(
        id = id,
        type = "IMAGE",
        deviceAssetId = null,
        exifInfo = null,
        fileModifiedAt = null,
        albumName = null,
        people = null,
        tags = null,
        originalPath = null,
        originalFileName = null
    )
}
