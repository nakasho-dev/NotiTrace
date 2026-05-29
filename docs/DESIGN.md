# NotiTrace 基本設計書

> **バージョン**: 1.6  
> **最終更新**: 2026-04-05  
> **対象プラットフォーム**: Android 10 (API 29) 以上

---

## 1. アプリ概要

**NotiTrace** は、Android 端末に届くすべての通知をローカルに安全に記録し、検索・閲覧できる通知ログアプリである。

ユーザーが「通知へのアクセス」を許可すると、`NotificationListenerService` を通じてすべての通知（サイレント通知を含む）をリアルタイムにキャプチャし、暗号化データベースへ保存する。保存された通知は全文検索・アプリ別タグによるフィルタリング・受信回数の確認が可能であり、暗号化 JSON 形式でのバックアップ・リストアにも対応する。

**一切のネットワーク通信を行わず、すべてのデータを端末内に閉じて管理する**ことが最大の特長であり、プライバシーを最優先とした設計思想を貫く。

---

## 2. 前提条件・制約

### 2.1 技術的前提

| 項目 | 内容 |
|---|---|
| 言語 | Kotlin |
| UI フレームワーク | Jetpack Compose + Material 3 |
| アーキテクチャ | MVVM + Repository パターン |
| DI | Hilt (Dagger) |
| DB | Room + SQLCipher (暗号化) |
| 検索 | SQLite FTS4 |
| minSdk | 29 (Android 10) |
| targetSdk | 36 |
| ビルドツール | Gradle (Kotlin DSL) + Version Catalog |

### 2.2 制約事項

| 制約 | 理由 |
|---|---|
| **ネットワーク通信の完全禁止** | プライバシー保護。INTERNET パーミッションを宣言しない |
| **AccessibilityService の使用禁止** | Play ストアポリシー違反のリスク回避 |
| **通知取得は NotificationListenerService のみ** | Play ストア許容範囲の正規 API |
| **端末ローカル保存のみ** | クラウド同期・外部送信を一切行わない |
| **データベースは常時暗号化** | root 端末でも DB ファイルを直接読めない設計 |

---

## 3. 画面一覧

### 3.1 画面遷移図（テキスト表現）

```
[起動]
  │
  ├─ 通知権限未許可 → [オンボーディング画面]
  │                       │
  │                       └─ 設定画面へ誘導 → 許可後 → [ホーム画面]
  │
  └─ 通知権限許可済 → [ホーム画面]
                          │
                          ├─ リストアイテムタップ → [通知詳細画面]
                          │                           │
                          │                           └─ JSON ボタン → [JSON ビューア画面]
                          │
                          ├─ 検索アイコン → [検索画面]
                          │                    │
                          │                    └─ 結果アイテムタップ → [通知詳細画面]
                          │
                          ├─ タグアイコン → [タグ管理画面]
                          │
                          └─ 設定アイコン → [設定画面]
                                              │
                                              └─ OSSライセンス → [OSSライセンス画面]
```

### 3.2 画面定義

| # | 画面名 | Route | 概要 |
|---|---|---|---|
| 1 | **オンボーディング画面** | `onboarding` | 初回起動時に通知リスナー権限の必要性を説明し、端末設定画面へ誘導する |
| 2 | **ホーム画面** | `home` | 通知ログの一覧を時系列で表示する。タグチップによるフィルタ機能を持つ |
| 3 | **通知詳細画面** | `detail/{id}` | 1件の通知の全フィールド（title / text / bigText / subText / ticker / extras）と受信統計（回数・初回・最終時刻）を表示する |
| 4 | **検索画面** | `search` | テキスト入力による FTS 全文検索。結果はリスト表示され、タップで詳細画面へ遷移する |
| 5 | **タグ管理画面** | `tags` | インストール済みアプリ（通知受信実績のあるもの）をパッケージ単位で一覧し、タグの付与・編集を行う |
| 6 | **設定画面** | `settings` | 通知リスナー権限の状態確認、バックアップ／リストア、アプリ情報を表示する |
| 7 | **JSON ビューア画面** | `detail/{id}/json` | 通知 1 件の全フィールドを整形 JSON でシンタックスハイライト表示し、クリップボードへのコピー機能を提供する |
| 8 | **OSSライセンス画面** | `oss_licenses` | 設定画面から遷移し、アプリが使用しているOSSライブラリのライセンス情報を一覧表示する |

### 3.3 各画面の詳細仕様

#### 3.3.1 オンボーディング画面

- **目的**: `NotificationListenerService` の権限がない場合に、ユーザーへ権限の必要性を説明し許可を促す
- **表示条件**: 通知リスナー権限が未許可の場合のみ表示
- **主要要素**:
  - アプリの説明テキスト（何を記録するか・データが端末外に出ないことの明記）
  - 「設定を開く」ボタン → `ACTION_NOTIFICATION_LISTENER_SETTINGS` を発行
  - 権限許可後は自動的にホーム画面へ遷移
- **権限再確認**: 設定画面からも権限状態の確認・再誘導が可能

#### 3.3.2 ホーム画面

- **主要要素**:
  - 通知ログの LazyColumn リスト（最新順）
  - 各リストアイテム: アプリアイコン、アプリ名、title、text（1行省略）、受信時刻、通知種別チップ
  - タグフィルタ用の横スクロール ChipGroup（上部）
  - 検索 / タグ管理 / 設定へのナビゲーション（TopAppBar または BottomBar）
- **表示仕様**: 同一内容の通知でもまとめず、受信ごとに 1 行ずつ表示する
- **データ更新**: `Flow` による自動更新。新しい通知が届くとリアルタイム反映

#### 3.3.3 通知詳細画面

- **主要要素**:
  - ヘッダ: アプリアイコン + アプリ名 + パッケージ名 + タグ
  - 通知種別: `NotificationTypeChip`（アイコン + ラベル）+ 種別の説明テキスト
  - 通知本文: title / text / bigText / subText / ticker（null のフィールドは非表示）
  - 受信統計: 受信回数、初回受信時刻、最終受信時刻
  - Extras セクション: key-value の展開表示（折りたたみ可能）
  - **JSON ボタン**: TopAppBar に `DataObject` アイコンを配置。タップで JSON ビューア画面へ遷移
  - 削除ボタン

