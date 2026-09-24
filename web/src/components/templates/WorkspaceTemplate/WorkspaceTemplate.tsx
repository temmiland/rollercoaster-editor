import { Activity, useState, type ReactNode } from 'react'
import type { LayoutClass } from '@/lib/layout/useMediaQuery.ts'
import { cx } from '@/lib/cx.ts'
import { Sheet } from '../../organisms/index.ts'
import styles from './WorkspaceTemplate.module.css'

export interface WorkspaceTemplateProps {
  layout: LayoutClass
  appBar: ReactNode
  /** Rendered as a rail on medium and expanded layouts, as a bottom bar on compact ones. */
  navigation: ReactNode
  list: ReactNode
  detail: ReactNode
  inspector?: ReactNode
  inspectorTitle?: string
  /** Compact layouts show one pane at a time; this picks the detail over the list. */
  showDetail: boolean
}

/**
 * Arranges navigation, list, detail and inspector for the three width classes in
 * docs/web-editor-ux.md. Hidden panes stay mounted through Activity, so the list keeps its
 * search and scroll position and the preview keeps its running engine.
 */
export function WorkspaceTemplate({
  layout,
  appBar,
  navigation,
  list,
  detail,
  inspector,
  inspectorTitle = 'Eigenschaften',
  showDetail,
}: WorkspaceTemplateProps) {
  const [inspectorOpen, setInspectorOpen] = useState(false)
  const compact = layout === 'compact'
  const inspectorSheet = inspector && (
    <Sheet
      title={inspectorTitle}
      placement={layout === 'expanded' ? 'side' : 'bottom'}
      open={inspectorOpen}
      onOpenChange={setInspectorOpen}
    >
      {inspector}
    </Sheet>
  )

  return (
    <div className={cx(styles.workspace, styles[layout])}>
      {!compact && navigation}
      <div className={styles.main}>
        {appBar}
        <div className={styles.panes}>
          <Activity mode={!compact || !showDetail ? 'visible' : 'hidden'}>
            <div className={styles.list}>{list}</div>
          </Activity>
          <Activity mode={!compact || showDetail ? 'visible' : 'hidden'}>
            <div className={styles.detail}>
              <div className={styles.content}>{detail}</div>
              {layout !== 'expanded' && inspectorSheet}
            </div>
          </Activity>
          {layout === 'expanded' && inspectorSheet}
        </div>
      </div>
      {compact && navigation}
    </div>
  )
}
