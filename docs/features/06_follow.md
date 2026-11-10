# 06. フォロー / フォロワー（FLW）

| 項目 | 内容 |
| --- | --- |
| 機能ID | FLW-01 〜 FLW-05 |
| 関連テーブル | `follows` |
| 関連資料 | [要件定義書 3.6](../requirements.md#36-フォロー--フォロワーfollow) |

---

## 1. 概要

気になるユーザーをフォローし、その人の新しい投稿をタイムラインで追える機能です。フォローは承認不要の一方向の関係です（相手の許可なくフォローできます）。

## 2. ユーザーストーリー

- ユーザーとして、好みの旅をしている人をフォローして、新しい投稿を見逃さないようにしたい。
- ユーザーとして、誰が自分をフォローしているか知りたい。
- ユーザーとして、興味がなくなった人のフォローを外したい。

## 3. 画面・UI

| 画面 | パス | 主な要素 |
| --- | --- | --- |
| ユーザープロフィール | `/users/:username` | アイコン、表示名、自己紹介、投稿数・フォロー数・フォロワー数、フォローボタン |
| フォロー中一覧 | `/users/:username/following` | ユーザーリスト（各行にフォローボタン） |
| フォロワー一覧 | `/users/:username/followers` | ユーザーリスト（各行にフォローボタン） |
| タイムライン | `/timeline` | フォロー中ユーザーの新着投稿 |

- フォローボタンは「フォローする」⇔「フォロー中」を切り替えます（楽観的更新）。
- 「フォロー中」ボタンにカーソルを乗せると「フォロー解除」と表示します。
- 自分のプロフィールではフォローボタンの代わりに「プロフィール編集」ボタンを表示します。
- タイムラインが空の場合は「おすすめユーザー」や新着一覧への導線を表示します。

## 4. API 仕様

| メソッド | パス | 認証 | 概要 |
| --- | --- | --- | --- |
| GET | `/users/{username}` | 不要 | プロフィール（件数・自分がフォローしているか） |
| PUT | `/users/{username}/follow` | 必要 | フォローする |
| DELETE | `/users/{username}/follow` | 必要 | フォロー解除 |
| GET | `/users/{username}/following` | 不要 | フォロー中一覧 |
| GET | `/users/{username}/followers` | 不要 | フォロワー一覧 |
| GET | `/timeline` | 必要 | フォロー中ユーザーの新着投稿 |

### 4.1 プロフィール `GET /users/{username}`

```json
{
  "id": 42,
  "username": "taro_trip",
  "displayName": "旅するタロウ",
  "bio": "週末は温泉めぐり♨",
  "avatarUrl": null,
  "spotCount": 28,
  "followingCount": 15,
  "followerCount": 120,
  "followedByMe": true
}
```

### 4.2 フォロー `PUT /users/{username}/follow` / 解除 `DELETE /users/{username}/follow`

レスポンス `200 OK`:

```json
{ "username": "taro_trip", "following": true, "followerCount": 121 }
```

PUT・DELETE とも冪等です。

### 4.3 一覧 `GET /users/{username}/followers?cursor=&limit=20`

```json
{
  "items": [
    { "id": 7, "username": "hanako", "displayName": "はなこ", "avatarUrl": null, "followedByMe": false }
  ],
  "nextCursor": null
}
```

並び順はフォローした日時の新しい順です。

### 4.4 タイムライン `GET /timeline?cursor=&limit=20`

レスポンスはスポット一覧（[02_spot.md 4.3](02_spot.md#43-一覧-get-spots)）と同じ形式です。

## 5. 業務ルール・バリデーション

| 項目 | ルール |
| --- | --- |
| 自分自身 | フォロー不可（API で 400、DB の CHECK 制約でも防止） |
| 重複 | 同じ相手を二重にフォローできない（主キー `(follower_id, followee_id)`） |
| 承認 | 不要（鍵アカウント機能は初期リリースでは提供しない） |
| タイムライン | フォロー中ユーザーの **公開** 投稿のみ、新着順。自分の投稿は含めない |
| 件数 | 投稿数は公開投稿のみ数える（本人が見る場合は非公開も含める） |
| 削除連動 | ユーザーが削除されるとフォロー関係も自動削除 |

## 6. 関連テーブル

| テーブル | 用途 |
| --- | --- |
| `follows` | フォローする側（`follower_id`）とされる側（`followee_id`） |
| `spots` | タイムラインの取得 |

## 7. エラーコード

| HTTP | コード | 発生条件 |
| --- | --- | --- |
| 400 | `CANNOT_FOLLOW_SELF` | 自分自身をフォロー |
| 401 | `UNAUTHORIZED` | 未ログインでフォロー・タイムライン取得 |
| 404 | `USER_NOT_FOUND` | 存在しないユーザー |

## 8. テスト観点

[品質チェック・テスト計画書 4.5](../qa_test_plan.md#45-フォローfollow) を参照。

## 9. 補足・将来検討

- 鍵アカウント（フォロー承認制）・ブロック機能は将来検討。
- フォローされたときの通知は将来検討。
- おすすめユーザーの表示ロジックは将来検討。
