import type { ReactNode } from 'react'
import styles from './PropertyList.module.css'

export interface Property {
  label: string
  value: ReactNode
}

export interface PropertyListProps {
  properties: Property[]
}

export function PropertyList({ properties }: PropertyListProps) {
  return (
    <dl className={styles.list}>
      {properties.map(({ label, value }) => (
        <div key={label} className={styles.row}>
          <dt className={styles.label}>{label}</dt>
          <dd className={styles.value}>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
