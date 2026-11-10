import { z } from 'zod'
import { MAX_PHOTOS } from '@/features/photos/prepareFile'
import { existingPhoto, type PhotoItem } from '@/features/photos/types'
import { todayInJapan } from '@/lib/date'
import type { Spot, SpotInput } from './types'

/** 入力チェック（サーバー側 SpotRequest と同じルール。docs/features/02_spot.md 5章）。 */
export const MAX_AREA_TAGS = 5
export const MAX_IMPRESSION_LENGTH = 5000

export const spotSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, 'タイトルを入力してください')
    .max(100, 'タイトルは100文字以内で入力してください'),
  locationName: z
    .string()
    .trim()
    .min(1, '場所を入力してください')
    .max(200, '場所は200文字以内で入力してください'),
  areaTagIds: z
    .array(z.number())
    .min(1, 'エリアタグを1つ以上選択してください')
    .max(MAX_AREA_TAGS, `エリアタグは${MAX_AREA_TAGS}個まで選択できます`),
  impression: z
    .string()
    .max(MAX_IMPRESSION_LENGTH, `感想は${MAX_IMPRESSION_LENGTH}文字以内で入力してください`),
  visitedOn: z
    .string()
    .refine((value) => value === '' || /^\d{4}-\d{2}-\d{2}$/.test(value), '日付の形式が正しくありません')
    // YYYY-MM-DD 同士は文字列比較で前後関係を判定できる
    .refine((value) => value === '' || value <= todayInJapan(), '訪問日に未来の日付は指定できません'),
  visibility: z.enum(['PUBLIC', 'PRIVATE']),
  photos: z
    .array(z.custom<PhotoItem>())
    .max(MAX_PHOTOS, `写真は${MAX_PHOTOS}枚までです`)
    .refine(
      (photos) => photos.every((photo) => photo.status !== 'preparing' && photo.status !== 'uploading'),
      '写真のアップロードが終わるまでお待ちください',
    )
    .refine(
      (photos) => photos.every((photo) => photo.status !== 'error'),
      'アップロードできなかった写真があります。再試行するか削除してください',
    ),
})

export type SpotFormValues = z.infer<typeof spotSchema>

export const emptySpotForm: SpotFormValues = {
  title: '',
  locationName: '',
  areaTagIds: [],
  impression: '',
  visitedOn: '',
  visibility: 'PUBLIC',
  photos: [],
}

export function toFormValues(spot: Spot): SpotFormValues {
  return {
    title: spot.title,
    locationName: spot.locationName,
    areaTagIds: spot.areaTags.map((tag) => tag.id),
    impression: spot.impression ?? '',
    visitedOn: spot.visitedOn ?? '',
    visibility: spot.visibility,
    photos: [...spot.photos]
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((photo) => existingPhoto(photo.s3Key, photo.url)),
  }
}

export function toSpotInput(values: SpotFormValues): SpotInput {
  return {
    title: values.title,
    locationName: values.locationName,
    areaTagIds: values.areaTagIds,
    impression: values.impression.trim() === '' ? null : values.impression,
    visitedOn: values.visitedOn === '' ? null : values.visitedOn,
    visibility: values.visibility,
    // 画面の並び順（先頭がカバー写真）をそのまま sortOrder にする
    photos: values.photos
      .flatMap((photo) => (photo.s3Key ? [photo.s3Key] : []))
      .map((s3Key, index) => ({ s3Key, sortOrder: index })),
  }
}
