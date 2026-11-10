import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { fetchMe } from '../api'

/** マイページ（ログイン必須）。訪問済み / 行きたいリストはステータス管理機能で追加する。 */
export function MyPage() {
  const { data: me, isPending, isError } = useQuery({ queryKey: ['users', 'me'], queryFn: fetchMe })

  return (
    <section className="space-y-6">
      <h1 className="text-2xl font-bold">マイページ</h1>
      <Card>
        <CardHeader>
          <CardTitle>アカウント情報</CardTitle>
          <CardDescription>プロフィールの編集は今後追加予定です。</CardDescription>
        </CardHeader>
        <CardContent>
          {isPending && <p className="text-sm text-muted-foreground">読み込み中…</p>}
          {isError && <p className="text-sm text-destructive">アカウント情報を取得できませんでした。</p>}
          {me && (
            <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
              <dt className="text-muted-foreground">表示名</dt>
              <dd>{me.displayName}</dd>
              <dt className="text-muted-foreground">ユーザー名</dt>
              <dd>@{me.username}</dd>
              <dt className="text-muted-foreground">メールアドレス</dt>
              <dd>{me.email}</dd>
            </dl>
          )}
        </CardContent>
      </Card>
    </section>
  )
}
