# TripDiary 🧳

旅先の思い出を写真と感想で記録し、みんなと共有できる **旅行記録・SNSアプリ** です。
自分だけの旅の記録帳として使いながら、他のユーザーの投稿から「次に行きたい場所」を見つけられます。

> 🚧 現在は **設計フェーズ** です。要件定義・DB設計・各種規約のドキュメントを整備しており、アプリケーションコードはまだありません。

---

## ✨ 主な機能

| # | 機能 | 概要 | ドキュメント |
| --- | --- | --- | --- |
| 1 | ユーザー認証 | 新規登録・JWT ログイン・トークン更新・ログアウト | [01_auth.md](docs/features/01_auth.md) |
| 2 | スポット投稿 | 旅行スポットの記録（タイトル・場所・エリア・感想・写真）の作成・閲覧・編集・削除 | [02_spot.md](docs/features/02_spot.md) |
| 3 | 写真アップロード | S3 署名付きURLを使った写真の直接アップロード | [03_photo_upload.md](docs/features/03_photo_upload.md) |
| 4 | 行ってみたい！ | 気になるスポットへのいいね | [04_like.md](docs/features/04_like.md) |
| 5 | コメント | スポットへのコメント投稿・編集・削除 | [05_comment.md](docs/features/05_comment.md) |
| 6 | フォロー | ユーザーのフォロー / フォロワー、フォロー中タイムライン | [06_follow.md](docs/features/06_follow.md) |
| 7 | エリア検索 | エリアタグ（地方 → 都道府県）による絞り込み検索 | [07_area_search.md](docs/features/07_area_search.md) |
| 8 | ステータス管理 | スポットを「訪問済み」「行きたい」リストで管理 | [08_spot_status.md](docs/features/08_spot_status.md) |

---

## 🛠 技術スタック

| 分類 | 技術 |
| --- | --- |
| フロントエンド | React / TypeScript / Vite / Tailwind CSS / shadcn/ui / TanStack Query / React Hook Form + Zod |
| バックエンド | Java 21 / Spring Boot 3 / Gradle / Spring Security（JWT）/ Spring Data JPA / Flyway |
| API 仕様 | springdoc-openapi（Swagger UI: `/swagger-ui/index.html`） |
| データベース | PostgreSQL 16 |
| インフラ | AWS（ALB / ECS Fargate※ / RDS / S3 / CloudFront / CloudWatch Logs / Secrets Manager） |
| ログ | Logback + logstash-logback-encoder（JSON 構造化ログ・MDC） |
| テスト | JUnit 5 / Mockito / Testcontainers / Vitest / React Testing Library / Playwright / k6 |
| CI | GitHub Actions |

※ API の実行基盤は仮置きです。詳細は [アーキテクチャ設計書](docs/architecture.md) を参照してください。

### システム構成（概要）

```mermaid
flowchart LR
    user([ブラウザ]) -->|HTTPS| cf[CloudFront<br/>React SPA]
    user -->|"/api/v1 (JWT)"| alb[ALB]
    alb --> api[Spring Boot API]
    api --> rds[(RDS PostgreSQL)]
    api -.署名付きURL発行.-> s3[(S3 写真)]
    user -->|写真を直接PUT| s3
```

---

## 📚 ドキュメント

### 設計書

| ドキュメント | 内容 |
| --- | --- |
| [要件定義書](docs/requirements.md) | 機能要件・非機能要件・画面一覧・API 概要 |
| [アーキテクチャ設計書](docs/architecture.md) | スキル図（技術スタック）・AWS 構成図・ログ設計・CI/CD |
| [DB 設計書](docs/database_schema.md) | テーブル定義・制約・インデックス・DDL |
| [ER 図](docs/er_diagram.md) | Mermaid による ER 図 |
| [品質チェック・テスト計画書](docs/qa_test_plan.md) | テスト方針・観点・カバレッジ目標・負荷テスト計画 |
| [開発ルール規約](docs/rules.md) | Git 運用・コーディング・API・ログ・テスト規約 |

### 機能別ドキュメント

[docs/features/](docs/features/) に機能ごとの仕様（ユースケース・画面・API・業務ルール・エラー・テスト観点）をまとめています。

---

## 📁 ディレクトリ構成（予定）

```
TripDiary/
├── README.md
├── docs/                 # 設計ドキュメント
│   └── features/         # 機能別ドキュメント
├── backend/              # Spring Boot（Gradle）
├── frontend/             # React + Vite
├── docker-compose.yml    # ローカル開発用（PostgreSQL / LocalStack）
└── .github/workflows/    # CI（ci.yml）/ 負荷テスト（perf.yml, 手動のみ）
```

---

## 🚀 セットアップ（予定）

> 実装開始後に更新します。

```bash
# 1. ローカル用のミドルウェアを起動（PostgreSQL / LocalStack）
docker compose up -d

# 2. バックエンド起動（http://localhost:8080, Swagger UI: /swagger-ui/index.html）
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

# 3. フロントエンド起動（http://localhost:5173）
cd frontend && npm install && npm run dev
```

---

## 🤝 開発ルール（抜粋）

詳細は [開発ルール規約](docs/rules.md) を参照してください。

- 作業は必ず `feature/*` / `fix/*` / `docs/*` などのトピックブランチで行います。
- Pull Request の作成までは誰でも行えますが、**`main` へのマージは開発者がレビューしたうえで手動で行います**（自動マージは禁止）。
- 機能を実装するたびに単体テスト・結合テストを作成し、**オールグリーンを確認してから** PR を作成します。
- CI（GitHub Actions）で単体・結合・E2E テストを自動実行します。**負荷テストは全機能完成時に1回だけ手動で実施します。**
- コミットメッセージは [Conventional Commits](https://www.conventionalcommits.org/ja/v1.0.0/) 形式（例: `feat(spot): スポット投稿APIを追加`）。
