import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'

export function SpotNotFound() {
  return (
    <section className="space-y-4 text-center">
      <h1 className="text-2xl font-bold">スポットが見つかりません</h1>
      <p className="text-muted-foreground">削除されたか、非公開に設定されている可能性があります。</p>
      <Link to="/" className={buttonVariants()}>
        トップへ戻る
      </Link>
    </section>
  )
}
