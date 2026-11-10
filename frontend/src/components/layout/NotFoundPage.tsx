import { Link } from 'react-router'
import { buttonVariants } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <section className="space-y-4 text-center">
      <h1 className="text-2xl font-bold">ページが見つかりません</h1>
      <p className="text-muted-foreground">URL が間違っているか、ページが削除された可能性があります。</p>
      <Link to="/" className={buttonVariants()}>
        トップへ戻る
      </Link>
    </section>
  )
}