#### 3.3.7 JSON ビューア画面

- **目的**: 通知の全フィールドを JSON 形式で一覧し、開発者やパワーユーザーが生データを確認・コピーできるようにする
- **主要要素**:
  - TopAppBar: タイトル「JSON 生データ」+ 戻るボタン + Copy ボタン（`ContentCopy` アイコン）
  - JSON 表示エリア: 整形済み（`prettyPrint`）JSON をモノスペースフォントでスクロール表示
  - シンタックスハイライト: `AnnotatedString` を用いてキー（青）・文字列値（緑）・数値（橙）・bool/null（紫）・記号（灰）を色分け
  - テキスト選択: `SelectionContainer` でテキストの部分選択が可能
  - Copy 機能: TopAppBar の Copy ボタンタップでクリップボードに JSON 全文をコピーし、`Snackbar` でフィードバック表示
- **データソース**: 通知受信時に `NotificationExtractor.buildRawJson()` が `StatusBarNotification` の全フィールドを直接読み取り、Android OS 由来の生データのみを整形 JSON に変換して `raw_json` カラムに保存する。アプリ独自の加工データ（`notificationType` / `signature` / `capturedAt` 等）は含まれない。`DetailViewModel` はこの値をそのまま `JsonViewerScreen` に渡す。v3 以前のデータは `raw_json` が空オブジェクト `{}` となる
- **設計ポイント**: 外部ライブラリを使用せず、簡易パーサーで JSON シンタックスハイライトを実装。ダーク/ライト両テーマで視認性を確保

#### 3.3.4 検索画面

- **主要要素**:
  - 検索バー（`SearchBar` Composable）
  - **FTS4 MATCH クエリを優先**したリアルタイム検索結果
  - FTS4 の結果が 0 件、または MATCH クエリが解釈できない場合は `LIKE` による部分一致検索へフォールバック
  - 結果リスト（ホーム画面と同一の `NotificationListItem` を再利用）
  - 検索対象: title / text / bigText / subText
- **日本語対応**: `unicode61` トークナイザを利用しつつ、ヒットしない短い語句や 1 文字検索は部分一致フォールバックで補完する

#### 3.3.5 タグ管理画面

- **目的**: 通知受信実績のある全アプリを一覧表示し、パッケージ単位でタグの付与・編集・削除を行う
- **主要要素**:
  - 通知受信実績のある全パッケージの一覧（アプリ名 + パッケージ名 + 現在のタグ or 追加ボタン）
  - タグ付きアプリ: `AssistChip` でタグを表示。タップでタグ編集ダイアログを開く
  - タグ未設定アプリ: 「+ タグ」の `OutlinedButton` を表示。タップでタグ追加ダイアログを開く
  - タグ編集ダイアログ: タグ文字列の入力 + 保存 / 削除 / キャンセル
  - タグを空文字で保存すると自動的にタグ削除として処理
- **データソース**: `NotificationDao.getDistinctPackageNames()` で通知実績パッケージを取得し、`AppTagDao.getAll()` と結合。タグのみのアプリ（通知実績なし）も一覧に含む
- **設計ポイント**: タグは `AppTagEntity` に保持し、通知テーブルとは JOIN で結合するため、タグ変更は過去ログ全体に即時反映される

#### 3.3.6 設定画面

- **主要要素**:
  - 通知リスナー権限の状態表示（有効 / 無効）+ 設定画面への誘導リンク
  - バックアップ: 「エクスポート」ボタン → パスワード入力 → SAF でファイル保存先選択
  - リストア: 「インポート」ボタン → SAF でファイル選択 → パスワード入力 → 復元
  - **JSONLエクスポート（通知データ）**: タグドロップダウンで対象を選択（「すべて」または特定タグ）→ 「JSONLエクスポート（通知データ）」ボタン → SAF でファイル保存先選択
  - **JSONLエクスポート（生データ・受信順）**: 上記のタグ選択を共有 → 「JSONLエクスポート（生データ・受信順）」ボタン → SAF でファイル保存先選択。`notification_raw_logs` テーブルから受信ごとの rawJson を受信時刻順に出力する
  - **生データ保持期間**: ドロップダウンで rawLog の保持日数を選択（0=無制限, 7, 14, 30, 60, 90, 180, 365 日）。変更時に古い rawLog を即時削除
  - ログ全削除（確認ダイアログ付き）
  - **OSSライセンス**: 「OSSライセンス」ボタン → OSSライセンス画面へ遷移
  - アプリバージョン情報

#### 3.3.8 OSSライセンス画面

- **目的**: アプリが依存するOSSライブラリのライセンス情報をユーザーに提示する
- **主要要素**:
  - TopAppBar: タイトル「OSSライセンス」+ 戻るボタン
  - ライブラリ一覧: AboutLibraries が Gradle プラグインでビルド時に生成した `aboutlibraries.json` を読み込み、ライブラリ名・ライセンス種別・ライセンス本文を表示する
- **実装**: `com.mikepenz:aboutlibraries-compose-m3` の `LibrariesContainer` Composable を使用。Gradle プラグイン `com.mikepenz.aboutlibraries.plugin` がビルド時に依存ライブラリのメタデータを自動収集する
- **設計ポイント**: ライブラリのメタデータはビルド時に生成されるため、追加・更新は依存関係変更のみで完結する

---

## 4. 機能一覧

### 4.1 機能マトリクス

