package org.ukky.notitrace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.paging.PagingSource
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationWithTag

@Dao
interface NotificationDao {

    // ── 一覧取得（Paging / リアルタイム更新） ────────────

    @Query(
        """
        SELECT n.id,
               n.package_name,
               n.title,
               n.text,
               n.big_text,
               n.notification_type,
               n.last_received_at AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        ORDER BY n.last_received_at DESC, n.id DESC
        """
    )
    fun getAllListItemsPaged(): PagingSource<Int, NotificationListItemModel>

    @Query(
        """
        SELECT n.id,
               n.package_name,
               n.title,
               n.text,
               n.big_text,
               n.notification_type,
               n.last_received_at AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        INNER JOIN app_tags a ON n.package_name = a.package_name
        WHERE a.tag = :tag
        ORDER BY n.last_received_at DESC, n.id DESC
        """
    )
    fun getListItemsByTagPaged(tag: String): PagingSource<Int, NotificationListItemModel>

    // ── FTS 全文検索 ──────────────────────────────────

    @Query(
        """
        SELECT COUNT(*)
        FROM notifications n
        INNER JOIN notifications_fts fts ON n.id = fts.rowid
        WHERE notifications_fts MATCH :query
        """
    )
    suspend fun countSearchFts(query: String): Int

    @Query(
        """
        WITH fts_results AS (
            SELECT n.id,
                   n.package_name,
                   n.title,
                   n.text,
                   n.big_text,
                   n.notification_type,
                   n.last_received_at AS received_at,
                   a.tag,
                   a.app_label
            FROM notifications n
            INNER JOIN notifications_fts fts ON n.id = fts.rowid
            LEFT JOIN app_tags a ON n.package_name = a.package_name
            WHERE notifications_fts MATCH :query
        ),
        fts_count AS (
            SELECT COUNT(*) AS count FROM fts_results
        )
        SELECT *
        FROM fts_results
        UNION ALL
        SELECT n.id,
               n.package_name,
               n.title,
               n.text,
               n.big_text,
               n.notification_type,
               n.last_received_at AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        WHERE (SELECT count FROM fts_count) = 0
          AND (
              n.title LIKE :pattern ESCAPE '\'
              OR n.text LIKE :pattern ESCAPE '\'
              OR n.big_text LIKE :pattern ESCAPE '\'
              OR n.sub_text LIKE :pattern ESCAPE '\'
          )
        ORDER BY received_at DESC, id DESC
        """
    )
    fun searchListItemsPaged(query: String, pattern: String): PagingSource<Int, NotificationListItemModel>

    @Query(
        """
        SELECT n.id,
               n.package_name,
               n.title,
               n.text,
               n.big_text,
               n.notification_type,
               n.last_received_at AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        WHERE n.title LIKE :pattern ESCAPE '\'
           OR n.text LIKE :pattern ESCAPE '\'
           OR n.big_text LIKE :pattern ESCAPE '\'
           OR n.sub_text LIKE :pattern ESCAPE '\'
        ORDER BY n.last_received_at DESC, n.id DESC
        """
    )
    fun searchListItemsPartialPaged(pattern: String): PagingSource<Int, NotificationListItemModel>

    // ── 個別取得 ──────────────────────────────────────

    @Query("SELECT * FROM notifications WHERE id = :id")
    fun getById(id: Long): Flow<NotificationEntity?>

    // ── 書き込み ──────────────────────────────────────

    @Insert
    suspend fun insert(entity: NotificationEntity): Long

    // ── 削除 ──────────────────────────────────────────

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    // ── バックアップ用（非Flow / 一括取得） ────────────

    @Query("SELECT * FROM notifications ORDER BY last_received_at DESC")
    suspend fun getAllForBackup(): List<NotificationEntity>

    // ── JSONL エクスポート用（非Flow / 一括取得） ────

    @Query(
        """
        SELECT n.*, a.tag, a.app_label
        FROM notifications n
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        ORDER BY n.last_received_at DESC
        """
    )
    suspend fun getAllWithTagList(): List<NotificationWithTag>

    @Query(
        """
        SELECT n.*, a.tag, a.app_label
        FROM notifications n
        INNER JOIN app_tags a ON n.package_name = a.package_name
        WHERE a.tag = :tag
        ORDER BY n.last_received_at DESC
        """
    )
    suspend fun getByTagList(tag: String): List<NotificationWithTag>

    // ── 通知実績アプリ一覧（タグ管理用） ────────────

    @Query("SELECT DISTINCT package_name FROM notifications ORDER BY package_name")
    fun getDistinctPackageNames(): Flow<List<String>>
}
