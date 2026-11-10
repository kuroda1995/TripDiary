/** エリアタグ（GET /api/v1/areas。level 1: 地方・海外、level 2: 都道府県・国や地域）。 */
export type AreaTagNode = {
  id: number
  name: string
  slug: string
  level: number
  children: AreaTagNode[]
}

/** スポットに付いたエリアタグ。 */
export type AreaTag = {
  id: number
  name: string
  slug: string
}
