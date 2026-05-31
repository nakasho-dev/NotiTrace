package org.ukky.notitrace.ui.screen.home

import app.cash.turbine.test
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.repository.AppTagRepository
import org.ukky.notitrace.data.repository.NotificationRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var notificationRepo: NotificationRepository
    private lateinit var tagRepo: AppTagRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        notificationRepo = mockk(relaxed = true)
        tagRepo = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初期状態ではフィルタなしで全通知を取得する`() = runTest {
        val items = listOf(
            listItem(id = 1L, title = "通知A", receivedAt = 2_000L, tag = "SNS", appLabel = "App"),
            listItem(id = 2L, title = "通知B", receivedAt = 1_000L),
        )
        every { notificationRepo.getAllListItems() } returns flowOf(PagingData.from(items))
        every { notificationRepo.getListItemsByTag(any()) } returns flowOf(PagingData.from(emptyList()))
        every { tagRepo.getAllTags() } returns flowOf(listOf("SNS"))

        val vm = HomeViewModel(notificationRepo, tagRepo)

        vm.uiState.test {
            awaitItem()
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertNull(state.selectedTag)
            assertEquals(listOf("SNS"), state.availableTags)
            cancelAndIgnoreRemainingEvents()
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `タグフィルタを切り替えると対応する通知だけ表示される`() = runTest {
        every { notificationRepo.getAllListItems() } returns flowOf(PagingData.from(emptyList()))
        every { notificationRepo.getListItemsByTag("仕事") } returns flowOf(
            PagingData.from(
                listOf(
                    listItem(id = 3L, title = "Slack通知", receivedAt = 3_000L, tag = "仕事", appLabel = "Slack"),
                )
            )
        )
        every { tagRepo.getAllTags() } returns flowOf(listOf("仕事", "SNS"))

        val vm = HomeViewModel(notificationRepo, tagRepo)

        vm.uiState.test {
            awaitItem()
            vm.selectTag("仕事")
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("仕事", state.selectedTag)
            cancelAndIgnoreRemainingEvents()
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `タグフィルタを解除すると全件に戻る`() = runTest {
        val all = listOf(
            listItem(id = 4L, title = "全件A", receivedAt = 1_000L),
        )
        every { notificationRepo.getAllListItems() } returns flowOf(PagingData.from(all))
        every { notificationRepo.getListItemsByTag(any()) } returns flowOf(PagingData.from(emptyList()))
        every { tagRepo.getAllTags() } returns flowOf(emptyList())

        val vm = HomeViewModel(notificationRepo, tagRepo)

        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.selectedTag)
            cancelAndIgnoreRemainingEvents()
        }
        vm.viewModelScope.cancel()
    }

    private fun listItem(
        id: Long,
        title: String,
        receivedAt: Long,
        tag: String? = null,
        appLabel: String? = null,
    ) = NotificationListItemModel(
        id = id,
        packageName = "com.test",
        title = title,
        text = null,
        bigText = null,
        notificationType = "local",
        receivedAt = receivedAt,
        tag = tag,
        appLabel = appLabel,
    )
}
