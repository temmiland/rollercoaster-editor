import { Box, Grid3x3, Map, MessagesSquare, PersonStanding, type LucideIcon } from 'lucide-react'
import type { AssetKind } from '@/lib/project/model.ts'

export interface SectionMeta {
  label: string
  icon: LucideIcon
  /** Shown when the project has no asset of this kind yet. */
  emptyHint: string
}

export const sections: Record<AssetKind, SectionMeta> = {
  maps: { label: 'Karten', icon: Map, emptyHint: 'Dieses Projekt enthält noch keine Karte.' },
  tilesets: { label: 'Tilesets', icon: Grid3x3, emptyHint: 'Dieses Projekt enthält noch kein Tileset.' },
  models: { label: 'Modelle', icon: Box, emptyHint: 'Dieses Projekt enthält noch kein Modell.' },
  sprites: { label: 'Sprites', icon: PersonStanding, emptyHint: 'Dieses Projekt enthält noch keinen Sprite.' },
  dialogues: { label: 'Dialoge', icon: MessagesSquare, emptyHint: 'Dieses Projekt enthält noch keinen Dialog.' },
}
