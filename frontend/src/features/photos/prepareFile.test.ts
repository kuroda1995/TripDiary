import { beforeEach, describe, expect, it, vi } from 'vitest'
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

describe('prepareForUpload', () => {
  beforeEach(() => heicTo.mockReset())

  it.each(['image/jpeg', 'image/png', 'image/webp'])('%s はそのまま送る', async (type) => {
    const file = fileOf('a', type)

    await expect(prepareForUpload(file)).resolves.toBe(file)
    expect(heicTo).not.toHaveBeenCalled()
  })

  it('HEIC は JPEG に変換し、拡張子も .jpg にする', async () => {
    convert = async () => new Blob([new Uint8Array(50)], { type: 'image/jpeg' })

    const prepared = await prepareForUpload(fileOf('IMG_0001.HEIC', 'image/heic'))

    expect(prepared.type).toBe('image/jpeg')
    expect(prepared.name).toBe('IMG_0001.jpg')
    expect(prepared.size).toBe(50)
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

  it('対応外の形式・10MB 超・空のファイルは PhotoFileError', async () => {
    await expect(prepareForUpload(fileOf('a.gif', 'image/gif'))).rejects.toThrow('対応していない画像形式です')
    await expect(prepareForUpload(fileOf('a.jpg', 'image/jpeg', MAX_PHOTO_BYTES + 1))).rejects.toThrow(
      '画像のサイズは10MBまでです',
    )
    await expect(prepareForUpload(fileOf('a.jpg', 'image/jpeg', 0))).rejects.toBeInstanceOf(PhotoFileError)
    // 上限ちょうどは可
    await expect(prepareForUpload(fileOf('a.jpg', 'image/jpeg', MAX_PHOTO_BYTES))).resolves.toBeDefined()
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
