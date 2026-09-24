import type { ReactNode } from 'react'
import { cx } from '@/lib/cx.ts'
import styles from './Toolbar.module.css'

export interface ToolbarProps {
  label: string
  children: ReactNode
  className?: string
}

export function Toolbar({ label, children, className }: ToolbarProps) {
  return (
    <div role="toolbar" aria-label={label} className={cx(styles.toolbar, className)}>
      {children}
    </div>
  )
}
