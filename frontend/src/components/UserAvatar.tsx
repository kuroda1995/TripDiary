import { cn } from '@/lib/utils'

type UserAvatarProps = {
  displayName: string
  avatarUrl: string | null
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const SIZES = {
  sm: 'size-6 text-xs',
  md: 'size-9 text-sm',
  lg: 'size-20 text-2xl',
} as const

/**
 * ユーザーのアイコン。未設定なら表示名の先頭1文字を表示する。
 * 名前のすぐ横に置く前提のため、読み上げは名前に任せて画像は装飾扱いにする。
 */
export function UserAvatar({ displayName, avatarUrl, size = 'md', className }: UserAvatarProps) {
  const base = cn(
    'inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-muted font-medium text-muted-foreground',
    SIZES[size],
    className,
  )
  if (avatarUrl) {
    return <img src={avatarUrl} alt="" aria-hidden className={cn(base, 'object-cover')} />
  }
  return (
    <span aria-hidden className={base}>
      {Array.from(displayName.trim())[0] ?? '?'}
    </span>
  )
}
