import type { ComponentProps } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import { Icon } from '../Icon/Icon.tsx'
import styles from './IconButton.module.css'

export interface IconButtonProps extends Omit<ComponentProps<'button'>, 'children'> {
  icon: LucideIcon
  /** Required: an icon alone has no accessible name. Also shown as tooltip. */
  label: string
  variant?: 'ghost' | 'secondary'
  /** Marks a toggle as on; renders `aria-pressed`. */
  pressed?: boolean
}

export function IconButton({
  icon,
  label,
  variant = 'ghost',
  pressed,
  className,
  type = 'button',
  ...props
}: IconButtonProps) {
  return (
    <button
      {...props}
      type={type}
      aria-label={label}
      title={label}
      aria-pressed={pressed}
      className={cx(styles.iconButton, styles[variant], pressed && styles.pressed, className)}
    >
      <Icon icon={icon} />
    </button>
  )
}
