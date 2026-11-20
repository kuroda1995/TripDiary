import { motion } from 'motion/react'
import type { ComponentProps } from 'react'
import { Button } from '@/components/ui/button'
import { tapScale, tapSpring } from '@/lib/motion'

/**
 * タップした瞬間に弾むボタン（行ってみたい！・マイリストなどのトグル用）。
 * 見た目・振る舞いは Button と同じで、描画する要素だけ motion.button に差し替える。
 */
export function TapButton(props: ComponentProps<typeof Button>) {
  return <Button render={<motion.button whileTap={tapScale} transition={tapSpring} />} {...props} />
}
