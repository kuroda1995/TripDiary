import { useEffect, useRef, useState } from 'react'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { uploadPhoto } from '@/features/photos/api'
import { ACCEPT_ATTRIBUTE, MAX_AVATAR_BYTES, prepareForUpload } from '@/features/photos/prepareFile'

/**
 * アイコンの選択状態。
 * - s3Key が undefined: 変更しない
 * - s3Key が null: 削除する
 * - s3Key が文字列: アップロード済みの新しいアイコンに差し替える
 */
export type AvatarValue = { s3Key: string | null | undefined; previewUrl: string | null }

type AvatarFieldProps = {
  displayName: string
  value: AvatarValue
  onChange: (value: AvatarValue) => void
  onUploadingChange: (uploading: boolean) => void
}

/** アイコン画像の選択・アップロード・削除（選んだ時点でアップロードする。保存はフォームの保存ボタンで行う）。 */
export function AvatarField({ displayName, value, onChange, onUploadingChange }: AvatarFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState<string | null>(null)

  // 作ったプレビュー用の blob: URL を解放する
  const objectUrl = useRef<string | null>(null)
  const replacePreview = (url: string | null) => {
    if (objectUrl.current) URL.revokeObjectURL(objectUrl.current)
    objectUrl.current = url
  }
  useEffect(() => () => replacePreview(null), [])

  const setUploadingState = (next: boolean) => {
    setUploading(next)
    onUploadingChange(next)
  }

  const select = async (file: File) => {
    setError(null)
    setProgress(0)
    setUploadingState(true)
    try {
      const prepared = await prepareForUpload(file, MAX_AVATAR_BYTES)
      const s3Key = await uploadPhoto('AVATAR', prepared, setProgress)
      const previewUrl = URL.createObjectURL(prepared)
      replacePreview(previewUrl)
      onChange({ s3Key, previewUrl })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'アップロードに失敗しました')
    } finally {
      setUploadingState(false)
    }
  }

  const remove = () => {
    replacePreview(null)
    setError(null)
    onChange({ s3Key: null, previewUrl: null })
  }

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">アイコン（任意）</legend>
      <div className="flex items-center gap-4">
        {value.previewUrl ? (
          <img
            src={value.previewUrl}
            alt="アイコンのプレビュー"
            className="size-20 shrink-0 rounded-full bg-muted object-cover"
          />
        ) : (
          <UserAvatar displayName={displayName || '?'} avatarUrl={null} size="lg" />
        )}
        <div className="space-y-2">
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={uploading}
              onClick={() => inputRef.current?.click()}
            >
              画像を選ぶ
            </Button>
            {value.previewUrl && (
              <Button type="button" variant="ghost" size="sm" disabled={uploading} onClick={remove}>
                アイコンを削除
              </Button>
            )}
          </div>
          <p className="text-xs text-muted-foreground">JPEG / PNG / WebP / HEIC、5MBまで。</p>
          {uploading && (
            <div
              role="progressbar"
              aria-label="アイコンのアップロード"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={progress}
              className="h-1.5 w-40 overflow-hidden rounded bg-muted"
            >
              <div className="h-full bg-primary" style={{ width: `${progress}%` }} />
            </div>
          )}
          {error && (
            <p role="alert" className="text-xs text-destructive">
              {error}
            </p>
          )}
        </div>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT_ATTRIBUTE}
        aria-label="アイコン画像を選ぶ"
        className="sr-only"
        tabIndex={-1}
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          if (file) void select(file)
        }}
      />
    </fieldset>
  )
}
