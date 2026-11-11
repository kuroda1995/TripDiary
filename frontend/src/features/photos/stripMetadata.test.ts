import { describe, expect, it } from 'vitest'
import {
  containsSecret,
  exifPayload,
  jpegBytes,
  pngBytes,
  strippedJpegBytes,
  strippedPngBytes,
  strippedWebpBytes,
  webpBytes,
} from '@/test/images'
import {
  detectImageFormat,
  MetadataStripError,
  orientationExif,
  readExifOrientation,
  stripMetadata,
} from './stripMetadata'

describe('detectImageFormat', () => {
  it('先頭のバイト列で JPEG / PNG / WebP を判定し、それ以外は null', () => {
    expect(detectImageFormat(jpegBytes())).toBe('jpeg')
    expect(detectImageFormat(pngBytes())).toBe('png')
    expect(detectImageFormat(webpBytes())).toBe('webp')
    expect(detectImageFormat(new Uint8Array([0x47, 0x49, 0x46, 0x38]))).toBeNull() // GIF
    expect(detectImageFormat(new Uint8Array(100))).toBeNull()
  })
})

describe('JPEG', () => {
  it('EXIF・XMP・IPTC・コメントを取り除き、JFIF・ICC・画像データは1バイトも変えずに残す', () => {
    const original = jpegBytes()
    expect(containsSecret(original)).toBe(true)

    const stripped = stripMetadata(original)

    expect(containsSecret(stripped)).toBe(false)
    expect(stripped).toEqual(strippedJpegBytes())
  })

  it.each([
    ['リトルエンディアン', true],
    ['ビッグエンディアン', false],
  ])('向き（Orientation）が 1 以外なら、向きだけの EXIF を JFIF の直後に残す（%s）', (_, littleEndian) => {
    const stripped = stripMetadata(jpegBytes({ orientation: 6, littleEndian }))

    expect(containsSecret(stripped)).toBe(false)
    const expected = strippedJpegBytes()
    const jfifEnd = 2 + 18
    expect(stripped).toEqual(
      new Uint8Array([
        ...expected.subarray(0, jfifEnd),
        ...orientationExif(6),
        ...expected.subarray(jfifEnd),
      ]),
    )
    expect(readExifOrientation(orientationExif(6).subarray(4))).toBe(6)
  })

  it('終端（EOI）の後ろに付け足された2枚目の画像（EXIF 付き）は捨てる', () => {
    const original = jpegBytes({ appendedImage: true })

    const stripped = stripMetadata(original)

    expect(stripped).toEqual(strippedJpegBytes())
  })

  it('取り除いた結果をもう一度処理しても変わらない', () => {
    const once = stripMetadata(jpegBytes({ orientation: 8 }))

    expect(stripMetadata(once)).toEqual(once)
  })

  it('途中で切れている・終端がない JPEG は MetadataStripError', () => {
    const original = jpegBytes()

    expect(() => stripMetadata(original.subarray(0, 30))).toThrow(MetadataStripError)
    expect(() => stripMetadata(original.subarray(0, original.length - 2))).toThrow(MetadataStripError)
    expect(() => stripMetadata(new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0x00]))).toThrow(MetadataStripError)
  })
})

describe('readExifOrientation', () => {
  it('EXIF でない・範囲外の値・壊れたデータは null', () => {
    expect(readExifOrientation(new Uint8Array([1, 2, 3]))).toBeNull()
    expect(readExifOrientation(new Uint8Array(exifPayload(9)))).toBeNull()
    expect(readExifOrientation(new Uint8Array(exifPayload(3).slice(0, 20)))).toBeNull()
    expect(readExifOrientation(new Uint8Array(exifPayload(3)))).toBe(3)
  })
})

describe('PNG', () => {
  it('eXIf・tEXt・iTXt・tIME を取り除き、画像のチャンクはそのまま残す', () => {
    const stripped = stripMetadata(pngBytes())

    expect(containsSecret(stripped)).toBe(false)
    expect(stripped).toEqual(strippedPngBytes())
  })

  it('途中で切れている PNG は MetadataStripError', () => {
    const original = pngBytes()
    expect(() => stripMetadata(original.subarray(0, original.length - 3))).toThrow(MetadataStripError)
  })
})

describe('WebP', () => {
  it('EXIF・XMP のチャンクを取り除き、VP8X の印を消して RIFF のサイズを直す', () => {
    const stripped = stripMetadata(webpBytes())

    expect(containsSecret(stripped)).toBe(false)
    expect(stripped).toEqual(strippedWebpBytes())
  })

  it('途中で切れている WebP は MetadataStripError', () => {
    const original = webpBytes()
    expect(() => stripMetadata(original.subarray(0, 25))).toThrow(MetadataStripError)
  })
})

it('対応外の形式は MetadataStripError', () => {
  expect(() => stripMetadata(new Uint8Array(10))).toThrow(MetadataStripError)
})
