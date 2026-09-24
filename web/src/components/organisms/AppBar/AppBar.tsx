import type { ReactNode } from 'react'
import { Heading, Text } from '../../atoms/index.ts'
import styles from './AppBar.module.css'

export interface AppBarProps {
  title: string
  subtitle?: string
  /** Usually a back button on compact layouts. */
  leading?: ReactNode
  actions?: ReactNode
  /** Shows a thin progress line, e.g. while a navigation renders in the background. */
  busy?: boolean
}

export function AppBar({ title, subtitle, leading, actions, busy = false }: AppBarProps) {
  return (
    <header className={styles.bar}>
      {leading}
      <div className={styles.titles}>
        <Heading level={1} size="md" className={styles.title}>
          {title}
        </Heading>
        {subtitle && (
          <Text size="xs" tone="muted" truncate>
            {subtitle}
          </Text>
        )}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
      {busy && <span className={styles.progress} role="progressbar" aria-label="Lädt" />}
    </header>
  )
}
