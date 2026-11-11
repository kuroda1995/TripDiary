/**
 * 画像からメタデータ（EXIF の位置情報・撮影機種、XMP、IPTC、コメントなど）を取り除く
 * （docs/features/03_photo_upload.md 5章）。
 *
 * - 画像を描き直さず、ファイル内のメタデータ部分だけを取り除くため、画質・サイズは変わらない
 * - JPEG の向き（EXIF の Orientation）だけは残す（消すとスマホの縦向き写真が横倒しで表示されるため）
 * - 形式の判定は Content-Type ではなく、ファイル先頭のバイト列で行う
 */

export class MetadataStripError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'MetadataStripError'
  }
}

export type ImageFormat = 'jpeg' | 'png' | 'webp'

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]

function startsWith(bytes: Uint8Array, prefix: number[], offset = 0): boolean {
  return prefix.every((b, i) => bytes[offset + i] === b)
}

function ascii(bytes: Uint8Array, offset: number, length: number): string {
  return String.fromCharCode(...bytes.subarray(offset, offset + length))
}

/** 先頭のバイト列から形式を判定する（対応外なら null）。 */
export function detectImageFormat(bytes: Uint8Array): ImageFormat | null {
  if (startsWith(bytes, [0xff, 0xd8, 0xff])) return 'jpeg'
  if (startsWith(bytes, PNG_SIGNATURE)) return 'png'
  if (bytes.length >= 12 && ascii(bytes, 0, 4) === 'RIFF' && ascii(bytes, 8, 4) === 'WEBP') return 'webp'
  return null
}

/** 形式に応じてメタデータを取り除く。構造が壊れていて処理できなければ MetadataStripError。 */
export function stripMetadata(bytes: Uint8Array): Uint8Array {
  switch (detectImageFormat(bytes)) {
    case 'jpeg':
      return stripJpeg(bytes)
    case 'png':
      return stripPng(bytes)
    case 'webp':
      return stripWebp(bytes)
    default:
      throw new MetadataStripError('unsupported image format')
  }
}

function concat(parts: Uint8Array[]): Uint8Array {
  const result = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0))
  let offset = 0
  for (const part of parts) {
    result.set(part, offset)
    offset += part.length
  }
  return result
}

function malformed(): MetadataStripError {
  return new MetadataStripError('malformed image')
}

// ---------------------------------------------------------------- JPEG

const SOI = 0xd8
const SOS = 0xda
const EOI = 0xd9
const APP0 = 0xe0
const APP1 = 0xe1
const APP2 = 0xe2
const APP14 = 0xee
const COM = 0xfe

/**
 * 残すセグメント: APP0（JFIF）・APP2（ICC カラープロファイル）・APP14（Adobe の色変換）と、画像そのもののデータ。
 * 取り除くもの: APP1（EXIF・XMP）・APP3〜APP13（IPTC など）・APP15・COM（コメント）。
 */
function isMetadataSegment(marker: number): boolean {
  if (marker === COM) return true
  if (marker < APP0 || marker > 0xef) return false
  return marker !== APP0 && marker !== APP2 && marker !== APP14
}

function stripJpeg(bytes: Uint8Array): Uint8Array {
  const kept: Uint8Array[] = [bytes.subarray(0, 2)] // SOI
  let orientation = 1
  let insertAt = 1 // 向きの EXIF を差し込む位置（kept の添字。JFIF があればその直後）
  let offset = 2
  let sawEoi = false

  while (offset < bytes.length) {
    if (bytes[offset] !== 0xff) throw malformed()
    // マーカーの前の詰め物（0xFF の連続）は飛ばす
    let markerOffset = offset
    while (bytes[markerOffset + 1] === 0xff) markerOffset++
    const marker = bytes[markerOffset + 1]
    if (marker === undefined || marker === SOI) throw malformed()

    if (marker === EOI) {
      // 終端より後ろ（スマホが付け足す2枚目の画像など。そこにも EXIF が入りうる）は捨てる
      kept.push(bytes.subarray(markerOffset, markerOffset + 2))
      sawEoi = true
      break
    }
    if (markerOffset + 3 >= bytes.length) throw malformed()
    const length = (bytes[markerOffset + 2] << 8) | bytes[markerOffset + 3]
    const end = markerOffset + 2 + length
    if (length < 2 || end > bytes.length) throw malformed()

    if (marker === SOS) {
      // スキャンのヘッダと圧縮データを、次のマーカー（次のスキャンの表・EOI など）の手前まで残す
      const next = findMarkerAfterEntropyData(bytes, end)
      kept.push(bytes.subarray(markerOffset, next))
      offset = next
      continue
    }

    const segment = bytes.subarray(markerOffset, end)
    if (marker === APP1) {
      orientation = readExifOrientation(segment.subarray(4)) ?? orientation
    }
    if (!isMetadataSegment(marker)) {
      kept.push(segment)
      if (marker === APP0 && kept.length === 2) insertAt = 2
    }
    offset = end
  }
  if (!sawEoi) throw malformed()

  if (orientation !== 1) {
    kept.splice(insertAt, 0, orientationExif(orientation))
  }
  return concat(kept)
}

