import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setAccessToken } from '@/lib/authSession'
import { server } from './msw/server'

// jsdom には URL.createObjectURL が無いため、写真のプレビュー用に代用する
let objectUrlCounter = 0
URL.createObjectURL ??= () => `blob:test/${++objectUrlCounter}`
URL.revokeObjectURL ??= () => {}

// 未定義の API 呼び出しはテスト失敗として検知する
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
  setAccessToken(null)
})
afterAll(() => server.close())
