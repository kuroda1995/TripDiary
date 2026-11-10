/**
 * アップロード前のファイルの確認と変換（docs/features/03_photo_upload.md 6章）。
 *
 * - HEIC / HEIF（iPhone の標準形式）は Chrome などで表示できないため、ブラウザで JPEG に変換してから送る
 * - 形式・サイズが条件外ならアップロード前にエラーにする（サーバーでも同じ条件で確認する）
 */

export const MAX_PHOTOS = 10
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024

/** サーバーへ送る形式（HEIC は変換後の JPEG で送る）。 */
const UPLOAD_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

/** ファイル選択で受け付ける形式（拡張子は Content-Type が空になるブラウザ向け）。 */
export const ACCEPT_ATTRIBUTE = 'image/jpeg,image/png,image/webp,image/heic,image/heif,.heic,.heif'

const HEIC_JPEG_QUALITY = 0.9

export class PhotoFileError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'PhotoFileError'
  }
}

export function isHeicFile(file: File): boolean {
  return file.type === 'image/heic' || file.type === 'image/heif' || /\.(heic|heif)$/i.test(file.name)
}

async function convertHeicToJpeg(file: File): Promise<File> {
  try {
    // 変換ライブラリ（libheif）は大きいため、HEIC を選んだときだけ読み込む
    const { heicTo } = await import('heic-to')
    const blob = await heicTo({ blob: file, type: 'image/jpeg', quality: HEIC_JPEG_QUALITY })
    const name = file.name.replace(/\.(heic|heif)$/i, '') + '.jpg'
    return new File([blob], name, { type: 'image/jpeg', lastModified: file.lastModified })
  } catch {
    throw new PhotoFileError('HEIC 画像を変換できませんでした。JPEG で保存し直してからお試しください')
  }
}

/** アップロードできる形にしたファイルを返す。条件外なら PhotoFileError。 */
export async function prepareForUpload(file: File): Promise<File> {
  const prepared = isHeicFile(file) ? await convertHeicToJpeg(file) : file
  if (!(UPLOAD_TYPES as readonly string[]).includes(prepared.type)) {
    throw new PhotoFileError('対応していない画像形式です（JPEG / PNG / WebP / HEIC のみ）')
  }
  if (prepared.size > MAX_PHOTO_BYTES) {
    throw new PhotoFileError('画像のサイズは10MBまでです')
  }
  if (prepared.size === 0) {
    throw new PhotoFileError('空のファイルはアップロードできません')
  }
  return prepared
}
