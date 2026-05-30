# NotiTrace

> **Android 端末に届くすべての通知を、安全にローカル記録するプライバシーファースト通知ログアプリ**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-29%2B-brightgreen.svg)](https://developer.android.com/studio/releases/platforms)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## ✨ 特長

| | |
|---|---|
| 🔒 **完全オフライン** | ネットワーク通信を一切行わず、`INTERNET` パーミッションすら宣言しない |
| 🛡️ **暗号化保存** | Room + SQLCipher による AES-256 暗号化 DB。鍵は Android Keystore で管理 |
| 📋 **全通知キャプチャ** | `NotificationListenerService` でサイレント通知を含む全通知を記録 |
| 🔍 **全文検索** | FTS4 + unicode61 トークナイザで日本語対応の高速検索 |
| 🏷️ **タグ管理** | 通知実績のある全アプリを一覧表示し、アプリ単位でタグを追加・編集・削除。過去ログ全体に即時反映 |
| 📊 **通知種別分類** | リモート/ローカル/サイレント/常駐など 7 種別を自動判定・表示 |
| 💾 **バックアップ** | 暗号化 JSON で SAF 経由エクスポート/インポート |
| 📤 **JSONL エクスポート** | 全件またはタグ絞り込みで通知ログを JSON Lines 形式でエクスポート。通知データエクスポートと受信ごとの生データエクスポートの 2 モードに対応 |
| 📄 **JSON 生データ表示** | 通知受信時の Android OS 由来の生データ（`StatusBarNotification` の全フィールド）を整形 JSON でシンタックスハイライト表示＋ワンタップコピー。デバッグ用途に最適 |
| 🗂️ **受信ごとの生データ保存** | 重複集約された通知でも、受信ごとの生 JSON を `notification_raw_logs` テーブルに個別保存。保持期間を日単位で設定可能 |

---

## 📱 スクリーンショット

> ※ スクリーンショットを追加してください

---

## 🏗️ アーキテクチャ

```
UI Layer (Jetpack Compose + Material 3)
    ↕ Flow<UiState>
ViewModel Layer (Hilt)
    ↕
Repository Layer (Interface + Impl)
    ↕
Data Layer (Room + SQLCipher / FTS4)
```

- **MVVM + Repository パターン**
- **Hilt** による依存性注入
- **Room Flow** によるリアクティブなデータ更新

---

## 📦 通知種別の自動分類

NotiTrace は受信した通知を以下の 7 種別にヒューリスティクスで自動分類します：

| 種別 | アイコン | 判定条件 |
|---|---|---|
| **常駐** | 📌 | `FLAG_FOREGROUND_SERVICE` |
| **進行中** | ▶️ | `FLAG_ONGOING_EVENT`（FS 以外） |
| **グループ** | 📚 | `FLAG_GROUP_SUMMARY` |
| **リモート** | ☁️ | FCM/GCM マーカーキーが extras に存在 |
| **リモート静音** | 🔇☁️ | FCM マーカー + 低優先度 (LOW/MIN) |
| **ローカル** | 📱 | 上記いずれにも該当しない |
| **サイレント** | 🔕 | FCM マーカーなし + 低優先度 |

---

## 🔧 技術スタック

| カテゴリ | 技術 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose + Material 3 (Material You) |
| DI | Hilt (Dagger) |
| DB | Room + SQLCipher |
| 全文検索 | SQLite FTS4 (unicode61 トークナイザ) |
| 画面遷移 | Navigation Compose |
| シリアライズ | Kotlin Serialization |
| 暗号化 | Android Keystore + AES-GCM / PBKDF2 |
| OSSライセンス | AboutLibraries (`aboutlibraries-compose-m3`) |
| ビルド | Gradle (Kotlin DSL) + Version Catalog |
| テスト | JUnit 4 + MockK + Turbine + Robolectric |

---

## 📋 必要条件

- Android 10 (API 29) 以上
- 通知へのアクセス権限（`NotificationListenerService`）

---

## 🚀 ビルド方法

```bash
# リポジトリをクローン
git clone https://github.com/your-username/NotiTrace.git
cd NotiTrace

# デバッグビルド
./gradlew assembleDebug

# テスト実行
./gradlew testDebugUnitTest
```

---

## 📁 プロジェクト構成

```
app/src/main/java/org/ukky/notitrace/
├── NotiTraceApplication.kt          # @HiltAndroidApp
├── MainActivity.kt                # Single Activity
├── data/
│   ├── db/
│   │   ├── NotiTraceDatabase.kt     # Room Database (v5)
│   │   ├── DatabaseProvider.kt    # SQLCipher 暗号化 DB 提供
│   │   ├── entity/
│   │   │   ├── NotificationEntity.kt
│   │   │   ├── NotificationFtsEntity.kt
│   │   │   ├── NotificationType.kt    # 7種別 enum
│   │   │   ├── NotificationRawLogEntity.kt # 受信ごとの生データ JSON
│   │   │   ├── RawLogWithTag.kt       # rawLog + タグ POJO
│   │   │   └── AppTagEntity.kt
│   │   └── dao/
│   ├── repository/
│   └── crypto/
│       └── KeyStoreManager.kt
├── service/
│   └── NotiTraceListenerService.kt  # 通知キャプチャ
├── ui/
│   ├── navigation/
│   │   ├── NotiTraceNavGraph.kt     # Navigation Compose
│   │   └── Route.kt              # ルート定義
│   ├── screen/                    # 各画面 (Compose)
│   │   ├── detail/
│   │   │   ├── DetailScreen.kt
│   │   │   ├── DetailViewModel.kt
│   │   │   └── JsonViewerScreen.kt  # JSON 生データ表示
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── SettingsViewModel.kt
│   │   │   └── OssLicensesScreen.kt # OSSライセンス表示
│   │   ├── tag/
│   │   │   ├── TagManageScreen.kt
│   │   │   ├── TagManageItem.kt     # タグ管理 UI モデル
│   │   │   └── TagViewModel.kt
│   │   └── ...
│   ├── component/                 # 共通 UI コンポーネント
│   └── theme/
├── di/
│   └── AppModule.kt              # Hilt Module
├── export/
│   └── JsonlExporter.kt          # JSONL エクスポート（通知データ + 生データ受信順）
└── util/
    ├── SignatureGenerator.kt      # SHA-256 通知シグネチャ生成
    └── NotificationExtractor.kt   # 通知→Entity 変換 + 種別分類
```

---

## 🔐 セキュリティ設計

- **DB 暗号化**: SQLCipher (AES-256-CBC)。パスフレーズは Android Keystore で管理
- **バックアップ暗号化**: ユーザーパスワードから PBKDF2 (210,000 iterations) で導出した鍵で AES-256-GCM 暗号化
- **ネットワーク遮断**: `INTERNET` パーミッション未宣言。通信コード自体が存在しない
- **auto backup 無効**: `android:allowBackup="false"` で ADB 経由のデータ抽出を防止

---

## 📄 ドキュメント

- [基本設計書](docs/BASIC_DESIGN.md)
- [シーケンス図: 通知受信〜保存フロー](docs/sequence-notification-capture.md)
- [シーケンス図: タグ管理フロー](docs/sequence-tag-management.md)
- [シーケンス図: OSSライセンス表示フロー](docs/sequence-oss-licenses.md)
- [シーケンス図: JSON ビューア操作フロー](docs/sequence-json-viewer.md)
- [シーケンス図: JSONL エクスポートフロー](docs/sequence-jsonl-export.md)

---

## 📜 ライセンス

このプロジェクトは [MIT License](LICENSE) の下で公開されています。

---

## 🤝 コントリビュート

Issue や Pull Request は歓迎します。大きな変更を加える前に、まず Issue で議論してください。
