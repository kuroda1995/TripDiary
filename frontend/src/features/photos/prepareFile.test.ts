import { beforeEach, describe, expect, it, vi } from 'vitest'
import { containsSecret, imageFile, jpegBytes, strippedJpegBytes } from '@/test/images'
import { isHeicFile, MAX_PHOTO_BYTES, PhotoFileError, prepareForUpload } from './prepareFile'

// 呼び出しの記録は vi.fn で、変換結果は差し替え可能な関数で返す
// （vi.fn の実装が reject すると、呼び出し側で処理していても Vitest がテスト失敗として扱うため）
const heicTo = vi.fn<(args: unknown) => void>()
let convert: () => Promise<Blob> = async () => new Blob()
vi.mock('heic-to', () => ({
  heicTo: (args: unknown) => {
    heicTo(args)
    return convert()
  },
}))

function fileOf(name: string, type: string, size = 100): File {
  return new File([new Uint8Array(size)], name, { type })
}

/** 圧縮データを詰めて、指定したサイズちょうどにした（メタデータのない）JPEG。 */
function jpegOfSize(size: number): File {
  const header = [0xff, 0xd8, 0xff, 0xda, 0x00, 0x08, 1, 1, 0, 0, 0x3f, 0]
  const bytes = new Uint8Array(size)
  bytes.set(header)
  bytes.set([0xff, 0xd9], size - 2)
  return new File([bytes], 'big.jpg', { type: 'image/jpeg' })
}

async function bytesOf(file: File) {
  return new Uint8Array(await file.arrayBuffer())
}

describe('prepareForUpload', () => {
  beforeEach(() => heicTo.mockReset())

  it.each(['image/jpeg', 'image/png', 'image/webp'] as const)(
    '%s は位置情報などのメタデータを取り除いて送る（名前・形式は変えない）',
    async (type) => {
      const file = imageFile('photo', type)
      expect(containsSecret(await bytesOf(file))).toBe(true)

      const prepared = await prepareForUpload(file)

      expect(prepared.name).toBe('photo')
      expect(prepared.type).toBe(type)
      expect(prepared.size).toBeLessThan(file.size)
      expect(containsSecret(await bytesOf(prepared))).toBe(false)
      expect(heicTo).not.toHaveBeenCalled()
    },
  )

  it('HEIC は JPEG に変換し、拡張子も .jpg にして、変換後のメタデータも取り除く', async () => {
    convert = async () => new Blob([jpegBytes() as Uint8Array<ArrayBuffer>], { type: 'image/jpeg' })

    const prepared = await prepareForUpload(fileOf('IMG_0001.HEIC', 'image/heic'))

    expect(prepared.type).toBe('image/jpeg')
    expect(prepared.name).toBe('IMG_0001.jpg')
    expect(await bytesOf(prepared)).toEqual(strippedJpegBytes())
    expect(heicTo).toHaveBeenCalledWith(expect.objectContaining({ type: 'image/jpeg' }))
  })

  it('HEIC の変換に失敗したら案内付きのエラーにする', async () => {
    convert = async () => {
      throw new Error('broken')
    }

    await expect(prepareForUpload(fileOf('a.heic', 'image/heic'))).rejects.toThrow(
      'HEIC 画像を変換できませんでした',
    )
  })

  it('中身が宣言された形式と違う・壊れている画像は、メタデータを確実に取り除けないため送らない', async () => {
    // 拡張子・Content-Type は JPEG だが中身は PNG
    await expect(
      prepareForUpload(imageFile('a.jpg', 'image/jpeg', await bytesOf(imageFile('a', 'image/png')))),
    ).rejects.toThrow('画像ファイルを読み込めませんでした')
    await expect(prepareForUpload(fileOf('a.jpg', 'image/jpeg'))).rejects.toBeInstanceOf(PhotoFileError)
    await expect(
      prepareForUpload(imageFile('a.jpg', 'image/jpeg', jpegBytes().subarray(0, 40))),
    ).rejects.toThrow('画像ファイルを読み込めませんでした')
  })

  it('対応外の形式・10MB 超・空のファイルは PhotoFileError', async () => {
    await expect(prepareForUpload(fileOf('a.gif', 'image/gif'))).rejects.toThrow('対応していない画像形式です')
    await expect(prepareForUpload(jpegOfSize(MAX_PHOTO_BYTES + 1))).rejects.toThrow(
      '画像のサイズは10MBまでです',
    )
    await expect(prepareForUpload(fileOf('a.jpg', 'image/jpeg', 0))).rejects.toThrow(
      '空のファイルはアップロードできません',
    )
    // 上限ちょうどは可
    await expect(prepareForUpload(jpegOfSize(MAX_PHOTO_BYTES))).resolves.toBeDefined()
  })
})

describe('isHeicFile', () => {
  it('Content-Type または拡張子で判定する（Content-Type が空になるブラウザがあるため）', () => {
    expect(isHeicFile(fileOf('a', 'image/heic'))).toBe(true)
    expect(isHeicFile(fileOf('a', 'image/heif'))).toBe(true)
    expect(isHeicFile(fileOf('IMG.HEIC', ''))).toBe(true)
    expect(isHeicFile(fileOf('a.heif', ''))).toBe(true)
    expect(isHeicFile(fileOf('a.jpg', 'image/jpeg'))).toBe(false)
  })
})
