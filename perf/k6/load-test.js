// 負荷テスト（docs/qa_test_plan.md 6章）。perf/seed.sql を投入した環境に対して実行する。
//
//   k6 run perf/k6/load-test.js                      # 5シナリオを順番に（約52分）
//   k6 run -e SCENARIO=detail perf/k6/load-test.js   # 1シナリオだけ
//   k6 run -e SMOKE=true perf/k6/load-test.js        # 動作確認（各シナリオ 2 VU・30秒）
//
// 環境変数: BASE_URL（既定 http://localhost:8080）、SCENARIO（all / latest / search / detail / login / mixed）、
//           SMOKE（true なら動作確認用の小さな負荷）、THINK_TIME（1リクエストごとの待ち時間の平均秒数。既定 1）
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const SMOKE = __ENV.SMOKE === 'true'
const ONLY = __ENV.SCENARIO || 'all'
const THINK_TIME = Number(__ENV.THINK_TIME || 1)

// perf/seed.sql の決まりごと
const USERS = 10000
const SPOTS = 100000
const PASSWORD = 'Passw0rd123'

/** 計画書 6.1 のシナリオと合格基準。順番に実行する（間に30秒の休み）。 */
const PLAN = [
  { name: 'latest', exec: 'latest', vus: 100, minutes: 10, p95: 500, errorRate: 0.01 },
  { name: 'search', exec: 'search', vus: 100, minutes: 10, p95: 500, errorRate: 0.01 },
  { name: 'detail', exec: 'detail', vus: 100, minutes: 10, p95: 300, errorRate: 0.01 },
  { name: 'login', exec: 'login', vus: 20, minutes: 5, p95: 800, errorRate: 0.01 },
  { name: 'mixed', exec: 'mixed', vus: 100, minutes: 15, p95: 500, errorRate: 0.01 },
]

const selected = PLAN.filter((s) => ONLY === 'all' || s.name === ONLY)
if (selected.length === 0) {
  throw new Error(`SCENARIO は all / ${PLAN.map((s) => s.name).join(' / ')} のいずれかを指定してください`)
}

const GAP_SECONDS = 30
const SMOKE_SECONDS = 30

function durationSeconds(s) {
  return SMOKE ? SMOKE_SECONDS : s.minutes * 60
}

const scenarios = {}
const thresholds = {}
let offset = 0
for (const s of selected) {
  scenarios[s.name] = {
    executor: 'constant-vus',
    exec: s.exec,
    vus: SMOKE ? 2 : s.vus,
    duration: `${durationSeconds(s)}s`,
    startTime: `${offset}s`,
    gracefulStop: '30s',
  }
  thresholds[`http_req_duration{scenario:${s.name}}`] = [`p(95)<${s.p95}`]
  thresholds[`http_req_failed{scenario:${s.name}}`] = [`rate<${s.errorRate}`]
  // 集計表のためにシナリオごとの件数も出す（判定には使わない）
  thresholds[`http_reqs{scenario:${s.name}}`] = ['count>=0']
  offset += durationSeconds(s) + GAP_SECONDS
}

// API 別の集計（判定には使わない。submetric を作るために閾値を置く）
const ENDPOINTS = [
  'GET /spots',
  'GET /spots?areaIds&sort=newest',
  'GET /spots?areaIds&sort=popular',
  'GET /spots/{id}',
  'GET /spots/{id}/comments',
  'GET /timeline',
  'POST /auth/login',
  'PUT /spots/{id}/like',
  'DELETE /spots/{id}/like',
  'POST /spots',
  'POST /spots/{id}/comments',
]
for (const name of ENDPOINTS) {
  thresholds[`http_req_duration{name:${name}}`] = ['max>=0']
}

export const options = {
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // 想定どおりの 404 などは各リクエストで扱う。ステータスに関係なくレスポンスを待つ
  discardResponseBodies: false,
}

// ------------------------------------------------------------ 共通

const JSON_HEADERS = { 'Content-Type': 'application/json' }

function randomInt(min, max) {
  return min + Math.floor(Math.random() * (max - min + 1))
}

/** 公開スポットの ID（id が 10 の倍数は非公開）。新しい投稿ほど見られやすくする。 */
function publicSpotId() {
  for (;;) {
    const id = 1 + Math.floor(Math.pow(Math.random(), 2) * SPOTS)
    if (id % 10 !== 0) return id
  }
}

function think() {
  // 平均 THINK_TIME 秒（0.5〜1.5 倍）
  sleep(THINK_TIME * (0.5 + Math.random()))
}

