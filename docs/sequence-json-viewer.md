# シーケンス図: JSON ビューア操作フロー

> 対象機能: F-13 JSON 生データ表示  
> 参照: [BASIC_DESIGN.md §3.3.7 JSON ビューア画面](./BASIC_DESIGN.md#337-json-ビューア画面)  
> 最終更新: 2026-05-31

---

## 1. Copy フロー

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Detail as DetailScreen
    participant Nav as NotiTraceNavGraph
    participant VM as DetailViewModel
    participant Json as JsonViewerScreen
    participant Clipboard as ClipboardManager
    participant Snackbar as SnackbarHostState

    User->>Detail: JSON ボタンをタップ
    Detail->>Nav: Route.JsonViewer(id)
    Nav->>VM: uiState を collect
    VM-->>Json: rawJson / notification を提供

    User->>Json: Copy ボタンをタップ
    Json->>Clipboard: setPrimaryClip("NotiTrace JSON", rawJson)
    Json->>Snackbar: 「JSON をクリップボードにコピーしました」
    Snackbar-->>User: コピー完了を表示
```

---

## 2. Share フロー

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Json as JsonViewerScreen
    participant IO as Dispatchers.IO
    participant Cache as cache/shared_json
    participant Provider as FileProvider
    participant Chooser as Android Sharesheet
    participant Target as Quick Share / 共有先アプリ
    participant Snackbar as SnackbarHostState

    User->>Json: Share ボタンをタップ
    Json->>IO: shareJsonFile(rawJson, shareFileName)
    IO->>Cache: UTF-8 の .json ファイルを書き込み
    Cache-->>IO: File
    IO->>Provider: getUriForFile(file)
    Provider-->>Json: content:// URI
    Json->>Chooser: ACTION_SEND(application/json, EXTRA_STREAM)
    Chooser-->>User: 共有先候補を表示
    User->>Chooser: Quick Share などを選択
    Chooser->>Target: 読み取り権限付き URI を送信

    alt 共有ファイル作成失敗
        IO-->>Json: IOException
        Json->>Snackbar: 「JSON ファイルの作成に失敗しました」
        Snackbar-->>User: エラー表示
    else 共有先アプリが見つからない
        Chooser-->>Json: ActivityNotFoundException
        Json->>Snackbar: 「共有できるアプリが見つかりません」
        Snackbar-->>User: エラー表示
    end
```

---

## 3. 設計上の留意点

| 項目 | 詳細 |
|---|---|
| 共有形式 | プレーンテキスト共有ではなく `.json` ファイル共有 |
| 一時保存先 | `cache/shared_json/` 配下。永続保存はしない |
| 公開方式 | `androidx.core.content.FileProvider` による一時 URI 共有 |
| MIME type | `application/json` |
| ファイル名 | `notitrace_<package>_<id>_<lastReceivedAt>.json` |
