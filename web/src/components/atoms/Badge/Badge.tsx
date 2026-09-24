import type { ReactNode } from 'react'
import { cx } from '@/lib/cx.ts'
import styles from './Badge.module.css'

export interface BadgeProps {
  tone?: 'neutral' | 'accent' | 'danger' | 'success' | 'warning'
  children: ReactNode
  className?: string
}

export function Badge({ tone = 'neutral', children, className }: BadgeProps) {
  return <span className={cx(styles.badge, styles[tone], className)}>{children}</span>
}