| # | 機能 | 優先度 | 概要 |
|---|---|---|---|
| F-01 | 通知キャプチャ | **必須** | `NotificationListenerService` で全通知を取得し DB に保存 |
| F-02 | 通知シグネチャ生成 | **必須** | `packageName + title + text + bigText + subText` から SHA-256 を生成し、通知相関用メタデータとして保存 |
| F-03 | 通知一覧表示 | **必須** | 保存済み通知を受信順に一覧表示。内容が同一でもまとめない |
| F-04 | 通知詳細表示 | **必須** | 通知の全フィールド・extras・受信統計を閲覧 |
| F-05 | 全文検索 | **必須** | FTS4 による高速な全文検索を優先し、0件時は部分一致検索へフォールバック（日本語対応） |
| F-06 | タグ管理 | **必須** | パッケージ単位でのタグ付与。タグによるフィルタリング |
| F-07 | データベース暗号化 | **必須** | Room + SQLCipher による DB 暗号化。鍵は Android Keystore 管理 |
| F-08 | バックアップ / リストア | **必須** | 暗号化 JSON の SAF 経由エクスポート・インポート |
| F-09 | オンボーディング | **必須** | 通知リスナー権限の許可誘導 |
| F-10 | 通知削除 | 推奨 | 個別削除・全件削除 |
| F-11 | テーマ切替 | 推奨 | ダーク / ライト / システム連動 |
| F-12 | 通知種別分類 | **必須** | 受信通知を 7 種別に自動分類し、一覧・詳細画面で視覚表示 |
| F-13 | JSON 生データ表示 | 推奨 | 通知詳細画面から遷移し、`StatusBarNotification` から直接ダンプした Android OS 由来の生データを整形 JSON でシンタックスハイライト表示。アプリ独自の加工データは含まない。クリップボードコピー機能付き |
| F-14 | OSSライセンス表示 | 推奨 | 設定画面から遷移し、アプリが依存する全OSSライブラリのライセンス情報を一覧表示する。AboutLibraries（`com.mikepenz:aboutlibraries-compose-m3`）を利用し、Gradle プラグインがビルド時に依存関係を自動収集する |
| F-15 | JSONL エクスポート（通知データ） | 推奨 | 設定画面から通知ログを JSON Lines（JSONL）形式で SAF 経由エクスポートする。全件エクスポートとタグによる絞り込みエクスポートに対応。`JsonlExporter` が 1 受信 = 1 行の JSON を UTF-8 で書き出す。暗号化なしのプレーンテキストのため、分析・加工用途を想定する |
| F-16 | JSONL 生データエクスポート（受信順） | 推奨 | `notification_raw_logs` テーブルから受信ごとの生データ JSON を受信時刻順（ASC）に JSONL 形式で出力する。通知トラフィックの時系列分析に適する。タグによる絞り込みにも対応 |
| F-17 | 生データ保持期間設定 | 推奨 | `notification_raw_logs` の保持日数を設定画面から設定可能。0（無制限）〜365 日で選択。保持期間外の rawLog を即時削除し、ストレージ肥大化を抑制する |

### 4.2 通知キャプチャの詳細フロー

```
[Android OS]
    │
    │ 通知発生
    ▼
[NotiTraceListenerService.onNotificationPosted()]
    │
    ├─ StatusBarNotification から以下を抽出:
    │   ├─ packageName
    │   ├─ notification.extras → title / text / bigText / subText / ticker
    │   └─ notification.extras → 全 key-value
    │
    ├─ NotificationExtractor.classifyNotification()
    │   └─ flags / priority / extras から 7 種別を判定
    │
    ├─ SignatureGenerator.generate()
    │   └─ SHA-256( packageName + title + text + bigText + subText )
    │
    │     ├─ NotificationExtractor.buildRawJson()
    │     │   └─ StatusBarNotification の全フィールドを prettyPrint JSON 文字列に変換
    │     │       （packageName / id / key / postTime / tag / groupKey /
    │     │        isOngoing / isClearable / flags / priority / tickerText /
    │     │        category / channelId / group / sortKey / when / number / extras）
    │     │       ※ アプリ独自の加工データ（notificationType / signature / capturedAt）は含めない
    │
    ├─ NotificationRepository.save(entity)
    │   │
    │   ├─ notifications に INSERT
    │   │   └─ receiveCount=1, firstReceivedAt=lastReceivedAt=now
    │   │
    │   └─ notification_raw_logs に rawJson + receivedAt を INSERT
    │
    └─ Room Flow が UI に自動通知
```

### 4.3 シグネチャ生成ロジック

- **署名（signature）生成**: `packageName`, `title`, `text`, `bigText`, `subText` を連結し SHA-256 ハッシュを計算
- **保存**: 生成した `signature` は各通知レコードに保存するが、重複排除には使用しない
- **利用目的**: バックアップ時の相関や、将来の分析・検索キーとして利用できるよう保持する
- **`extras` は署名に含めない**: extras にはタイムスタンプ等の変動値が含まれるため、判定から除外する
- **rawLog の保持**: すべての通知受信について生データを `notification_raw_logs` に個別保存する。保持期間設定（F-17）により古い rawLog は自動削除される

### 4.4 通知種別分類ロジック（F-12）

受信した通知を `StatusBarNotification` の flags / priority / extras からヒューリスティクスで 7 種別に分類する。`NotificationExtractor.classifyNotification()` で実装。

#### 4.4.1 種別一覧

| # | 種別 | DB コード | UI ラベル | アイコン | 判定条件 |
|---|---|---|---|---|---|
| 1 | フォアグラウンドサービス | `foreground_service` | 常駐 | 📌 PushPin | `FLAG_FOREGROUND_SERVICE` が立っている |
| 2 | 進行中 | `ongoing` | 進行中 | ▶️ PlayCircle | `FLAG_ONGOING_EVENT` が立っている（FS 以外） |
| 3 | グループサマリー | `group_summary` | グループ | 📚 Layers | `FLAG_GROUP_SUMMARY` が立っている |
| 4 | リモートサイレント | `remote_silent` | リモート静音 | 🔇 CloudOff | FCM マーカー有 + 低優先度 (LOW/MIN) |
| 5 | リモートプッシュ | `remote_push` | リモート | ☁️ Cloud | FCM マーカー有 + 通常以上の優先度 |
| 6 | ローカルサイレント | `local_silent` | サイレント | 🔕 NotificationsOff | FCM マーカーなし + 低優先度 |
| 7 | ローカル | `local` | ローカル | 📱 PhoneAndroid | 上記いずれにも該当しない（デフォルト） |

#### 4.4.2 判定フロー

