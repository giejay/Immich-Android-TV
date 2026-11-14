package nl.giejay.android.tv.immich.timeline

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Memory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelFactoryTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun create(apiClient: ApiClient, userId: String?): TimelineViewModel =
        TimelineViewModelFactory(apiClient, userId).create(TimelineViewModel::class.java)

    @Test
    fun `partner timeline loads that user's buckets`() = runTest {
        val apiClient = mock<ApiClient>()
        whenever(apiClient.getTimeBuckets("partner-id")).thenReturn(Either.Right(emptyList()))

        create(apiClient, "partner-id").loadBucketList()

        verify(apiClient).getTimeBuckets("partner-id")
    }

    @Test
    fun `partner timeline has no memories and never fetches them`() = runTest {
        val apiClient = mock<ApiClient>()
        val vm = create(apiClient, "partner-id")

        vm.loadMemories()

        assertTrue(vm.memoriesReady.value)
        assertEquals(emptyList<Memory>(), vm.memories.value)
        verify(apiClient, never()).getMemories()
    }

    @Test
    fun `own timeline loads own buckets and memories`() = runTest {
        val apiClient = mock<ApiClient>()
        whenever(apiClient.getTimeBuckets(null)).thenReturn(Either.Right(emptyList()))
        whenever(apiClient.getMemories()).thenReturn(Either.Right(emptyList()))
        val vm = create(apiClient, null)

        vm.loadBucketList()
        vm.loadMemories()

        verify(apiClient).getTimeBuckets(null)
        verify(apiClient).getMemories()
    }
}
