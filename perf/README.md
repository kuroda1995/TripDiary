# 負荷テスト（k6）

計画・合格基準は [docs/qa_test_plan.md 6章](../docs/qa_test_plan.md#6-パフォーマンステスト負荷テスト) を参照してください。
**日々の CI では実行しません**（全機能完成時に1回、手動で実施。[docs/rules.md](../docs/rules.md) 7章）。

| ファイル | 内容 |
| --- | --- |
| [`seed.sql`](seed.sql) | ユーザー1万・スポット10万と、行ってみたい！・コメント・フォロー・ステータスを投入する。**既存のデータを全部消す** |
| [`k6/load-test.js`](k6/load-test.js) | 5シナリオ（新着一覧・検索・詳細・ログイン・混合）を順番に実行し、合格基準を判定する |
| [`../.github/workflows/perf.yml`](../.github/workflows/perf.yml) | GitHub Actions で実行する workflow（`workflow_dispatch` のみ） |
| [`../backend/src/main/resources/application-perf.yml`](../backend/src/main/resources/application-perf.yml) | 負荷テスト用の設定（ログインのレート制限・アクセストークンの有効期限を緩め、ログを INFO に） |

## GitHub Actions で実行する（通常はこちら）

1. GitHub の **Actions** タブ →「負荷テスト（k6）」→ **Run workflow**
2. まず `smoke` にチェックを入れて実行し（各シナリオ 2 VU・30秒、全体で数分）、最後まで通ることを確認する
3. `smoke` を外し、`scenario: all` で実行する（約52分）
4. ジョブのサマリーに結果表と重いクエリが出る。詳細は artifact `perf-results-*` を参照

## ローカルで実行する

開発用の DB を消さないよう、**使い捨ての PostgreSQL を別ポートで起動**して使います。

```bash
# 1. 使い捨ての PostgreSQL（5433 番）
docker run -d --rm --name tripdiary-perf-db -p 5433:5432 \
  -e POSTGRES_DB=tripdiary -e POSTGRES_USER=tripdiary -e POSTGRES_PASSWORD=tripdiary \
  postgres:16-alpine -c shared_preload_libraries=pg_stat_statements

# 2. バックエンド（8081 番。起動時に Flyway でテーブルが作られる）
cd backend && ./gradlew bootJar
DB_URL=jdbc:postgresql://localhost:5433/tripdiary \
  java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,perf --server.port=8081

# 3. シード（別のターミナルで。1分ほど）
docker exec -i tripdiary-perf-db psql -U tripdiary -d tripdiary -v ON_ERROR_STOP=1 < perf/seed.sql

# 4. k6（https://grafana.com/docs/k6/latest/set-up/install-k6/）
k6 run -e BASE_URL=http://localhost:8081 -e SMOKE=true perf/k6/load-test.js

# 5. 片付け
docker stop tripdiary-perf-db
```

k6 の環境変数: `BASE_URL`（既定 `http://localhost:8080`）、`SCENARIO`（`all` / `latest` / `search` / `detail` / `login` / `mixed`）、`SMOKE`（`true` で動作確認用の小さな負荷）、`THINK_TIME`（リクエストごとの待ち時間の平均秒数。既定 1）。
結果はカレントディレクトリの `summary.md` / `summary.json` にも書き出されます。

## シードデータの決まりごと

`k6/load-test.js` はこれを前提にしています。

- ユーザー: id 1〜10000、メールアドレス `perf{id}@example.com`、パスワード `Passw0rd123`
- スポット: id 1〜100000。**id が 10 の倍数のものは非公開**、それ以外は公開。id が小さいほど新しい
- 行ってみたい！・コメント・フォローは、一部の投稿・ユーザーに偏らせている（人気順の並び替えを現実に近づけるため）
