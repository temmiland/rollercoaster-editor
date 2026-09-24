/** Asset kinds the editor lists; each one is its own navigation section. */
export type AssetKind = 'maps' | 'tilesets' | 'models' | 'sprites' | 'dialogues'

interface AssetBase {
  /** Unique within its kind; used in URLs. */
  id: string
  name: string
  /** Project file the asset is defined in. */
  path: string
}

export interface MapAsset extends AssetBase {
  kind: 'maps'
  /** Folded maps are several planes in one file; the preview cannot show them yet. */
  folded: boolean
  /** Walking planes of a folded map; 1 for an ordinary map. */
  planes: number
  width: number
  depth: number
  tilesetId?: string
  props: number
  entities: number
  lights: number
  transitions: number
  events: number
}

export interface TilesetAsset extends AssetBase {
  kind: 'tilesets'
  texture: string
  tileWidth: number
  tileHeight: number
  tiles: number
}

export interface ModelAsset extends AssetBase {
  kind: 'models'
  source: string
  height: number
  walkable: boolean
}

export interface SpriteAsset extends AssetBase {
  kind: 'sprites'
  atlas: string
  height: number
  directions: string[]
}

export interface DialogueAsset extends AssetBase {
  kind: 'dialogues'
  startNode: string
  nodes: number
}

export type Asset = MapAsset | TilesetAsset | ModelAsset | SpriteAsset | DialogueAsset

export type AssetOf<K extends AssetKind> = Extract<Asset, { kind: K }>

export interface ProjectProblem {
  path: string
  message: string
}

export interface Project {
  id: string
  name: string
  files: ReadonlyMap<string, Uint8Array>
  assets: { [K in AssetKind]: AssetOf<K>[] }
  problems: ProjectProblem[]
}
