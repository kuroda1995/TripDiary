import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '@/lib/apiClient'

/** サーバーのエラーコードを入力項目に対応付ける（例: EMAIL_ALREADY_EXISTS → email）。 */
export type FieldErrorCodes<T extends FieldValues> = Partial<Record<string, Path<T>>>

/**
 * API エラーをフォームに反映する。
 *
 * - 入力エラー（VALIDATION_ERROR）は項目ごとに表示する
 * - 項目に対応付けたエラーコードはその項目に表示する
 * - それ以外はフォーム全体のエラー（root.server）として表示する
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
  codeToField: FieldErrorCodes<T> = {},
): void {
  if (!(error instanceof ApiError)) {
    setError('root.server', { message: '予期しないエラーが発生しました' })
    return
  }
  const field = codeToField[error.code]
  if (field) {
    setError(field, { type: 'server', message: error.message }, { shouldFocus: true })
    return
  }
  const fieldErrors = error.details.filter((d) => (fields as readonly string[]).includes(d.field))
  if (error.code === 'VALIDATION_ERROR' && fieldErrors.length > 0) {
    fieldErrors.forEach((d, i) =>
      setError(d.field as Path<T>, { type: 'server', message: d.message }, { shouldFocus: i === 0 }),
    )
    return
  }
  setError('root.server', { message: error.message })
}
