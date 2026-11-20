-- ユーザーが作るテーマ別コミュニティ（docs/features/12_communities.md / docs/database_schema.md 3.14・3.15）。

-- 1. コミュニティ
CREATE TABLE communities (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 作成者（オーナー）。オーナーが退会するとコミュニティも削除する
    owner_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          VARCHAR(50)  NOT NULL,
    description   TEXT         NOT NULL,
    -- カテゴリタグ。決まった種類から選ぶだけなので、spots.temple_features（V6）と同じく配列の列で持つ
    categories    VARCHAR(30)[] NOT NULL DEFAULT '{}',
    -- 参加メンバー数（オーナーを含む）。人気順の並べ替えに使うため、community_members のトリガーで保つ
    member_count  INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_communities_name_not_blank     CHECK (char_length(trim(name)) >= 1),
    CONSTRAINT ck_communities_description_length CHECK (char_length(description) BETWEEN 1 AND 1000),
    CONSTRAINT ck_communities_categories         CHECK (
        cardinality(categories) >= 1
        AND categories <@ ARRAY['REGIONAL', 'GOSHUIN', 'ARCHITECTURE_GARDEN', 'BEGINNER_FRIENDLY', 'PHOTOGRAPHY']::VARCHAR(30)[]),
    CONSTRAINT ck_communities_member_count_not_negative CHECK (member_count >= 0)
);
-- 同じ名前のコミュニティは作れない（英字の大文字・小文字は区別しない）
CREATE UNIQUE INDEX uq_communities_name_lower ON communities (lower(name));
-- 一覧（新着順・人気順のカーソルページング）
CREATE INDEX idx_communities_created_at   ON communities (created_at DESC, id DESC);
CREATE INDEX idx_communities_member_count ON communities (member_count DESC, created_at DESC, id DESC);
-- カテゴリでの絞り込み（categories @> ARRAY[...]）とキーワード検索（ILIKE）
CREATE INDEX idx_communities_categories      ON communities USING GIN (categories);
CREATE INDEX idx_communities_name_trgm       ON communities USING GIN (name gin_trgm_ops);
CREATE INDEX idx_communities_description_trgm ON communities USING GIN (description gin_trgm_ops);
CREATE INDEX idx_communities_owner_id        ON communities (owner_id);

-- 2. 参加関係（ユーザーとコミュニティの中間テーブル）
CREATE TABLE community_members (
    community_id  BIGINT      NOT NULL REFERENCES communities (id) ON DELETE CASCADE,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (community_id, user_id)
);
-- メンバー一覧（参加した順のカーソルページング）
CREATE INDEX idx_community_members_community_joined ON community_members (community_id, joined_at, user_id);
-- ユーザーが参加しているコミュニティ
CREATE INDEX idx_community_members_user_id ON community_members (user_id);

CREATE FUNCTION communities_maintain_member_count() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE communities SET member_count = member_count + 1 WHERE id = NEW.community_id;
        RETURN NEW;
    END IF;
    -- コミュニティの削除に伴う連鎖削除では、コミュニティの行はもう無いため何も更新されない
    UPDATE communities SET member_count = member_count - 1 WHERE id = OLD.community_id;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_community_members_member_count
    AFTER INSERT OR DELETE ON community_members
    FOR EACH ROW EXECUTE FUNCTION communities_maintain_member_count();

-- 3. コミュニティ内のトピック（内輪トーク）は掲示板（V7）のトピック・返信をそのまま使う。
--    community_id が NULL のトピックはこれまでどおり「お寺コミュニティ広場」に出る。
ALTER TABLE board_topics ADD COLUMN community_id BIGINT REFERENCES communities (id) ON DELETE CASCADE;

-- 広場の一覧はコミュニティ外のトピックだけを読むため、部分インデックスに置き換える
DROP INDEX idx_board_topics_last_activity;
CREATE INDEX idx_board_topics_last_activity
    ON board_topics (last_activity_at DESC, id DESC)
    WHERE community_id IS NULL;
CREATE INDEX idx_board_topics_community_last_activity
    ON board_topics (community_id, last_activity_at DESC, id DESC)
    WHERE community_id IS NOT NULL;
