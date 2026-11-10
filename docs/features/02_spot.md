# 02. スポット投稿（SPOT）

| 項目 | 内容 |
| --- | --- |
| 機能ID | SPOT-01 〜 SPOT-08 |
| 関連テーブル | `spots`, `spot_area_tags`, `spot_photos` |
| 関連資料 | [要件定義書 3.2](../requirements.md#32-スポット投稿-crudspot) / [写真アップロード](03_photo_upload.md) / [エリア検索](07_area_search.md) |

---

## 1. 概要

TripDiary の中心となる機能です。ユーザーは訪れた（または訪れたい）旅行スポットを、タイトル・場所・エリアタグ・感想・写真付きで投稿し、閲覧・編集・削除できます。

## 2. ユーザーストーリー

- ユーザーとして、旅先で撮った写真と感想をスポットとして記録したい。
- ユーザーとして、投稿の誤字を直したり写真を差し替えたりしたい。
- ユーザーとして、不要になった投稿を削除したい。
- ユーザーとして、自分だけが見られる非公開のメモとして記録したい。
- ゲストとして、ログインしなくても新着の投稿を眺めたい。

## 3. 画面・UI

| 画面 | パス | 主な要素 |
| --- | --- | --- |
| 新着一覧（トップ） | `/` | スポットカード（カバー写真・タイトル・場所・エリアタグ・投稿者・行ってみたい！数・コメント数）、無限スクロール |
| スポット詳細 | `/spots/:id` | 写真カルーセル、タイトル、場所、エリアタグ、訪問日、感想、投稿者、行ってみたい！ボタン、ステータスボタン、コメント欄 |
| 投稿作成 | `/spots/new` | タイトル、場所名、エリアタグ選択（複数）、訪問日、感想、写真（ドラッグ&ドロップ・並び替え）、公開範囲 |
| 投稿編集 | `/spots/:id/edit` | 作成画面と同じ（既存値を表示） |
| ユーザープロフィール | `/users/:username` | 投稿一覧（グリッド） |

- 編集・削除ボタンは投稿者本人にのみ表示します（サーバー側でも必ず権限を確認）。
- 削除時は確認ダイアログ（shadcn/ui の `AlertDialog`）を表示します。
- 写真のアップロード中は投稿ボタンを無効化し、進捗を表示します。

## 4. API 仕様

| メソッド | パス | 認証 | 概要 |
| --- | --- | --- | --- |
| GET | `/spots` | 不要 | 一覧・検索（検索条件は [07_area_search.md](07_area_search.md)） |
| POST | `/spots` | 必要 | 作成 |
| GET | `/spots/{id}` | 不要 | 詳細 |
| PUT | `/spots/{id}` | 必要（本人） | 更新（全項目置換） |
| DELETE | `/spots/{id}` | 必要（本人） | 削除 |
| GET | `/users/{username}/spots` | 不要 | ユーザー別一覧 |

### 4.1 作成 `POST /spots`

リクエスト:

```json
{
  "title": "朝の清水寺",
  "locationName": "清水寺（京都府京都市東山区）",
  "areaTagIds": [2],
  "impression": "開門直後に行くと人が少なくて最高でした。",
  "visitedOn": "2026-10-03",
  "visibility": "PUBLIC",
  "photos": [
    { "s3Key": "spots/42/9c1e....jpg", "sortOrder": 0 },
    { "s3Key": "spots/42/a7d2....jpg", "sortOrder": 1 }
  ]
}
```

レスポンス `201 Created`（`Location: /api/v1/spots/1024`）: 詳細と同じ形式。

### 4.2 詳細 `GET /spots/{id}`

```json
{
  "id": 1024,
  "title": "朝の清水寺",
  "locationName": "清水寺（京都府京都市東山区）",
  "areaTags": [{ "id": 2, "name": "京都", "slug": "kyoto" }],
  "impression": "開門直後に行くと人が少なくて最高でした。",
  "visitedOn": "2026-10-03",
  "visibility": "PUBLIC",
  "photos": [
    { "id": 1, "url": "https://img.example.com/spots/42/9c1e....jpg", "sortOrder": 0 }
  ],
  "author": { "id": 42, "username": "taro_trip", "displayName": "旅するタロウ", "avatarUrl": null },
  "likeCount": 12,
  "commentCount": 3,
  "likedByMe": false,
  "myStatus": "WANT_TO_GO",
  "createdAt": "2026-10-04T09:00:00Z",
  "updatedAt": "2026-10-04T09:00:00Z"
}
```

`likedByMe` / `myStatus` は未ログイン時は `false` / `null` を返します。

### 4.3 一覧 `GET /spots`

クエリ: `cursor`, `limit`（初期値20・最大50）、`sort`、検索条件。

```json
{
  "items": [ { "id": 1024, "title": "朝の清水寺", "coverPhotoUrl": "...", "...": "..." } ],
  "nextCursor": "eyJjcmVhdGVkQXQiOi..."
}
```

`nextCursor` が `null` の場合は最後のページです。

### 4.4 更新 `PUT /spots/{id}` / 削除 `DELETE /spots/{id}`

- 更新: 作成と同じリクエスト形式。写真は送信された配列で置き換えます（含まれない写真は削除）。レスポンス `200 OK`。
- 削除: レスポンス `204 No Content`。

## 5. 業務ルール・バリデーション

| 項目 | ルール |
| --- | --- |
| タイトル | 必須、空白のみ不可、1〜100文字 |
| 場所名 | 必須、1〜200文字 |
| エリアタグ | 1〜5個、存在するタグ ID のみ、重複不可 |
| 感想 | 任意、最大5,000文字。プレーンテキストとして扱う（HTML 不可） |
| 訪問日 | 任意、未来日不可 |
| 写真 | 0〜10枚、`sortOrder` は 0〜9 で重複不可。0 番がカバー写真 |
| 公開範囲 | `PUBLIC`（全員）/ `PRIVATE`（本人のみ）。初期値 `PUBLIC` |
| 権限 | 編集・削除は投稿者本人のみ |
| 非公開投稿 | 本人以外からは一覧に出さず、詳細は 404 を返す（存在を知られないため 403 ではなく 404） |
| 削除 | 写真・エリアタグ・いいね・コメント・ステータスも削除（DB の CASCADE）。S3 の写真ファイルはコミット後に非同期で削除 |
| 並び順 | 一覧は新着順（`createdAt` 降順、同時刻は `id` 降順） |

## 6. 関連テーブル

| テーブル | 用途 |
| --- | --- |
| `spots` | スポット本体 |
| `spot_area_tags` | スポットとエリアタグの紐付け |
| `spot_photos` | 写真（[03_photo_upload.md](03_photo_upload.md)） |

## 7. エラーコード

| HTTP | コード | 発生条件 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 入力値の誤り |
| 400 | `AREA_TAG_NOT_FOUND` | 存在しないエリアタグ ID |
| 400 | `INVALID_PHOTO_KEY` | 他ユーザーの写真キー・存在しない写真キー |
| 403 | `FORBIDDEN` | 他人の投稿の編集・削除 |
| 404 | `SPOT_NOT_FOUND` | 存在しない、または他人の非公開投稿 |

## 8. テスト観点

[品質チェック・テスト計画書 4.2](../qa_test_plan.md#42-スポット投稿-crudspot-写真photo) を参照。

## 9. 補足・将来検討

- 緯度・経度（`latitude` / `longitude`）は DB にカラムのみ用意し、地図表示は将来対応（SPOT-08）。
- 下書き保存は将来検討。
