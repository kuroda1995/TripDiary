import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

export function HomePage() {
  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">旅の思い出を、みんなと。</h1>
        <p className="text-muted-foreground">
          TripDiary は、訪れたスポットを写真と感想で記録して共有できる旅行記録アプリです。
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>新着スポット</CardTitle>
          <CardDescription>スポット投稿機能は準備中です。</CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">まだ投稿はありません。</CardContent>
      </Card>
    </section>
  )
}
