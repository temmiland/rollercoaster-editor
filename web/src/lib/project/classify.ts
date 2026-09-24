import type { Asset, DialogueAsset, MapAsset, ModelAsset, Region, SpriteAsset, TileDefinition, TilesetAsset } from './model.ts'

type Json = Record<string, unknown>

const isObject = (value: unknown): value is Json => typeof value === 'object' && value !== null && !Array.isArray(value)
const array = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])
const count = (value: unknown) => array(value).length
const text = (value: unknown, fallback = '') => (typeof value === 'string' ? value : fallback)
const number = (value: unknown, fallback = 0) => (typeof value === 'number' ? value : fallback)
const baseName = (path: string) => path.slice(path.lastIndexOf('/') + 1).replace(/\.json$/, '')

/**
 * Recognises engine documents by their shape rather than their location, so a project may
 * organise its folders freely. Returns an empty list for JSON that is no known document.
 */
export function classify(path: string, json: unknown): Asset[] {
  if (!isObject(json)) return []
  if (isObject(json.layers) && Array.isArray(json.size)) return [mapAsset(path, json, false)]
  if (Array.isArray(json.planes)) return [mapAsset(path, json, true)]
  if (Array.isArray(json.tiles) && typeof json.texture === 'string') return [tilesetAsset(path, json)]
  if (Array.isArray(json.models)) return array(json.models).filter(isObject).map((model) => modelAsset(path, model))
  if (typeof json.atlas === 'string' && Array.isArray(json.sprites)) {
    return array(json.sprites).filter(isObject).map((sprite) => spriteAsset(path, json, sprite))
  }
  if (Array.isArray(json.dialogues)) return array(json.dialogues).filter(isObject).map((dialogue) => dialogueAsset(path, dialogue))
  return []
}

function mapAsset(path: string, json: Json, folded: boolean): MapAsset {
  const size = array(json.size)
  return {
    kind: 'maps',
    id: path,
    name: text(json.name, baseName(path)),
    path,
    folded,
    planes: folded ? count(json.planes) : 1,
    width: number(size[0]),
    depth: number(size[1]),
    tilesetId: typeof json.tileset === 'string' ? json.tileset : undefined,
    props: count(json.props),
    entities: count(json.entities),
    lights: count(json.lights),
    transitions: count(json.transitions),
    events: count(json.events),
  }
}

function tilesetAsset(path: string, json: Json): TilesetAsset {
  const tileSize = array(json.tileSize)
  const id = text(json.id, baseName(path))
  return {
    kind: 'tilesets',
    id,
    name: id,
    path,
    texture: text(json.texture),
    tileWidth: number(tileSize[0]),
    tileHeight: number(tileSize[1]),
    tiles: array(json.tiles).filter(isObject).map(tileDefinition),
  }
}

function region(value: unknown): Region | undefined {
  const [x, y, width, height] = array(value)
  return [x, y, width, height].every((part) => typeof part === 'number')
    ? [x as number, y as number, width as number, height as number]
    : undefined
}

function tileDefinition(tile: Json): TileDefinition {
  return { id: text(tile.id), region: region(tile.region) ?? [0, 0, 0, 0], side: region(tile.side), walkable: tile.walkable !== false }
}

function modelAsset(path: string, model: Json): ModelAsset {
  const id = text(model.id)
  return {
    kind: 'models',
    id,
    name: id,
    path,
    source: text(model.source),
    height: number(model.height),
    walkable: model.walkable === true,
  }
}

function spriteAsset(path: string, manifest: Json, sprite: Json): SpriteAsset {
  const id = text(sprite.id)
  return {
    kind: 'sprites',
    id,
    name: id,
    path,
    atlas: text(manifest.atlas),
    height: number(sprite.height),
    directions: isObject(sprite.directions) ? Object.keys(sprite.directions) : [],
  }
}

function dialogueAsset(path: string, dialogue: Json): DialogueAsset {
  const id = text(dialogue.id)
  return { kind: 'dialogues', id, name: id, path, startNode: text(dialogue.startNode), nodes: count(dialogue.nodes) }
}
