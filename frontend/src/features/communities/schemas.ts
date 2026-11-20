import { z } from 'zod'
import { COMMUNITY_CATEGORIES } from './categories'

/** 入力チェック（サーバー側 CommunityRequest と同じルール。docs/features/12_communities.md 5章）。 */
export const MAX_COMMUNITY_NAME_LENGTH = 50
export const MAX_COMMUNITY_DESCRIPTION_LENGTH = 1000

export const communitySchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'コミュニティ名を入力してください')
    .max(MAX_COMMUNITY_NAME_LENGTH, `コミュニティ名は${MAX_COMMUNITY_NAME_LENGTH}文字以内で入力してください`),
  description: z
    .string()
    .trim()
    .min(1, '説明文を入力してください')
    .max(
      MAX_COMMUNITY_DESCRIPTION_LENGTH,
      `説明文は${MAX_COMMUNITY_DESCRIPTION_LENGTH}文字以内で入力してください`,
    ),
  categories: z.array(z.enum(COMMUNITY_CATEGORIES)).min(1, 'カテゴリを1つ以上選んでください'),
})

export type CommunityFormValues = z.infer<typeof communitySchema>
