// デモ環境にサンプルデータ（ユーザー・投稿・コメント・行ってみたい！・フォロー・ステータス）を入れる（docs/deploy.md）。
//
//   $env:DEMO_PASSWORD = '...'   # 3ユーザー共通のパスワード（英字と数字を含む8文字以上）。コミットしないこと
//   node infra/demo-seed.mjs https://xxxx.cloudfront.net
//
// API を通して登録するため、DB を空にした直後に1回だけ実行する（同じユーザーがいると登録で失敗する）。
// 写真は付けない（必要ならアプリの画面から追加する）。

const baseUrl = process.argv[2]?.replace(/\/$/, '')
const password = process.env.DEMO_PASSWORD
if (!baseUrl || !password) {
  console.error('使い方: DEMO_PASSWORD を設定して node infra/demo-seed.mjs <アプリの URL>')
  process.exit(1)
}

async function api(method, path, { token, body } = {}) {
  const res = await fetch(`${baseUrl}/api/v1${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : null
}

// ---- エリアタグ（名前 → ID） ----
const areaIds = new Map()
for (const region of await api('GET', '/areas')) {
  areaIds.set(region.name, region.id)
  for (const child of region.children ?? []) areaIds.set(child.name, child.id)
}
const area = (...names) =>
  names.map((name) => {
    const id = areaIds.get(name)
    if (!id) throw new Error(`エリアタグが見つかりません: ${name}`)
    return id
  })

// ---- ユーザー ----
const users = {
  haruka: {
    email: 'haruka@example.com',
    username: 'haruka_trip',
    displayName: '旅するハルカ',
    bio: '週末はだいたいどこかに出かけています。古い町並みと甘いものが好き。',
  },
  kenta: {
    email: 'kenta@example.com',
    username: 'kenta_camera',
    displayName: 'カメラ好きのケンタ',
    bio: '景色を撮りに全国をまわっています。朝焼けと夜景が得意です📷',
  },
  misaki: {
    email: 'misaki@example.com',
    username: 'misaki_onsen',
    displayName: '温泉めぐりのミサキ',
    bio: '温泉と地元のごはんが旅の目的。行った温泉は100か所を超えました♨',
  },
}

for (const user of Object.values(users)) {
  const { accessToken } = await api('POST', '/auth/signup', {
    body: { email: user.email, username: user.username, displayName: user.displayName, password },
  })
  user.token = accessToken
  await api('PATCH', '/users/me', { token: user.token, body: { bio: user.bio } })
  console.log(`ユーザー: ${user.displayName}（${user.email}）`)
}

// ---- 投稿（古い順に作るので、最後のものが新着の先頭になる） ----
const spotDefs = [
  {
    key: 'shirakawago',
    by: 'kenta',
    title: '雪の白川郷ライトアップ',
    locationName: '白川郷（岐阜県大野郡白川村）',
    areaTagIds: area('岐阜'),
    impression:
      '冬のライトアップは抽選制ですが、運よく当たりました。展望台から見下ろす合掌造りの集落は、雪明かりに浮かんで本当に絵本のよう。三脚が使えない場所が多いので、高感度に強いカメラがおすすめです。',
    visitedOn: '2026-01-25',
  },
  {
    key: 'kurashiki',
    by: 'haruka',
    title: '倉敷美観地区で川舟に乗る',
    locationName: '倉敷美観地区（岡山県倉敷市）',
    areaTagIds: area('岡山'),
    impression:
      '白壁の蔵と柳並木のあいだを、川舟でゆっくり進みます。所要20分ほど。降りたあとは、デニムストリートでデニムまんを食べ歩きしました。大原美術館も見ごたえがあって、半日では足りませんでした。',
    visitedOn: '2026-03-21',
  },
  {
    key: 'beppu',
    by: 'misaki',
    title: '別府で地獄めぐりと砂湯',
    locationName: '別府温泉（大分県別府市）',
    areaTagIds: area('大分'),
    impression:
      '海地獄の青さは写真で見るよりずっと鮮やか。地獄蒸しプリンもおいしかったです。締めは竹瓦温泉の砂湯へ。温かい砂に埋まって10分、体の芯までぽかぽかになりました。',
    visitedOn: '2026-04-12',
  },
  {
    key: 'kamikochi',
    by: 'kenta',
    title: '早朝の上高地・大正池',
    locationName: '上高地 大正池（長野県松本市）',
    areaTagIds: area('長野'),
    impression:
      '5時台のバスで入って、朝もやの大正池を撮影。風がなければ穂高連峰が水面に映ります。河童橋まで歩いて約1時間、木道が整備されていて歩きやすかったです。',
    visitedOn: '2026-06-07',
  },
  {
    key: 'taipei',
    by: 'haruka',
    title: '九份の夜と台北の小籠包',
    locationName: '九份（台湾 新北市）',
    areaTagIds: area('台湾'),
    impression:
      '提灯が灯る夕方の九份は、階段がとにかく混むので平日がおすすめ。阿妹茶樓の向かいの茶屋から眺めると落ち着いて見られます。台北に戻って、鼎泰豐の小籠包で締めました。',
    visitedOn: '2026-07-18',
  },
  {
    key: 'kurokawa',
    by: 'misaki',
    title: '黒川温泉で入湯手形めぐり',
    locationName: '黒川温泉（熊本県阿蘇郡南小国町）',
    areaTagIds: area('熊本'),
    impression:
      '入湯手形（1,500円）で3か所の露天風呂に入れます。新明館の洞窟風呂がいちばんのお気に入り。温泉街全体が落ち着いた雰囲気で、浴衣で歩くのが楽しい場所です。',
    visitedOn: '2026-08-30',
  },
  {
    key: 'miyajima',
    by: 'kenta',
    title: '満潮の厳島神社',
    locationName: '厳島神社（広島県廿日市市）',
    areaTagIds: area('広島'),
    impression:
      '潮見表を調べて満潮の時間に合わせて行きました。海に浮かぶ大鳥居と社殿は圧巻です。干潮のときは鳥居の足元まで歩けるので、1日いて両方見るのがおすすめ。',
    visitedOn: '2026-09-23',
  },
  {
    key: 'kyoto',
    by: 'haruka',
    title: '朝の伏見稲荷大社',
    locationName: '伏見稲荷大社（京都府京都市伏見区）',
    areaTagIds: area('京都'),
    impression:
      '7時前に着くと、千本鳥居をほぼ独り占めできました。四ツ辻まで登ると京都市内が見渡せます。山頂までは往復2時間ほど。帰りに稲荷寿司ときつねうどんを食べて大満足でした。',
    visitedOn: '2026-10-12',
  },
  {
    key: 'okinawa',
    by: 'misaki',
    title: '古宇利島のハートロック',
    locationName: '古宇利島（沖縄県国頭郡今帰仁村）',
    areaTagIds: area('沖縄'),
    impression:
      '古宇利大橋を渡るときの海の色に感動。ハートロックは干潮の時間に行くとハートの形がはっきり見えます。島の名物のウニ丼は夏限定なので、次は時期を合わせて行きたいです。',
    visitedOn: '2026-10-26',
  },
  {
    key: 'private',
    by: 'haruka',
    title: '次の連休に行きたい場所メモ',
    locationName: '金沢（石川県金沢市）',
    areaTagIds: area('石川'),
    impression: 'ひがし茶屋街・兼六園・近江町市場。自分だけのメモなので非公開にしています。',
    visitedOn: null,
    visibility: 'PRIVATE',
  },
]

const spots = {}
for (const { key, by, visibility = 'PUBLIC', ...fields } of spotDefs) {
  const spot = await api('POST', '/spots', {
    token: users[by].token,
    body: { ...fields, visibility, photos: [] },
  })
  spots[key] = spot.id
  console.log(`投稿: ${fields.title}（${users[by].displayName}${visibility === 'PRIVATE' ? '・非公開' : ''}）`)
}

// ---- フォロー ----
const follows = [
  ['haruka', 'kenta'],
  ['haruka', 'misaki'],
  ['kenta', 'haruka'],
  ['misaki', 'haruka'],
]
for (const [from, to] of follows) {
  await api('PUT', `/users/${users[to].username}/follow`, { token: users[from].token })
}

// ---- 行ってみたい！ ----
const likes = {
  haruka: ['shirakawago', 'beppu', 'kurokawa', 'okinawa', 'miyajima'],
  kenta: ['kyoto', 'kurashiki', 'okinawa', 'taipei'],
  misaki: ['kyoto', 'kamikochi', 'taipei', 'shirakawago'],
}
for (const [by, keys] of Object.entries(likes)) {
  for (const key of keys) await api('PUT', `/spots/${spots[key]}/like`, { token: users[by].token })
}

// ---- コメント ----
const comments = [
  ['kyoto', 'kenta', '朝の千本鳥居、光の入り方がきれいですよね。次は撮影しに行きます！'],
  ['kyoto', 'haruka', 'ぜひ！7時前なら人がほとんど写らないですよ。'],
  ['okinawa', 'haruka', '海の色がすごそう…ウニ丼の時期、私も調べてみます。'],
  ['okinawa', 'misaki', '6〜8月ごろです。売り切れることもあるので早めの時間がおすすめです！'],
  ['shirakawago', 'misaki', 'ライトアップ当たったんですね、うらやましい！近くの平瀬温泉もいいですよ。'],
  ['taipei', 'kenta', '九份は夕方から夜に変わる時間がいちばん好きです。'],
  ['beppu', 'haruka', '地獄蒸しプリン、気になります🍮'],
]
for (const [key, by, body] of comments) {
  await api('POST', `/spots/${spots[key]}/comments`, { token: users[by].token, body: { body } })
}

// ---- 行った！・行きたい ----
const statuses = [
  ['haruka', 'okinawa', 'WANT_TO_GO'],
  ['haruka', 'kurokawa', 'WANT_TO_GO'],
  ['haruka', 'miyajima', 'VISITED'],
  ['kenta', 'taipei', 'WANT_TO_GO'],
  ['misaki', 'kyoto', 'VISITED'],
]
for (const [by, key, status] of statuses) {
  await api('PUT', `/spots/${spots[key]}/status`, { token: users[by].token, body: { status } })
}

console.log(
  `完了: ユーザー ${Object.keys(users).length}・投稿 ${spotDefs.length}・コメント ${comments.length}・フォロー ${follows.length}`,
)
