package org.ukky.notitrace.backup

import kotlinx.serialization.json.Json
import org.ukky.notitrace.data.db.entity.AppTagEntity
import org.ukky.notitrace.data.db.entity.NotificationEntity
import org.ukky.notitrace.data.db.entity.NotificationRawLogEntity
import org.ukky.notitrace.data.repository.AppTagRepository
import org.ukky.notitrace.data.repository.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * バックアップのエクスポート / インポートを管理する。
 *
 * フロー:
 *   Entity → BackupItem → JSON → AES-GCM 暗号化 → ByteArray（ファイル出力は呼び出し側）
 */
@Singleton
class BackupManager @Inject constructor(
    private val notificationRepo: NotificationRepository,
    private val tagRepo: AppTagRepository,
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    /**
     * 暗号化バックアップデータを生成する。
     */
    suspend fun export(password: String): ByteArray {
        val notifications = notificationRepo.getAllForBackup()
        val notificationItems = notifications.map { it.toBackupItem() }
        val tags = tagRepo.getAllForBackup().map { it.toBackupItem() }

        // rawLog を signature で紐付けてエクスポート
        val rawLogs = notificationRepo.getAllRawLogsForBackup()
        val signatureById = notifications.associate { it.id to it.signature }
        val rawLogItems = rawLogs.mapNotNull { raw ->
            val sig = signatureById[raw.notificationId] ?: return@mapNotNull null
            RawLogBackupItem(
                notificationSignature = sig,
                rawJson = raw.rawJson,
                receivedAt = raw.receivedAt,
            )
        }

        val backup = BackupData(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            notifications = notificationItems,
            tags = tags,
            rawLogs = rawLogItems,
        )

        val jsonBytes = json.encodeToString(BackupData.serializer(), backup)
            .toByteArray(Charsets.UTF_8)

        return BackupCrypto.encrypt(jsonBytes, password)
    }

    /**
     * 暗号化バックアップからデータを復元する。
     * バックアップ内の通知を受信単位のレコードとして復元する。
     */
    suspend fun import(encryptedData: ByteArray, password: String) {
        val jsonBytes = BackupCrypto.decrypt(encryptedData, password)
        val backup = json.decodeFromString(BackupData.serializer(), String(jsonBytes, Charsets.UTF_8))

        backup.notifications.forEach { item ->
            val entity = item.toEntity()
            notificationRepo.save(entity)
        }

        backup.tags.forEach { item ->
            tagRepo.setTag(item.toEntity())
        }

        // rawLogs の復元はスキップ（upsert 時に新たに rawLog が作られるため、
        // 既存データとの重複が生じやすい。復元元の rawLog は参考用途）
    }

    // ── マッピング ────────────────────────────────────

    private fun NotificationEntity.toBackupItem() = NotificationBackupItem(
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        ticker = ticker,
        extrasJson = extrasJson,
        rawJson = rawJson,
        signature = signature,
        receiveCount = receiveCount,
        firstReceivedAt = firstReceivedAt,
        lastReceivedAt = lastReceivedAt,
    )

    private fun NotificationBackupItem.toEntity() = NotificationEntity(
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        ticker = ticker,
        extrasJson = extrasJson,
        rawJson = rawJson,
        signature = signature,
        receiveCount = receiveCount,
        firstReceivedAt = firstReceivedAt,
        lastReceivedAt = lastReceivedAt,
    )

    private fun AppTagEntity.toBackupItem() = TagBackupItem(
        packageName = packageName,
        tag = tag,
        appLabel = appLabel,
    )

    private fun TagBackupItem.toEntity() = AppTagEntity(
        packageName = packageName,
        tag = tag,
        appLabel = appLabel,
    )
}
