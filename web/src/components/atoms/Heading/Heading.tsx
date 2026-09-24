import type { ComponentProps } from 'react'
import { cx } from '@/lib/cx.ts'
import styles from './Heading.module.css'

export interface HeadingProps extends ComponentProps<'h1'> {
  /** Document outline level; independent of the visual size. */
  level: 1 | 2 | 3 | 4
  size?: 'sm' | 'md' | 'lg' | 'xl'
}

const defaultSizes = { 1: 'xl', 2: 'lg', 3: 'md', 4: 'sm' } as const

export function Heading({ level, size = defaultSizes[level], className, ...props }: HeadingProps) {
  const Tag = `h${level}` as const
  return <Tag {...props} className={cx(styles.heading, styles[size], className)} />
}
