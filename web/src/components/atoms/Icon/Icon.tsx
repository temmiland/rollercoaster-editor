import type { LucideIcon } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import styles from './Icon.module.css'

export type IconSize = 'sm' | 'md' | 'lg'

export interface IconProps {
  icon: LucideIcon
  size?: IconSize
  /** Announced to assistive technology; decorative icons omit it. */
  label?: string
  className?: string
}

export function Icon({ icon: Glyph, size = 'md', label, className }: IconProps) {
  return (
    <Glyph
      className={cx(styles.icon, styles[size], className)}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      role={label ? 'img' : undefined}
      strokeWidth={1.75}
    />
  )
}
