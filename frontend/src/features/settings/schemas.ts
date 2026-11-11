import { z } from 'zod'
import { PASSWORD_MESSAGE, PASSWORD_PATTERN } from '@/features/auth/schemas'

/** 入力チェック（サーバー側 ProfileUpdateRequest / PasswordChangeRequest と同じルール。docs/features/01_auth.md 5章）。 */
export const MAX_BIO_LENGTH = 500

export const profileSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(1, '表示名を入力してください')
    .max(50, '表示名は50文字以内で入力してください'),
  bio: z.string().max(MAX_BIO_LENGTH, `自己紹介は${MAX_BIO_LENGTH}文字以内で入力してください`),
})

export type ProfileFormValues = z.infer<typeof profileSchema>

export const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, '現在のパスワードを入力してください'),
    newPassword: z
      .string()
      .min(1, '新しいパスワードを入力してください')
      .regex(PASSWORD_PATTERN, PASSWORD_MESSAGE),
    newPasswordConfirmation: z.string().min(1, '確認用のパスワードを入力してください'),
  })
  .refine((values) => values.newPassword === values.newPasswordConfirmation, {
    path: ['newPasswordConfirmation'],
    message: 'パスワードが一致しません',
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: '新しいパスワードが現在のパスワードと同じです',
  })

export type PasswordFormValues = z.infer<typeof passwordSchema>
