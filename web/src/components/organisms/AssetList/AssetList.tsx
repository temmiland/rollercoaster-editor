import { useDeferredValue, useEffect, useEffectEvent, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { SearchX } from 'lucide-react'
import { Heading } from '../../atoms/index.ts'
import { EmptyState, ListItem, SearchField } from '../../molecules/index.ts'
import styles from './AssetList.module.css'

export interface AssetListItem {
  id: string
  href: string
  title: string
  subtitle?: string
  icon?: LucideIcon
  trailing?: ReactNode
}

export interface AssetListProps {
  title: string
  items: AssetListItem[]
  selectedId?: string
  /** Shown when the list itself is empty, as opposed to a search without results. */
  empty: ReactNode
}

/** Searchable list; `/` focuses the search, arrow keys move between entries. */
export function AssetList({ title, items, selectedId, empty }: AssetListProps) {
  const [query, setQuery] = useState('')
  const deferredQuery = useDeferredValue(query)
  const searchRef = useRef<HTMLInputElement>(null)
  const listRef = useRef<HTMLUListElement>(null)

  const needle = deferredQuery.trim().toLowerCase()
  const visible = needle
    ? items.filter((item) => item.title.toLowerCase().includes(needle) || item.subtitle?.toLowerCase().includes(needle))
    : items

  const onGlobalKey = useEffectEvent((event: globalThis.KeyboardEvent) => {
    const target = event.target as HTMLElement
    if (event.key !== '/' || target.closest('input, textarea, [contenteditable]')) return
    event.preventDefault()
    searchRef.current?.focus()
  })

  useEffect(() => {
    window.addEventListener('keydown', onGlobalKey)
    return () => window.removeEventListener('keydown', onGlobalKey)
  }, [])

  const moveFocus = (event: KeyboardEvent<HTMLUListElement>) => {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
    const links = [...(listRef.current?.querySelectorAll('a') ?? [])]
    const index = links.indexOf(document.activeElement as HTMLAnchorElement)
    const next = links[event.key === 'ArrowDown' ? index + 1 : Math.max(index - 1, 0)]
    if (next) {
      event.preventDefault()
      next.focus()
    }
  }

  return (
    <section className={styles.list} aria-label={title}>
      <div className={styles.header}>
        <Heading level={2} size="sm">
          {title}
        </Heading>
        {items.length > 0 && <SearchField ref={searchRef} label={`${title} durchsuchen`} value={query} onChange={setQuery} />}
      </div>
      {items.length === 0 ? (
        empty
      ) : visible.length === 0 ? (
        <EmptyState icon={SearchX} title="Keine Treffer" description={`Nichts passt zu „${deferredQuery}“.`} />
      ) : (
        <ul ref={listRef} className={styles.items} onKeyDown={moveFocus}>
          {visible.map((item) => (
            <li key={item.id}>
              <ListItem
                href={item.href}
                title={item.title}
                subtitle={item.subtitle}
                icon={item.icon}
                trailing={item.trailing}
                selected={item.id === selectedId}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