```
classifyNotification(sbn)
    │
    ├─ FLAG_FOREGROUND_SERVICE ?  → FOREGROUND_SERVICE
    │
    ├─ FLAG_ONGOING_EVENT ?       → ONGOING
    │
    ├─ FLAG_GROUP_SUMMARY ?       → GROUP_SUMMARY
    │
    ├─ hasRemotePushMarkers(sbn) ?
    │   ├─ FLAG_LOCAL_ONLY → false（リモート判定無効）
    │   └─ extras に FCM/GCM マーカーキーが存在 → true
    │       │
    │       ├─ isSilentPriority() ? → REMOTE_SILENT
    │       └─ else                 → REMOTE_PUSH
    │
    ├─ isSilentPriority() ?       → LOCAL_SILENT
    │
    └─ default                    → LOCAL
```

#### 4.4.3 FCM/GCM リモートプッシュ判定キー

以下のいずれかが `notification.extras` に含まれる場合、リモートプッシュ通知と推定する。
ただし `FLAG_LOCAL_ONLY` が立っている場合はリモートとみなさない。

| キー | 由来 |
|---|---|
| `google.message_id` | FCM メッセージ ID |
| `google.sent_time` | FCM 送信時刻 |
| `google.delivered_priority` | FCM 配信優先度 |
| `google.original_priority` | FCM 元優先度 |
| `google.c.a.e` | FCM Analytics |
| `google.c.sender.id` | FCM 送信者 ID |
| `gcm.n.e` | GCM レガシー通知キー |
| `com.google.firebase.messaging.default_notification_channel_id` | Firebase チャネル ID |

#### 4.4.4 サイレント判定

`Notification.priority` が `PRIORITY_LOW` (-1) 以下（`PRIORITY_MIN` (-2) を含む）の場合にサイレントと判定する。

#### 4.4.5 UI 表示

- **一覧画面**: 各通知行のフッタに `NotificationTypeChip`（アイコン + ラベル）を表示。種別ごとに Material You カラーが異なる
- **詳細画面**: ヘッダ部に `NotificationTypeChip` + 種別の説明テキストを表示

#### 4.4.6 設計上の留意点

- **判定順序は固定**: flags → FCM マーカー → priority の順で評価し、最初にマッチした種別を返す
- **ヒューリスティクス**: Android OS には「リモート/ローカル」を区別するフラグが存在しないため、FCM extras キーの存在で推定する。100% の正確性は保証しない
- **拡張性**: `NotificationType` enum に新しいエントリを追加するだけで種別を拡張可能。DB には TEXT (code) で保存しているため、マイグレーション不要

---

## 5. アーキテクチャ設計

### 5.1 全体構成

MVVM + Repository + Clean Architecture の簡易構成を採用する。

```
┌──────────────────────────────────────────────────────────┐
│                        UI Layer                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐  │
│  │  Screens   │  │ ViewModels │  │  UI State / Event  │  │
│  │ (Compose)  │──│  (Hilt)    │──│  (data class)      │  │
│  └────────────┘  └─────┬──────┘  └────────────────────┘  │
│                        │                                 │
│                        │ Flow<UiState>                   │
└────────────────────────┼─────────────────────────────────┘
                         │
┌────────────────────────┼─────────────────────────────────┐
│                   Domain Layer                           │
│                        │                                 │
│               ┌────────┴────────┐                        │
│               │  Repositories   │                        │
│               │  (interfaces)   │                        │
│               └────────┬────────┘                        │
│                        │                                 │
└────────────────────────┼─────────────────────────────────┘
                         │
┌────────────────────────┼─────────────────────────────────┐
│                    Data Layer                            │
│                        │                                 │
│  ┌─────────────────────┼──────────────────────────────┐  │
│  │        ┌────────────┴────────────┐                 │  │
│  │        │  Repository Impl (Hilt) │                 │  │
│  │        └────────────┬────────────┘                 │  │
│  │                     │                              │  │
│  │     ┌───────────────┼───────────────┐              │  │
│  │     ▼               ▼               ▼              │  │
│  │  ┌──────┐    ┌────────────┐  ┌──────────────┐     │  │
│  │  │ DAOs │    │ KeyStore   │  │ BackupManager│     │  │
│  │  │(Room)│    │ Manager    │  │ (SAF + AES)  │     │  │
│  │  └──┬───┘    └────────────┘  └──────────────┘     │  │
│  │     │                                             │  │
│  │     ▼                                             │  │
│  │  ┌──────────────────────┐                         │  │
│  │  │  NotiTraceDatabase     │                         │  │
│  │  │  (Room + SQLCipher)  │                         │  │
│  │  └──────────────────────┘                         │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │  NotiTraceListenerService                            │  │
│  │  (NotificationListenerService)                     │  │
│  │  → Repository 経由で DB 書き込み                     │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 5.2 レイヤー責務

| レイヤー | 責務 | 主要クラス |
|---|---|---|
| **UI Layer** | 画面描画、ユーザー操作の受付、UI 状態管理 | `HomeScreen`, `HomeViewModel`, `UiState` |
| **Domain Layer** | ビジネスロジックの抽象化（Repository インターフェース） | `NotificationRepository`, `AppTagRepository` |
| **Data Layer** | データの永続化・取得・暗号化 | `NotificationRepositoryImpl`, `NotiTraceDatabase`, `KeyStoreManager` |
| **Service Layer** | バックグラウンドでの通知キャプチャ | `NotiTraceListenerService` |

### 5.3 パッケージ構成

```
org.ukky.notitrace/
│
├── NotiTraceApplication.kt              # Application クラス（@HiltAndroidApp）
├── MainActivity.kt                    # Single Activity（@AndroidEntryPoint）
│
├── data/
│   ├── db/
│   │   ├── NotiTraceDatabase.kt         # @Database 定義 (v5)
│   │   ├── DatabaseProvider.kt        # SQLCipher SupportFactory 生成
│   │   ├── entity/
│   │   │   ├── NotificationEntity.kt  # 通知テーブル
│   │   │   ├── NotificationFtsEntity.kt # FTS4 仮想テーブル
│   │   │   ├── NotificationType.kt    # 通知種別 enum（7 種別）
│   │   │   ├── NotificationRawLogEntity.kt # 受信ごとの生データ JSON 子テーブル（ON DELETE CASCADE）
│   │   │   ├── RawLogWithTag.kt       # rawLog + タグ情報の POJO（JSONL 生データエクスポート用）
│   │   │   └── AppTagEntity.kt        # アプリタグテーブル
│   │   ├── dao/
│   │   │   ├── NotificationDao.kt     # 通知 CRUD + FTS 検索
│   │   │   ├── NotificationRawLogDao.kt # rawLog CRUD + エクスポート用クエリ
│   │   │   └── AppTagDao.kt           # タグ CRUD
│   │   └── converter/
│   │       └── Converters.kt          # TypeConverter（Long↔Date 等）
│   │
│   ├── repository/
│   │   ├── NotificationRepository.kt           # インターフェース
│   │   ├── NotificationRepositoryImpl.kt       # 実装
│   │   ├── AppTagRepository.kt                 # インターフェース
│   │   ├── AppTagRepositoryImpl.kt             # 実装
│   │   └── BackupRepository.kt                 # バックアップ/リストア
│   │
│   └── crypto/
│       └── KeyStoreManager.kt         # Android Keystore 操作
│
├── service/
│   └── NotiTraceListenerService.kt      # NotificationListenerService 実装
│
├── ui/
│   ├── navigation/
│   │   ├── NotiTraceNavGraph.kt         # Navigation Compose ルート定義
│   │   └── Route.kt                   # ルート定義（sealed interface）
│   ├── screen/
│   │   ├── onboarding/
│   │   │   └── OnboardingScreen.kt    # 権限誘導画面
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── detail/
│   │   │   ├── DetailScreen.kt
│   │   │   ├── DetailViewModel.kt
│   │   │   └── JsonViewerScreen.kt     # JSON 生データ表示（シンタックスハイライト + コピー）
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   ├── tag/
│   │   │   ├── TagManageScreen.kt
│   │   │   ├── TagManageItem.kt        # タグ管理画面の UI モデル
│   │   │   └── TagViewModel.kt
│   │   └── settings/
│   │       ├── SettingsScreen.kt
│   │       ├── SettingsViewModel.kt
│   │       └── OssLicensesScreen.kt    # OSSライセンス表示（AboutLibraries）
│   ├── component/
│   │   ├── NotificationListItem.kt    # 通知リスト行の共通 Composable
│   │   ├── TagChip.kt                 # タグフィルタ用チップ
│   │   └── EmptyState.kt             # データなし時の表示
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
├── di/
│   └── AppModule.kt                   # Hilt @Module（DB, DAO, Repository 提供）
│
├── backup/
│   ├── BackupManager.kt               # JSON シリアライズ + SAF 出力
│   └── BackupCrypto.kt                # バックアップファイルの AES-GCM 暗号化
│
├── export/
│   └── JsonlExporter.kt               # JSONL エクスポート（通知データ + 生データ受信順の 2 モード）
│
└── util/
    ├── SignatureGenerator.kt           # SHA-256 ハッシュ生成
    └── NotificationExtractor.kt       # StatusBarNotification → Entity 変換 + 種別分類
