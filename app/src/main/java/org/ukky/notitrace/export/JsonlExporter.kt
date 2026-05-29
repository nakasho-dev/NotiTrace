package org.ukky.notitrace.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ukky.notitrace.data.db.entity.NotificationWithTag
import org.ukky.notitrace.data.db.entity.RawLogWithTag
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知データを JSONL（JSON Lines）形式でエクスポートする。
 *
 * 2 つのエクスポートモードを提供する:
 * - [export]: 通知データ（1 受信 = 1 通知 = 1 行）
 * - [exportRawLogs]: 受信ごとの生データ（1 受信 = 1 行、受信順）
 */
@Singleton
class JsonlExporter @Inject constructor() {

    private val json = Json { encodeDefaults = true }

    /**
     * 通知データを JSONL 形式で [outputStream] に書き出す。
     */
    fun export(items: List<NotificationWithTag>, outputStream: OutputStream) {
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        items.forEach { item ->
            writer.write(json.encodeToString(item.toExportItem()))
            writer.newLine()
        }
        writer.flush()
    }

    /**
     * 受信ごとの生データを JSONL 形式で [outputStream] に書き出す。
     * 各行は受信時刻順（ASC）で出力される。
     */
    fun exportRawLogs(items: List<RawLogWithTag>, outputStream: OutputStream) {
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        items.forEach { item ->
            writer.write(json.encodeToString(item.toRawExportItem()))
            writer.newLine()
        }
        writer.flush()
    }

    private fun NotificationWithTag.toExportItem() = JsonlExportItem(
        id = notification.id,
        packageName = notification.packageName,
        title = notification.title,
        text = notification.text,
        bigText = notification.bigText,
        subText = notification.subText,
        ticker = notification.ticker,
        tag = tag,
        appLabel = appLabel,
        notificationType = notification.notificationType,
        receiveCount = notification.receiveCount,
        firstReceivedAt = notification.firstReceivedAt,
        lastReceivedAt = notification.lastReceivedAt,
    )

    private fun RawLogWithTag.toRawExportItem() = JsonlRawExportItem(
        rawJson = rawJson,
        receivedAt = receivedAt,
        packageName = packageName,
        notificationType = notificationType,
        tag = tag,
        appLabel = appLabel,
    )
}

/**
 * JSONL エクスポートの 1 行分のデータモデル。
 */
@Serializable
data class JsonlExportItem(
    val id: Long,
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val ticker: String?,
    val tag: String?,
    val appLabel: String?,
    val notificationType: String,
    val receiveCount: Int,
    val firstReceivedAt: Long,
    val lastReceivedAt: Long,
)

/**
 * JSONL 生データエクスポートの 1 行分のデータモデル。
 *
 * - [rawJson] は通知受信時に StatusBarNotification から直接ダンプした生データ
 * - [receivedAt] は受信時刻（Unix ミリ秒）
 * - 受信ごとに 1 行出力され、集約前の個々の生データが保持される
 */
@Serializable
data class JsonlRawExportItem(
    val rawJson: String,
    val receivedAt: Long,
    val packageName: String,
    val notificationType: String,
    val tag: String?,
    val appLabel: String?,
)
