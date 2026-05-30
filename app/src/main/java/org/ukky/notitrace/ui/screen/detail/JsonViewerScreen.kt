package org.ukky.notitrace.ui.screen.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * JSON 生データを整形表示しシンタックスハイライトする画面。
 * Copy ボタンで生データ JSON をクリップボードにコピーできる。
 * Share ボタンで JSON ファイルとして他アプリへ共有できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonViewerScreen(
    rawJson: String,
    shareFileName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("JSON 生データ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        copyToClipboard(context, rawJson)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "JSON をクリップボードにコピーしました",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "コピー")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                shareJsonFile(context, rawJson, shareFileName)
                            } catch (e: IOException) {
                                snackbarHostState.showSnackbar(
                                    message = "JSON ファイルの作成に失敗しました",
                                    duration = SnackbarDuration.Short,
                                )
                            } catch (e: ActivityNotFoundException) {
                                snackbarHostState.showSnackbar(
                                    message = "共有できるアプリが見つかりません",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "共有")
                    }
                },
            )
        },
    ) { innerPadding ->
        val colors = JsonHighlightColors.fromMaterialTheme()
        val highlighted = remember(rawJson, colors) { highlightJson(rawJson, colors) }

        SelectionContainer {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("NotiTrace JSON", text)
    clipboard.setPrimaryClip(clip)
}

private suspend fun shareJsonFile(
    context: Context,
    rawJson: String,
    fileName: String,
) {
    val uri = withContext(Dispatchers.IO) {
        createShareJsonUri(context, rawJson, fileName)
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "JSON ファイルを共有"))
}

private fun createShareJsonUri(
    context: Context,
    rawJson: String,
    fileName: String,
) = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    writeShareJsonFile(context, rawJson, fileName),
)

private fun writeShareJsonFile(
    context: Context,
    rawJson: String,
    fileName: String,
): File {
    val shareDir = File(context.cacheDir, "shared_json")
    if (!shareDir.exists() && !shareDir.mkdirs()) {
        throw IOException("Failed to create share directory: ${shareDir.absolutePath}")
    }

    return File(shareDir, fileName).apply {
        writeText(rawJson, Charsets.UTF_8)
    }
}

internal fun buildJsonShareFileName(
    packageName: String?,
    notificationId: Long?,
    lastReceivedAt: Long?,
): String {
    val packagePart = packageName
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?.takeIf { it.isNotBlank() }
        ?: "notification"
    val idPart = notificationId?.toString() ?: "unknown"
    val receivedAtPart = lastReceivedAt?.toString() ?: "latest"
    return "notitrace_${packagePart}_${idPart}_${receivedAtPart}.json"
}

// ──────────────────────────────────────────────
//  JSON シンタックスハイライト
// ──────────────────────────────────────────────

/**
 * JSON シンタックスハイライト用の配色。
 * Material You テーマに合わせてダーク/ライト両対応する。
 */
internal data class JsonHighlightColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val boolNull: Color,
    val brace: Color,
) {
    companion object {
        @Composable
        fun fromMaterialTheme() = JsonHighlightColors(
            key = MaterialTheme.colorScheme.primary,
            string = MaterialTheme.colorScheme.tertiary,
            number = MaterialTheme.colorScheme.error,
            boolNull = MaterialTheme.colorScheme.secondary,
            brace = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 整形済み JSON 文字列を解析し、キー・文字列・数値・bool/null・記号を色分けした
 * [AnnotatedString] を返す。
 *
 * 簡易パーサーで、kotlinx.serialization の prettyPrint 出力を想定。
 */
internal fun highlightJson(json: String, colors: JsonHighlightColors): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = json.length

        while (i < len) {
            val ch = json[i]

            when {
                // 文字列 (キーまたは値)
                ch == '"' -> {
                    val end = findStringEnd(json, i)
                    val str = json.substring(i, end + 1) // 引用符を含む

                    // キーかどうか: この文字列の後ろ（空白を飛ばして）に ':' が来ればキー
                    val isKey = isJsonKey(json, end + 1)

                    withStyle(SpanStyle(color = if (isKey) colors.key else colors.string)) {
                        append(str)
                    }
                    i = end + 1
                }

                // 数値
                ch == '-' || ch.isDigit() -> {
                    val start = i
                    while (i < len && (json[i].isDigit() || json[i] == '.' || json[i] == '-'
                                || json[i] == '+' || json[i] == 'e' || json[i] == 'E')
                    ) {
                        i++
                    }
                    withStyle(SpanStyle(color = colors.number)) {
                        append(json.substring(start, i))
                    }
                }

                // true / false / null
                json.startsWith("true", i) -> {
                    withStyle(SpanStyle(color = colors.boolNull)) { append("true") }
                    i += 4
                }
                json.startsWith("false", i) -> {
                    withStyle(SpanStyle(color = colors.boolNull)) { append("false") }
                    i += 5
                }
                json.startsWith("null", i) -> {
                    withStyle(SpanStyle(color = colors.boolNull)) { append("null") }
                    i += 4
                }

                // 構造記号
                ch in "{}[]:," -> {
                    withStyle(SpanStyle(color = colors.brace)) { append(ch) }
                    i++
                }

                // 空白・改行はそのまま
                else -> {
                    append(ch)
                    i++
                }
            }
        }
    }
}

/** ダブルクォートで始まる文字列の閉じ引用符の位置を返す。エスケープを考慮。 */
private fun findStringEnd(json: String, start: Int): Int {
    var i = start + 1
    while (i < json.length) {
        when (json[i]) {
            '\\' -> i += 2  // エスケープ文字をスキップ
            '"' -> return i
            else -> i++
        }
    }
    return json.length - 1 // 閉じ引用符が無い場合は末尾
}

/** [pos] 以降の空白を飛ばして ':' が現れるかどうかを返す。 */
private fun isJsonKey(json: String, pos: Int): Boolean {
    var i = pos
    while (i < json.length && json[i].isWhitespace()) i++
    return i < json.length && json[i] == ':'
}



