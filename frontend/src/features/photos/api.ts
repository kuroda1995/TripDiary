import { apiFetch } from '@/lib/apiClient'

export type UploadPurpose = 'SPOT_PHOTO' | 'AVATAR'

export type UploadUrl = {
  uploadUrl: string
  s3Key: string
  expiresAt: string
}

export class UploadError extends Error {
  constructor(message = 'アップロードに失敗しました') {
    super(message)
    this.name = 'UploadError'
  }
}

/** 署名付き URL を発行する（POST /api/v1/uploads/presigned-url）。 */
export function requestUploadUrl(purpose: UploadPurpose, file: File): Promise<UploadUrl> {
  return apiFetch<UploadUrl>('/api/v1/uploads/presigned-url', {
    method: 'POST',
    body: JSON.stringify({ purpose, contentType: file.type, contentLength: file.size }),
  })
}

/**
 * 署名付き URL へファイルを直接 PUT する（API サーバーを経由しない）。
 *
 * fetch ではアップロードの進み具合を取れないため XMLHttpRequest を使う。
 * Content-Type は署名の条件に含まれるため、URL 発行時と同じ値を送る。
 */
export function putToStorage(
  uploadUrl: string,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', uploadUrl)
    xhr.setRequestHeader('Content-Type', file.type)
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    })
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress(100)
        resolve()
      } else {
        reject(new UploadError())
      }
    })
    xhr.addEventListener('error', () => reject(new UploadError()))
    xhr.addEventListener('abort', () => reject(new UploadError('アップロードを中止しました')))
    xhr.send(file)
  })
}

/** 写真をアップロードし、投稿に指定する S3 キーを返す。 */
export async function uploadPhoto(
  purpose: UploadPurpose,
  file: File,
  onProgress: (percent: number) => void,
): Promise<string> {
  const { uploadUrl, s3Key } = await requestUploadUrl(purpose, file)
  await putToStorage(uploadUrl, file, onProgress)
  return s3Key
}
