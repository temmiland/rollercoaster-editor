import type { AssetKind } from '../project/model.ts'

export const assetKinds: readonly AssetKind[] = ['maps', 'tilesets', 'models', 'sprites', 'dialogues']

export type Route =
  | { page: 'welcome' }
  | { page: 'workspace'; projectId: string; section: AssetKind; itemId?: string }
  | { page: 'notFound' }

const isAssetKind = (value: string | undefined): value is AssetKind => assetKinds.includes(value as AssetKind)

/** `/projects/<project>/<section>/<item>`; item IDs may contain slashes and are URL-encoded. */
export function parseRoute(path: string): Route {
  const [first, projectId, section, itemId, ...rest] = path.split('/').filter(Boolean).map(decodeURIComponent)
  if (!first) return { page: 'welcome' }
  if (first !== 'projects' || !projectId || rest.length > 0) return { page: 'notFound' }
  if (!section) return { page: 'workspace', projectId, section: 'maps' }
  if (!isAssetKind(section)) return { page: 'notFound' }
  return { page: 'workspace', projectId, section, itemId }
}

export function workspaceHref(projectId: string, section: AssetKind, itemId?: string): string {
  const parts = ['projects', projectId, section, ...(itemId ? [itemId] : [])]
  return '/' + parts.map(encodeURIComponent).join('/')
}
