package org.ukky.notitrace.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.ukky.notitrace.data.repository.NotificationRepository
import org.ukky.notitrace.util.NotificationExtractor
import javax.inject.Inject

/**
 * 通知リスナーサービス。
 *
 * Android OS がバインドし、すべての通知イベントを配信する。
 * 受信した通知を Entity に変換し、Repository 経由で暗号化 DB に保存する。
 */
@AndroidEntryPoint
class NotiTraceListenerService : NotificationListenerService() {

    @Inject
    lateinit var repository: NotificationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 自アプリの通知は記録しない（無限ループ防止）
        if (sbn.packageName == applicationContext.packageName) return

        scope.launch {
            try {
                val entity = NotificationExtractor.extract(sbn)
                repository.save(entity)
            } catch (e: Exception) {
                // サービスクラッシュを防ぐ。ログは端末の logcat に残る。
                android.util.Log.e("NotiTraceListener", "Failed to save notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 通知の削除イベントは記録しない（ログは残す方針）
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
