package org.ukky.notitrace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * 通知一覧・検索一覧向けの POJO。
 *
 * notifications を基準に notification_raw_logs を LEFT JOIN し、
 * 受信ごとに 1 行ずつ表示できるようにする。
 * rawLog が存在しない旧データは notification.id 由来の疑似キーで扱う。
 */
data class ReceivedNotificationWithTag(
    @Embedded val notification: NotificationEntity,
    @ColumnInfo(name = "raw_log_id") val rawLogId: Long,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "tag") val tag: String?,
    @ColumnInfo(name = "app_label") val appLabel: String?,
)
