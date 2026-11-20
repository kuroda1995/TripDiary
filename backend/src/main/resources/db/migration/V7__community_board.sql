-- お寺コミュニティ広場（掲示板）のトピックと返信（docs/features/10_community_board.md / docs/database_schema.md 3.11・3.12）。

CREATE TABLE board_topics (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title             VARCHAR(100) NOT NULL,
    body              TEXT         NOT NULL,
    -- 最後に書き込みがあった日時（作成時は created_at、返信のたびに進める）。一覧はこの新しい順
    last_activity_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_board_topics_title_not_blank CHECK (char_length(trim(title)) >= 1),
    CONSTRAINT ck_board_topics_body_length     CHECK (char_length(body) BETWEEN 1 AND 2000)
);
CREATE INDEX idx_board_topics_last_activity ON board_topics (last_activity_at DESC, id DESC);
CREATE INDEX idx_board_topics_user_id       ON board_topics (user_id);

CREATE TABLE board_replies (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic_id    BIGINT      NOT NULL REFERENCES board_topics (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_board_replies_body_length CHECK (char_length(body) BETWEEN 1 AND 1000)
);
-- 返信一覧（古い順のカーソルページング）と、トピックごとの返信数の集計
CREATE INDEX idx_board_replies_topic_id_created_at ON board_replies (topic_id, created_at, id);
CREATE INDEX idx_board_replies_user_id             ON board_replies (user_id);
