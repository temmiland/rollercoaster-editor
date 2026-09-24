import type { ComponentProps } from 'react'
import { cx } from '@/lib/cx.ts'
import styles from './Input.module.css'

export type InputProps = ComponentProps<'input'>

export function Input({ className, ...props }: InputProps) {
  return <input {...props} className={cx(styles.input, className)} />
}
