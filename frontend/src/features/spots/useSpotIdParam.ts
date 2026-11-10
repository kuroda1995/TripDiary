import { useParams } from 'react-router'

/** URL の :id をスポット ID（正の整数）として読む。不正なら null。 */
export function useSpotIdParam(): number | null {
  const { id } = useParams()
  if (!id || !/^\d+$/.test(id)) return null
  const value = Number(id)
  return Number.isSafeInteger(value) && value > 0 ? value : null
}