function get(path, name, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  return http.get(`${BASE_URL}${path}`, { headers, tags: { name } })
}

function loginAs(userId) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: `perf${userId}@example.com`, password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'POST /auth/login' } },
  )
  check(res, { 'ログイン 200': (r) => r.status === 200 })
  return res.status === 200 ? res.json('accessToken') : null
}

/** 新着一覧を1〜3ページ（カーソルでたどる）。 */
function browseLatest(token) {
  let cursor = null
  const pages = randomInt(1, 3)
  for (let i = 0; i < pages; i++) {
    const query = cursor ? `?limit=20&cursor=${cursor}` : '?limit=20'
    const res = get(`/api/v1/spots${query}`, 'GET /spots', token)
    check(res, { '新着一覧 200': (r) => r.status === 200 })
    if (res.status !== 200) return
    cursor = res.json('nextCursor')
    if (!cursor) return
    think()
  }
}

// ------------------------------------------------------------ セットアップ

/** 検索に使うエリアタグ（地方と都道府県・国や地域）を取得する。 */
export function setup() {
  const res = http.get(`${BASE_URL}/api/v1/areas`)
  if (res.status !== 200) {
    throw new Error(`エリアタグを取得できません（${res.status}）。バックエンドとシードを確認してください`)
  }
  const regions = res.json()
  const regionIds = regions.map((r) => r.id)
  const prefectureIds = regions.flatMap((r) => r.children.map((c) => c.id))
  const probe = get('/api/v1/spots/1', 'setup')
  if (probe.status !== 200) {
    throw new Error('スポット id=1 が見つかりません。perf/seed.sql を投入してください')
  }
  return { regionIds, prefectureIds }
}

// ------------------------------------------------------------ シナリオ

/** 新着一覧の閲覧（未ログイン）。 */
export function latest() {
  browseLatest(null)
  think()
}

/** エリアタグ検索（都道府県を1〜2個、または地方。新着順・人気順）。 */
export function search(data) {
  const ids =
    Math.random() < 0.3
      ? [data.regionIds[randomInt(0, data.regionIds.length - 1)]]
      : Array.from({ length: randomInt(1, 2) }, () => data.prefectureIds[randomInt(0, data.prefectureIds.length - 1)])
  const sort = Math.random() < 0.5 ? 'newest' : 'popular'
  const res = get(`/api/v1/spots?limit=20&areaIds=${ids.join(',')}&sort=${sort}`, `GET /spots?areaIds&sort=${sort}`)
  check(res, { '検索 200': (r) => r.status === 200 })
  think()
}

/** スポット詳細（未ログイン）。 */
export function detail() {
  const res = get(`/api/v1/spots/${publicSpotId()}`, 'GET /spots/{id}')
  check(res, { '詳細 200': (r) => r.status === 200 })
  think()
}

/** ログイン。 */
export function login() {
  loginAs(randomInt(1, USERS))
  think()
}

// 混合シナリオでは VU ごとに1回ログインしてトークンを使い回す
let mixedToken = null

