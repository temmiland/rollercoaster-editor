import type { ShowMapRequest } from '../preview/protocol.ts'
import type { MapAsset, Project } from './model.ts'

type PreviewRequestResult = ShowMapRequest | { error: string }

// Keyed by the map asset, which lives as long as its loaded project; a stable identity keeps
// the preview from reloading a map it already shows.
const cache = new WeakMap<MapAsset, PreviewRequestResult>()

/** Builds the preview message for a map, or explains why the map cannot be shown. */
export function previewRequest(project: Project, map: MapAsset): PreviewRequestResult {
  let result = cache.get(map)
  if (!result) {
    result = buildRequest(project, map)
    cache.set(map, result)
  }
  return result
}

function buildRequest(project: Project, map: MapAsset): PreviewRequestResult {
  if (map.folded) return { error: 'Gefaltete Karten kann die Vorschau noch nicht anzeigen.' }
  const tileset = project.assets.tilesets.find((candidate) => candidate.id === map.tilesetId)
  if (!tileset) return { error: `Tileset '${map.tilesetId ?? '?'}' fehlt im Projekt.` }
  return {
    type: 'showMap',
    // The preview resolves relative references itself, so it gets every file.
    files: Object.fromEntries(project.files),
    map: map.path,
    tileset: tileset.path,
    models: project.assets.models[0]?.path,
    sprites: project.assets.sprites[0]?.path,
  }
}
