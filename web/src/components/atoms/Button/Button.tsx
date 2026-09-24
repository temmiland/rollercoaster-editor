import type { ComponentProps } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import { Icon } from '../Icon/Icon.tsx'
import { Spinner } from '../Spinner/Spinner.tsx'
import styles from './Button.module.css'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

export interface ButtonProps extends ComponentProps<'button'> {
  variant?: ButtonVariant
  icon?: LucideIcon
  /** Shows a spinner and blocks further presses, e.g. while an action is pending. */
  loading?: boolean
  fullWidth?: boolean
}

export function Button({
  variant = 'secondary',
  icon,
  loading = false,
  fullWidth = false,
  disabled,
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(styles.button, styles[variant], fullWidth && styles.fullWidth, className)}
    >
      {loading ? <Spinner size="sm" /> : icon && <Icon icon={icon} size="sm" />}
      <span className={styles.label}>{children}</span>
    </button>
  )
}
