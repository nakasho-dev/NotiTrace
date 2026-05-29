package org.ukky.notitrace.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.ukky.notitrace.data.db.dao.AppTagDao
import org.ukky.notitrace.data.db.dao.NotificationDao
import org.ukky.notitrace.data.db.dao.NotificationRawLogDao
import org.ukky.notitrace.data.db.entity.AppTagEntity
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationFtsEntity
import org.ukky.notitrace.data.db.entity.NotificationRawLogEntity

@Database(
    entities = [
        NotificationEntity::class,
        NotificationFtsEntity::class,
        AppTagEntity::class,
        NotificationRawLogEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class NotiTraceDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun appTagDao(): AppTagDao
    abstract fun notificationRawLogDao(): NotificationRawLogDao

    companion object {
        /** v1 → v2: リモート/ローカル通知判定用カラムを追加 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN is_remote INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v2 → v3: 通知種別カラム (notification_type) を追加し、is_remote からバックフィル */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN notification_type TEXT NOT NULL DEFAULT 'local'"
                )
                // 既存データ: is_remote=1 → 'remote_push' にバックフィル
                db.execSQL(
                    "UPDATE notifications SET notification_type = 'remote_push' WHERE is_remote = 1"
                )
            }
        }

        /** v1 → v3: v1 から直接 v3 へ移行（is_remote + notification_type を一括追加） */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN is_remote INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN notification_type TEXT NOT NULL DEFAULT 'local'"
                )
            }
        }

        /** v3 → v4: 通知受信時の生データ JSON カラムを追加 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN raw_json TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        /**
         * v4 → v5: 受信ごとの生データ JSON 子テーブルを追加。
         *
         * - notification_raw_logs テーブルを CREATE
         * - 既存の notifications.raw_json を notification_raw_logs へバックフィル
         *   （raw_json が '{}' でないレコードのみ、first_received_at を received_at に使う）
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_raw_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        notification_id INTEGER NOT NULL,
                        raw_json TEXT NOT NULL,
                        received_at INTEGER NOT NULL,
                        FOREIGN KEY (notification_id) REFERENCES notifications(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_raw_logs_notification_id ON notification_raw_logs(notification_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_raw_logs_received_at ON notification_raw_logs(received_at)"
                )
                // 既存データのバックフィル
                db.execSQL(
                    """
                    INSERT INTO notification_raw_logs (notification_id, raw_json, received_at)
                    SELECT id, raw_json, first_received_at
                    FROM notifications
                    WHERE raw_json != '{}'
                    """.trimIndent()
                )
            }
        }

        /** v5 → v6: signature の UNIQUE 制約を解除し、通常インデックスへ変更 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_notifications_signature")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_signature ON notifications(signature)"
                )
            }
        }
    }
}
