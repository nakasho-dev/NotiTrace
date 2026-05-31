# シーケンス図: 通知一覧・検索表示フロー

> 対象機能: F-03 通知一覧表示 / F-05 全文検索  
> 参照: [BASIC_DESIGN.md §3.3.2 ホーム画面](./BASIC_DESIGN.md#332-ホーム画面) / [BASIC_DESIGN.md §3.3.4 検索画面](./BASIC_DESIGN.md#334-検索画面) / [BASIC_DESIGN.md §6.3 主要クエリ設計](./BASIC_DESIGN.md#63-主要クエリ設計)

---

## 1. ホーム一覧表示

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Home as HomeScreen
    participant VM as HomeViewModel
    participant Repo as NotificationRepositoryImpl
    participant DAO as NotificationDao
    participant DB as Room + SQLCipher

    User->>Home: ホーム画面を開く
    Home->>VM: notifications を collect
    VM->>Repo: getAllListItems()
    Repo->>DAO: getAllListItemsPaged()
    DAO->>DB: notifications + app_tags を軽量 DTO で SELECT
    DB-->>DAO: PagingSource<Int, NotificationListItemModel>
    DAO-->>Repo: PagingSource
    Repo-->>VM: Flow<PagingData<NotificationListItemModel>>
    VM-->>Home: PagingData
    Home-->>User: 先頭ページだけ先に表示

    User->>Home: 下へスクロール
    Home->>DB: 次ページを自動ロード
    DB-->>Home: 続きの行データ

    User->>Home: タグを選択
    Home->>VM: selectTag(tag)
    VM->>Repo: getListItemsByTag(tag)
    Repo->>DAO: getListItemsByTagPaged(tag)
    DAO->>DB: tag 条件付きで再クエリ
    DB-->>Home: フィルタ後の先頭ページ
```

---

## 2. 検索表示

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Search as SearchScreen
    participant VM as SearchViewModel
    participant Repo as NotificationRepositoryImpl
    participant DAO as NotificationDao
    participant DB as Room + SQLCipher

    User->>Search: 検索語を入力
    Search->>VM: onQueryChange(query)
    VM->>Repo: searchListItems(query)
    Repo->>DAO: countSearchFts(query)

    alt MATCH クエリ解釈可能
        Repo->>DAO: searchListItemsPaged(query, likePattern)
        DAO->>DB: FTS 結果を優先し、0件なら LIKE にフォールバック
    else MATCH クエリ解釈不可
        Repo->>DAO: searchListItemsPartialPaged(likePattern)
        DAO->>DB: LIKE のみで検索
    end

    DB-->>Search: Flow<PagingData<NotificationListItemModel>>
    Search-->>User: 結果の先頭ページを表示
```

---

## 3. 設計上のポイント

| 項目 | 内容 |
|---|---|
| 一覧 DTO | `NotificationListItemModel` で一覧に不要な `raw_json` / `extras_json` を読まない |
| ソート基準 | `notifications.last_received_at DESC, notifications.id DESC` |
| タグ反映 | `app_tags` を JOIN して読むため、タグ変更は過去ログにも即時反映 |
| Paging | 初回表示は先頭ページのみ取得し、スクロール時に追加ページを読む |
| 検索互換性 | FTS 優先 + 0件/解釈不可時 LIKE フォールバックを維持 |
