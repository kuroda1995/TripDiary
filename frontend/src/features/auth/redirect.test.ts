import { describe, expect, it } from 'vitest'
import { loginPathFor, safeRedirectPath } from './redirect'

describe('safeRedirectPath', () => {
  it('サイト内のパスはそのまま使う', () => {
    expect(safeRedirectPath('/me')).toBe('/me')
    expect(safeRedirectPath('/spots/1?tab=comments')).toBe('/spots/1?tab=comments')
  })

  it('外部サイトへ飛ばされる値はトップに置き換える（オープンリダイレクト対策）', () => {
    expect(safeRedirectPath('https://evil.example.com')).toBe('/')
    expect(safeRedirectPath('//evil.example.com')).toBe('/')
    expect(safeRedirectPath('/\\evil.example.com')).toBe('/')
    expect(safeRedirectPath('javascript:alert(1)')).toBe('/')
  })

  it('未指定ならトップ', () => {
    expect(safeRedirectPath(null)).toBe('/')
    expect(safeRedirectPath('')).toBe('/')
  })
})

describe('loginPathFor', () => {
  it('元の画面を redirect パラメータに入れる', () => {
    expect(loginPathFor('/me?tab=1')).toBe('/login?redirect=%2Fme%3Ftab%3D1')
  })
})
