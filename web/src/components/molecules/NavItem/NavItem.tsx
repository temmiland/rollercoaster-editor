import type { ComponentProps } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import { Icon } from '../../atoms/index.ts'
import styles from './NavItem.module.css'

export interface NavItemProps extends ComponentProps<'a'> {
  href: string
  icon: LucideIcon
  label: string
  current?: boolean
  /** Small count shown on the icon, e.g. unsaved changes. */
  count?: number
}

/** One destination of the main navigation; lays out vertically in a rail and in a bottom bar alike. */
export function NavItem({ icon, label, current = false, count, className, ...props }: NavItemProps) {
  return (
    <a {...props} aria-current={current ? 'page' : undefined} className={cx(styles.item, current && styles.current, className)}>
      <span className={styles.indicator}>
        <Icon icon={icon} />
        {count ? <span className={styles.count}>{count > 99 ? '99+' : count}</span> : null}
      </span>
      <span className={styles.label}>{label}</span>
    </a>
  )
}
