import { describe, expect, it } from 'vitest'
import { signupSchema } from './schemas'

const valid = {
  email: 'taro@example.com',
  username: 'taro_trip',
  displayName: '旅するタロウ',
  password: 'Passw0rd123',
  passwordConfirmation: 'Passw0rd123',
}

function errorsFor(values: Partial<typeof valid>) {
  const result = signupSchema.safeParse({ ...valid, ...values })
  return result.success
    ? {}
    : Object.fromEntries(result.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('signupSchema（サーバーと同じ入力ルール）', () => {
  it('正しい入力は通る', () => {
    expect(signupSchema.safeParse(valid).success).toBe(true)
  })

  it.each([
    ['onlyletters', '英字のみ'],
    ['12345678', '数字のみ'],
    ['Pass1', '7文字以下'],
    ['パスワード1234abcd', '全角文字を含む'],
    ['a1' + 'x'.repeat(71), '73文字以上'],
  ])('パスワード %s（%s）は不可', (password) => {
    expect(errorsFor({ password, passwordConfirmation: password }).password).toBeDefined()
  })

  it('ユーザー名は半角英数字と _ で3〜30文字', () => {
    expect(errorsFor({ username: 'ab' }).username).toBeDefined()
    expect(errorsFor({ username: 'taro-trip' }).username).toBeDefined()
    expect(errorsFor({ username: 'a'.repeat(31) }).username).toBeDefined()
    expect(errorsFor({ username: 'Taro_123' }).username).toBeUndefined()
  })

  it('メールアドレスの形式・表示名の必須をチェックする', () => {
    expect(errorsFor({ email: 'not-an-email' }).email).toBe('メールアドレスの形式が正しくありません')
    expect(errorsFor({ displayName: '   ' }).displayName).toBe('表示名を入力してください')
  })

  it('確認用パスワードが一致しないとエラー', () => {
    expect(errorsFor({ passwordConfirmation: 'Different1' }).passwordConfirmation).toBe(
      'パスワードが一致しません',
    )
  })
})
