import type { Ref } from 'react'
import { Search, X } from 'lucide-react'
import { Icon, IconButton, Input } from '../../atoms/index.ts'
import styles from './SearchField.module.css'

export interface SearchFieldProps {
  value: string
  onChange: (value: string) => void
  label: string
  placeholder?: string
  ref?: Ref<HTMLInputElement>
}

export function SearchField({ value, onChange, label, placeholder, ref }: SearchFieldProps) {
  return (
    <div className={styles.field}>
      <Icon icon={Search} size="sm" className={styles.icon} />
      <Input
        ref={ref}
        type="search"
        aria-label={label}
        placeholder={placeholder ?? label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && value) {
            event.stopPropagation()
            onChange('')
          }
        }}
        className={styles.input}
        enterKeyHint="search"
        autoComplete="off"
        spellCheck={false}
      />
      {value && <IconButton icon={X} label="Suche leeren" className={styles.clear} onClick={() => onChange('')} />}
    </div>
  )
}
