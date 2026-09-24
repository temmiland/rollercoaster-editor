import type { MapAsset, Project } from './model.ts'

export interface ShowMapRequest {
  type: 'showMap'
  files: Record<string, Uint8Array>
  map: string
  tileset: string
  models?: string
  sprites?: string
}

/** Builds the preview message for a map, or explains why the map cannot be shown. */
export function previewRequest(project: Project, map: MapAsset): ShowMapRequest | { error: string } {
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