```

---

## 6. データベース設計

### 6.1 ER 図（テキスト表現）

```
┌─────────────────────────────────┐          ┌──────────────────────────┐
│        notifications            │          │       app_tags           │
├─────────────────────────────────┤          ├──────────────────────────┤
│ id               : Long (PK,AI)│          │ package_name : Text (PK) │
│ package_name     : Text (INDEX) │───JOIN──▶│ tag          : Text      │
│ title            : Text?        │          │ app_label    : Text?     │
│ text             : Text?        │          └──────────────────────────┘
│ big_text         : Text?        │
│ sub_text         : Text?        │          ┌──────────────────────────┐
│ ticker           : Text?        │          │  notifications_fts       │
│ extras_json      : Text         │          │  (FTS4 Virtual Table)    │
│ raw_json         : Text         │          ├──────────────────────────┤
│ signature        : Text (UNIQUE)│          │ rowid → notifications.id │
│ notification_type: Text         │          │ title    : Text          │
│ is_remote        : Int (非推奨) │          │ text     : Text          │
│ receive_count    : Int          │          │ big_text : Text          │
│ first_received_at: Long         │          │ sub_text : Text          │
│ last_received_at : Long         │          └──────────────────────────┘
└────────────┬────────────────────┘
             │ 1:N (CASCADE)
             ▼
