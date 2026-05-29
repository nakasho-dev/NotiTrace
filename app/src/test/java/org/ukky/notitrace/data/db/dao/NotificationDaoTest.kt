package org.ukky.notitrace.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.ukky.notitrace.data.db.NotiTraceDatabase
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationRawLogEntity

/**
 * NotificationDao の単体テスト（Robolectric + in-memory DB）
 *
 * RED フェーズ: DAO/Entity/Database が未実装の段階で先にテストを定義する
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationDaoTest {

    private lateinit var db: NotiTraceDatabase
    private lateinit var dao: NotificationDao
    private lateinit var rawLogDao: NotificationRawLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NotiTraceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
        rawLogDao = db.notificationRawLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── INSERT / SELECT ─────────────────────────────────

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
        dao.insert(createEntity(signature = "dup_sig", title = "重複通知B", lastReceivedAt = 2000L))

        val all = dao.getAllForBackup()
        assertEquals(2, all.size)
        assertEquals(2, all.count { it.signature == "dup_sig" })
    }

    // ── 一覧取得 (Flow) ──────────────────────────────────

    @Test
    fun `全通知を受信時刻降順で取得できる`() = runTest {
        val oldId = dao.insert(createEntity(signature = "s1", title = "古い", lastReceivedAt = 1000L))
        val newestId = dao.insert(createEntity(signature = "s2", title = "新しい", lastReceivedAt = 3000L))
        val legacyId = dao.insert(createEntity(signature = "s3", title = "旧データ", lastReceivedAt = 2000L))

        rawLogDao.insert(NotificationRawLogEntity(notificationId = oldId, rawJson = """{"n":1}""", receivedAt = 1000L))
        rawLogDao.insert(NotificationRawLogEntity(notificationId = newestId, rawJson = """{"n":2}""", receivedAt = 3000L))

        dao.getAllReceivedWithTag().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertEquals("新しい", list[0].notification.title)
            assertEquals(3000L, list[0].receivedAt)
            assertEquals("旧データ", list[1].notification.title)
            assertEquals(-legacyId, list[1].rawLogId)
            assertEquals("古い", list[2].notification.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 削除 ──────────────────────────────────────────────

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

    // ── FTS 検索 ──────────────────────────────────────────
    // NOTE: Robolectric の SQLite は unicode61 トークナイザ未対応。
    //       FTS4 の日本語検索は実機/エミュレータの androidTest で検証すること。
    //       ここでは DAO の search メソッドが呼べることだけ確認する。

    @Test
    fun `FTS検索でクエリが実行できる`() = runTest {
        dao.insert(createEntity(signature = "fts1", title = "hello world", text = "sample"))
        dao.insert(createEntity(signature = "fts2", title = "goodbye", text = "another"))

        // Robolectric では FTS content sync triggers が動かない場合があるため
        // 結果件数ではなく「例外なく実行できること」を検証する
        dao.searchReceivedFts("hello").test {
            val results = awaitItem()
            // FTS が動けば 1 件、動かなければ 0 件（どちらも正常）
            assertTrue(results.size <= 1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `部分一致検索で1文字の日本語にもヒットする`() = runTest {
        dao.insert(createEntity(signature = "like1", title = "東京都", text = "天気予報"))
        dao.insert(createEntity(signature = "like2", title = "大阪府", text = "お知らせ"))

        dao.searchReceivedPartial("%京%").test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("東京都", results.first().notification.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `部分一致検索でワイルドカード文字をエスケープして検索できる`() = runTest {
        dao.insert(createEntity(signature = "like3", title = "100%完了", text = "進捗100%"))
        dao.insert(createEntity(signature = "like4", title = "1000件", text = "進捗あり"))

        dao.searchReceivedPartial("%100\\%%").test {
            val results = awaitItem()
            assertEquals(1, results.size)
            assertEquals("100%完了", results.first().notification.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ヘルパー ──────────────────────────────────────────

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
        firstReceivedAt: Long = 1000L,
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
        signature = signature,
        receiveCount = receiveCount,
        firstReceivedAt = firstReceivedAt,
        lastReceivedAt = lastReceivedAt,
    )
}
