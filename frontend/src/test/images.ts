/**
 * テスト用の小さな画像バイト列（構造だけ正しく、メタデータを差し込める）。
 * メタデータには `SECRET` という目印の文字列を入れ、取り除かれたかを確かめられるようにする。
 */

export const SECRET = 'GPS-SECRET-35.0116N-135.7681E'

const text = (s: string) => Array.from(s, (c) => c.charCodeAt(0))
const u16be = (n: number) => [(n >> 8) & 0xff, n & 0xff]
const u32be = (n: number) => [(n >>> 24) & 0xff, (n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff]
const u32le = (n: number) => [n & 0xff, (n >> 8) & 0xff, (n >> 16) & 0xff, (n >>> 24) & 0xff]

function segment(marker: number, payload: number[]): number[] {
  return [0xff, marker, ...u16be(payload.length + 2), ...payload]
}

/** Orientation とダミーの位置情報を持つ EXIF（APP1 の中身）。 */
export function exifPayload(orientation: number, littleEndian = true): number[] {
  const u16 = (n: number) => (littleEndian ? [n & 0xff, (n >> 8) & 0xff] : u16be(n))
  const u32 = (n: number) => (littleEndian ? u32le(n) : u32be(n))
  return [
    ...text('Exif\0\0'),
    ...(littleEndian ? text('II') : text('MM')),
    ...u16(42),
    ...u32(8),
    ...u16(2), // エントリ数
    ...u16(0x010f),
    ...u16(2),
    ...u32(4),
    ...text('Cam\0'), // Make（機種）
    ...u16(0x0112),
    ...u16(3),
    ...u32(1),
    ...u16(orientation),
    0,
    0, // Orientation
    ...u32(0),
    ...text(SECRET),
  ]
}

const JFIF = segment(0xe0, [...text('JFIF\0'), 1, 1, 0, 0, 1, 0, 1, 0, 0])
const ICC = segment(0xe2, [...text('ICC_PROFILE\0'), 1, 1, 0xaa, 0xbb])
const DQT = segment(0xdb, [0, ...new Array(64).fill(1)])
// 圧縮データ中の 0xFF 0x00（詰め物）と 0xFF 0xD0（リスタートマーカー）を含める
const SCAN = [...segment(0xda, [1, 1, 0, 0, 0x3f, 0]), 0x12, 0xff, 0x00, 0x34, 0xff, 0xd0, 0x56]
const EOI = [0xff, 0xd9]

type JpegOptions = {
  orientation?: number
  /** EXIF の TIFF のバイト順 */
  littleEndian?: boolean
  /** 終端（EOI）の後ろに、EXIF 付きの2枚目の画像を付け足す */
  appendedImage?: boolean
}

export function jpegBytes({ orientation = 1, littleEndian = true, appendedImage = false }: JpegOptions = {}) {
  const image = [
    0xff,
    0xd8,
    ...JFIF,
    ...segment(0xe1, exifPayload(orientation, littleEndian)),
    ...segment(0xe1, text(`http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>${SECRET}</x:xmpmeta>`)), // XMP
    ...ICC,
    ...segment(0xed, text(`Photoshop 3.0\0${SECRET}`)), // IPTC
    ...segment(0xfe, text(SECRET)), // コメント
    ...DQT,
    ...SCAN,
    ...EOI,
  ]
  const appended = appendedImage
    ? [0xff, 0xd8, ...segment(0xe1, exifPayload(1)), ...DQT, ...SCAN, ...EOI]
    : []
  return new Uint8Array([...image, ...appended])
}

/** メタデータを取り除いた後に残るべき JPEG（Orientation が 1 の場合）。 */
export function strippedJpegBytes() {
  return new Uint8Array([0xff, 0xd8, ...JFIF, ...ICC, ...DQT, ...SCAN, ...EOI])
}

function pngChunk(type: string, data: number[]): number[] {
  // CRC はこのモジュールでは検証しないためダミー
  return [...u32be(data.length), ...text(type), ...data, 0, 0, 0, 0]
}

const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
const IHDR = pngChunk('IHDR', [0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0])
const IDAT = pngChunk('IDAT', [1, 2, 3, 4])
const IEND = pngChunk('IEND', [])

export function pngBytes() {
  return new Uint8Array([
    ...PNG_SIGNATURE,
    ...IHDR,
    ...pngChunk('eXIf', exifPayload(1).slice(6)),
    ...pngChunk('tEXt', text(`Comment\0${SECRET}`)),
    ...pngChunk('iTXt', text(`XML:com.adobe.xmp\0\0\0\0\0${SECRET}`)),
    ...pngChunk('tIME', [7, 234, 11, 11, 0, 0, 0]),
    ...IDAT,
    ...IEND,
  ])
}

/** 画像データを詰めて、指定したサイズちょうどにした（メタデータのない）PNG。 */
export function pngOfSize(size: number) {
  const fixed = PNG_SIGNATURE.length + IHDR.length + 12 + IEND.length
  const bytes = new Uint8Array(size)
  bytes.set([...PNG_SIGNATURE, ...IHDR, ...u32be(size - fixed), ...text('IDAT')])
  bytes.set(IEND, size - IEND.length)
  return bytes
}

export function strippedPngBytes() {
  return new Uint8Array([...PNG_SIGNATURE, ...IHDR, ...IDAT, ...IEND])
}

function riffChunk(type: string, data: number[]): number[] {
  return [...text(type), ...u32le(data.length), ...data, ...(data.length % 2 ? [0] : [])]
}

function riff(chunks: number[]): Uint8Array {
  return new Uint8Array([...text('RIFF'), ...u32le(chunks.length + 4), ...text('WEBP'), ...chunks])
}

/** VP8X のフラグ（EXIF: 0x08、XMP: 0x04、アルファ: 0x10）と、1×1 のキャンバスサイズ。 */
const vp8x = (flags: number) => riffChunk('VP8X', [flags, 0, 0, 0, 0, 0, 0, 0, 0, 0])
const VP8 = riffChunk('VP8 ', [9, 8, 7]) // 奇数長（詰め物 1 バイト）

export function webpBytes() {
  return riff([
    ...vp8x(0x08 | 0x04 | 0x10),
    ...VP8,
    ...riffChunk('EXIF', exifPayload(1).slice(6)),
    ...riffChunk('XMP ', text(`<x:xmpmeta>${SECRET}</x:xmpmeta>`)),
  ])
}

export function strippedWebpBytes() {
  return riff([...vp8x(0x10), ...VP8])
}

export function containsSecret(bytes: Uint8Array): boolean {
  return new TextDecoder('latin1').decode(bytes).includes(SECRET)
}

/** 画像ファイル（中身は正しい構造）。 */
export function imageFile(name: string, type: 'image/jpeg' | 'image/png' | 'image/webp', bytes?: Uint8Array) {
  const content =
    bytes ?? (type === 'image/jpeg' ? jpegBytes() : type === 'image/png' ? pngBytes() : webpBytes())
  return new File([content as Uint8Array<ArrayBuffer>], name, { type })
}