/** 混合（閲覧80% / 行ってみたい！10% / 投稿・コメント10%）。ログインしたユーザーとして操作する。 */
export function mixed(data) {
  if (!mixedToken) {
    mixedToken = loginAs(randomInt(1, USERS))
    if (!mixedToken) {
      think()
      return
    }
  }
  const token = mixedToken
  const roll = Math.random()

  if (roll < 0.8) {
    // 閲覧: 新着一覧・詳細＋コメント・検索・タイムライン
    const kind = Math.random()
    if (kind < 0.35) {
      browseLatest(token)
    } else if (kind < 0.7) {
      const id = publicSpotId()
      const res = get(`/api/v1/spots/${id}`, 'GET /spots/{id}', token)
      check(res, { '詳細 200': (r) => r.status === 200 })
      const comments = get(`/api/v1/spots/${id}/comments?limit=20`, 'GET /spots/{id}/comments', token)
      check(comments, { 'コメント一覧 200': (r) => r.status === 200 })
    } else if (kind < 0.85) {
      const tag = data.prefectureIds[randomInt(0, data.prefectureIds.length - 1)]
      const res = get(`/api/v1/spots?limit=20&areaIds=${tag}`, 'GET /spots?areaIds&sort=newest', token)
      check(res, { '検索 200': (r) => r.status === 200 })
    } else {
      const res = get('/api/v1/timeline?limit=20', 'GET /timeline', token)
      check(res, { 'タイムライン 200': (r) => r.status === 200 })
    }
  } else if (roll < 0.9) {
    // 行ってみたい！を付ける・外す
    const id = publicSpotId()
    const method = Math.random() < 0.7 ? 'PUT' : 'DELETE'
    const res = http.request(method, `${BASE_URL}/api/v1/spots/${id}/like`, null, {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: `${method} /spots/{id}/like` },
    })
    check(res, { '行ってみたい！ 200': (r) => r.status === 200 })
  } else if (Math.random() < 0.5) {
    // 投稿（写真なし）
    const res = http.post(
      `${BASE_URL}/api/v1/spots`,
      JSON.stringify({
        title: `負荷テストの投稿 ${Date.now()}`,
        locationName: 'どこか',
        impression: '負荷テストで投稿しました。',
        areaTagIds: [data.prefectureIds[randomInt(0, data.prefectureIds.length - 1)]],
        visibility: 'PUBLIC',
      }),
      { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name: 'POST /spots' } },
    )
    check(res, { '投稿 201': (r) => r.status === 201 })
  } else {
    // コメント
    const res = http.post(
      `${BASE_URL}/api/v1/spots/${publicSpotId()}/comments`,
      JSON.stringify({ body: '負荷テストのコメントです。' }),
      {
        headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` },
        tags: { name: 'POST /spots/{id}/comments' },
      },
    )
    check(res, { 'コメント 201': (r) => r.status === 201 })
  }
  think()
}

// ------------------------------------------------------------ 結果の出力

function metric(data, name) {
  return data.metrics[name] ? data.metrics[name].values : null
}

function ms(value) {
  return value === undefined || value === null ? '-' : `${value.toFixed(0)}ms`
}

/** シナリオごとの p50 / p95 / p99・スループット・エラー率と合否を Markdown の表にする。 */
export function handleSummary(data) {
  const rows = selected.map((s) => {
    const duration = metric(data, `http_req_duration{scenario:${s.name}}`) || {}
    const failed = metric(data, `http_req_failed{scenario:${s.name}}`) || {}
    const reqs = metric(data, `http_reqs{scenario:${s.name}}`) || {}
    const errorRate = failed.rate ?? 0
    const p95ok = (duration['p(95)'] ?? Infinity) < s.p95
    const errorOk = errorRate < s.errorRate
    const rps = (reqs.count ?? 0) / durationSeconds(s)
    return (
      `| ${s.name} | ${SMOKE ? 2 : s.vus} VU・${SMOKE ? `${SMOKE_SECONDS}秒` : `${s.minutes}分`} ` +
      `| ${ms(duration.med)} | ${ms(duration['p(95)'])} | ${ms(duration['p(99)'])} | ${ms(duration.max)} ` +
      `| ${reqs.count ?? 0} | ${rps.toFixed(1)} | ${(errorRate * 100).toFixed(2)}% ` +
      `| p95 < ${s.p95}ms ${p95ok ? '✅' : '❌'} / エラー率 < ${s.errorRate * 100}% ${errorOk ? '✅' : '❌'} |`
    )
  })

  const endpoints = Object.keys(data.metrics)
    .filter((key) => key.startsWith('http_req_duration{name:'))
    .filter((key) => (data.metrics[key].values.max ?? 0) > 0)
  const markdown = [
    `## 負荷テスト結果${SMOKE ? '（動作確認・SMOKE）' : ''}`,
    '',
    `対象: ${BASE_URL} / シナリオ: ${ONLY} / 待ち時間: 平均${THINK_TIME}秒`,
    '',
    '| シナリオ | 負荷 | p50 | p95 | p99 | 最大 | リクエスト数 | 秒間リクエスト | エラー率 | 合格基準 |',
    '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |',
    ...rows,
    '',
    ...(endpoints.length > 0
      ? [
          '### API 別（全シナリオ合計）',
          '',
          '| API | p50 | p95 | p99 | 最大 |',
          '| --- | --- | --- | --- | --- |',
          ...endpoints.map((key) => {
            const v = data.metrics[key].values
            return `| ${key.slice('http_req_duration{name:'.length, -1)} | ${ms(v.med)} | ${ms(v['p(95)'])} | ${ms(v['p(99)'])} | ${ms(v.max)} |`
          }),
          '',
        ]
      : []),
  ].join('\n')

  return {
    stdout: `${markdown}\n`,
    'summary.md': `${markdown}\n`,
    'summary.json': JSON.stringify(data, null, 2),
  }
}
