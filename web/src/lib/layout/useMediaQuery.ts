import { useSyncExternalStore } from 'react'

export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    () => window.matchMedia(query).matches,
    () => false,
  )
}

/** Width classes from docs/web-editor-ux.md; keep in sync with the CSS media queries. */
export type LayoutClass = 'compact' | 'medium' | 'expanded'

export function useLayoutClass(): LayoutClass {
  const expanded = useMediaQuery('(min-width: 1024px)')
  const medium = useMediaQuery('(min-width: 640px)')
  return expanded ? 'expanded' : medium ? 'medium' : 'compact'
}
