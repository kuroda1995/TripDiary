// デモ環境にサンプルデータ（ユーザー・アイコン・投稿・写真・コメント・行ってみたい！・参考になった・フォロー・ステータス・
// お寺の評価付きの参拝記録・お寺コミュニティ広場のトピックと返信・テーマ別コミュニティとメンバー・内輪トーク）を入れる（docs/deploy.md）。
//
//   $env:DEMO_PASSWORD = '...'   # 3ユーザー共通のパスワード（英字と数字を含む8文字以上）。コミットしないこと
//   $env:OWNER_PASSWORD = '...'  # 任意。設定すると、作者が操作する用のアカウント（kuro@example.com）も作る
//   node infra/demo-seed.mjs https://xxxx.cloudfront.net
//
// API を通して登録するため、DB を空にした直後に1回だけ実行する（同じユーザーがいると登録で失敗する）。
//
// 写真は Wikimedia Commons の CC0・パブリックドメインのもの（クレジット表記が不要）を実行時にダウンロードし、
// アプリと同じ手順（署名付きURLの発行 → S3 へ PUT → 投稿に紐付け）で登録する。リポジトリには画像を置かない。
// メタデータは画面と同じ処理（frontend の stripMetadata.ts）で取り除く。

import { detectImageFormat, stripMetadata } from '../frontend/src/features/photos/stripMetadata.ts'

const baseUrl = process.argv[2]?.replace(/\/$/, '')
const password = process.env.DEMO_PASSWORD
const ownerPassword = process.env.OWNER_PASSWORD
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

// ---- 写真（Wikimedia Commons から取得して S3 にアップロード） ----
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
const CONTENT_TYPES = { jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' }

async function downloadCommonsPhoto(fileName, width) {
  // 指定した幅の縮小版（Wikimedia が決まったサイズに丸めるため、少し大きくなることがある）。
  // Wikimedia は自動アクセスに User-Agent を求め、短時間に続けると 429 を返す
  const url = `https://commons.wikimedia.org/wiki/Special:FilePath/${encodeURIComponent(fileName)}?width=${width}`
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(url, { headers: { 'User-Agent': 'demo-seed-script/1.0' } })
    if (res.ok) return new Uint8Array(await res.arrayBuffer())
    if (res.status !== 429 || attempt >= 5) throw new Error(`写真を取得できません: ${fileName} → ${res.status}`)
    await wait(attempt * 10_000)
  }
}

