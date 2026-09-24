import type { ReactNode } from 'react'
import { Heading } from '../../atoms/index.ts'
import { PropertyList, type Property } from '../../molecules/index.ts'
import styles from './Inspector.module.css'

export interface InspectorProps {
  title: string
  properties: Property[]
  /** Extra content below the properties, e.g. a texture preview. */
  children?: ReactNode
}

export function Inspector({ title, properties, children }: InspectorProps) {
  return (
    <div className={styles.inspector}>
      <Heading level={2} size="sm">
        {title}
      </Heading>
      <PropertyList properties={properties} />
      {children}
    </div>
  )
}
