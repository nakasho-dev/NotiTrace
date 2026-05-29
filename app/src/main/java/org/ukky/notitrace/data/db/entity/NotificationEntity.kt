package org.ukky.notitrace.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通知ログのメインテーブル。
 *
 * - 1 回の通知受信につき 1 行を保存する
 * - signature (SHA-256) は重複検出ではなく検索・相関用メタデータとして保持する
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["signature"]),
        Index(value = ["package_name"]),
        Index(value = ["last_received_at"]),
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    val title: String?,

    val text: String?,

    @ColumnInfo(name = "big_text")
    val bigText: String?,

    @ColumnInfo(name = "sub_text")
    val subText: String?,

    val ticker: String?,

    @ColumnInfo(name = "extras_json", defaultValue = "{}")
    val extrasJson: String = "{}",

    /**
     * 通知受信時に StatusBarNotification から直接ダンプした生データ JSON。
     * Android OS 由来のフィールド（packageName / id / key / postTime /
     * tag / groupKey / flags / priority / tickerText / category /
     * channelId / group / sortKey / when / number / extras）のみを
     * prettyPrint で保持する。
     *
     * アプリ独自の加工データ（notificationType / signature / capturedAt 等）は
     * 含まない。モデルクラスへの変換を行わず、SBN の値を忠実に保存する。
     *
     * v4 で追加。v3 以前のデータは空オブジェクト "{}" がデフォルト。
     */
    @ColumnInfo(name = "raw_json", defaultValue = "{}")
    val rawJson: String = "{}",

    val signature: String,

    /**
     * 通知の種別コード（[NotificationType.code] を TEXT で保存）。
     *
     * @see NotificationType
     */
    @ColumnInfo(name = "notification_type", defaultValue = "local")
    val notificationType: String = NotificationType.LOCAL.code,

    /**
     * リモートプッシュ通知かどうか（v2 互換用 — 非推奨）。
     * 新しいコードでは [notificationType] を使用すること。
     */
    @Deprecated("Use notificationType instead")
    @ColumnInfo(name = "is_remote", defaultValue = "0")
    val isRemote: Boolean = false,

    @ColumnInfo(name = "receive_count", defaultValue = "1")
    val receiveCount: Int = 1,

    @ColumnInfo(name = "first_received_at")
    val firstReceivedAt: Long,

    @ColumnInfo(name = "last_received_at")
    val lastReceivedAt: Long,
)
