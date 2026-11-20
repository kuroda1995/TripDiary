import { Link, useNavigate } from 'react-router'
import { Card, CardContent } from '@/components/ui/card'
import { useCreateCommunity } from '../api'
import { CommunityForm } from '../components/CommunityForm'

/** コミュニティの作成画面（ログインが必要）。作成すると詳細画面へ移動する。 */
export function CommunityNewPage() {
  const navigate = useNavigate()
  const createCommunity = useCreateCommunity()

  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <Link to="/communities" className="text-sm text-muted-foreground hover:underline">
        ← コミュニティ一覧
      </Link>
      <header className="space-y-2">
        <h1 className="text-2xl font-bold">コミュニティを作る</h1>
        <p className="text-sm text-muted-foreground">
          作成したあなたがオーナーになり、自動で参加します。メンバーはコミュニティの中でトピックを立てて話せます。
        </p>
      </header>
      <Card>
        <CardContent>
          <CommunityForm
            onSubmit={async (input) => {
              const community = await createCommunity.mutateAsync(input)
              navigate(`/communities/${community.id}`)
            }}
          />
        </CardContent>
      </Card>
    </section>
  )
}
