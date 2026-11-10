import { createContext } from 'react'
import type { LoginInput, Me, SignupInput } from './types'

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'authenticated'; user: Me }
  | { status: 'anonymous'; user: null }

export type AuthContextValue = AuthState & {
  login: (input: LoginInput) => Promise<Me>
  signup: (input: SignupInput) => Promise<Me>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
