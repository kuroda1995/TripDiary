/** フォームで扱う写真（アップロード済みの既存写真、またはアップロード中・失敗した写真）。 */
export type PhotoItem = {
  /** 画面内での識別子（既存写真は s3Key、新しい写真はランダムな ID）。 */
  id: string
  status: 'preparing' | 'uploading' | 'done' | 'error'
  /** 0〜100 */
  progress: number
  /** アップロードが終わると入る（投稿時にサーバーへ送る）。 */
  s3Key: string | null
  /** サムネイル表示用の URL（既存写真は配信 URL、新しい写真は blob: URL）。 */
  previewUrl: string | null
  error: string | null
  /** 再試行用の元ファイル（形式・サイズ違反など再試行しても直らない場合は null）。 */
  retryFile: File | null
}

export function existingPhoto(s3Key: string, url: string): PhotoItem {
  return {
    id: s3Key,
    status: 'done',
    progress: 100,
    s3Key,
    previewUrl: url,
    error: null,
    retryFile: null,
  }
}
