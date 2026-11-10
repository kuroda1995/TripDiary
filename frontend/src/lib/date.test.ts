import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, todayInJapan } from './date'

describe('date', () => {
  it('todayInJapan は日本時間の日付を返す（UTC では前日でも日本では翌日）', () => {
    expect(todayInJapan(new Date('2026-11-11T15:30:00Z'))).toBe('2026-11-12')
    expect(todayInJapan(new Date('2026-11-11T14:59:59Z'))).toBe('2026-11-11')
  })

  it('formatDate は「YYYY年M月D日」形式', () => {
    expect(formatDate('2026-10-03')).toBe('2026年10月3日')
  })

  it('formatDateTime は日本時間で表示する', () => {
    expect(formatDateTime('2026-10-04T15:05:00Z')).toBe('2026/10/05 00:05')
  })
})
