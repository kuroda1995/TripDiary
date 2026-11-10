import { useNavigate } from 'react-router'
import { useCreateSpot } from '../api'
import { SpotForm } from '../components/SpotForm'
import { emptySpotForm } from '../schemas'

export function SpotNewPage() {
  const navigate = useNavigate()
  const createSpot = useCreateSpot()

  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">スポットを投稿</h1>
      <SpotForm
        defaultValues={emptySpotForm}
        submitLabel="投稿する"
        onSubmit={async (input) => {
          const spot = await createSpot.mutateAsync(input)
          navigate(`/spots/${spot.id}`, { replace: true })
        }}
      />
    </section>
  )
}
