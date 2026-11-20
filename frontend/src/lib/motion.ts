import type { Transition } from 'motion/react'

/**
 * UI のマイクロインタラクションで共通に使うアニメーション設定（motion/react）。
 * 値をここにまとめ、画面ごとに動きの速さや弾み方がばらつかないようにする。
 */

/** タップ時に弾むスプリング（押し込んで、少し行き過ぎてから戻る） */
export const tapSpring: Transition = { type: 'spring', stiffness: 500, damping: 15, mass: 0.6 }

/** ボタンを押した瞬間の縮み具合 */
export const tapScale = { scale: 0.88 }

/** 一覧のカードが下からフェードインするときの動き */
const CARD_RISE_TRANSITION = { duration: 0.45, ease: [0.22, 1, 0.36, 1] } as const

/** 同時に画面に入ったカードを順番に出すときの間隔（秒） */
const CARD_STAGGER = 0.08

/** 間隔をあけるのは画面に一度に入る枚数程度まで（「もっと見る」の後や下の方のカードを待たせない） */
const CARD_STAGGER_SLOTS = 4

/**
 * 一覧のカード（li）に付ける表示アニメーション。
 * 画面に入ったときに一度だけ再生し（once）、再スクロールでのチラつきを防ぐ。
 */
export function cardRevealProps(index: number) {
  return {
    initial: { opacity: 0, y: 24 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true, amount: 0.15 },
    transition: { ...CARD_RISE_TRANSITION, delay: (index % CARD_STAGGER_SLOTS) * CARD_STAGGER },
  }
}
