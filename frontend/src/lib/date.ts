/** 日付の表示・判定（DB は UTC、表示は日本時間。docs/requirements.md NFR-UX-05）。 */

const TIME_ZONE = 'Asia/Tokyo'

/** 日本時間の今日（YYYY-MM-DD）。 */
export function todayInJapan(now: Date = new Date()): string {
  // en-CA ロケールは YYYY-MM-DD 形式で出力される
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(now)
}

/** "2026-10-03" → "2026年10月3日" */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return `${year}年${month}月${day}日`
}

/** ISO 8601 の日時 → 日本時間の "2026/11/11 12:34" */
export function formatDateTime(isoDateTime: string): string {
  return new Intl.DateTimeFormat('ja-JP', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(isoDateTime))
}
