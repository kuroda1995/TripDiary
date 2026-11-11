import { z } from 'zod'

/** 入力チェック（サーバー側 CommentRequest と同じルール。docs/features/05_comment.md 5章）。 */
export const MAX_COMMENT_LENGTH = 1000

export const commentSchema = z.object({
  body: z
    .string()
    .trim()
    .min(1, 'コメントを入力してください')
    .max(MAX_COMMENT_LENGTH, `コメントは${MAX_COMMENT_LENGTH}文字以内で入力してください`),
})

export type CommentFormValues = z.infer<typeof commentSchema>
