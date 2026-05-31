package org.ukky.notitrace.data.db.entity

import androidx.room.ColumnInfo

/**
 * ホーム/検索一覧向けの軽量 DTO。
 *
 * 一覧表示に不要な raw_json / extras_json などを読み込まず、
 * 画面表示に必要な列だけを取得する。
 */
data class NotificationListItemModel(
    val id: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    val title: String?,
    val text: String?,
    @ColumnInfo(name = "big_text")
    val bigText: String?,
    @ColumnInfo(name = "notification_type")
    val notificationType: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    val tag: String?,
    @ColumnInfo(name = "app_label")
    val appLabel: String?,
)
