-- リフレッシュトークンの「ローテーションで使用済み」を、ログアウト等の「失効」と区別して記録する
-- （docs/features/01_auth.md 5章。使用済み直後の短い猶予期間だけ再利用を許すため）。
-- 既存の行で revoked_at が入っているものは、どちらの理由か区別できないため従来どおり失効扱いのままとする。

ALTER TABLE refresh_tokens ADD COLUMN rotated_at TIMESTAMPTZ;
