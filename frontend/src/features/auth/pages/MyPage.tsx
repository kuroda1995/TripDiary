import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { UserAvatar } from '@/components/UserAvatar'
import { buttonVariants } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
        </CardHeader>
        <CardContent className="space-y-4">
          {isPending && <p className="text-sm text-muted-foreground">読み込み中…</p>}
          {isError && <p className="text-sm text-destructive">アカウント情報を取得できませんでした。</p>}
          {me && (
            <>
              <div className="flex items-center gap-4">
                <UserAvatar displayName={me.displayName} avatarUrl={me.avatarUrl} size="lg" />
                <div className="min-w-0">
                  <p className="text-lg font-semibold">{me.displayName}</p>
                  <p className="text-sm text-muted-foreground">@{me.username}</p>
                </div>
              </div>
              {me.bio && <p className="text-sm whitespace-pre-wrap">{me.bio}</p>}
              <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
                <dt className="text-muted-foreground">メールアドレス</dt>
                <dd>{me.email}</dd>
              </dl>
              <div className="flex flex-wrap gap-2">
                <Link to="/settings/profile" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
                  プロフィールを編集
                </Link>
                <Link to="/settings/password" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
                  パスワードを変更
                </Link>
                <Link
                  to={`/users/${me.username}`}
                  className={buttonVariants({ variant: 'ghost', size: 'sm' })}
                >
                  自分の投稿
                </Link>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </section>
  )
}
