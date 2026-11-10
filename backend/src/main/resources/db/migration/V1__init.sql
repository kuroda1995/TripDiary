-- TripDiary 初期スキーマ（docs/database_schema.md 5章）
-- 適用済みのマイグレーションは編集しないこと。変更は新しいバージョン（V2__...）で行う。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. users
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    username       VARCHAR(30)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(50)  NOT NULL,
    bio            VARCHAR(500),
    avatar_s3_key  VARCHAR(512),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[A-Za-z0-9_]{3,30}$')
);
CREATE UNIQUE INDEX uq_users_email_lower    ON users (lower(email));
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));

-- 2. refresh_tokens
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- 3. spots
CREATE TABLE spots (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title          VARCHAR(100) NOT NULL,
    location_name  VARCHAR(200) NOT NULL,
    impression     TEXT,
    visited_on     DATE,
    visibility     VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC',
    latitude       NUMERIC(9, 6),
    longitude      NUMERIC(9, 6),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_spots_title_not_blank   CHECK (char_length(trim(title)) >= 1),
    CONSTRAINT ck_spots_impression_length CHECK (impression IS NULL OR char_length(impression) <= 5000),
    CONSTRAINT ck_spots_visibility        CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_spots_latitude          CHECK (latitude  IS NULL OR latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_spots_longitude         CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
CREATE INDEX idx_spots_user_id_created_at ON spots (user_id, created_at DESC);
CREATE INDEX idx_spots_public_created_at  ON spots (created_at DESC, id DESC) WHERE visibility = 'PUBLIC';
CREATE INDEX idx_spots_title_trgm         ON spots USING GIN (title gin_trgm_ops);
CREATE INDEX idx_spots_location_trgm      ON spots USING GIN (location_name gin_trgm_ops);

-- 4. spot_photos
CREATE TABLE spot_photos (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spot_id       BIGINT       NOT NULL REFERENCES spots (id) ON DELETE CASCADE,
    s3_key        VARCHAR(512) NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    sort_order    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot_photos_s3_key       UNIQUE (s3_key),
    CONSTRAINT uq_spot_photos_spot_sort    UNIQUE (spot_id, sort_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_spot_photos_sort_order   CHECK (sort_order BETWEEN 0 AND 9),
    CONSTRAINT ck_spot_photos_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/heic'))
);

-- 5. area_tags
CREATE TABLE area_tags (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id   BIGINT      REFERENCES area_tags (id) ON DELETE RESTRICT,
    name        VARCHAR(50) NOT NULL,
    slug        VARCHAR(50) NOT NULL,
    level       SMALLINT    NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_area_tags_slug        UNIQUE (slug),
    CONSTRAINT uq_area_tags_parent_name UNIQUE NULLS NOT DISTINCT (parent_id, name),
    CONSTRAINT ck_area_tags_slug_format CHECK (slug ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_area_tags_level       CHECK (level BETWEEN 1 AND 3)
);
CREATE INDEX idx_area_tags_parent_id ON area_tags (parent_id);

-- 6. spot_area_tags
CREATE TABLE spot_area_tags (
    spot_id      BIGINT NOT NULL REFERENCES spots (id)     ON DELETE CASCADE,
    area_tag_id  BIGINT NOT NULL REFERENCES area_tags (id) ON DELETE RESTRICT,
    PRIMARY KEY (spot_id, area_tag_id)
);
CREATE INDEX idx_spot_area_tags_area_tag_id ON spot_area_tags (area_tag_id, spot_id);

-- 7. spot_likes
CREATE TABLE spot_likes (
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    spot_id     BIGINT      NOT NULL REFERENCES spots (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, spot_id)
);
CREATE INDEX idx_spot_likes_spot_id ON spot_likes (spot_id);

-- 8. comments
CREATE TABLE comments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spot_id     BIGINT      NOT NULL REFERENCES spots (id) ON DELETE CASCADE,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_comments_body_length CHECK (char_length(body) BETWEEN 1 AND 1000)
);
CREATE INDEX idx_comments_spot_id_created_at ON comments (spot_id, created_at, id);
CREATE INDEX idx_comments_user_id            ON comments (user_id);

-- 9. follows
CREATE TABLE follows (
    follower_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id  BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
);
CREATE INDEX idx_follows_followee_id ON follows (followee_id, follower_id);

-- 10. user_spot_statuses
CREATE TABLE user_spot_statuses (
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    spot_id     BIGINT      NOT NULL REFERENCES spots (id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL,
    visited_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, spot_id),
    CONSTRAINT ck_user_spot_statuses_status     CHECK (status IN ('VISITED', 'WANT_TO_GO')),
    CONSTRAINT ck_user_spot_statuses_visited_at CHECK (status = 'VISITED' OR visited_at IS NULL)
);
CREATE INDEX idx_user_spot_statuses_user_status ON user_spot_statuses (user_id, status, updated_at DESC);
