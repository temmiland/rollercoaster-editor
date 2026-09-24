import type { LucideIcon } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import { NavItem } from '../../molecules/index.ts'
import styles from './Navigation.module.css'

export interface NavigationItem {
  key: string
  href: string
  icon: LucideIcon
  label: string
  count?: number
}

export interface NavigationProps {
  items: NavigationItem[]
  currentKey: string
  /** A vertical rail beside the content or a bar along the bottom edge. */
  placement: 'rail' | 'bar'
  label: string
}

export function Navigation({ items, currentKey, placement, label }: NavigationProps) {
  return (
    <nav aria-label={label} className={cx(styles.navigation, styles[placement])}>
      {items.map((item) => (
        <NavItem key={item.key} href={item.href} icon={item.icon} label={item.label} count={item.count} current={item.key === currentKey} />
      ))}
    </nav>
  )
}
