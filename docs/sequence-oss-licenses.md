# シーケンス図: OSSライセンス表示フロー

> 対象機能: F-14 OSSライセンス表示  
> 参照: [BASIC_DESIGN.md §3.3.8 OSSライセンス画面](./BASIC_DESIGN.md#338-ossライセンス画面)

---

## 1. テキスト形式シーケンス図

### 1.1 OSSライセンス画面の表示

```
User             SettingsScreen        NavController        OssLicensesScreen      aboutlibraries.json
  │                    │                    │                      │                       │
  │  OSSライセンスボタン │                    │                      │                       │
  │  をタップ          │                    │                      │                       │
  │───────────────────▶│                    │                      │                       │
  │                    │ navigate("oss_licenses")                   │                       │
  │                    │───────────────────▶│                      │                       │
  │                    │                    │ composable 起動        │                       │
  │                    │                    │─────────────────────▶│                       │
  │                    │                    │                      │ LibrariesContainer()  │
  │                    │                    │                      │  ライブラリ情報を読込  │
  │                    │                    │                      │──────────────────────▶│
  │                    │                    │                      │                       │
  │                    │                    │                      │  List<Library>        │
  │                    │                    │                      │◀──────────────────────│
  │                    │                    │                      │                       │
  │  ライブラリ一覧表示  │                    │                      │                       │
  │◀───────────────────────────────────────────────────────────────│                       │
  │                    │                    │                      │                       │
  │  戻るボタンをタップ  │                    │                      │                       │
  │───────────────────────────────────────────────────────────────▶│                       │
  │                    │                    │ popBackStack()        │                       │
  │                    │                    │◀─────────────────────│                       │
  │                    │                    │                      │                       │
  │  設定画面に戻る      │                    │                      │                       │
  │◀───────────────────│                    │                      │                       │
```

---

## 2. 処理概要

### 2.1 ライブラリ情報の生成タイミング（ビルド時）

OSSライセンス情報の収集は **アプリ起動時ではなく Gradle ビルド時** に行われる。

```
[Gradle ビルド時]
    │
    ├─ com.mikepenz.aboutlibraries.plugin が依存関係をスキャン
    │   └─ 全 implementation/api 依存のメタデータを収集
    │
    └─ app/src/main/assets/aboutlibraries.json を自動生成
        └─ ライブラリ名 / バージョン / ライセンス種別 / ライセンス本文を含む JSON
```

### 2.2 画面表示時のデータフロー

```
[OssLicensesScreen 起動時]
    │
    ├─ LibrariesContainer() が assets/aboutlibraries.json を読み込む
    │
    └─ LazyColumn でライブラリ一覧を表示
        ├─ ライブラリ名 + バージョン
        ├─ ライセンス種別 (MIT / Apache-2.0 等)
        └─ タップでライセンス本文ダイアログを展開
```

---

## 3. 設計ポイント

| 項目 | 内容 |
|---|---|
| **ライブラリ** | `com.mikepenz:aboutlibraries-compose-m3` |
| **プラグイン** | `com.mikepenz.aboutlibraries.plugin` |
| **データ生成** | Gradle ビルド時に `aboutlibraries.json` を自動生成。手動メンテ不要 |
| **ViewModel 不要** | 画面表示に必要なデータは AboutLibraries ライブラリが内部処理するため ViewModel を持たない |
| **ネットワーク通信なし** | assets から読み込むためネットワーク通信は発生しない |
| **ナビゲーション** | `Route.OssLicenses`（`"oss_licenses"`）。`SettingsScreen` の `onOssLicensesClick` コールバック経由で遷移 |

