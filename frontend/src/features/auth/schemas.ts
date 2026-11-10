import { z } from 'zod'

/**
 * 入力チェック（サーバー側 SignupRequest / LoginRequest と同じルール。docs/features/01_auth.md 5章）。
 * 最終的な検証はサーバーで行い、ここでは入力中に分かりやすくエラーを出すために使う。
 */

export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,30}$/
/** BCrypt の72バイト制限のため、1文字1バイトの半角英数字・記号のみ。 */
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*[0-9])[\x21-\x7E]{8,72}$/
export const PASSWORD_MESSAGE =
  'パスワードは8〜72文字の半角英数字・記号で、英字と数字をそれぞれ1文字以上含めてください'

export const signupSchema = z
  .object({
    email: z
      .string()
      .trim()
      .min(1, 'メールアドレスを入力してください')
      .max(255, 'メールアドレスは255文字以内で入力してください')
      .pipe(z.email('メールアドレスの形式が正しくありません')),
    username: z
      .string()
      .min(1, 'ユーザー名を入力してください')
      .regex(USERNAME_PATTERN, 'ユーザー名は半角英数字と _ で3〜30文字で入力してください'),
    displayName: z
      .string()
      .trim()
      .min(1, '表示名を入力してください')
      .max(50, '表示名は50文字以内で入力してください'),
    password: z.string().min(1, 'パスワードを入力してください').regex(PASSWORD_PATTERN, PASSWORD_MESSAGE),
    passwordConfirmation: z.string().min(1, '確認用のパスワードを入力してください'),
  })
  .refine((values) => values.password === values.passwordConfirmation, {
    path: ['passwordConfirmation'],
    message: 'パスワードが一致しません',
  })

export type SignupFormValues = z.infer<typeof signupSchema>

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'メールアドレスを入力してください'),
  password: z.string().min(1, 'パスワードを入力してください'),
})

export type LoginFormValues = z.infer<typeof loginSchema>
