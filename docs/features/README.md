# 機能別ドキュメント

TripDiary の各機能の仕様をまとめたドキュメントです。全体の要件は [要件定義書](../requirements.md)、テーブル定義は [DB 設計書](../database_schema.md) を参照してください。

| # | 機能 | 機能ID | 主なテーブル |
| --- | --- | --- | --- |
| 1 | [ユーザー認証](01_auth.md) | AUTH | `users`, `refresh_tokens` |
| 2 | [スポット投稿](02_spot.md) | SPOT | `spots`, `spot_area_tags` |
| 3 | [写真アップロード](03_photo_upload.md) | PHOTO | `spot_photos` |
| 4 | [行ってみたい！（いいね）](04_like.md) | LIKE | `spot_likes` |
| 5 | [コメント](05_comment.md) | CMT | `comments` |
| 6 | [フォロー / フォロワー](06_follow.md) | FLW | `follows` |
| 7 | [エリアタグ絞り込み検索](07_area_search.md) | SRCH | `area_tags`, `spot_area_tags` |
| 8 | [スポットのステータス管理](08_spot_status.md) | STS | `user_spot_statuses` |

## 各ドキュメントの構成

1. 概要
2. ユーザーストーリー
3. 画面・UI
4. API 仕様
5. 業務ルール・バリデーション
6. 関連テーブル
7. エラーコード
8. テスト観点
9. 補足・将来検討

## 共通仕様

- API のベースパスは `/api/v1`、リクエスト / レスポンスは JSON（`camelCase`）です。
- 日時は ISO 8601（UTC）で返し、画面では JST で表示します。
- 認証が必要な API は `Authorization: Bearer <アクセストークン>` ヘッダが必須です。
- エラーは共通形式で返します。

```json
{
  "code": "VALIDATION_ERROR",
  "message": "入力内容に誤りがあります",
  "details": [{ "field": "title", "message": "タイトルは必須です" }]
}
```

| HTTP ステータス | 共通エラーコード | 意味 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 入力値の検証エラー |
| 401 | `UNAUTHORIZED` | 未認証・トークン不正 |
| 401 | `TOKEN_EXPIRED` | アクセストークンの有効期限切れ |
| 403 | `FORBIDDEN` | 権限なし（他人のリソースの操作など） |
| 404 | `*_NOT_FOUND` | リソースが存在しない |
| 500 | `INTERNAL_ERROR` | サーバー内部エラー（レスポンスに `requestId` を含める） |
