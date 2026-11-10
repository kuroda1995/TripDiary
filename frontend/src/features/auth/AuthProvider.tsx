import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { onSessionExpired } from '@/lib/authSession'
import * as authApi from './api'
import { AuthContext, type AuthContextValue, type AuthState } from './AuthContext'
import type { LoginInput, SignupInput } from './types'

/**
 * ログイン状態を管理する。
 *
 * 起動時にリフレッシュトークン（HttpOnly Cookie）でセッションの復元を試み、
 * トークン更新に失敗した場合（ログイン切れ）は未ログイン状態に戻す。
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })
  const restoreStarted = useRef(false)

  useEffect(() => {
    // StrictMode の二重実行でリフレッシュトークンを二重に使わないようにする
    if (restoreStarted.current) return
    restoreStarted.current = true
    authApi
      .restoreSession()
      .then((user) => setState({ status: 'authenticated', user }))
      .catch(() => setState({ status: 'anonymous', user: null }))
  }, [])

  useEffect(
    () =>
      onSessionExpired(() => {
        setState({ status: 'anonymous', user: null })
        queryClient.clear()
      }),
    [queryClient],
  )

  // ログイン後は見える範囲（本人の非公開スポットなど）が変わるため、取得済みのデータを古い扱いにする
  const login = useCallback(
    async (input: LoginInput) => {
      const user = await authApi.login(input)
      setState({ status: 'authenticated', user })
      void queryClient.invalidateQueries()
      return user
    },
    [queryClient],
  )

  const signup = useCallback(
    async (input: SignupInput) => {
      const user = await authApi.signup(input)
      setState({ status: 'authenticated', user })
      void queryClient.invalidateQueries()
      return user
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setState({ status: 'anonymous', user: null })
      // 前のユーザーのデータを画面に残さない
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, login, signup, logout }),
    [state, login, signup, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