┌──────────────────────────────────┐
│    notification_raw_logs         │
├──────────────────────────────────┤
│ id              : Long (PK, AI)  │
│ notification_id : Long (FK,INDEX)│──▶ notifications.id
│ raw_json        : Text           │
│ received_at     : Long (INDEX)   │
└──────────────────────────────────┘
```

### 6.2 テーブル定義

#### 6.2.1 `notifications` — 通知ログテーブル

受信した通知の本体データを格納するメインテーブル。

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, AUTOINCREMENT | 一意な通知 ID |
| `package_name` | Text | NOT NULL, INDEX | 通知発行元アプリのパッケージ名 |
| `title` | Text | NULLABLE | `Notification.EXTRA_TITLE` |
| `text` | Text | NULLABLE | `Notification.EXTRA_TEXT` |
| `big_text` | Text | NULLABLE | `Notification.EXTRA_BIG_TEXT` |
| `sub_text` | Text | NULLABLE | `Notification.EXTRA_SUB_TEXT` |
| `ticker` | Text | NULLABLE | `Notification.tickerText` |
| `extras_json` | Text | NOT NULL, DEFAULT '{}' | extras の全 key-value を JSON 文字列で保持 |
| `raw_json` | Text | NOT NULL, DEFAULT '{}' | 通知受信時の Android OS 由来の生データを整形 JSON で保持（v4 追加）。アプリ独自の加工データ（notificationType / signature / capturedAt）は含まず、`StatusBarNotification` のフィールドのみを忠実にダンプする。JSON ビューア画面の表示ソース |
| `signature` | Text | NOT NULL, INDEX | 通知相関用 SHA-256 ハッシュ |
| `notification_type` | Text | NOT NULL, DEFAULT 'local' | 通知種別コード（`NotificationType.code`）。7 種別のいずれか |
| `is_remote` | Integer | NOT NULL, DEFAULT 0 | リモートプッシュか否か（v2 互換 — 非推奨。`notification_type` を使用すること） |
| `receive_count` | Integer | NOT NULL, DEFAULT 1 | 現行保存モデルでは常に 1。旧データ互換のため保持 |
| `first_received_at` | Long | NOT NULL | このレコードの受信時刻（Unix millis） |
| `last_received_at` | Long | NOT NULL | このレコードの受信時刻（Unix millis）。旧集約データでは最終受信時刻を保持 |

**インデックス**:
- `idx_notifications_signature` ON `signature` — signature ベース検索の高速化
- `idx_notifications_package` ON `package_name` — アプリ別フィルタの高速化
- `idx_notifications_last_received` ON `last_received_at` DESC — 時系列ソートの高速化

#### 6.2.1a マイグレーション履歴

現在の DB バージョンは **v6**。以下のマイグレーションパスが定義されている。

| パス | 内容 |
|---|---|
| **v1 → v2** | `is_remote INTEGER NOT NULL DEFAULT 0` カラムを追加 |
| **v2 → v3** | `notification_type TEXT NOT NULL DEFAULT 'local'` カラムを追加。`is_remote=1` の既存レコードを `notification_type='remote_push'` にバックフィル |
| **v1 → v3** | v1 から直接 v3 へ移行。`is_remote` と `notification_type` を一括追加（バックフィル不要 — v1 にはリモート判定データなし） |
| **v3 → v4** | `raw_json TEXT NOT NULL DEFAULT '{}'` カラムを追加。通知受信時の生データ JSON 保存用 |
| **v4 → v5** | `notification_raw_logs` テーブルを CREATE（外部キー `notification_id` → `notifications.id` ON DELETE CASCADE）。既存の `notifications.raw_json` が `'{}'` でないレコードを `notification_raw_logs` にバックフィル |
| **v5 → v6** | `signature` インデックスの UNIQUE 制約を解除。以後は同一 signature の通知も個別保存する |

#### 6.2.2 `notifications_fts` — 全文検索用 FTS4 仮想テーブル

`notifications` テーブルに対する Content FTS4 テーブル。検索対象カラムのみを保持する。

| カラム名 | 説明 |
|---|---|
| `rowid` | `notifications.id` に対応 |
| `title` | 検索対象: 通知タイトル |
| `text` | 検索対象: 通知テキスト |
| `big_text` | 検索対象: 拡張テキスト |
| `sub_text` | 検索対象: サブテキスト |

**Room 定義**:
```kotlin
@Fts4(contentEntity = NotificationEntity::class, tokenizer = "unicode61")
@Entity(tableName = "notifications_fts")
data class NotificationFtsEntity(
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?
)
```

**トークナイザ選定理由**: `unicode61` は Unicode を扱えるため日本語を含む通知でも FTS4 を適用しやすい。一方で短い語句や 1 文字検索は端末実装や MATCH 解釈により期待どおりヒットしない場合があるため、本アプリでは FTS4 を優先しつつ `LIKE` による部分一致検索をフォールバックとして併用する。

#### 6.2.2a `notification_raw_logs` — 受信ごとの生データ JSON テーブル

通知受信のたびに `StatusBarNotification` から直接ダンプした生データ JSON を 1 行ずつ記録する子テーブル。重複通知（signature 一致）でも受信ごとに個別の行を保持するため、集約前の全通知トラフィックを時系列で追跡できる。

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| `id` | Long | PRIMARY KEY, AUTOINCREMENT | 一意な rawLog ID |
| `notification_id` | Long | NOT NULL, INDEX, FK → notifications.id (CASCADE) | 親通知の ID |
| `raw_json` | Text | NOT NULL | 受信時の生データ JSON（`NotificationExtractor.buildRawJson()` の出力） |
| `received_at` | Long | NOT NULL, INDEX | 受信時刻（Unix millis） |

**外部キー制約**:
- `notification_id` → `notifications.id` ON DELETE CASCADE — 親通知の削除時に rawLog も自動削除

**インデックス**:
- `index_notification_raw_logs_notification_id` ON `notification_id` — 親通知との結合高速化
- `index_notification_raw_logs_received_at` ON `received_at` — 受信順ソートの高速化

**保持期間**: 設定画面から 0（無制限）〜 365 日で保持日数を設定可能。保持期間外の rawLog は `deleteOlderThan(cutoffMillis)` で即時削除される（F-17）。

#### 6.2.3 `app_tags` — アプリタグテーブル

パッケージ名単位でユーザーが付与するタグを管理する。通知テーブルとは分離し、JOIN で結合する設計のため、タグの変更は過去ログ全件に即時反映される。

| カラム名 | 型 | 制約 | 説明 |
|---|---|---|---|
| `package_name` | Text | PRIMARY KEY | 対象アプリのパッケージ名 |
| `tag` | Text | NOT NULL | ユーザーが付与したタグ文字列 |
| `app_label` | Text | NULLABLE | アプリの表示名（キャッシュ用） |

**インデックス**:
- `idx_app_tags_tag` ON `tag` — タグフィルタの高速化

### 6.3 主要クエリ設計

#### 通知一覧取得（タグ付き）
```sql
SELECT n.*,
       COALESCE(r.id, -n.id) AS raw_log_id,
       COALESCE(r.received_at, n.last_received_at) AS received_at,
       a.tag, a.app_label
FROM notifications n
LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
LEFT JOIN app_tags a ON n.package_name = a.package_name
ORDER BY received_at DESC, raw_log_id DESC
```

#### タグフィルタ付き一覧
```sql
SELECT n.*,
       COALESCE(r.id, -n.id) AS raw_log_id,
       COALESCE(r.received_at, n.last_received_at) AS received_at,
       a.tag, a.app_label
FROM notifications n
LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
INNER JOIN app_tags a ON n.package_name = a.package_name
WHERE a.tag = :tag
ORDER BY received_at DESC, raw_log_id DESC
```

#### 全文検索
```sql
SELECT n.*,
       COALESCE(r.id, -n.id) AS raw_log_id,
       COALESCE(r.received_at, n.last_received_at) AS received_at,
       a.tag, a.app_label
