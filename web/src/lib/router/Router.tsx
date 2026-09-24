import { createContext, use, useEffect, useEffectEvent, useState, useTransition, type ReactNode } from 'react'

export interface NavigateOptions {
  replace?: boolean
}

interface RouterValue {
  path: string
  /** True while a navigation is still rendering in the background. */
  isPending: boolean
  navigate: (to: string, options?: NavigateOptions) => void
}

const RouterContext = createContext<RouterValue | null>(null)

const currentPath = () => window.location.pathname

/**
 * Keeps the location in React state and updates it inside transitions, so a section that is still
 * loading keeps the previous screen visible instead of flashing a fallback. Same-origin link clicks
 * are intercepted globally, which lets presentational components render plain anchors.
 */
export function RouterProvider({ children }: { children: ReactNode }) {
  const [path, setPath] = useState(currentPath)
  const [isPending, startTransition] = useTransition()

  const navigate = (to: string, { replace = false }: NavigateOptions = {}) => {
    const url = new URL(to, window.location.href)
    if (url.pathname === currentPath() && !replace) return
    window.history[replace ? 'replaceState' : 'pushState'](null, '', url)
    startTransition(() => setPath(url.pathname))
  }

  const onPopState = useEffectEvent(() => startTransition(() => setPath(currentPath())))

  const onClick = useEffectEvent((event: MouseEvent) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    const anchor = (event.target as Element | null)?.closest('a')
    if (!anchor || anchor.target || anchor.hasAttribute('download') || anchor.origin !== window.location.origin) return
    event.preventDefault()
    navigate(anchor.href)
  })

  useEffect(() => {
    window.addEventListener('popstate', onPopState)
    document.addEventListener('click', onClick)
    return () => {
      window.removeEventListener('popstate', onPopState)
      document.removeEventListener('click', onClick)
    }
  }, [])

  return <RouterContext value={{ path, isPending, navigate }}>{children}</RouterContext>
}

export function useRouter(): RouterValue {
  const router = use(RouterContext)
  if (!router) throw new Error('useRouter needs a RouterProvider')
  return router
}
