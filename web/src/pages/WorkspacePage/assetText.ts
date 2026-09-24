import type { Asset } from '@/lib/project/model.ts'
import type { Property } from '@/components/molecules/index.ts'

const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`

/** One-line summary for list entries. */
export function describe(asset: Asset): string {
  switch (asset.kind) {
    case 'maps':
      return asset.folded ? plural(asset.planes, 'Ebene', 'Ebenen') : `${asset.width} × ${asset.depth} · ${asset.tilesetId ?? 'ohne Tileset'}`
    case 'tilesets':
      return `${plural(asset.tiles.length, 'Kachel', 'Kacheln')} · ${asset.tileWidth} × ${asset.tileHeight} px`
    case 'models':
      return `${asset.height} hoch${asset.walkable ? ' · begehbar' : ''}`
    case 'sprites':
      return plural(asset.directions.length, 'Richtung', 'Richtungen')
    case 'dialogues':
      return plural(asset.nodes, 'Knoten', 'Knoten')
  }
}

export function properties(asset: Asset): Property[] {
  const file = { label: 'Datei', value: asset.path }
  switch (asset.kind) {
    case 'maps':
      return asset.folded
        ? [{ label: 'Name', value: asset.name }, { label: 'Art', value: 'Gefaltete Karte' }, { label: 'Ebenen', value: asset.planes }, file]
        : [
            { label: 'Name', value: asset.name },
            { label: 'Größe', value: `${asset.width} × ${asset.depth} Kacheln` },
            { label: 'Tileset', value: asset.tilesetId ?? '–' },
            { label: 'Props', value: asset.props },
            { label: 'Entities', value: asset.entities },
            { label: 'Lichter', value: asset.lights },
            { label: 'Übergänge', value: asset.transitions },
            { label: 'Events', value: asset.events },
            file,
          ]
    case 'tilesets':
      return [
        { label: 'ID', value: asset.id },
        { label: 'Kacheln', value: asset.tiles.length },
        { label: 'Kachelgröße', value: `${asset.tileWidth} × ${asset.tileHeight} px` },
        { label: 'Textur', value: asset.texture },
        file,
      ]
    case 'models':
      return [
        { label: 'ID', value: asset.id },
        { label: 'Quelle', value: asset.source },
        { label: 'Höhe', value: asset.height },
        { label: 'Begehbar', value: asset.walkable ? 'ja' : 'nein' },
        file,
      ]
    case 'sprites':
      return [
        { label: 'ID', value: asset.id },
        { label: 'Höhe', value: asset.height },
        { label: 'Richtungen', value: asset.directions.join(', ') || '–' },
        { label: 'Atlas', value: asset.atlas },
        file,
      ]
    case 'dialogues':
      return [
        { label: 'ID', value: asset.id },
        { label: 'Startknoten', value: asset.startNode },
        { label: 'Knoten', value: asset.nodes },
        file,
      ]
  }
}
