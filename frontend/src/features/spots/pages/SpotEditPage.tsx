import { useNavigate } from 'react-router'
import { useAuth } from '@/features/auth/useAuth'
import { ApiError } from '@/lib/apiClient'
import { useSpot, useUpdateSpot } from '../api'
import { SpotForm } from '../components/SpotForm'
import { SpotNotFound } from '../components/SpotNotFound'
import { toFormValues } from '../schemas'
import { useSpotIdParam } from '../useSpotIdParam'

export function SpotEditPage() {
  const spotId = useSpotIdParam()
  if (spotId === null) return <SpotNotFound />
  return <SpotEdit spotId={spotId} />
}

function SpotEdit({ spotId }: { spotId: number }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const { data: spot, isPending, error } = useSpot(spotId, auth.status !== 'loading')
  const updateSpot = useUpdateSpot(spotId)

  if (isPending) {
    return (
      <p role="status" className="text-sm text-muted-foreground">
        読み込み中…
      </p>
    )
  }
  if (error) {
    return error instanceof ApiError && error.status === 404 ? (
      <SpotNotFound />
    ) : (
      <p className="text-sm text-destructive">スポットを読み込めませんでした。</p>
    )
  }
  // この画面は RequireAuth の内側なので auth.user がある
  if (auth.user?.id !== spot.author.id) {
    return (
      <section className="space-y-2 text-center">
        <h1 className="text-2xl font-bold">編集できません</h1>
        <p className="text-muted-foreground">他のユーザーのスポットは編集できません。</p>
      </section>
    )
  }

  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">スポットを編集</h1>
      <SpotForm
        defaultValues={toFormValues(spot)}
        submitLabel="保存する"
        onSubmit={async (input) => {
          await updateSpot.mutateAsync(input)
          navigate(`/spots/${spotId}`, { replace: true })
        }}
      />
    </section>
  )
}
