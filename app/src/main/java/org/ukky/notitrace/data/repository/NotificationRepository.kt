package org.ukky.notitrace.data.repository

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationRawLogEntity
import org.ukky.notitrace.data.db.entity.NotificationWithTag
import org.ukky.notitrace.data.db.entity.RawLogWithTag

/**
 * 通知データへのアクセスを抽象化するインターフェース。
 */
interface NotificationRepository {
    fun getAllListItems(): Flow<PagingData<NotificationListItemModel>>
    fun getListItemsByTag(tag: String): Flow<PagingData<NotificationListItemModel>>
    /**
     * 通知を検索する。
     *
     * まず FTS4 で全文検索し、結果が 0 件または MATCH クエリが解釈できない場合は
     * title / text / bigText / subText に対する部分一致検索へフォールバックする。
     */
    fun searchListItems(query: String): Flow<PagingData<NotificationListItemModel>>
    fun getById(id: Long): Flow<NotificationEntity?>
    suspend fun save(entity: NotificationEntity)
    suspend fun deleteById(id: Long)
    suspend fun deleteAll()
    suspend fun getAllForBackup(): List<NotificationEntity>
    /**
     * JSONL エクスポート用に通知一覧をタグ情報付きで取得する。
     *
     * @param tag null の場合は全件、非 null の場合は指定タグでフィルタ
     */
    suspend fun getForExport(tag: String?): List<NotificationWithTag>
    /**
     * JSONL 生データエクスポート用に rawLog を受信順で取得する。
     *
     * @param tag null の場合は全件、非 null の場合は指定タグでフィルタ
     */
    suspend fun getForRawExport(tag: String?): List<RawLogWithTag>
    /**
     * バックアップ用に全 rawLog を取得する。
     */
    suspend fun getAllRawLogsForBackup(): List<NotificationRawLogEntity>
    /**
     * 保持期間を超えた古い rawLog を削除する。
     *
     * @param cutoffMillis この時刻より前のレコードを削除
     * @return 削除件数
     */
    suspend fun cleanupOldRawLogs(cutoffMillis: Long): Int
    fun getDistinctPackageNames(): Flow<List<String>>
}
