# 02. スポット投稿（SPOT）

| 項目 | 内容 |
| --- | --- |
| 機能ID | SPOT-01 〜 SPOT-08 |
| 関連テーブル | `spots`, `spot_area_tags`, `spot_photos` |
| 関連資料 | [要件定義書 3.2](../requirements.md#32-スポット投稿-crudspot) / [写真アップロード](03_photo_upload.md) / [エリア検索](07_area_search.md) |

---

## 1. 概要

TripDiary の中心となる機能です。ユーザーは訪れた（または訪れたい）旅行スポットを、タイトル・場所・エリアタグ・感想・写真付きで投稿し、閲覧・編集・削除できます。

### 実装状況

| 項目 | 状況 |
| --- | --- |
| 作成・詳細・編集・削除・新着一覧・ユーザー別一覧・公開範囲（SPOT-01〜07） | 実装済み |
| 写真（`photos`・カバー写真 `coverPhotoUrl`） | 実装済み（[03_photo_upload.md](03_photo_upload.md)） |
| 「行ってみたい！」数・`likedByMe`・コメント数 | 実装済み（[04_like.md](04_like.md) / [05_comment.md](05_comment.md)） |
| `myStatus` | ステータス管理機能（[08](08_spot_status.md)）で追加 |
| 一覧の検索条件・並び替え（`areaIds` / `q` / `sort`） | エリア検索機能（[07_area_search.md](07_area_search.md)）で追加 |

以下の API 例のうち、未実装の項目には「（未実装）」と記載しています。

## 2. ユーザーストーリー

- ユーザーとして、旅先で撮った写真と感想をスポットとして記録したい。
- ユーザーとして、投稿の誤字を直したり写真を差し替えたりしたい。
- ユーザーとして、不要になった投稿を削除したい。
- ユーザーとして、自分だけが見られる非公開のメモとして記録したい。
- ゲストとして、ログインしなくても新着の投稿を眺めたい。

## 3. 画面・UI

| 画面 | パス | 主な要素 |
| --- | --- | --- |
| 新着一覧（トップ） | `/` | スポットカード（カバー写真・タイトル・場所・エリアタグ・感想の冒頭・投稿者・訪問日・行ってみたい！数・コメント数）、「もっと見る」で続きを読み込み |
| スポット詳細 | `/spots/:id` | 写真カルーセル、タイトル、場所、エリアタグ、訪問日、感想、投稿者、投稿日時、行ってみたい！ボタン、ステータスボタン、コメント欄 |
| 投稿作成 | `/spots/new` | タイトル、場所名、エリアタグ選択（地方ごとに折りたたみ・複数選択）、訪問日、感想（文字数カウンタ付き）、写真（ドラッグ&ドロップ・並び替え）、公開範囲（ログイン必須） |
| 投稿編集 | `/spots/:id/edit` | 作成画面と同じ（既存値を表示。ログイン必須・本人のみ） |
| ユーザープロフィール | `/users/:username` | 投稿一覧（本人には非公開スポットも表示） |

- ヘッダーにはログイン中のみ「投稿する」ボタンを表示します。
- 編集・削除ボタンは投稿者本人にのみ表示します（サーバー側でも必ず権限を確認）。他人のスポットの編集画面を直接開いた場合は「編集できません」と表示します。
- 削除時は確認ダイアログ（shadcn/ui の `AlertDialog`）を表示し、削除後は投稿者のページへ移動します。
- エリアタグは5個選ぶと残りを選べなくし、選択中のタグはチップで表示して × で外せます。
- ページを再読み込みした直後はログイン状態の復元を待ってから詳細・一覧を取得します（本人の非公開スポットが 404 にならないようにするため）。
- 写真のアップロード中は投稿ボタンを無効化し、進捗を表示します。アップロードに失敗した写真が残っている間も投稿できません（再試行または削除を促す）。
- 一覧のカードにはカバー写真（先頭の写真）を表示します。詳細画面では写真を前後ボタン・サムネイルで切り替えられます。

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
  "areaTagIds": [29],
  "impression": "開門直後に行くと人が少なくて最高でした。",
  "visitedOn": "2026-10-03",
  "visibility": "PUBLIC",
  "photos": [
    { "s3Key": "spots/42/9c1e....jpg", "sortOrder": 0 },
    { "s3Key": "spots/42/a7d2....jpg", "sortOrder": 1 }
  ]
}
```

`impression` と `visitedOn` は省略または `null` にできます。`photos` は省略または `null` で写真なしになります（`s3Key` は [署名付き URL 発行](03_photo_upload.md#51-署名付きurl発行-post-uploadspresigned-url認証必要) で受け取ったキー）。

レスポンス `201 Created`（`Location: /api/v1/spots/1024`）: 詳細と同じ形式。

### 4.2 詳細 `GET /spots/{id}`

```json
{
  "id": 1024,
  "title": "朝の清水寺",
  "locationName": "清水寺（京都府京都市東山区）",
  "areaTags": [{ "id": 29, "name": "京都", "slug": "kyoto" }],
  "impression": "開門直後に行くと人が少なくて最高でした。",
  "visitedOn": "2026-10-03",
  "visibility": "PUBLIC",
  "photos": [
    { "s3Key": "spots/42/9c1e....jpg", "url": "https://img.example.com/spots/42/9c1e....jpg", "sortOrder": 0 }
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

- `areaTags` は表示順（地方 → 都道府県、各階層の並び順）で返します。
- `photos` は表示順（先頭がカバー写真）で返します。写真は全スポットで一意な `s3Key` で識別し、編集時はそのまま `photos[].s3Key` として送り返します（写真の ID は返しません）。
- `myStatus` はステータス管理機能で追加します（未実装）。一覧（4.3）にも `likeCount` / `likedByMe` / `commentCount` を含めます。
- `likedByMe` / `myStatus` は未ログイン時は `false` / `null` を返します。

### 4.3 一覧 `GET /spots`

クエリ: `cursor`（前のページの `nextCursor`）、`limit`（初期値20・1〜50）。`sort` と検索条件はエリア検索機能で追加します（未実装）。

```json
{
  "items": [
    {
      "id": 1024,
      "title": "朝の清水寺",
      "locationName": "清水寺（京都府京都市東山区）",
      "areaTags": [{ "id": 29, "name": "京都", "slug": "kyoto" }],
      "impressionExcerpt": "開門直後に行くと人が少なくて最高でした。",
      "visitedOn": "2026-10-03",
      "visibility": "PUBLIC",
      "coverPhotoUrl": "https://img.example.com/spots/42/9c1e....jpg",
      "author": { "id": 42, "username": "taro_trip", "displayName": "旅するタロウ", "avatarUrl": null },
      "createdAt": "2026-10-04T09:00:00Z"
    }
  ],
  "nextCursor": "MjAyNi0xMC0wNFQwOTowMDowMFp8MTAyNA"
}
```

- 一覧では感想の全文ではなく、先頭120文字（超える場合は末尾に「…」）を `impressionExcerpt` として返します。
- `nextCursor` は「最後のスポットの投稿日時と ID」を Base64URL でエンコードした文字列です。クライアントは中身を解釈せず、そのまま次のリクエストに渡します。`null` の場合は最後のページです。
- `coverPhotoUrl` はカバー写真（先頭の写真）の URL です。写真がなければ `null`。
- 一覧は SQL を件数に比例させずに取得します（投稿者は結合して1回、エリアタグ・写真は `hibernate.default_batch_fetch_size` でそれぞれまとめて1回）。

### 4.4 更新 `PUT /spots/{id}` / 削除 `DELETE /spots/{id}`

- 更新: 作成と同じリクエスト形式。写真は送信された配列で置き換えます（含まれない写真は削除）。レスポンス `200 OK`。
- 削除: レスポンス `204 No Content`。

## 5. 業務ルール・バリデーション

| 項目 | ルール |
| --- | --- |
| タイトル | 必須、空白のみ不可、1〜100文字。前後の空白は除いて保存 |
| 場所名 | 必須、1〜200文字。前後の空白は除いて保存 |
| エリアタグ | 1〜5個、存在するタグ ID のみ、重複不可 |
| 感想 | 任意、最大5,000文字。プレーンテキストとして扱う（HTML 不可）。空白のみなら未入力（`null`）として保存 |
| 訪問日 | 任意、未来日不可（日本時間の今日まで） |
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
| 400 | `DUPLICATE_AREA_TAG` | 同じエリアタグ ID が重複している |
| 400 | `VISITED_ON_IN_FUTURE` | 訪問日が未来（日本時間の明日以降） |
| 400 | `INVALID_CURSOR` | 一覧のカーソルの形式が不正 |
| 400 | `TOO_MANY_PHOTOS` | 写真が11枚以上 |
| 400 | `DUPLICATE_PHOTO_ORDER` | 写真の `sortOrder` が重複している |
| 400 | `INVALID_PHOTO_KEY` | 他ユーザーの写真キー・不正な形式のキー・S3 に存在しないキー・他の投稿で使用済みのキー・同じキーの重複 |
| 401 | `UNAUTHORIZED` | 未ログインで作成・更新・削除 |
| 403 | `FORBIDDEN` | 他人の公開投稿の編集・削除 |
| 404 | `SPOT_NOT_FOUND` | 存在しない、または他人の非公開投稿 |
| 404 | `USER_NOT_FOUND` | ユーザー別一覧で、ユーザーが存在しない |

## 8. テスト観点

[品質チェック・テスト計画書 4.2](../qa_test_plan.md#42-スポット投稿-crudspot-写真photo) を参照。

## 9. 補足・将来検討

- 緯度・経度（`latitude` / `longitude`）は DB にカラムのみ用意し、地図表示は将来対応（SPOT-08）。
- 下書き保存は将来検討。
