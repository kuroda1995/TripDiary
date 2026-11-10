# TripDiary ER図

| 項目 | 内容 |
| --- | --- |
| ドキュメント | ER図（Mermaid 記法） |
| バージョン | 0.1（初版ドラフト） |
| 作成日 | 2026-11-11 |
| 関連資料 | [database_schema.md](./database_schema.md)（型・制約・インデックス・DDL の詳細） |

> 本図はテーブル構造の全体像を示す。各カラムの詳細な型・CHECK 制約・インデックスは [database_schema.md](./database_schema.md) を正とする。スキーマを変更する場合は両ファイルを同時に更新すること。

---

## 1. ER図（全カラム）

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "発行される"
    users ||--o{ spots : "投稿する"
    users ||--o{ spot_likes : "行ってみたい！する"
    users ||--o{ comments : "書く"
    users ||--o{ follows : "フォローする (follower_id)"
    users ||--o{ follows : "フォローされる (followee_id)"
    users ||--o{ user_spot_statuses : "ステータスを付ける"

    spots ||--o{ spot_photos : "持つ (最大10枚)"
    spots ||--|{ spot_area_tags : "タグ付けされる (1〜5)"
    spots ||--o{ spot_likes : "受ける"
    spots ||--o{ comments : "受ける"
    spots ||--o{ user_spot_statuses : "付けられる"

    area_tags ||--o{ spot_area_tags : "付与される"
    area_tags |o--o{ area_tags : "親子階層 (parent_id)"

    users {
        BIGINT id PK
        VARCHAR_255 email UK "lower(email) で一意"
        VARCHAR_30 username UK "^[A-Za-z0-9_]{3,30}$"
        VARCHAR_100 password_hash "BCrypt"
        VARCHAR_50 display_name
        VARCHAR_500 bio "NULL可"
        VARCHAR_512 avatar_s3_key "NULL可"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    refresh_tokens {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR_64 token_hash UK "SHA-256"
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ revoked_at "NULL可"
        TIMESTAMPTZ created_at
    }

    spots {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR_100 title
        VARCHAR_200 location_name
        TEXT impression "NULL可 / 最大5000文字"
        DATE visited_on "NULL可"
        VARCHAR_10 visibility "PUBLIC | PRIVATE"
        NUMERIC latitude "NULL可"
        NUMERIC longitude "NULL可"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    spot_photos {
        BIGINT id PK
        BIGINT spot_id FK
        VARCHAR_512 s3_key UK
        VARCHAR_50 content_type "jpeg/png/webp/heic"
        SMALLINT sort_order "0〜9"
        TIMESTAMPTZ created_at
    }

    area_tags {
        BIGINT id PK
        BIGINT parent_id FK "NULL = 最上位"
        VARCHAR_50 name
        VARCHAR_50 slug UK
        SMALLINT level "1〜3"
        INTEGER sort_order
        TIMESTAMPTZ created_at
    }

    spot_area_tags {
        BIGINT spot_id PK,FK
        BIGINT area_tag_id PK,FK
    }

    spot_likes {
        BIGINT user_id PK,FK
        BIGINT spot_id PK,FK
        TIMESTAMPTZ created_at
    }

    comments {
        BIGINT id PK
        BIGINT spot_id FK
        BIGINT user_id FK
        TEXT body "1〜1000文字"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    follows {
        BIGINT follower_id PK,FK
        BIGINT followee_id PK,FK "follower_id と異なる"
        TIMESTAMPTZ created_at
    }

    user_spot_statuses {
        BIGINT user_id PK,FK
        BIGINT spot_id PK,FK
        VARCHAR_20 status "VISITED | WANT_TO_GO"
        TIMESTAMPTZ visited_at "VISITED 時のみ"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
```

> Mermaid の erDiagram は型名に `(` `)` を使えないため、`VARCHAR(255)` を `VARCHAR_255` と表記している。

---

## 2. 機能別ビュー

### 2.1 認証・ユーザー

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "発行される"
    users ||--o{ follows : "follower"
    users ||--o{ follows : "followee"
```

### 2.2 スポット投稿・検索

```mermaid
erDiagram
    users ||--o{ spots : "投稿する"
    spots ||--o{ spot_photos : "持つ"
    spots ||--|{ spot_area_tags : "タグ付け"
    area_tags ||--o{ spot_area_tags : "付与"
    area_tags |o--o{ area_tags : "親子"
```

### 2.3 リアクション・個人リスト

```mermaid
erDiagram
    users ||--o{ spot_likes : ""
    spots ||--o{ spot_likes : ""
    users ||--o{ comments : ""
    spots ||--o{ comments : ""
    users ||--o{ user_spot_statuses : ""
    spots ||--o{ user_spot_statuses : ""
```

---

## 3. カーディナリティ早見表

| 関係 | 種別 | 実現方法 |
| --- | --- | --- |
| ユーザー → スポット | 1 : N | `spots.user_id` |
| スポット → 写真 | 1 : 0..10 | `spot_photos.spot_id` |
| スポット ⇔ エリアタグ | N : M（1スポットに1〜5タグ） | `spot_area_tags` |
| エリアタグ → エリアタグ | 1 : N（自己参照） | `area_tags.parent_id` |
| ユーザー ⇔ スポット（行ってみたい！） | N : M | `spot_likes` |
| ユーザー ⇔ スポット（訪問済み/行きたい） | N : M（1組につき1ステータス） | `user_spot_statuses` |
| ユーザー → コメント ← スポット | 1 : N / 1 : N | `comments.user_id`, `comments.spot_id` |
| ユーザー ⇔ ユーザー（フォロー） | N : M（自己参照） | `follows` |
