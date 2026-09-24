import { useId, type ReactNode } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { cx } from '@/lib/cx.ts'
import { Icon, Text } from '../../atoms/index.ts'
import styles from './Sheet.module.css'

export interface SheetProps {
  title: string
  /** A fixed column beside the content, or a collapsible panel along the bottom edge. */
  placement: 'side' | 'bottom'
  open: boolean
  onOpenChange: (open: boolean) => void
  children: ReactNode
}

/** Non-modal panel: the content behind it, such as the preview, stays usable. */
export function Sheet({ title, placement, open, onOpenChange, children }: SheetProps) {
  const bodyId = useId()
  if (placement === 'side') {
    return (
      <aside className={cx(styles.sheet, styles.side)} aria-label={title}>
        {children}
      </aside>
    )
  }
  return (
    <aside className={cx(styles.sheet, styles.bottom, open && styles.open)} aria-label={title}>
      <button
        type="button"
        className={styles.handle}
        aria-expanded={open}
        aria-controls={bodyId}
        onClick={() => onOpenChange(!open)}
      >
        <span className={styles.grip} aria-hidden />
        <Text size="sm" weight="medium">
          {title}
        </Text>
        <Icon icon={open ? ChevronDown : ChevronUp} size="sm" />
      </button>
      <div id={bodyId} className={styles.body} hidden={!open}>
        {children}
      </div>
    </aside>
  )
}
