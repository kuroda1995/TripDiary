-- お寺の5段階評価と特徴タグ（docs/features/09_temple_review.md / docs/database_schema.md 3.3）。
--
-- どちらも任意項目（お寺以外の旅行記録では使わない）。評価は4項目それぞれ 1〜5 か NULL（未評価）。
-- 特徴タグは決まった5種類から選ぶだけで、名前の変更・追加もアプリのリリースと一緒に行うため、
-- 別テーブルにせず配列の列で持つ（一覧で投稿ごとに読み込むクエリが増えないようにする）。

ALTER TABLE spots
    ADD COLUMN rating_tranquility SMALLINT,
    ADD COLUMN rating_beauty      SMALLINT,
    ADD COLUMN rating_worship     SMALLINT,
    ADD COLUMN rating_access      SMALLINT,
    ADD COLUMN temple_features    VARCHAR(30)[] NOT NULL DEFAULT '{}';

ALTER TABLE spots
    ADD CONSTRAINT ck_spots_rating_tranquility CHECK (rating_tranquility IS NULL OR rating_tranquility BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_spots_rating_beauty      CHECK (rating_beauty      IS NULL OR rating_beauty      BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_spots_rating_worship     CHECK (rating_worship     IS NULL OR rating_worship     BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_spots_rating_access      CHECK (rating_access      IS NULL OR rating_access      BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_spots_temple_features    CHECK (
        temple_features <@ ARRAY['GOSHUIN', 'PARKING', 'GARDEN', 'CULTURAL_PROPERTY', 'QUIET']::VARCHAR(30)[]);

-- 特徴タグでの絞り込み（temple_features @> ARRAY[...]）用
CREATE INDEX idx_spots_public_temple_features
    ON spots USING GIN (temple_features)
    WHERE visibility = 'PUBLIC';