// purpose: SPOT_PHOTO（スポット写真。幅 1600px）/ AVATAR（アイコン。幅 800px）
async function uploadPhoto(token, fileName, purpose = 'SPOT_PHOTO') {
  const bytes = stripMetadata(await downloadCommonsPhoto(fileName, purpose === 'AVATAR' ? 800 : 1600))
  const contentType = CONTENT_TYPES[detectImageFormat(bytes)]
  const { uploadUrl, s3Key } = await api('POST', '/uploads/presigned-url', {
    token,
    body: { purpose, contentType, contentLength: bytes.length },
  })
  const res = await fetch(uploadUrl, { method: 'PUT', headers: { 'Content-Type': contentType }, body: bytes })
  if (!res.ok) throw new Error(`S3 へのアップロードに失敗しました: ${fileName} → ${res.status}`)
  await wait(1_000)
  return s3Key
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
// avatar は Wikimedia Commons のファイル名。架空のユーザーなので人の顔は使わず、自己紹介に合うものにする
const users = {
  haruka: {
    email: 'haruka@example.com',
    username: 'haruka_trip',
    displayName: '旅するハルカ',
    bio: '週末はだいたいどこかに出かけています。古い町並みと甘いものが好き。',
    avatar: 'Sakura-mochi 003.jpg',
  },
  kenta: {
    email: 'kenta@example.com',
    username: 'kenta_camera',
    displayName: 'カメラ好きのケンタ',
    bio: '景色を撮りに全国をまわっています。朝焼けと夜景が得意です📷',
    avatar: 'Canon AE-1 Program (Unsplash).jpg',
  },
  misaki: {
    email: 'misaki@example.com',
    username: 'misaki_onsen',
    displayName: '温泉めぐりのミサキ',
    bio: '温泉と地元のごはんが旅の目的。行った温泉は100か所を超えました♨',
    avatar: '251130 Yubatake, Kusatsu 01.jpg',
  },
}

for (const user of Object.values(users)) {
  const { accessToken } = await api('POST', '/auth/signup', {
    body: { email: user.email, username: user.username, displayName: user.displayName, password },
  })
  user.token = accessToken
  const avatarS3Key = await uploadPhoto(user.token, user.avatar, 'AVATAR')
  await api('PATCH', '/users/me', { token: user.token, body: { bio: user.bio, avatarS3Key } })
  console.log(`ユーザー: ${user.displayName}（${user.email}）`)
}

// ---- 投稿（古い順に作るので、最後のものが新着の先頭になる） ----
// photos は Wikimedia Commons のファイル名（先頭がカバー写真）。作者・ライセンスは docs/deploy.md 3.3 を参照。
const spotDefs = [
  {
    key: 'shirakawago',
    by: 'kenta',
    title: '夏の白川郷で合掌造りめぐり',
    locationName: '白川郷（岐阜県大野郡白川村）',
    areaTagIds: area('岐阜'),
    impression:
      '夏の白川郷は、田んぼと山の緑がまぶしい季節。合掌造りの茅葺き屋根は、近くで見ると想像以上の厚みでした。池のほとりの民家は水面に映り込むので、風のない朝がおすすめ。展望台から見下ろす集落の眺めも外せません。',
    visitedOn: '2025-08-10',
    photos: ['Shirakawa-go - Shirakawa178.jpg'],
  },
  {
    key: 'kurashiki',
    by: 'haruka',
    title: '倉敷美観地区で川舟に乗る',
    locationName: '倉敷美観地区（岡山県倉敷市）',
    areaTagIds: area('岡山'),
    impression:
      '白壁の蔵と柳並木のあいだを、川舟でゆっくり進みます。所要20分ほど。降りたあとは、本町通りの古い町家を眺めながら、デニムまんを食べ歩きしました。大原美術館も見ごたえがあって、半日では足りませんでした。',
    visitedOn: '2025-09-21',
    photos: ['倉敷美観地区3.jpg'],
  },
  {
    key: 'kamikochi',
    by: 'kenta',
    title: '紅葉の上高地・大正池',
    locationName: '上高地 大正池（長野県松本市）',
    areaTagIds: area('長野'),
    impression:
      '紅葉の時期に、5時台のバスで入って朝の大正池を撮影。風がなければ、雪をかぶった穂高連峰と色づいた木々が水面に映ります。河童橋まで歩いて約1時間、木道が整備されていて歩きやすかったです。',
    visitedOn: '2025-10-25',
    photos: ['Kamikochi - Kamikochi6519.jpg', 'Kamikochi - Kamikochi6522.jpg'],
  },
  {
    key: 'beppu',
    by: 'misaki',
    title: '別府で地獄めぐりと砂湯',
    locationName: '別府温泉（大分県別府市）',
    areaTagIds: area('大分'),
    impression:
      '血の池地獄の赤さは写真で見るよりずっと鮮やかで、湯けむりの迫力もすごかったです。海地獄の青との違いも面白い。地獄蒸しプリンもおいしかったです。締めは竹瓦温泉の砂湯へ。温かい砂に埋まって10分、体の芯までぽかぽかになりました。',
    visitedOn: '2026-04-12',
    photos: ['Akaike (Red Pond Hotspring) Beppu Japan.jpg'],
  },
  {
    key: 'taipei',
    by: 'haruka',
    title: '九份の夜と台北の小籠包',
    locationName: '九份（台湾 新北市）',
    areaTagIds: area('台湾'),
    impression:
      '提灯が灯る夕方の九份は、階段がとにかく混むので平日がおすすめ。阿妹茶樓の向かいの茶屋から眺めると落ち着いて見られます。山の上から見下ろす夜景もきれいでした。台北に戻って、鼎泰豐の小籠包で締めました。',
    visitedOn: '2026-07-18',
    photos: ['A-mei Tea House.jpg', 'Night in Jioufen view from Mt. Keelung 20130711.jpg'],
  },
  {
    key: 'kurokawa',
    by: 'misaki',
    title: '黒川温泉で入湯手形めぐり',
    locationName: '黒川温泉（熊本県阿蘇郡南小国町）',
    areaTagIds: area('熊本'),
    impression:
      '入湯手形（1,500円）で3か所の露天風呂に入れます。新明館の洞窟風呂がいちばんのお気に入り。夜は川沿いに明かりが灯って、温泉街全体が幻想的な雰囲気に。浴衣で歩くのが楽しい場所です。',
    visitedOn: '2026-08-30',
    photos: ['Kurokawa-Onsen Light-up.jpg'],
  },
  {
    key: 'miyajima',
    by: 'kenta',
    title: '満潮の厳島神社',
    locationName: '厳島神社（広島県廿日市市）',
    areaTagIds: area('広島'),
    impression:
      '潮見表を調べて満潮の時間に合わせて行きました。夕方の光に染まる、海に浮かぶ大鳥居は圧巻です。干潮のときは鳥居の足元まで歩けるので、1日いて両方見るのがおすすめ。',
    visitedOn: '2026-09-23',
    photos: ['20181111 Itsukushima Shrine torii-2.jpg'],
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
    photos: [
      '20181110 Fushimi Inari Torii 4.jpg',
      '20181110 Fushimi Inari Torii 1.jpg',
      '20181110 Fushimi Inari Torii 5.jpg',
    ],
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
    photos: ['Kouri-Beach.jpg', 'Kouri Island.JPG'],
  },
  // ---- お寺の参拝記録（5段階評価・特徴タグ付き。docs/features/09_temple_review.md） ----
  // 写真は付けない（ライセンスを確認した CC0・パブリックドメインの写真を用意できたら photos に足す）
  {
    key: 'zuiryuji',
    by: 'kenta',
    title: '雪の瑞龍寺で伽藍を撮る',
    locationName: '高岡山 瑞龍寺（富山県高岡市）',
    areaTagIds: area('富山'),
    impression:
      '山門・仏殿・法堂の3棟が国宝の曹洞宗のお寺。回廊にぐるりと囲まれた伽藍は左右対称で、正面から撮ると本当に美しいです。雪の朝は人も少なく、足音が響くほどの静けさでした。御朱印は書き置きでいただきました。',
    visitedOn: '2026-01-18',
    ratings: { tranquility: 5, beauty: 5, worship: 4, access: 4 },
    templeFeatures: ['GOSHUIN', 'PARKING', 'CULTURAL_PROPERTY', 'QUIET'],
  },
  {
    key: 'ryoanji',
    by: 'haruka',
    title: '龍安寺の石庭でぼんやり',
    locationName: '大雲山 龍安寺（京都府京都市右京区）',
    areaTagIds: area('京都'),
    impression:
      '方丈の縁側に座って石庭を眺めていると、あっという間に30分。開門すぐの時間は人が少なく、ゆっくり過ごせました。鏡容池のまわりの散策路も気持ちよかったです。',
    visitedOn: '2026-03-02',
    ratings: { tranquility: 4, beauty: 5, worship: 3, access: 4 },
    templeFeatures: ['GOSHUIN', 'PARKING', 'GARDEN', 'CULTURAL_PROPERTY', 'QUIET'],
  },
  {
    key: 'hasedera',
    by: 'misaki',
    title: '紫陽花の長谷寺と由比ヶ浜の眺め',
    locationName: '海光山 長谷寺（神奈川県鎌倉市）',
    areaTagIds: area('神奈川'),
    impression:
      '紫陽花の季節に行きました。眺望散策路は整理券が出るほどの人気で、早めの時間がおすすめです。見晴台から見下ろす由比ヶ浜がきれい。観音堂の大きな観音さまにも圧倒されました。御朱印は数種類あって迷います。',
    visitedOn: '2026-06-14',
    ratings: { tranquility: 3, beauty: 5, worship: 5, access: 3 },
    templeFeatures: ['GOSHUIN', 'GARDEN'],
  },
  {
    key: 'kiyomizu',
    by: 'haruka',
    title: '紅葉の清水寺、夜間特別拝観',
    locationName: '音羽山 清水寺（京都府京都市東山区）',
    areaTagIds: area('京都'),
    impression:
      'ライトアップされた舞台と紅葉が幻想的でした。ただし坂道も境内もかなり混むので、静かに過ごしたい人は朝いちばんがおすすめ。車で行くより、バスと徒歩のほうが気楽です。御朱印は昼間にいただきました。',
    visitedOn: '2026-11-08',
    ratings: { tranquility: 2, beauty: 5, worship: 4, access: 2 },
    templeFeatures: ['GOSHUIN', 'CULTURAL_PROPERTY'],
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
let photoCount = 0
for (const { key, by, visibility = 'PUBLIC', photos: fileNames = [], ...fields } of spotDefs) {
  const { token } = users[by]
  const photos = []
  for (const fileName of fileNames) {
    photos.push({ s3Key: await uploadPhoto(token, fileName), sortOrder: photos.length })
  }
  const spot = await api('POST', '/spots', { token, body: { ...fields, visibility, photos } })
  spots[key] = spot.id
  photoCount += photos.length
  console.log(
    `投稿: ${fields.title}（${users[by].displayName}・写真 ${photos.length}枚${visibility === 'PRIVATE' ? '・非公開' : ''}）`,
  )
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
  ['shirakawago', 'misaki', '緑の季節の白川郷もいいですね！近くの平瀬温泉もおすすめですよ。'],
  ['taipei', 'kenta', '九份は夕方から夜に変わる時間がいちばん好きです。'],
  ['beppu', 'haruka', '地獄蒸しプリン、気になります🍮'],
  // お寺の投稿でのやり取り
  ['zuiryuji', 'haruka', '雪の瑞龍寺、すごくきれいですね。駐車場は冬でも停めやすかったですか？'],
  ['zuiryuji', 'kenta', '除雪されていて問題なかったです！朝8時台なら空いていました。'],
  ['zuiryuji', 'misaki', '高岡なら雨晴海岸もセットで回れますね。今度行ってみます。'],
  ['kiyomizu', 'misaki', '夜間拝観は何時ごろに行きましたか？混雑が心配で…'],
  ['kiyomizu', 'haruka', '18時すぎに入りました。20時ごろのほうが少し空いていた気がします。'],
  ['hasedera', 'kenta', '見晴台からの写真、いつか撮りに行きたいです📷'],
  ['hasedera', 'misaki', 'ぜひ！紫陽花の時期は平日の朝がおすすめですよ。'],
  ['ryoanji', 'kenta', '石庭は15個の石がどこからも全部は見えないんですよね。'],
]
for (const [key, by, body] of comments) {
  await api('POST', `/spots/${spots[key]}/comments`, { token: users[by].token, body: { body } })
}

// ---- 参考になった ----
const helpfuls = {
  haruka: ['zuiryuji', 'hasedera'],
  kenta: ['kiyomizu', 'ryoanji', 'hasedera'],
  misaki: ['zuiryuji', 'kiyomizu', 'ryoanji'],
}
for (const [by, keys] of Object.entries(helpfuls)) {
  for (const key of keys) await api('PUT', `/spots/${spots[key]}/helpful`, { token: users[by].token })
}

// ---- お寺コミュニティ広場（掲示板。古い順に作るので、最後に返信があったトピックが先頭になる） ----
const topics = [
  {
    by: 'haruka',
    title: '御朱印情報交換',
    body: '最近いただいた御朱印や、限定御朱印の情報を気軽に書き込んでください。書き置きか直書きかも教えてもらえると助かります。',
    replies: [
      ['kenta', '瑞龍寺は書き置きでした。冬は受付が早めに終わるので注意です。'],
      ['misaki', '長谷寺は数種類あって、季節の御朱印もありました。'],
    ],
  },
  {
    by: 'misaki',
    title: 'おすすめのお寺相談：紅葉の時期の京都',
    body: '11月下旬に京都へ行きます。混雑を避けつつ紅葉がきれいなお寺を教えてください。車ではなく電車とバスで回る予定です。',
    replies: [
      ['haruka', '清水寺は夜間拝観がきれいですが、かなり混みます。朝いちばんの東福寺もおすすめです。'],
      ['kenta', '少し足をのばして、大原の三千院は比較的ゆったり見られました。'],
      ['misaki', 'ありがとうございます！朝の東福寺と大原で計画してみます。'],
    ],
  },
  {
    by: 'kenta',
    title: 'お寺で写真を撮るときのマナー',
    body: '撮影禁止の場所や三脚の使用など、気をつけていることを共有しませんか？',
    replies: [['haruka', '堂内は撮影禁止のところが多いので、入口の案内を必ず見るようにしています。']],
  },
]
let replyCount = 0
for (const { by, title, body, replies } of topics) {
  const topic = await api('POST', '/board-topics', { token: users[by].token, body: { title, body } })
  for (const [replyBy, replyBody] of replies) {
    await api('POST', `/board-topics/${topic.id}/replies`, {
      token: users[replyBy].token,
      body: { body: replyBody },
    })
    replyCount += 1
  }
}

// ---- コミュニティ（docs/features/12_communities.md）----
// 作成者（owner）は自動で参加する。members が参加し、メンバー同士でトピックと返信をやり取りする。
// ゲスト（ハルカ）は「限定御朱印情報シェアの会」のオーナー、ほか2つのメンバーで、
// 「はじめてのお寺巡り相談室」には参加していない（参加ボタンを試せるようにする）。
const communities = [
  {
    owner: 'kenta',
    name: '北陸・富山のお寺巡り同好会',
    description:
      '国宝・瑞龍寺や勝興寺など、富山を中心に北陸のお寺をめぐる人の集まりです。\n雪の時期の参拝情報、駐車場、近くのごはん屋さんなど、地元ならではの情報を気軽に交換しましょう。初めての方も歓迎です。',
    categories: ['REGIONAL', 'BEGINNER_FRIENDLY'],
    members: ['haruka', 'misaki'],
    topics: [
      {
        by: 'kenta',
        title: '冬の瑞龍寺・勝興寺を1日で回るルート',
        body: '高岡駅から瑞龍寺 → 高岡大仏 → 勝興寺の順で回ると、バスと電車で1日で回れました。雪の日は勝興寺の唐門まわりが滑りやすいので注意です。',
        replies: [
          ['haruka', '参考になります！勝興寺は伏木駅から歩けますか？'],
          ['kenta', '伏木駅から徒歩10分くらいです。坂が少しあるので、雪の日はゆっくりがおすすめです。'],
          ['misaki', '帰りに氷見まで足をのばして、寒ぶりを食べるのもおすすめですよ。'],
        ],
      },
      {
        by: 'haruka',
        title: '高岡大仏のまわりでおすすめのお昼ごはん',
        body: '来月、高岡大仏に行きます。近くでおすすめのお店があれば教えてください。',
        replies: [['kenta', '大仏から歩いてすぐの町家カフェが落ち着けました。高岡コロッケも名物です。']],
      },
    ],
  },
  {
    owner: 'haruka',
    name: '限定御朱印情報シェアの会',
    description:
      '季節限定・特別拝観の御朱印の情報を共有する会です。\n「いつ・どこで・書き置きか直書きか」を書いてもらえると助かります。いただいた御朱印の自慢も大歓迎！',
    categories: ['GOSHUIN'],
    members: ['misaki', 'kenta'],
    topics: [
      {
        by: 'haruka',
        title: '11月の紅葉限定御朱印まとめ',
        body: '紅葉の時期だけの御朱印を見つけたら、このトピックに書き込んでください。授与の期間や受付時間も書いてもらえると助かります。',
        replies: [
          ['misaki', '鎌倉で季節の絵柄入りの御朱印をいただきました。人気のものは午前中に行くのが安心です。'],
          ['kenta', '冬にまた瑞龍寺へ行くので、御朱印の情報があれば共有します！'],
        ],
      },
      {
        by: 'misaki',
        title: '御朱印帳はお寺用と神社用で分けていますか？',
        body: '最近たくさん集まってきたので迷っています。皆さんはどうしていますか？',
        replies: [
          ['haruka', '私は分けています。お寺によっては、神社の御朱印と同じ帳面だと断られることもあるそうです。'],
          ['kenta', '旅先ごとに1冊にしています。見返すと旅の記録になって楽しいですよ。'],
          ['misaki', 'なるほど…まずはお寺用を1冊作ってみます！'],
        ],
      },
    ],
  },
  {
    owner: 'misaki',
    name: '枯山水・日本庭園を愛でる会',
    description:
      '石と砂で山水を表す枯山水、池泉回遊式の庭園など、お寺の庭が好きな人の集まりです。\nおすすめの庭、空いている時間帯、写真の撮り方などを話しましょう。',
    categories: ['ARCHITECTURE_GARDEN', 'PHOTOGRAPHY'],
    members: ['haruka', 'kenta'],
    topics: [
      {
        by: 'misaki',
        title: '石庭を静かに眺めるならどのお寺？',
        body: '龍安寺の石庭が有名ですが、混雑しているときもあると聞きました。ゆっくり座って眺められるおすすめの庭を教えてください。',
        replies: [
          ['haruka', '龍安寺も開門すぐの時間ならかなり静かでした。30分くらいぼんやりできましたよ。'],
          ['kenta', '大徳寺の塔頭（大仙院など）は小さいながら見ごたえがあって、比較的ゆったり見られます。'],
        ],
      },
      {
        by: 'kenta',
        title: '庭園を撮るときの構図のコツ',
        body: '縁側から撮ると、柱や軒を額縁のように入れると奥行きが出ます。皆さんのコツも教えてください📷',
        replies: [['misaki', '雨上がりは苔の緑が濃くなるので、あえて雨の翌朝を狙っています。']],
      },
    ],
  },
  {
    owner: 'misaki',
    name: 'はじめてのお寺巡り相談室',
    description:
      'お寺巡りを始めたばかりの人のための相談室です。参拝の作法、服装、拝観料のことなど、どんな小さな疑問でも気軽にどうぞ。',
    categories: ['BEGINNER_FRIENDLY'],
    members: ['kenta'],
    topics: [
      {
        by: 'kenta',
        title: 'お寺での参拝の作法を教えてください',
        body: '神社は二礼二拍手一礼ですが、お寺では手を叩かないと聞きました。ほかに気をつけることはありますか？',
        replies: [['misaki', 'お寺では静かに合掌して一礼します。山門をくぐるときに一礼すると気持ちが引き締まりますよ。']],
      },
    ],
  },
]
let communityTopicCount = 0
let communityReplyCount = 0
let membershipCount = 0
for (const { owner, name, description, categories, members, topics: communityTopics } of communities) {
  const community = await api('POST', '/communities', {
    token: users[owner].token,
    body: { name, description, categories },
  })
  for (const member of members) {
    await api('PUT', `/communities/${community.id}/membership`, { token: users[member].token })
    membershipCount += 1
  }
  for (const { by, title, body, replies } of communityTopics) {
    const topic = await api('POST', `/communities/${community.id}/topics`, {
      token: users[by].token,
      body: { title, body },
    })
    communityTopicCount += 1
    for (const [replyBy, replyBody] of replies) {
      await api('POST', `/board-topics/${topic.id}/replies`, {
        token: users[replyBy].token,
        body: { body: replyBody },
      })
      communityReplyCount += 1
    }
  }
  console.log(`コミュニティ: ${name}（オーナー ${users[owner].displayName}・メンバー ${members.length + 1}人）`)
}

// ---- 行った！・行きたい ----
const statuses = [
  ['haruka', 'okinawa', 'WANT_TO_GO'],
  ['haruka', 'kurokawa', 'WANT_TO_GO'],
  ['haruka', 'zuiryuji', 'WANT_TO_GO'],
  ['haruka', 'hasedera', 'WANT_TO_GO'],
  ['kenta', 'kiyomizu', 'VISITED'],
  ['haruka', 'miyajima', 'VISITED'],
  ['kenta', 'taipei', 'WANT_TO_GO'],
  ['misaki', 'kyoto', 'VISITED'],
]
for (const [by, key, status] of statuses) {
  await api('PUT', `/spots/${spots[key]}/status`, { token: users[by].token, body: { status } })
}

// ---- 作者が操作する用のアカウント（OWNER_PASSWORD を設定したときだけ） ----
// アイコンは黒猫（旅先のブライエス湖で撮られたもの）。投稿は持たず、サンプルユーザー3人をフォローしておく（ログインするとタイムラインに投稿が並ぶ）
if (ownerPassword) {
  const owner = { email: 'kuro@example.com', username: 'kuro', displayName: 'クロ' }
  const { accessToken } = await api('POST', '/auth/signup', { body: { ...owner, password: ownerPassword } })
  const avatarS3Key = await uploadPhoto(
    accessToken,
    'Black stray cat of Braies Lake, Trentino-Alto Adige, Italy.jpg',
    'AVATAR',
  )
  await api('PATCH', '/users/me', { token: accessToken, body: { bio: '旅の記録をつけています。', avatarS3Key } })
  for (const user of Object.values(users)) {
    await api('PUT', `/users/${user.username}/follow`, { token: accessToken })
  }
  console.log(`ユーザー: ${owner.displayName}（${owner.email}・作者用）`)
}

console.log(
  `完了: ユーザー ${Object.keys(users).length}・投稿 ${spotDefs.length}・写真 ${photoCount}・コメント ${comments.length}・フォロー ${follows.length}・掲示板 ${topics.length}トピック/${replyCount}返信・` +
    `コミュニティ ${communities.length}（参加 ${membershipCount}・${communityTopicCount}トピック/${communityReplyCount}返信）`,
)
