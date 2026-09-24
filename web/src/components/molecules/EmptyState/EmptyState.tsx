import type { ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { Heading, Icon, Text } from '../../atoms/index.ts'
import styles from './EmptyState.module.css'

export interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description?: ReactNode
  /** Usually one button that resolves the empty state. */
  action?: ReactNode
  tone?: 'default' | 'danger'
}

export function EmptyState({ icon, title, description, action, tone = 'default' }: EmptyStateProps) {
  return (
    <div className={styles.empty} role={tone === 'danger' ? 'alert' : undefined}>
      <Icon icon={icon} size="lg" className={tone === 'danger' ? styles.danger : styles.icon} />
      <Heading level={2} size="md">
        {title}
      </Heading>
      {description && (
        <Text as="p" size="sm" tone="muted" className={styles.description}>
          {description}
        </Text>
      )}
      {action}
    </div>
  )
}
