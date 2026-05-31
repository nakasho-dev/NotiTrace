package org.ukky.notitrace.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ukky.notitrace.data.db.dao.NotificationDao
import org.ukky.notitrace.data.db.dao.NotificationRawLogDao
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.db.entity.NotificationWithTag
import org.ukky.notitrace.data.db.entity.RawLogWithTag

class NotificationRepositoryImplTest {

    private lateinit var dao: NotificationDao
    private lateinit var rawLogDao: NotificationRawLogDao
    private lateinit var repository: NotificationRepository

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        rawLogDao = mockk(relaxed = true)
        repository = NotificationRepositoryImpl(dao, rawLogDao)
    }

    @Test
    fun `通知を保存するとINSERTされる`() = runTest {
        coEvery { dao.insert(any()) } returns 1L
        coEvery { rawLogDao.insert(any()) } returns 1L

        val entity = createEntity(signature = "new_sig")
        repository.save(entity)

        coVerify(exactly = 1) { dao.insert(entity) }
    }

    @Test
    fun `通知保存でrawLogも記録される`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        coEvery { rawLogDao.insert(any()) } returns 1L

        val entity = createEntity(signature = "new_sig", rawJson = """{"test":"data"}""")
        repository.save(entity)

        coVerify(exactly = 1) {
            rawLogDao.insert(match {
                it.notificationId == 42L &&
                    it.rawJson == """{"test":"data"}""" &&
                    it.receivedAt == entity.lastReceivedAt
            })
        }
    }

    @Test
    fun `同一signatureでも保存のたびにINSERTされる`() = runTest {
        coEvery { dao.insert(any()) } returnsMany listOf(10L, 11L)
        coEvery { rawLogDao.insert(any()) } returns 1L

        repository.save(createEntity(signature = "dup_sig", lastReceivedAt = 4_000L))
        repository.save(createEntity(signature = "dup_sig", lastReceivedAt = 5_000L))

        coVerify(exactly = 2) { dao.insert(any()) }
    }

    @Test
    fun `全通知をPagingで取得できる`() = runTest {
        val mockList = listOf(
            listItem(id = 10L, title = "通知1", receivedAt = 3_000L, tag = "SNS", appLabel = "App1"),
            listItem(id = 11L, title = "通知2", receivedAt = 2_000L),
        )
        every { dao.getAllListItemsPaged() } returns TestNotificationPagingSource(mockList)

        val result = repository.getAllListItems().asSnapshot()
        assertEquals(2, result.size)
        assertEquals("通知1", result[0].title)
        assertEquals("SNS", result[0].tag)
        assertNull(result[1].tag)
    }

    @Test
    fun `タグで絞り込める`() = runTest {
        val filtered = listOf(
            listItem(id = 20L, title = "Title", receivedAt = 4_000L, tag = "仕事", appLabel = "SlackApp"),
        )
        every { dao.getListItemsByTagPaged("仕事") } returns TestNotificationPagingSource(filtered)

        val result = repository.getListItemsByTag("仕事").asSnapshot()
        assertEquals(1, result.size)
        assertEquals("仕事", result[0].tag)
    }

    @Test
    fun `FTS検索でマッチした通知を取得できる`() = runTest {
        val searchResult = listOf(
            listItem(id = 30L, title = "東京天気", receivedAt = 1_000L),
        )
        coEvery { dao.countSearchFts("東京") } returns 1
        every { dao.searchListItemsPaged("東京", "%東京%") } returns TestNotificationPagingSource(searchResult)

        val result = repository.searchListItems("東京").asSnapshot()
        assertEquals(1, result.size)
        assertEquals("東京天気", result[0].title)

        coVerify(exactly = 1) { dao.countSearchFts("東京") }
        verify(exactly = 1) { dao.searchListItemsPaged("東京", "%東京%") }
        verify(exactly = 0) { dao.searchListItemsPartialPaged(any()) }
    }

    @Test
    fun `FTS検索が失敗すると部分一致検索にフォールバックする`() = runTest {
        val fallbackResult = listOf(
            listItem(id = 31L, title = "100%完了", receivedAt = 1_000L),
        )
        coEvery { dao.countSearchFts("100%") } throws IllegalStateException("bad MATCH query")
        every { dao.searchListItemsPartialPaged("%100\\%%") } returns TestNotificationPagingSource(fallbackResult)

        val result = repository.searchListItems("100%").asSnapshot()
        assertEquals(1, result.size)
        assertEquals("100%完了", result[0].title)

        coVerify(exactly = 1) { dao.countSearchFts("100%") }
        verify(exactly = 1) { dao.searchListItemsPartialPaged("%100\\%%") }
        verify(exactly = 0) { dao.searchListItemsPaged(any(), any()) }
    }

    @Test
    fun `IDで通知を削除する`() = runTest {
        repository.deleteById(42L)
        coVerify { dao.deleteById(42L) }
    }

    @Test
    fun `全件削除でrawLogも一括削除される`() = runTest {
        repository.deleteAll()
        coVerify(exactly = 1) { rawLogDao.deleteAll() }
        coVerify(exactly = 1) { dao.deleteAll() }
    }

    @Test
    fun `getForExport_tagNull_全件をAllWithTagListから取得する`() = runTest {
        val allItems = listOf(
            NotificationWithTag(createEntity(signature = "e1"), "SNS", "App1"),
            NotificationWithTag(createEntity(signature = "e2"), null, null),
        )
        coEvery { dao.getAllWithTagList() } returns allItems

        val result = repository.getForExport(null)
        assertEquals(2, result.size)
        coVerify(exactly = 1) { dao.getAllWithTagList() }
        coVerify(exactly = 0) { dao.getByTagList(any()) }
    }

    @Test
    fun `getForExport_tag指定_指定タグでフィルタした一覧を取得する`() = runTest {
        val filtered = listOf(
            NotificationWithTag(createEntity(signature = "e3"), "仕事", "SlackApp"),
        )
        coEvery { dao.getByTagList("仕事") } returns filtered

        val result = repository.getForExport("仕事")
        assertEquals(1, result.size)
        assertEquals("仕事", result[0].tag)
        coVerify(exactly = 0) { dao.getAllWithTagList() }
        coVerify(exactly = 1) { dao.getByTagList("仕事") }
    }

    @Test
    fun `getForExport_存在しないタグ_空リストが返る`() = runTest {
        coEvery { dao.getByTagList("存在しないタグ") } returns emptyList()

        val result = repository.getForExport("存在しないタグ")
        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { dao.getByTagList("存在しないタグ") }
    }

    @Test
    fun `getForRawExport_tagNull_全件を受信順で取得する`() = runTest {
        val rawItems = listOf(
            RawLogWithTag("""{"a":1}""", 1_000L, "com.a", "local", "SNS", "App1"),
            RawLogWithTag("""{"b":2}""", 2_000L, "com.b", "remote_push", null, null),
        )
        coEvery { rawLogDao.getAllWithTagOrderByReceivedAt() } returns rawItems

        val result = repository.getForRawExport(null)
        assertEquals(2, result.size)
        assertEquals(1_000L, result[0].receivedAt)
        assertEquals(2_000L, result[1].receivedAt)
    }

    @Test
    fun `getForRawExport_tag指定_指定タグでフィルタする`() = runTest {
        val rawItems = listOf(
            RawLogWithTag("""{"c":3}""", 3_000L, "com.c", "local", "仕事", "Slack"),
        )
        coEvery { rawLogDao.getByTagOrderByReceivedAt("仕事") } returns rawItems

        val result = repository.getForRawExport("仕事")
        assertEquals(1, result.size)
        assertEquals("仕事", result[0].tag)
    }

    @Test
    fun `cleanupOldRawLogs_指定時刻より古いrawLogを削除する`() = runTest {
        coEvery { rawLogDao.deleteOlderThan(5_000L) } returns 3

        val deleted = repository.cleanupOldRawLogs(5_000L)
        assertEquals(3, deleted)
        coVerify(exactly = 1) { rawLogDao.deleteOlderThan(5_000L) }
    }

    @Test
    fun `通知実績のある全パッケージ名をFlowで取得できる`() = runTest {
        every { dao.getDistinctPackageNames() } returns flowOf(listOf("com.a", "com.b"))

        repository.getDistinctPackageNames().collect {
            assertEquals(2, it.size)
            assertEquals("com.a", it[0])
            assertEquals("com.b", it[1])
            return@collect
        }
    }

    @Suppress("DEPRECATION")
    private fun createEntity(
        id: Long = 0,
        signature: String,
        title: String? = "Title",
        receiveCount: Int = 1,
        lastReceivedAt: Long = 1_000L,
        notificationType: String = "local",
        rawJson: String = "{}",
    ) = NotificationEntity(
        id = id,
        packageName = "com.test",
        title = title,
        text = "text",
        bigText = null,
        subText = null,
        ticker = null,
        extrasJson = "{}",
        rawJson = rawJson,
        signature = signature,
        notificationType = notificationType,
        isRemote = notificationType == "remote_push" || notificationType == "remote_silent",
        receiveCount = receiveCount,
        firstReceivedAt = 1_000L,
        lastReceivedAt = lastReceivedAt,
    )

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
        text = "text",
        bigText = null,
        notificationType = "local",
        receivedAt = receivedAt,
        tag = tag,
        appLabel = appLabel,
    )

    private class TestNotificationPagingSource(
        private val items: List<NotificationListItemModel>,
    ) : PagingSource<Int, NotificationListItemModel>() {

        override fun getRefreshKey(state: PagingState<Int, NotificationListItemModel>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, NotificationListItemModel> {
            val start = params.key ?: 0
            val end = (start + params.loadSize).coerceAtMost(items.size)
            val data = if (start >= items.size) emptyList() else items.subList(start, end)
            return LoadResult.Page(
                data = data,
                prevKey = if (start == 0) null else maxOf(start - params.loadSize, 0),
                nextKey = if (end < items.size) end else null,
            )
        }
    }
}
