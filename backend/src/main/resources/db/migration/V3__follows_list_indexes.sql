-- フォロー中一覧・フォロワー一覧を「フォローした日時の新しい順」でカーソルページングするためのインデックス
-- （docs/features/06_follow.md 4.3）。
-- フォロワー側は既存の idx_follows_followee_id（followee_id, follower_id）を置き換える。
-- 先頭列が同じため、フォロワー数のカウントも新しいインデックスで賄える。

CREATE INDEX idx_follows_follower_id_created_at
    ON follows (follower_id, created_at DESC, followee_id DESC);

CREATE INDEX idx_follows_followee_id_created_at
    ON follows (followee_id, created_at DESC, follower_id DESC);

DROP INDEX idx_follows_followee_id;
