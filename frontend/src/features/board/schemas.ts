import { z } from 'zod'

/** 入力チェック（サーバー側 BoardTopicRequest と同じルール。docs/features/10_community_board.md 5章）。 */
export const MAX_TOPIC_TITLE_LENGTH = 100
export const MAX_TOPIC_BODY_LENGTH = 2000

export const topicSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, 'タイトルを入力してください')
    .max(MAX_TOPIC_TITLE_LENGTH, `タイトルは${MAX_TOPIC_TITLE_LENGTH}文字以内で入力してください`),
  body: z
    .string()
    .trim()
    .min(1, '本文を入力してください')
    .max(MAX_TOPIC_BODY_LENGTH, `本文は${MAX_TOPIC_BODY_LENGTH}文字以内で入力してください`),
})

export type TopicFormValues = z.infer<typeof topicSchema>
