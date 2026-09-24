import { cx } from '@/lib/cx.ts'
import styles from './Spinner.module.css'

export interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg'
  /** Announced while loading; omit when surrounding text already says so. */
  label?: string
  className?: string
}

export function Spinner({ size = 'md', label, className }: SpinnerProps) {
  return (
    <span
      className={cx(styles.spinner, styles[size], className)}
      role={label ? 'status' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    />
  )
}