FROM notifications n
LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
INNER JOIN notifications_fts fts ON n.id = fts.rowid
LEFT JOIN app_tags a ON n.package_name = a.package_name
WHERE notifications_fts MATCH :query
ORDER BY received_at DESC, raw_log_id DESC
```

#### 全文検索フォールバック（0件時 / MATCH 解釈不可時）
```sql
SELECT n.*,
       COALESCE(r.id, -n.id) AS raw_log_id,
       COALESCE(r.received_at, n.last_received_at) AS received_at,
       a.tag, a.app_label
FROM notifications n
LEFT JOIN notification_raw_logs r ON r.notification_id = n.id
LEFT JOIN app_tags a ON n.package_name = a.package_name
WHERE n.title LIKE :pattern ESCAPE '\\'
   OR n.text LIKE :pattern ESCAPE '\\'
   OR n.big_text LIKE :pattern ESCAPE '\\'
   OR n.sub_text LIKE :pattern ESCAPE '\\'
ORDER BY received_at DESC, raw_log_id DESC
```

#### 通知保存
```sql
INSERT INTO notifications (...) VALUES (...)
INSERT INTO notification_raw_logs (notification_id, raw_json, received_at)
VALUES (:new_id, :rawJson, :receivedAt)
```

#### rawLog 保持期間クリーンアップ
```sql
DELETE FROM notification_raw_logs WHERE received_at < :cutoffMillis
```

#### rawLog エクスポート（受信順・全件）
```sql
SELECT r.raw_json, r.received_at, n.package_name, n.notification_type,
       a.tag, a.app_label
FROM notification_raw_logs r
INNER JOIN notifications n ON r.notification_id = n.id
LEFT JOIN app_tags a ON n.package_name = a.package_name
ORDER BY r.received_at ASC
```

#### rawLog エクスポート（受信順・タグフィルタ）
```sql
SELECT r.raw_json, r.received_at, n.package_name, n.notification_type,
       a.tag, a.app_label
FROM notification_raw_logs r
INNER JOIN notifications n ON r.notification_id = n.id
INNER JOIN app_tags a ON n.package_name = a.package_name
WHERE a.tag = :tag
ORDER BY r.received_at ASC
```

#### 通知実績アプリ一覧（タグ管理用）
```sql
SELECT DISTINCT package_name FROM notifications ORDER BY package_name
```
タグ管理画面では上記で取得したパッケージ一覧と `app_tags` テーブルをアプリケーション層で結合し、タグ未設定アプリも含めた全アプリ一覧を生成する。

---

## 7. セキュリティ設計

### 7.1 脅威モデル

| 脅威 | 対策 |
|---|---|
| root 端末での DB ファイル直読み | SQLCipher による AES-256-CBC 暗号化 |
| 暗号鍵の漏洩 | Android Keystore（ハードウェア格納）で管理。鍵はプロセス外に抽出不可 |
| バックアップファイルからの情報漏洩 | ユーザーパスワードから PBKDF2 導出鍵で AES-GCM 暗号化 |
| ネットワーク経由のデータ流出 | `INTERNET` パーミッション未宣言。通信コード自体が存在しない |
| 他アプリからのデータアクセス | Android サンドボックス + DB 暗号化の二重保護 |
| バックアップ経由での漏洩 | `android:allowBackup="false"` に変更、`dataExtractionRules` で除外設定 |

### 7.2 データベース暗号化フロー

```
[アプリ初回起動]
    │
    ├─ Android Keystore に AES-256 鍵を生成
        ├─ KeyAlias: "notitrace_db_key"
    │   ├─ KeyGenParameterSpec:
    │   │   ├─ PURPOSE_ENCRYPT | PURPOSE_DECRYPT
    │   │   ├─ BLOCK_MODE_GCM
    │   │   ├─ ENCRYPTION_PADDING_NONE
    │   │   └─ setUserAuthenticationRequired(false)
    │   │
    │   └─ 鍵はハードウェアセキュリティモジュール（TEE/SE）に格納
    │
    ├─ Keystore 鍵で DB パスフレーズ用のランダムバイト列を暗号化
    │   └─ 暗号化済みパスフレーズを SharedPreferences（EncryptedSharedPreferences）に保存
    │
    └─ DB オープン時:
        ├─ SharedPreferences から暗号化パスフレーズ取得
        ├─ Keystore 鍵で復号
        └─ SQLCipher の SupportFactory に復号済みパスフレーズを渡して DB を開く

[アプリ2回目以降]
    │
    └─ 既存の暗号化パスフレーズを Keystore 鍵で復号 → DB オープン
```

### 7.3 バックアップ暗号化フロー

```
[エクスポート時]
    ユーザーがパスワードを入力
        │
        ├─ PBKDF2WithHmacSHA256 (iterations=210000, saltLength=16bytes)
        │   └─ 導出鍵 (256bit)
        │
        ├─ 全通知 + 全タグを JSON シリアライズ
        │
        ├─ AES-256-GCM で暗号化 (IV: 12bytes, ランダム生成)
        │
        └─ ファイル構造: [salt(16)] [iv(12)] [ciphertext] [tag(16)]
            └─ SAF 経由で出力

[インポート時]
    ユーザーがパスワードを入力
        │
        ├─ ファイルから salt を読み取り
        ├─ PBKDF2 で同一鍵を導出
        ├─ AES-256-GCM で復号
        ├─ JSON デシリアライズ
        └─ DB にマージ（signature ベースで重複排除）
