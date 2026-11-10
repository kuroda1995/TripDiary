import { describe, expect, it } from 'vitest'
import { ApiError } from './apiClient'
import { createQueryClient, shouldRetry } from './queryClient'

const apiError = (status: number) => new ApiError(status, { code: 'X', message: 'x' }, null)

describe('shouldRetry', () => {
  it('4xx はリトライしない', () => {
    expect(shouldRetry(0, apiError(400))).toBe(false)
    expect(shouldRetry(0, apiError(404))).toBe(false)
  })

  it('5xx・通信エラーは1回だけリトライする', () => {
    expect(shouldRetry(0, apiError(500))).toBe(true)
    expect(shouldRetry(0, apiError(0))).toBe(true)
    expect(shouldRetry(1, apiError(500))).toBe(false)
  })

  it('createQueryClient はリトライ判定を既定値に設定する', () => {
    expect(createQueryClient().getDefaultOptions().queries?.retry).toBe(shouldRetry)
  })
})
