import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setAccessToken } from '@/lib/authSession'
import { server } from './msw/server'

// 未定義の API 呼び出しはテスト失敗として検知する
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
  setAccessToken(null)
})
afterAll(() => server.close())
