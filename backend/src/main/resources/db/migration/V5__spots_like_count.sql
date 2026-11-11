-- スポットに「行ってみたい！」の件数を持たせる（docs/perf_test_report.md 4章 #1 / docs/database_schema.md 3.3）。
--
-- 人気順の並び替えで、条件に合うスポットすべての件数を毎回数えていたのをやめ、この列とインデックスで並べる。
-- 件数は spot_likes へのトリガーで増減させる。アプリの経路だけでなく、ユーザー削除に伴う連鎖削除
-- （ON DELETE CASCADE）でも件数がずれないよう、アプリではなく DB で保つ。

ALTER TABLE spots ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;

UPDATE spots s
SET like_count = c.cnt
FROM (SELECT spot_id, count(*) AS cnt FROM spot_likes GROUP BY spot_id) c
WHERE c.spot_id = s.id;

ALTER TABLE spots ADD CONSTRAINT ck_spots_like_count_not_negative CHECK (like_count >= 0);

CREATE FUNCTION spot_likes_maintain_like_count() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE spots SET like_count = like_count + 1 WHERE id = NEW.spot_id;
        RETURN NEW;
    END IF;
    -- スポットの削除に伴う連鎖削除では、スポットの行はもう無いため何も更新されない
    UPDATE spots SET like_count = like_count - 1 WHERE id = OLD.spot_id;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_spot_likes_like_count
    AFTER INSERT OR DELETE ON spot_likes
    FOR EACH ROW EXECUTE FUNCTION spot_likes_maintain_like_count();

-- 人気順（公開スポット全体）の並び替え・カーソルページング用。
-- エリアタグで絞り込む場合も、この順に読みながらタグを確かめる計画を選べる
CREATE INDEX idx_spots_public_like_count
    ON spots (like_count DESC, created_at DESC, id DESC)
    WHERE visibility = 'PUBLIC';
