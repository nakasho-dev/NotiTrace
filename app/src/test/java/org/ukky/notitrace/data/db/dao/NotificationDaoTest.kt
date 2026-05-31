package org.ukky.notitrace.data.db.dao

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.ukky.notitrace.data.db.NotiTraceDatabase
import org.ukky.notitrace.data.db.entity.AppTagEntity
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationListItemModel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationDaoTest {

    private lateinit var db: NotiTraceDatabase
    private lateinit var dao: NotificationDao
    private lateinit var tagDao: AppTagDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotiTraceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
        tagDao = db.appTagDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `通知を挿入し取得できる`() = runTest {
        val entity = createEntity(signature = "sig_001", title = "テスト通知")
        val id = dao.insert(entity)

        assertTrue(id > 0)

        dao.getById(id).test {
            val result = awaitItem()
            assertNotNull(result)
            assertEquals("テスト通知", result!!.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `同一signatureでも複数挿入できる`() = runTest {
        dao.insert(createEntity(signature = "dup_sig", title = "重複通知A"))
        dao.insert(createEntity(signature = "dup_sig", title = "重複通知B", lastReceivedAt = 2_000L))

        val all = dao.getAllForBackup()
        assertEquals(2, all.size)
        assertEquals(2, all.count { it.signature == "dup_sig" })
    }

    @Test
    fun `全通知を受信時刻降順で取得できる`() = runTest {
        dao.insert(createEntity(signature = "s1", title = "古い", lastReceivedAt = 1_000L))
        dao.insert(createEntity(signature = "s2", title = "新しい", lastReceivedAt = 3_000L))
        dao.insert(createEntity(signature = "s3", title = "中間", lastReceivedAt = 2_000L))

        val list = dao.getAllListItemsPaged().loadFirstPage()
        assertEquals(3, list.size)
        assertEquals("新しい", list[0].title)
        assertEquals(3_000L, list[0].receivedAt)
        assertEquals("中間", list[1].title)
        assertEquals("古い", list[2].title)
    }

    @Test
    fun `タグで絞り込んだ一覧を取得できる`() = runTest {
        dao.insert(createEntity(signature = "tag1", packageName = "com.example.slack", title = "Slack"))
        dao.insert(createEntity(signature = "tag2", packageName = "com.example.mail", title = "Mail"))
        tagDao.upsert(AppTagEntity(packageName = "com.example.slack", tag = "仕事", appLabel = "Slack"))

        val list = dao.getListItemsByTagPaged("仕事").loadFirstPage()
        assertEquals(1, list.size)
        assertEquals("Slack", list.first().appLabel)
        assertEquals("仕事", list.first().tag)
    }

    @Test
    fun `IDで通知を削除できる`() = runTest {
        val id = dao.insert(createEntity(signature = "del_me"))
        dao.deleteById(id)

        dao.getById(id).test {
            val result = awaitItem()
            assertNull(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `全件削除できる`() = runTest {
        dao.insert(createEntity(signature = "a1"))
        dao.insert(createEntity(signature = "a2"))
        dao.deleteAll()

        val all = dao.getAllForBackup()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `FTS検索でクエリが実行できる`() = runTest {
        dao.insert(createEntity(signature = "fts1", title = "hello world", text = "sample"))
        dao.insert(createEntity(signature = "fts2", title = "goodbye", text = "another"))

        dao.countSearchFts("hello")
    }

    @Test
    fun `1文字検索はFTSが0件でも部分一致へフォールバックする`() = runTest {
        dao.insert(createEntity(signature = "like1", title = "東京都", text = "天気予報"))
        dao.insert(createEntity(signature = "like2", title = "大阪府", text = "お知らせ"))

        val results = dao.searchListItemsPaged("京", "%京%").loadFirstPage()
        assertEquals(1, results.size)
        assertEquals("東京都", results.first().title)
    }

    @Test
    fun `部分一致検索でワイルドカード文字をエスケープして検索できる`() = runTest {
        dao.insert(createEntity(signature = "like3", title = "100%完了", text = "進捗100%"))
        dao.insert(createEntity(signature = "like4", title = "1000件", text = "進捗あり"))

        val results = dao.searchListItemsPartialPaged("%100\\%%").loadFirstPage()
        assertEquals(1, results.size)
        assertEquals("100%完了", results.first().title)
    }

    private fun createEntity(
        signature: String,
        packageName: String = "com.example.test",
        title: String? = "Default Title",
        text: String? = "Default Text",
        bigText: String? = null,
        subText: String? = null,
        ticker: String? = null,
        extrasJson: String = "{}",
        receiveCount: Int = 1,
        firstReceivedAt: Long = 1_000L,
        lastReceivedAt: Long = firstReceivedAt,
    ) = NotificationEntity(
        id = 0,
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        ticker = ticker,
        extrasJson = extrasJson,
        rawJson = "{}",
        signature = signature,
        notificationType = "local",
        isRemote = false,
        receiveCount = receiveCount,
        firstReceivedAt = firstReceivedAt,
        lastReceivedAt = lastReceivedAt,
    )

    private suspend fun PagingSource<Int, NotificationListItemModel>.loadFirstPage(): List<NotificationListItemModel> {
        val result = load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            )
        )
        return when (result) {
            is PagingSource.LoadResult.Page -> result.data
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> error("PagingSource invalidated")
        }
    }
}
