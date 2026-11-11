-- 負荷テスト用のシードデータ（docs/qa_test_plan.md 6章）。
--
-- ユーザー1万・スポット10万と、行ってみたい！・コメント・フォロー・ステータスを投入する。
-- バックエンドを起動して Flyway のマイグレーションが済んだ DB に対して実行する。
--
-- !! 既存のユーザー・投稿をすべて削除する。負荷テスト専用の DB にだけ実行すること !!
--
-- 決まりごと（k6 のスクリプト perf/k6/load-test.js が前提にしている）:
--   - ユーザー: id 1〜10000、メールアドレス perf{id}@example.com、パスワード Passw0rd123
--   - スポット: id 1〜100000。id が 10 の倍数のものは非公開、それ以外は公開
--   - 投稿日時は id が小さいほど新しい（5分おき、約1年分）

\set ON_ERROR_STOP on
\timing on

CREATE EXTENSION IF NOT EXISTS pgcrypto;

TRUNCATE users, spots, comments RESTART IDENTITY CASCADE;

-- ユーザー（パスワードはアプリと同じ BCrypt コスト10。全員同じハッシュを使う）
INSERT INTO users (email, username, password_hash, display_name, bio, created_at, updated_at)
SELECT 'perf' || i || '@example.com',
       'perf_user_' || i,
       h.hash,
       '負荷テスト' || i,
       CASE WHEN i % 3 = 0 THEN '週末は旅に出ています。' END,
       timestamptz '2025-10-01' + i * interval '1 minute',
       timestamptz '2025-10-01' + i * interval '1 minute'
FROM generate_series(1, 10000) AS i,
     (SELECT crypt('Passw0rd123', gen_salt('bf', 10)) AS hash) AS h;

-- スポット（id が 10 の倍数は非公開）
INSERT INTO spots (user_id, title, location_name, impression, visited_on, visibility, created_at, updated_at)
SELECT 1 + floor(random() * 10000)::int,
       (ARRAY['朝の', '夕暮れの', '雨の日の', '冬の', '春の', '夏の', '秋の'])[1 + floor(random() * 7)::int]
           || (ARRAY['温泉', '神社', 'カフェ', '展望台', '市場', '滝', '古い町並み', '美術館', 'ラーメン屋', '海辺'])[1 + floor(random() * 10)::int]
           || ' #' || i,
       (ARRAY['駅前', '旧市街', '湖のほとり', '山頂', '港町', '商店街', '城下町', '島'])[1 + floor(random() * 8)::int] || ' ' || i,
       repeat('景色がとても良く、また行きたいと思いました。', 1 + floor(random() * 10)::int),
       date '2026-11-01' - floor(random() * 700)::int,
       CASE WHEN i % 10 = 0 THEN 'PRIVATE' ELSE 'PUBLIC' END,
       timestamptz '2026-11-01' - i * interval '5 minutes',
       timestamptz '2026-11-01' - i * interval '5 minutes'
FROM generate_series(1, 100000) AS i;

-- エリアタグ（都道府県・国や地域を1〜2個）
WITH tags AS (SELECT array_agg(id ORDER BY id) AS ids FROM area_tags WHERE level = 2)
INSERT INTO spot_area_tags (spot_id, area_tag_id)
SELECT s.id, tags.ids[1 + floor(random() * array_length(tags.ids, 1))::int]
FROM spots s, tags
UNION ALL
SELECT s.id, tags.ids[1 + floor(random() * array_length(tags.ids, 1))::int]
FROM spots s, tags
WHERE random() < 0.3
ON CONFLICT DO NOTHING;

-- 行ってみたい！（約50万件。新しい投稿・一部の人気投稿に偏らせる）
INSERT INTO spot_likes (user_id, spot_id, created_at)
SELECT 1 + floor(random() * 10000)::int,
       1 + floor(power(random(), 3) * 100000)::int,
       timestamptz '2026-11-01' - random() * interval '300 days'
FROM generate_series(1, 500000)
ON CONFLICT DO NOTHING;

-- コメント（約20万件）
INSERT INTO comments (spot_id, user_id, body, created_at, updated_at)
SELECT x.spot_id, 1 + floor(random() * 10000)::int, '素敵な場所ですね！ ' || x.n, x.at, x.at
FROM (SELECT n,
             1 + floor(power(random(), 2) * 100000)::int AS spot_id,
             timestamptz '2026-11-01' - random() * interval '300 days' AS at
      FROM generate_series(1, 200000) AS n) AS x;

-- フォロー（約20万件。一部の人気ユーザーに偏らせる）
INSERT INTO follows (follower_id, followee_id, created_at)
SELECT f.follower, f.followee, timestamptz '2026-11-01' - random() * interval '300 days'
FROM (SELECT 1 + floor(random() * 10000)::int AS follower,
             1 + floor(power(random(), 2) * 10000)::int AS followee
      FROM generate_series(1, 200000)) AS f
WHERE f.follower <> f.followee
ON CONFLICT DO NOTHING;

-- ステータス（約10万件）
INSERT INTO user_spot_statuses (user_id, spot_id, status, visited_at, created_at, updated_at)
SELECT x.user_id, x.spot_id, x.status,
       CASE WHEN x.status = 'VISITED' THEN x.at END, x.at, x.at
FROM (SELECT 1 + floor(random() * 10000)::int AS user_id,
             1 + floor(random() * 100000)::int AS spot_id,
             CASE WHEN random() < 0.5 THEN 'VISITED' ELSE 'WANT_TO_GO' END AS status,
             timestamptz '2026-11-01' - random() * interval '300 days' AS at
      FROM generate_series(1, 100000)) AS x
ON CONFLICT DO NOTHING;

ANALYZE;

SELECT (SELECT count(*) FROM users) AS users,
       (SELECT count(*) FROM spots) AS spots,
       (SELECT count(*) FROM spot_area_tags) AS spot_area_tags,
       (SELECT count(*) FROM spot_likes) AS likes,
       (SELECT count(*) FROM comments) AS comments,
       (SELECT count(*) FROM follows) AS follows,
       (SELECT count(*) FROM user_spot_statuses) AS statuses;
