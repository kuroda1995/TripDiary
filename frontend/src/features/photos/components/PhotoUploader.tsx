import { ChevronLeft, ChevronRight, ImagePlus, RotateCw, X } from 'lucide-react'
import { useEffect, useId, useLayoutEffect, useRef, useState, type DragEvent } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { uploadPhoto } from '../api'
import { ACCEPT_ATTRIBUTE, MAX_PHOTOS, PhotoFileError, prepareForUpload } from '../prepareFile'
import type { PhotoItem } from '../types'

type PhotoUploaderProps = {
  value: PhotoItem[]
  onChange: (items: PhotoItem[]) => void
  error?: string
}

/**
 * スポット写真の選択・アップロード・並べ替え（docs/features/03_photo_upload.md 4章）。
 *
 * - 選んだ時点でアップロードを始め、サムネイルと進捗を表示する
 * - ドラッグ（またはボタン）で並べ替えでき、先頭がカバー写真になる
 * - 失敗した写真は再試行できる
 */
export function PhotoUploader({ value, onChange, error }: PhotoUploaderProps) {
  const inputId = useId()
  const [notice, setNotice] = useState<string | null>(null)
  const [dragIndex, setDragIndex] = useState<number | null>(null)
  const [isDropTarget, setIsDropTarget] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // アップロードは非同期で並行して進むため、常に最新の値に対して更新を適用する
  const latest = useRef(value)
  useLayoutEffect(() => {
    latest.current = value
  }, [value])
  const update = (fn: (items: PhotoItem[]) => PhotoItem[]) => {
    latest.current = fn(latest.current)
    onChange(latest.current)
  }
  const patch = (id: string, changes: Partial<PhotoItem>) =>
    update((items) => items.map((item) => (item.id === id ? { ...item, ...changes } : item)))

  // 作った blob: URL は不要になったら解放する
  const objectUrls = useRef(new Set<string>())
  useEffect(() => {
    const urls = objectUrls.current
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [])
  const createPreview = (file: File) => {
    const url = URL.createObjectURL(file)
    objectUrls.current.add(url)
    return url
  }
  const releasePreview = (url: string | null) => {
    if (url && objectUrls.current.delete(url)) URL.revokeObjectURL(url)
  }

  const startUpload = async (id: string, original: File) => {
    try {
      const prepared = await prepareForUpload(original)
      const previous = latest.current.find((item) => item.id === id)
      if (!previous) return // 準備中に削除された
      releasePreview(previous.previewUrl)
      patch(id, { status: 'uploading', progress: 0, previewUrl: createPreview(prepared) })
      const s3Key = await uploadPhoto('SPOT_PHOTO', prepared, (progress) => patch(id, { progress }))
      patch(id, { status: 'done', progress: 100, s3Key, error: null, retryFile: null })
    } catch (e) {
      const retryable = !(e instanceof PhotoFileError)
      patch(id, {
        status: 'error',
        error: e instanceof Error ? e.message : 'アップロードに失敗しました',
        retryFile: retryable ? original : null,
      })
    }
  }

  const addFiles = (files: File[]) => {
    if (files.length === 0) return
    const room = MAX_PHOTOS - latest.current.length
    const accepted = files.slice(0, Math.max(room, 0))
    setNotice(
      accepted.length < files.length
        ? `写真は${MAX_PHOTOS}枚までです。${files.length - accepted.length}枚は追加しませんでした`
        : null,
    )
    const added = accepted.map((file) => ({ file, id: crypto.randomUUID() }))
    update((items) => [
      ...items,
      ...added.map(({ id }): PhotoItem => ({
        id,
        status: 'preparing',
        progress: 0,
        s3Key: null,
        previewUrl: null,
        error: null,
        retryFile: null,
      })),
    ])
    added.forEach(({ id, file }) => void startUpload(id, file))
  }

  const retry = (item: PhotoItem) => {
    if (!item.retryFile) return
    patch(item.id, { status: 'preparing', progress: 0, error: null })
    void startUpload(item.id, item.retryFile)
  }

  const remove = (item: PhotoItem) => {
    releasePreview(item.previewUrl)
    setNotice(null)
    update((items) => items.filter((other) => other.id !== item.id))
  }

  const move = (from: number, to: number) => {
    if (to < 0 || to >= latest.current.length || from === to) return
    update((items) => {
      const next = [...items]
      const [moved] = next.splice(from, 1)
      next.splice(to, 0, moved)
      return next
    })
  }

  const onDropFiles = (event: DragEvent) => {
    event.preventDefault()
    setIsDropTarget(false)
    if (dragIndex !== null) return // 並べ替えのドラッグ
    addFiles(Array.from(event.dataTransfer.files))
  }

  const isFull = value.length >= MAX_PHOTOS

  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-medium">写真（任意・{MAX_PHOTOS}枚まで）</legend>
      <p className="text-xs text-muted-foreground">
        JPEG / PNG / WebP / HEIC、1枚10MBまで。先頭の写真がカバー写真になります（ドラッグで並べ替え）。
      </p>

      {value.length > 0 && (
        <ol aria-label="選択した写真" className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {value.map((item, index) => (
            <li
              key={item.id}
              draggable={item.status === 'done'}
              onDragStart={() => setDragIndex(index)}
              onDragEnd={() => setDragIndex(null)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault()
                event.stopPropagation()
                if (dragIndex !== null) move(dragIndex, index)
                setDragIndex(null)
              }}
              className={`relative overflow-hidden rounded-md border bg-muted ${dragIndex === index ? 'opacity-50' : ''}`}
            >
              <div className="aspect-square">
                {item.previewUrl ? (
                  <img
                    src={item.previewUrl}
                    alt={`写真${index + 1}`}
                    className="size-full object-cover"
                    draggable={false}
                  />
                ) : (
                  <div className="flex size-full items-center justify-center text-xs text-muted-foreground">
                    {item.status === 'error' ? '' : '準備中…'}
                  </div>
                )}
              </div>

              {index === 0 && (
                <Badge className="absolute top-1.5 left-1.5" variant="secondary">
                  カバー
                </Badge>
              )}

              {item.status === 'uploading' && (
                <div
                  role="progressbar"
                  aria-label={`写真${index + 1}のアップロード`}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={item.progress}
                  className="absolute inset-x-0 bottom-0 h-1.5 bg-black/20"
                >
                  <div
                    className="h-full bg-primary transition-[width]"
                    style={{ width: `${item.progress}%` }}
                  />
                </div>
              )}

              {item.status === 'error' && (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-background/90 p-2 text-center">
                  <p role="alert" className="text-xs text-destructive">
                    {item.error}
                  </p>
                  {item.retryFile && (
                    <Button type="button" size="sm" variant="outline" onClick={() => retry(item)}>
                      <RotateCw aria-hidden />
                      再試行
                    </Button>
                  )}
                </div>
              )}

              <div className="absolute top-1.5 right-1.5 flex gap-1">
                <Button
                  type="button"
                  size="icon-xs"
                  variant="secondary"
                  aria-label={`写真${index + 1}を前へ移動`}
                  disabled={index === 0}
                  onClick={() => move(index, index - 1)}
                >
                  <ChevronLeft aria-hidden />
                </Button>
                <Button
                  type="button"
                  size="icon-xs"
                  variant="secondary"
                  aria-label={`写真${index + 1}を後ろへ移動`}
                  disabled={index === value.length - 1}
                  onClick={() => move(index, index + 1)}
                >
                  <ChevronRight aria-hidden />
                </Button>
                <Button
                  type="button"
                  size="icon-xs"
                  variant="secondary"
                  aria-label={`写真${index + 1}を削除`}
                  onClick={() => remove(item)}
                >
                  <X aria-hidden />
                </Button>
              </div>
            </li>
          ))}
        </ol>
      )}

      {!isFull && (
        <div
          onDragOver={(event) => {
            event.preventDefault()
            if (dragIndex === null) setIsDropTarget(true)
          }}
          onDragLeave={() => setIsDropTarget(false)}
          onDrop={onDropFiles}
          className={`flex flex-col items-center gap-2 rounded-md border border-dashed p-6 text-sm text-muted-foreground ${isDropTarget ? 'border-primary bg-primary/5' : ''}`}
        >
          <ImagePlus aria-hidden className="size-6" />
          <p>ここに写真をドラッグ&ドロップ、または</p>
          <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()}>
            写真を選ぶ
          </Button>
          <input
            ref={inputRef}
            id={inputId}
            type="file"
            accept={ACCEPT_ATTRIBUTE}
            multiple
            aria-label="写真を追加"
            className="sr-only"
            tabIndex={-1}
            onChange={(event) => {
              addFiles(Array.from(event.target.files ?? []))
              // 同じファイルをもう一度選べるようにする
              event.target.value = ''
            }}
          />
        </div>
      )}

      {notice && <p className="text-xs text-muted-foreground">{notice}</p>}
      {error && <p className="text-xs text-destructive">{error}</p>}
    </fieldset>
  )
}
