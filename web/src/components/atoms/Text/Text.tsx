import type { ComponentProps } from 'react'
import { cx } from '@/lib/cx.ts'
import styles from './Text.module.css'

export type TextTone = 'default' | 'muted' | 'danger' | 'success' | 'warning'

export interface TextProps extends Omit<ComponentProps<'span'>, 'ref'> {
  as?: 'span' | 'p' | 'div'
  size?: 'xs' | 'sm' | 'md'
  tone?: TextTone
  weight?: 'regular' | 'medium'
  mono?: boolean
  /** Cuts off at one line with an ellipsis. */
  truncate?: boolean
}

export function Text({
  as: Tag = 'span',
  size = 'md',
  tone = 'default',
  weight = 'regular',
  mono = false,
  truncate = false,
  className,
  ...props
}: TextProps) {
  return (
    <Tag
      {...props}
      className={cx(
        styles.text,
        styles[size],
        styles[tone],
        weight === 'medium' && styles.medium,
        mono && styles.mono,
        truncate && styles.truncate,
        className,
      )}
    />
  )
}
