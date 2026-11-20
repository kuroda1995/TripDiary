-- 「参考になった」とコミュニティ称号（docs/features/11_community_reactions.md / docs/database_schema.md 3.1・3.13）。

-- 1. 参考になった（spot_likes と同じ形。行ってみたい！とは別の反応）
CREATE TABLE spot_helpfuls (
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    spot_id     BIGINT      NOT NULL REFERENCES spots (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, spot_id)
);
CREATE INDEX idx_spot_helpfuls_spot_id ON spot_helpfuls (spot_id);

-- 2. コミュニティ活動数（投稿・コメント・掲示板のトピック・返信の合計）。称号の判定に使う。
--    ユーザー名を表示するすべての画面で称号を出すため、毎回数えずに列で持つ。
--    件数はトリガーで増減させ、連鎖削除（スポットの削除で他人のコメントが消える等）でもずれないようにする。
ALTER TABLE users ADD COLUMN activity_count INTEGER NOT NULL DEFAULT 0;

UPDATE users u
SET activity_count = (SELECT count(*) FROM spots s WHERE s.user_id = u.id)
                   + (SELECT count(*) FROM comments c WHERE c.user_id = u.id)
                   + (SELECT count(*) FROM board_topics t WHERE t.user_id = u.id)
                   + (SELECT count(*) FROM board_replies r WHERE r.user_id = u.id);

ALTER TABLE users ADD CONSTRAINT ck_users_activity_count_not_negative CHECK (activity_count >= 0);

CREATE FUNCTION users_maintain_activity_count() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE users SET activity_count = activity_count + 1 WHERE id = NEW.user_id;
        RETURN NEW;
    END IF;
    -- ユーザーの削除に伴う連鎖削除では、ユーザーの行はもう無いため何も更新されない
    UPDATE users SET activity_count = activity_count - 1 WHERE id = OLD.user_id;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_spots_activity_count
    AFTER INSERT OR DELETE ON spots
    FOR EACH ROW EXECUTE FUNCTION users_maintain_activity_count();
CREATE TRIGGER trg_comments_activity_count
    AFTER INSERT OR DELETE ON comments
    FOR EACH ROW EXECUTE FUNCTION users_maintain_activity_count();
CREATE TRIGGER trg_board_topics_activity_count
    AFTER INSERT OR DELETE ON board_topics
    FOR EACH ROW EXECUTE FUNCTION users_maintain_activity_count();
CREATE TRIGGER trg_board_replies_activity_count
    AFTER INSERT OR DELETE ON board_replies
    FOR EACH ROW EXECUTE FUNCTION users_maintain_activity_count();
