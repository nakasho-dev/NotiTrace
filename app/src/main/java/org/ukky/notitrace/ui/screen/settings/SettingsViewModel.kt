package org.ukky.notitrace.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ukky.notitrace.backup.BackupManager
import org.ukky.notitrace.data.repository.AppTagRepository
import org.ukky.notitrace.data.repository.NotificationRepository
import org.ukky.notitrace.export.JsonlExporter
import javax.inject.Inject

data class SettingsUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val isJsonlExporting: Boolean = false,
    val isRawJsonlExporting: Boolean = false,
    val rawLogRetentionDays: Int = 30,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val notificationRepo: NotificationRepository,
    private val appTagRepo: AppTagRepository,
    private val jsonlExporter: JsonlExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** エクスポート対象のタグ選択肢（空文字列リスト、空の場合はタグなし）。 */
    val tags: StateFlow<List<String>> = appTagRepo.getAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun export(context: Context, uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, message = null)
            try {
                val data = backupManager.export(password)
                context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                _uiState.value = _uiState.value.copy(isExporting = false, message = "エクスポート完了")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false, message = "エクスポート失敗: ${e.message}")
            }
        }
    }

    fun import(context: Context, uri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, message = null)
            try {
                val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("ファイルを読み込めません")
                backupManager.import(data, password)
                _uiState.value = _uiState.value.copy(isImporting = false, message = "インポート完了")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isImporting = false, message = "インポート失敗: ${e.message}")
            }
        }
    }

    /**
     * 通知データを JSONL 形式でエクスポートする。
     */
    fun exportJsonl(context: Context, uri: Uri, tag: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isJsonlExporting = true, message = null)
            try {
                val items = notificationRepo.getForExport(tag)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    jsonlExporter.export(items, out)
                }
                val suffix = if (tag != null) "（タグ: $tag）" else "（全件）"
                _uiState.value = _uiState.value.copy(
                    isJsonlExporting = false,
                    message = "JSONLエクスポート完了$suffix: ${items.size}件",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isJsonlExporting = false,
                    message = "JSONLエクスポート失敗: ${e.message}",
                )
            }
        }
    }

    /**
     * 受信ごとの生データを JSONL 形式でエクスポートする（受信順）。
     */
    fun exportRawJsonl(context: Context, uri: Uri, tag: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRawJsonlExporting = true, message = null)
            try {
                val items = notificationRepo.getForRawExport(tag)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    jsonlExporter.exportRawLogs(items, out)
                }
                val suffix = if (tag != null) "（タグ: $tag）" else "（全件）"
                _uiState.value = _uiState.value.copy(
                    isRawJsonlExporting = false,
                    message = "生データJSONLエクスポート完了$suffix: ${items.size}件",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRawJsonlExporting = false,
                    message = "生データJSONLエクスポート失敗: ${e.message}",
                )
            }
        }
    }

    /**
     * rawLog の保持期間を設定する。
     *
     * @param days 保持日数（0 = 無制限）
     */
    fun setRawLogRetentionDays(days: Int) {
        _uiState.value = _uiState.value.copy(rawLogRetentionDays = days)
        if (days > 0) {
            viewModelScope.launch {
                val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
                val deleted = notificationRepo.cleanupOldRawLogs(cutoff)
                _uiState.value = _uiState.value.copy(
                    message = "保持期間を${days}日に設定しました（${deleted}件の古い生データを削除）",
                )
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            notificationRepo.deleteAll()
            _uiState.value = _uiState.value.copy(message = "全ログを削除しました")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
