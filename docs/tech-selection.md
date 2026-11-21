# TripDiary 技術選定理由・UX/UI へのこだわり

| 項目 | 内容 |
| --- | --- |
| ドキュメント | 技術スタックの選定理由と、品質・UX/UI 向上への活かし方 |
| 想定読者 | 採用担当者・面接官・このリポジトリを初めて読む開発者 |
| 作成日 | 2026-11-21 |
| 関連資料 | [architecture.md](./architecture.md)（構成・設計意図）/ [qa_test_plan.md](./qa_test_plan.md)（テスト計画）/ [rules.md](./rules.md)（開発ルール） |

各技術について「なぜ採用したか」「どう活かしたか」を、実際のコード・設定ファイルの場所とあわせて書いています。

---

## 1. 全体方針

- **「動くデモ」ではなく「運用できるアプリ」を目指す。** 型・テスト・CI・構造化ログ・負荷テストまでを最初から計画に入れ、機能ごとに単体・結合・E2E テストをそろえてから PR を出す（[rules.md](./rules.md)）。
- **定番で情報が多い技術を選ぶ。** チーム開発に入ったときにそのまま活かせること、困ったときに一次情報（公式ドキュメント）で解決できることを優先した。
- **お金をかけずに本番相当の構成を試す。** 有償 APM は使わず、AWS 標準の CloudWatch と Spring Boot の構造化ログで監視する。AWS は Terraform で作り、使い終わったら `terraform destroy` で一括削除する。

---

## 2. フロントエンド：React / TypeScript

| 技術 | バージョン（[package.json](../frontend/package.json)） |
| --- | --- |
| React | 19 |
| TypeScript | 7 |
| Vite | 8 |
| React Router / TanStack Query / React Hook Form + Zod | 8 / 5 / 7 + 4 |
| Tailwind CSS v4 + shadcn/ui（Base UI） | 4 |

### なぜ採用したか

