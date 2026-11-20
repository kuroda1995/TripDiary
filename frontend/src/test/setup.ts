import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { MotionGlobalConfig } from 'motion/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setAccessToken } from '@/lib/authSession'
import { server } from './msw/server'

// jsdom には URL.createObjectURL が無いため、写真のプレビュー用に代用する
let objectUrlCounter = 0
URL.createObjectURL ??= () => `blob:test/${++objectUrlCounter}`
URL.revokeObjectURL ??= () => {}

// アニメーションは即座に最終状態にする（待ち時間や途中の状態でテストが揺れないようにする）
MotionGlobalConfig.skipAnimations = true

// jsdom には IntersectionObserver が無い。motion の whileInView は無いと何もしないため、
// 一覧のカードが透明（opacity: 0）のまま残ってしまう。監視を始めた要素はすぐ「画面内」と通知する
class ImmediateIntersectionObserver implements IntersectionObserver {
  readonly root = null
  readonly rootMargin = '0px'
  readonly thresholds = [0]
  readonly scrollMargin = '0px'
  private readonly callback: IntersectionObserverCallback
  constructor(callback: IntersectionObserverCallback) {
    this.callback = callback
  }
  observe(target: Element) {
    const rect = target.getBoundingClientRect()
    const entry: IntersectionObserverEntry = {
      target,
      isIntersecting: true,
      intersectionRatio: 1,
      time: performance.now(),
      boundingClientRect: rect,
      intersectionRect: rect,
      rootBounds: null,
    }
    this.callback([entry], this)
  }
  unobserve() {}
  disconnect() {}
  takeRecords(): IntersectionObserverEntry[] {
    return []
  }
}
globalThis.IntersectionObserver ??= ImmediateIntersectionObserver

// 未定義の API 呼び出しはテスト失敗として検知する
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
  setAccessToken(null)
})
afterAll(() => server.close())
