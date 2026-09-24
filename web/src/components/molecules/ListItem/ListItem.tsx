import type { ComponentProps, ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import { Icon, Text } from '../../atoms/index.ts'
import styles from './ListItem.module.css'

export interface ListItemProps extends Omit<ComponentProps<'a'>, 'title'> {
  href: string
  title: string
  subtitle?: string
  icon?: LucideIcon
  /** Right-aligned content such as a badge or a chevron. */
  trailing?: ReactNode
  selected?: boolean
}

export function ListItem({ title, subtitle, icon, trailing, selected = false, className, ...props }: ListItemProps) {
  return (
    <a {...props} aria-current={selected ? 'page' : undefined} className={cx(styles.item, selected && styles.selected, className)}>
      {icon && <Icon icon={icon} className={styles.icon} />}
      <span className={styles.body}>
        <Text weight="medium" truncate>
          {title}
        </Text>
        {subtitle && (
          <Text size="sm" tone="muted" truncate>
            {subtitle}
          </Text>
        )}
      </span>
      {trailing}
    </a>
  )
}