- **React**：求人で最も多く使われている UI ライブラリで、コンポーネント単位で画面を組み立てられる。投稿カード・いいねボタン・評価の星など、同じ部品を一覧・詳細・マイページで使い回す本アプリに合っている。
- **TypeScript**：API のレスポンス型（[features/*/types.ts](../frontend/src/features/spots/types.ts)）を定義しておくことで、バックエンドの項目追加（例：お寺の評価 `ratings`）を画面側で書き漏らすと型チェックで気付ける。CI でも `npm run typecheck` を必須にしている。
- **TanStack Query**：サーバーのデータ（一覧・詳細・件数）のキャッシュと再取得を任せ、画面のコードを「何を表示するか」に集中させるため。いいね・「行きたい」などを押した後は、関係するキャッシュだけを更新・再取得する。
- **React Hook Form + Zod**：入力チェックを Zod のスキーマ1か所にまとめ、画面ですぐエラーを出す。サーバー側の入力エラー（`details`）も同じ項目の下に表示する。
- **shadcn/ui（Base UI）+ Tailwind CSS**：アクセシビリティに配慮した部品（キーボード操作・読み上げ）を土台に、見た目は Tailwind で自由に調整できるため。

### 品質・UX への活かし方

- **機能単位のディレクトリ構成**（[frontend/src/features/](../frontend/src/features/)）。`auth` / `spots` / `communities` などの機能ごとに `api.ts`・`components/`・`pages/` をまとめ、機能を追加しても他の機能に影響しにくくした。
- **検索条件を URL に持つ。** 検索（`?features=GOSHUIN,GARDEN`）やコミュニティ一覧（`?q=富山&category=REGIONAL&sort=popular`）は条件を URL のクエリにし、共有・ブラウザの戻る操作で同じ結果に戻れるようにした。
- **アクセシビリティ。** マイリストのタブは WAI-ARIA の Tabs パターン（左右キーで移動）、評価の星はラジオボタン（左右キーで点数を変更、「5段階中4」と読み上げ）で作っている。

---

## 3. motion（旧 Framer Motion）によるマイクロインタラクション

### なぜ採用したか

- 押した・保存できたという **手応え** を、操作の邪魔にならない短い動きで伝えたかった。CSS の `transition` だけでは「押し込んで少し行き過ぎて戻る」スプリングの動きや、画面に入ったときに一度だけ再生する動きを書きにくい。
- motion は React のコンポーネントとして宣言的に書け（`whileTap` / `whileHover` / `whileInView`）、OS の「視差効果を減らす」設定への対応も1行でできる。

### 工夫した点

| 動き | 場所 | 意図 |
| --- | --- | --- |
| タップで弾む | [TapButton.tsx](../frontend/src/components/TapButton.tsx)・[StatusSelect.tsx](../frontend/src/features/status/components/StatusSelect.tsx) | 「行ってみたい！」「参考になった」「行きたい」などのトグルを押した瞬間に 0.88 倍に縮み、スプリングで戻る。押したことが指先で分かる |
| カードのホバー | [SpotCard.tsx](../frontend/src/features/spots/components/SpotCard.tsx) | カードを 4px 浮かせ、カバー写真を 1.05 倍にズーム。ホバーはタッチ端末では発火しないため、スマホで誤作動しない |
| 一覧のフェードイン | [SpotList.tsx](../frontend/src/features/spots/components/SpotList.tsx)・[MyStatusList.tsx](../frontend/src/features/status/components/MyStatusList.tsx) | 画面に入ったカードを下からふわっと表示し、0.08 秒ずつずらして順番に出す |

- **動きの値を1か所にまとめた**（[lib/motion.ts](../frontend/src/lib/motion.ts)）。スプリングの硬さや縮み具合を共通の定数にし、画面ごとに動きの速さがばらつかないようにした。
- **待たせない工夫。** フェードインの時間差は「画面に一度に入る枚数（4枚）」までにとどめ、「もっと見る」で追加したカードや下の方のカードを長く待たせない。`viewport: { once: true }` で一度だけ再生し、スクロールを戻したときのチラつきを防いでいる。
- **動きが苦手な人への配慮。** アプリ全体を `<MotionConfig reducedMotion="user">` で包み（[main.tsx](../frontend/src/main.tsx)）、OS で「視差効果を減らす」を設定している人には動きを抑える。
- **読み上げとの両立。** カバー写真はタイトルのリンクと内容が重なるため `alt=""` の装飾扱いにし、アニメーションを付けても読み上げが増えないようにした。

---

## 4. バックエンド・DB：Java 21 / Spring Boot 4 / PostgreSQL 16

| 技術 | 用途 |
| --- | --- |
| Java 21（LTS）/ Spring Boot 4 | REST API（Web MVC・Security・Data JPA・Validation・Actuator） |
| Spring Security + JWT | ステートレス認証。アクセストークン15分・リフレッシュトークン14日（HttpOnly Cookie、使うたびに交換） |
| Flyway | スキーマのバージョン管理（V1〜V9） |
| PostgreSQL 16 | 本番は RDS |

### なぜ採用したか

- **Java / Spring Boot**：業務システムで広く使われ、認証・トランザクション・入力チェックなどの定番機能がそろっている。Spring Boot 3.x は OSS サポートが終わるため、4.x を採用した。
- **JWT によるステートレス認証**：サーバーがセッションを持たないため、ALB の後ろで API を何台にでも増やせる。アクセストークンはメモリだけに持ち、リフレッシュトークンは JavaScript から読めない HttpOnly Cookie にして、盗まれにくくした（[01_auth.md](./features/01_auth.md)）。
- **PostgreSQL**：配列型・GIN インデックス・部分インデックス・トリガー・CHECK 制約など、データの正しさと検索の速さを DB 側で担保できる機能が豊富。お寺の特徴タグやコミュニティのカテゴリを配列の列で持ち、GIN インデックスで絞り込む設計はこの機能を活かしている（[architecture.md 7章](./architecture.md#7-設計意図)）。
- **Flyway**：スキーマの変更を SQL ファイルとして履歴に残し、ローカル・CI・AWS で同じ手順で DB を作れる。

### 品質への活かし方

- **入力チェックを二重にする。** API では Bean Validation、DB では CHECK 制約（例：評価は 1〜5、特徴タグは決まった5種類）で同じルールを保証し、アプリのバグがあっても不正なデータが入らないようにした。
- **N+1 問題をテストで防ぐ。** 一覧取得で SQL の発行回数が件数に比例しないことを、Hibernate の統計で数える結合テスト（[SpotQueryCountIntegrationTest](../backend/src/test/java/com/tripdiary/spot/SpotQueryCountIntegrationTest.java)）で確認している。
- **負荷テストの結果から改善する。** k6 の負荷テスト（[perf_test_report.md](./perf_test_report.md)）で人気順検索が遅いと分かったため、いいね数を `spots.like_count` 列にトリガーで保つようにし、該当 SQL を 385ms → 0.1ms に短縮した。

---

## 5. AWS（インフラ・S3 画像アップロード）

### なぜ採用したか

- 実務で最も使われているクラウドで、ALB・ECS Fargate・RDS・S3・CloudFront という定番構成を一通り経験できるため。
- **ECS Fargate**：コンテナを動かすだけでよく、EC2 の OS 管理が不要。
- **Terraform**：AWS のリソースをコードで管理し、同じ環境を何度でも作り直せるようにした。デモ環境は使い終わったら `terraform destroy` で一括削除し、費用を抑えた（[deploy.md](./deploy.md)）。

### S3 画像アップロードの工夫

写真は **署名付き URL** でブラウザから S3 へ直接アップロードする（[architecture.md 4.2](./architecture.md#42-写真アップロードフロー)）。

- **API サーバーの負荷を減らす。** 画像のバイナリが API を通らないため、大きな写真が続いても API のメモリ・通信量を圧迫しない。
- **アップロードできるものを絞る。** 署名付き URL は5分で失効し、形式（JPEG / PNG / WebP / HEIC）とサイズの条件を付けて発行する。投稿時には、写真のキーが自分のユーザーのフォルダ（`spots/{userId}/…`）のものかを API で検証し、他人の写真を自分の投稿に付けられないようにした。
- **使われなかった写真を掃除する。** アップロードしたまま投稿しなかった写真は、定期処理で S3 から削除する（`OrphanPhotoCleanerIntegrationTest` で確認）。
- **ローカルでも同じ動きを試す。** ローカルと CI では S3 の代わりに S3Mock を Docker で動かし、本番と同じ「署名付き URL に PUT する」流れを E2E テストで確認している。

> デモ環境は 2026-11-11 に構築して動作確認し、確認後に削除した（現在は停止中）。再構築は `infra/deploy.ps1` で行える。

---

## 6. Swagger（springdoc-openapi）

### なぜ採用したか

- Controller のアノテーションから API 仕様書（Swagger UI）を **自動生成** するため、コードと仕様書がずれない。
- フロントエンドを作るときや、面接官が API を確認するときに、ブラウザだけでリクエストを試せる。

### 活かし方

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`、仕様 JSON: `/v3/api-docs`。
- JWT の認証方式を定義し（[OpenApiConfig.java](../backend/src/main/java/com/tripdiary/common/config/OpenApiConfig.java)）、Swagger UI の「Authorize」にトークンを入れれば、ログインが必要な API も試せる。
- エラー時のレスポンス（例：ゲストログインが無効なときの `GUEST_LOGIN_DISABLED`）もアノテーションで記載し、どんなエラーが返るかを仕様書で分かるようにした。

---

## 7. 自動テスト：Vitest / Playwright（＋ JUnit / Testcontainers）

### テストの全体像

| 種類 | ツール | 対象 | 規模（2026-11-21 時点） |
| --- | --- | --- | --- |
| BE 単体 | JUnit 5 / Mockito | ドメインのルール・Service | テストクラス 48（うち結合 23） |
| BE 結合 | Testcontainers（本物の PostgreSQL） | API〜DB まで通し | 同上 |
| FE 単体 | Vitest / React Testing Library / MSW | 画面・部品の振る舞い | テストファイル 25 |
| E2E | Playwright | ブラウザから実際の API・DB まで | シナリオファイル 14 |
| 負荷 | k6 | 主要 API | 5シナリオ（全合格・エラー率 0%） |

バックエンドは行カバレッジ 70% 未満でビルドが失敗するようにしている（[build.gradle.kts](../backend/build.gradle.kts)）。

### なぜ採用したか

- **Vitest**：Vite と設定を共有でき、高速に動く。Jest とほぼ同じ書き方なので学習コストが小さい。
- **React Testing Library**：実装の細部ではなく「ユーザーに見えるもの（ボタンの名前・見出し）」で要素を探すため、リファクタリングしてもテストが壊れにくい。読み上げ名で探すことで、アクセシビリティの確認にもなる。
- **MSW**：`fetch` を差し替えるのではなく、ネットワークの層で API をモックする。画面のコードは本番と同じ `fetch` を使ったままテストできる。
- **Playwright**：本物のブラウザで、画面 → API → DB → S3Mock まで通しで確認できる。
- **Testcontainers**：結合テストで H2 などの代用品ではなく本物の PostgreSQL を使い、配列型・トリガー・CHECK 制約まで本番と同じ動きで確認できる。

### モック調整と品質担保の工夫（[src/test/setup.ts](../frontend/src/test/setup.ts)）

- **アニメーションでテストが揺れないようにする。** `MotionGlobalConfig.skipAnimations = true` で motion の動きを即座に最終状態にし、アニメーションの途中の状態や待ち時間でテスト結果が変わらないようにした。
- **jsdom に無い API を補う。** jsdom には `IntersectionObserver` が無く、motion の `whileInView` が発火しないため、一覧のカードが透明（`opacity: 0`）のまま残ってしまう問題があった。監視を始めた要素をすぐ「画面内」と通知する代用クラスを用意して解決した。写真プレビュー用の `URL.createObjectURL` も同様に補っている。
- **未定義の API 呼び出しを失敗にする。** MSW を `onUnhandledFrame: 'error'` で起動し、テストで想定していない API 呼び出しがあればテストを失敗させる。呼び出し漏れ・余計な呼び出しに気付ける。
- **既定の応答をそろえる。** [msw/handlers.ts](../frontend/src/test/msw/handlers.ts) に「API は正常・未ログイン・データ0件・ゲストログインは無効」という既定の応答を用意し、各テストでは必要な部分だけを上書きする。S3 への直接アップロードも、署名付き URL の発行・CORS のプリフライト・PUT までモックしている。
- **テストの後始末。** テストごとにモックの上書き・画面・アクセストークンをリセットし、前のテストの状態が次のテストに漏れないようにした。

---

## 8. GitHub Actions（CI/CD）

### なぜ採用したか

- GitHub と一体で使え、PR の画面でテスト結果を確認できる。公開リポジトリなら無料で使える。

### 活かし方（[.github/workflows/ci.yml](../.github/workflows/ci.yml)）

| ジョブ | 内容 |
| --- | --- |
| Backend | Spotless（書式）・単体・結合（Testcontainers）・カバレッジ判定 |
| Frontend | Prettier・oxlint・型チェック・単体テスト＋カバレッジ・本番ビルド |
| Infra | Terraform の書式・構文チェック（AWS には接続しない） |
| E2E | PostgreSQL と S3Mock をサービスコンテナで起動し、Playwright で通しテスト |

- **PR と全ブランチへの push で自動実行**し、オールグリーンを確認してから開発者がレビュー・手動マージする（自動マージはしない）。
- **負荷テストは CI で常時実行しない。** k6 の負荷テストは `perf.yml`（手動実行の `workflow_dispatch` のみ）に分け、全機能完成時に1回だけ実行した。CI の時間と費用を抑えるため。
- 失敗時に原因を追えるよう、テストレポート・カバレッジ・E2E のレポート（失敗時のトレース）・バックエンドのログを成果物として7日間保存する。

---

## 9. Claude Code（AI コーディングエージェント）

### なぜ採用したか

- 設計書・実装・テスト・ドキュメントを一人で一貫して作るため、ペアプログラミングの相手として使った。調査（既存コードの読み込み）やテストケースの洗い出しを任せることで、設計判断とレビューに時間を割けるようにした。

### 品質を落とさないためのルール

AI に任せる範囲と人が判断する範囲を [rules.md](./rules.md) で決め、人間の開発者と同じルールを AI にも適用している。

- **作業は必ずトピックブランチで行い、`main` へのマージは開発者だけが行う。** AI は PR の作成までで、マージはしない。
- **機能ごとに単体・結合テストを書き、オールグリーンを確認してから完了とする。** テストが通らないまま「完了」としない。
- **設計判断は開発者が行う。** 例えば「参考になった」を既存の「行ってみたい！」とは別のテーブルにする、ゲストのパスワード変更を禁止する、といった仕様は開発者が決め、AI はその判断に沿って実装・テストする。
- **変更はすべて PR の差分で開発者がレビューする。** 自分が説明できないコードはマージしない。