```

### 7.4 暗号化パラメータ一覧

| 用途 | アルゴリズム | 鍵長 | 備考 |
|---|---|---|---|
| DB 暗号化 | AES-256-CBC (SQLCipher) | 256bit | SQLCipher デフォルト。鍵は Keystore 管理 |
| DB パスフレーズ暗号化 | AES-256-GCM (Keystore) | 256bit | パスフレーズの暗号化保存用 |
| バックアップ暗号化 | AES-256-GCM | 256bit | ユーザーパスワードから PBKDF2 導出 |
| 鍵導出 | PBKDF2WithHmacSHA256 | — | 反復回数 210,000 (OWASP 推奨) |
| 通知シグネチャ | SHA-256 | — | 暗号用途ではなく通知相関用メタデータ |

---

## 8. Play ストアポリシー上の注意点

### 8.1 通知リスナー権限に関するポリシー

`NotificationListenerService` は **制限付き権限 (Special App Access)** に該当する。Play ストア公開時には以下の対応が必須:

| 対応事項 | 詳細 |
|---|---|
| **権限宣言フォームの提出** | Play Console の「アプリのコンテンツ」→「権限の申告」で `BIND_NOTIFICATION_LISTENER_SERVICE` の使用目的を明記する |
| **コア機能での使用** | 通知ログ記録がアプリの主要機能であることを説明。補助的用途での使用は拒否される可能性がある |
| **最小限の権限行使** | 通知データの記録のみに使用し、通知の dismiss や他アプリの制御は行わないことを明記する |
| **プライバシーポリシー** | 収集するデータの種類（通知内容）、保存方法（端末内暗号化のみ）、第三者共有なしを明記したプライバシーポリシーの URL を設定する |

### 8.2 データ安全性セクション

Play Console の「データ安全性」セクションで以下を正確に宣言する:

| 項目 | 宣言内容 |
|---|---|
| データ収集 | 「アプリの使用状況データ」（通知内容）を収集する |
| データ共有 | **第三者とは共有しない** |
| データの暗号化 | 転送中: **該当なし**（ネットワーク通信しない）、保存時: **暗号化される** |
| データの削除 | ユーザーがアプリ内から削除可能 |

### 8.3 その他のポリシー準拠事項

| 項目 | 対応 |
|---|---|
| **INTERNET パーミッション** | 宣言しない。ネットワーク通信コードも一切含めない |
| **AccessibilityService** | 使用しない（ポリシー違反リスク回避） |
| **バックグラウンドデータアクセス** | `NotificationListenerService` はシステムがバインドするため、バックグラウンド制限の対象外。ただし Foreground Service としての通知表示は不要 |
| **android:allowBackup** | `false` に設定し、`adb backup` によるデータ抽出を防止する |
| **ターゲット API レベル** | API 36 を対象。Play ストアの最新 targetSdk 要件を満たす |
| **プライバシーポリシー** | 通知内容の端末内記録・暗号化保存・外部送信なしを明記したポリシーを作成し、ストア掲載情報に URL を記載する |

### 8.4 審査時の想定リスクと対策

| リスク | 対策 |
|---|---|
| 通知リスナー権限の使用目的が不明確と判断される | アプリ説明文・プライバシーポリシー・権限申告フォームで「通知ログの記録・検索が唯一の目的」であることを一貫して説明する |
| スパイウェアとみなされる | ネットワーク通信の完全排除（INTERNET パーミッション非宣言）を審査チームに示す。暗号化設計も併記する |
| 機密データの取り扱い | DB 暗号化 + Keystore 鍵管理の実装を明示。バックアップも暗号化されることを説明する |

---

## 付録 A: 技術スタック一覧

| カテゴリ | ライブラリ | 用途 |
|---|---|---|
| DI | Hilt (Dagger) | 依存性注入 |
| DB | Room | ORM / DAO |
| DB 暗号化 | SQLCipher for Android (`net.zetetic:sqlcipher-android`) | DB ファイル暗号化 |
| 検索 | SQLite FTS4 (Room 組み込み) | 全文検索 |
| 画面遷移 | Navigation Compose | Single Activity 画面遷移 |
| シリアライズ | Kotlin Serialization | JSON バックアップ / extras 保存 / JSONL エクスポート |
| 暗号化 | Android Keystore API | DB 暗号鍵管理 |
| 暗号化 | javax.crypto (AES-GCM, PBKDF2) | バックアップ暗号化 |
| テスト | JUnit 4 + Compose UI Test + Turbine | 単体 / UI テスト |
| OSSライセンス | AboutLibraries (`com.mikepenz:aboutlibraries-compose-m3`) | OSSライセンス一覧画面。Gradle プラグインがビルド時に依存ライブラリを自動収集 |

## 付録 B: データフロー概要図

```
┌───────────────┐
│  Android OS   │
│  (通知発生)    │
└──────┬────────┘
       │ onNotificationPosted()
       ▼
┌──────────────────────────┐
│ NotiTraceListenerService   │
│ ├─ 通知フィールド抽出     │
│ ├─ Signature 生成        │
│ └─ Repository.save()     │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐      ┌──────────────────────┐
│ NotificationRepository   │      │   AppTagRepository   │
│ ├─ upsert (挿入/更新)    │      │ ├─ setTag()          │
│ ├─ getAll() → Flow       │      │ ├─ getAll() → Flow   │
│ ├─ search() → Flow       │      │ └─ deleteTag()       │
│ └─ delete()              │      └──────────┬───────────┘
└──────────┬───────────────┘                 │
           │                                 │
           ▼                                 ▼
┌────────────────────────────────────────────────────────┐
│                  NotiTraceDatabase                       │
│                (Room + SQLCipher)                      │
│  ┌──────────────┐ ┌──────────────┐ ┌───────────────┐  │
│  │notifications │ │notifications │ │   app_tags    │  │
│  │              │ │    _fts      │ │               │  │
│  └──────────────┘ └──────────────┘ └───────────────┘  │
└────────────────────────────────────────────────────────┘
           │                                 │
           │         Flow<List<T>>           │
           ▼                                 ▼
┌────────────────────────────────────────────────────────┐
│                    ViewModels                          │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ ┌───────────┐  │
│  │  Home    │ │  Detail  │ │ Search │ │    Tag    │  │
│  │ ViewModel│ │ ViewModel│ │ViewModel│ │ ViewModel │  │
│  └────┬─────┘ └────┬─────┘ └───┬────┘ └─────┬─────┘  │
│       │            │           │             │        │
└───────┼────────────┼───────────┼─────────────┼────────┘
        │            │           │             │
        ▼            ▼           ▼             ▼
┌────────────────────────────────────────────────────────┐
│                  Jetpack Compose UI                    │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ ┌───────────┐  │
│  │  Home    │ │  Detail  │ │ Search │ │ TagManage │  │
│  │ Screen   │ │  Screen  │ │ Screen │ │  Screen   │  │
│  └──────────┘ └──────────┘ └────────┘ └───────────┘  │
└────────────────────────────────────────────────────────┘
```

---

> **本設計書は NotiTrace v1.1 の基本設計を定義するものであり、実装の進行に伴い詳細設計書・API 仕様書を別途作成する。**