/**
 * 圧縮データの終わり（次のマーカーの位置）を探す。
 * 圧縮データ中の 0xFF は「0xFF 0x00」（詰め物）か「0xFF 0xD0〜0xD7」（リスタートマーカー）としてしか現れない。
 */
function findMarkerAfterEntropyData(bytes: Uint8Array, from: number): number {
  for (let i = from; i + 1 < bytes.length; i++) {
    if (bytes[i] !== 0xff) continue
    const next = bytes[i + 1]
    if (next === 0x00 || next === 0xff || (next >= 0xd0 && next <= 0xd7)) continue
    return i
  }
  throw malformed()
}

const EXIF_HEADER = [0x45, 0x78, 0x69, 0x66, 0x00, 0x00] // "Exif\0\0"
const ORIENTATION_TAG = 0x0112

/** APP1 の中身から Orientation（1〜8）を読む。EXIF でない・見つからない・壊れている場合は null。 */
export function readExifOrientation(payload: Uint8Array): number | null {
  if (!startsWith(payload, EXIF_HEADER)) return null
  const tiff = payload.subarray(EXIF_HEADER.length)
  if (tiff.length < 8) return null
  const little = tiff[0] === 0x49 && tiff[1] === 0x49 // "II"
  const big = tiff[0] === 0x4d && tiff[1] === 0x4d // "MM"
  if (!little && !big) return null
  const view = new DataView(tiff.buffer, tiff.byteOffset, tiff.byteLength)
  const u16 = (at: number) => view.getUint16(at, little)
  const u32 = (at: number) => view.getUint32(at, little)
  if (u16(2) !== 42) return null

  const ifd = u32(4)
  if (ifd + 2 > tiff.length) return null
  const count = u16(ifd)
  for (let i = 0; i < count; i++) {
    const entry = ifd + 2 + i * 12
    if (entry + 12 > tiff.length) return null
    if (u16(entry) === ORIENTATION_TAG) {
      const value = u16(entry + 8)
      return value >= 1 && value <= 8 ? value : null
    }
  }
  return null
}

/** Orientation だけを持つ最小の EXIF（APP1 セグメント）。 */
export function orientationExif(orientation: number): Uint8Array {
  // prettier-ignore
  return new Uint8Array([
    0xff, APP1, 0x00, 0x22, // マーカーと長さ（34 バイト）
    ...EXIF_HEADER,
    0x4d, 0x4d, 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08, // TIFF ヘッダ（ビッグエンディアン、IFD0 は 8 バイト目から）
    0x00, 0x01, // IFD0 のエントリ数
    0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation, 0x00, 0x00, // Orientation（SHORT × 1）
    0x00, 0x00, 0x00, 0x00, // 次の IFD なし
  ])
}

// ---------------------------------------------------------------- PNG

/** 取り除くチャンク（EXIF・テキスト（XMP を含む）・更新日時）。 */
const PNG_METADATA_CHUNKS = new Set(['eXIf', 'tEXt', 'iTXt', 'zTXt', 'tIME'])

function stripPng(bytes: Uint8Array): Uint8Array {
  const kept: Uint8Array[] = [bytes.subarray(0, PNG_SIGNATURE.length)]
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  let offset = PNG_SIGNATURE.length
  while (offset < bytes.length) {
    if (offset + 8 > bytes.length) throw malformed()
    const length = view.getUint32(offset)
    const type = ascii(bytes, offset + 4, 4)
    const end = offset + 12 + length // 長さ・種類・データ・CRC
    if (end > bytes.length) throw malformed()
    if (!PNG_METADATA_CHUNKS.has(type)) kept.push(bytes.subarray(offset, end))
    offset = end
    if (type === 'IEND') break
  }
  return concat(kept)
}

// ---------------------------------------------------------------- WebP

const VP8X_EXIF_FLAG = 0x08
const VP8X_XMP_FLAG = 0x04

function stripWebp(bytes: Uint8Array): Uint8Array {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const riffEnd = Math.min(bytes.length, 8 + view.getUint32(4, true))
  const kept: Uint8Array[] = []
  let offset = 12
  while (offset + 8 <= riffEnd) {
    const type = ascii(bytes, offset, 4)
    const size = view.getUint32(offset + 4, true)
    const end = offset + 8 + size + (size % 2) // チャンクは偶数バイトに詰める
    if (end > riffEnd + (size % 2)) throw malformed()
    if (type === 'VP8X') {
      // 拡張ヘッダの「EXIF あり」「XMP あり」の印を消す
      const chunk = bytes.slice(offset, Math.min(end, bytes.length))
      chunk[8] &= ~(VP8X_EXIF_FLAG | VP8X_XMP_FLAG)
      kept.push(chunk)
    } else if (type !== 'EXIF' && type !== 'XMP ') {
      kept.push(bytes.subarray(offset, Math.min(end, bytes.length)))
    }
    offset = end
  }
  if (offset < riffEnd) throw malformed()

  const body = concat(kept)
  const header = new Uint8Array(12)
  header.set(bytes.subarray(0, 4)) // "RIFF"
  new DataView(header.buffer).setUint32(4, body.length + 4, true)
  header.set(bytes.subarray(8, 12), 8) // "WEBP"
  return concat([header, body])
}
