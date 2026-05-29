package org.ukky.notitrace.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.ReceivedNotificationWithTag
import org.ukky.notitrace.data.db.entity.NotificationWithTag

@Dao
interface NotificationDao {

    // ── 一覧取得（Flow / リアルタイム更新） ────────────

    @Query(
        """
        SELECT n.*,
               COALESCE(r.id, -n.id) AS raw_log_id,
               COALESCE(r.received_at, n.last_received_at) AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        ORDER BY received_at DESC, raw_log_id DESC
        """
    )
    fun getAllReceivedWithTag(): Flow<List<ReceivedNotificationWithTag>>

    @Query(
        """
        SELECT n.*,
               COALESCE(r.id, -n.id) AS raw_log_id,
               COALESCE(r.received_at, n.last_received_at) AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
        INNER JOIN app_tags a ON n.package_name = a.package_name
        WHERE a.tag = :tag
        ORDER BY received_at DESC, raw_log_id DESC
        """
    )
    fun getReceivedByTag(tag: String): Flow<List<ReceivedNotificationWithTag>>

    // ── FTS 全文検索 ──────────────────────────────────

    @Query(
        """
        SELECT n.*,
               COALESCE(r.id, -n.id) AS raw_log_id,
               COALESCE(r.received_at, n.last_received_at) AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
        INNER JOIN notifications_fts fts ON n.id = fts.rowid
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        WHERE notifications_fts MATCH :query
        ORDER BY received_at DESC, raw_log_id DESC
        """
    )
    fun searchReceivedFts(query: String): Flow<List<ReceivedNotificationWithTag>>

    @Query(
        """
        SELECT n.*,
               COALESCE(r.id, -n.id) AS raw_log_id,
               COALESCE(r.received_at, n.last_received_at) AS received_at,
               a.tag,
               a.app_label
        FROM notifications n
        LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
        LEFT JOIN app_tags a ON n.package_name = a.package_name
        WHERE n.title LIKE :pattern ESCAPE '\'
           OR n.text LIKE :pattern ESCAPE '\'
           OR n.big_text LIKE :pattern ESCAPE '\'
           OR n.sub_text LIKE :pattern ESCAPE '\'
        ORDER BY received_at DESC, raw_log_id DESC
        """
    )
    fun searchReceivedPartial(pattern: String): Flow<List<ReceivedNotificationWithTag>>

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
