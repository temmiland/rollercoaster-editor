import { Box, ImageOff, Layers, MessagesSquare } from 'lucide-react'
import type { Asset, Project } from '@/lib/project/model.ts'
import { atlasPageImage, imageUrl, pngSize, resolveRelative } from '@/lib/project/files.ts'
import { previewRequest } from '@/lib/project/previewRequest.ts'
import { EmptyState } from '@/components/molecules/index.ts'
import { PreviewViewport, TileGallery } from '@/components/organisms/index.ts'
import styles from './WorkspacePage.module.css'

export function AssetDetail({ project, asset }: { project: Project; asset: Asset }) {
  switch (asset.kind) {
    case 'maps': {
      const request = previewRequest(project, asset)
      if ('error' in request) return <EmptyState icon={Layers} title="Keine Vorschau" description={request.error} />
      return <PreviewViewport request={request} />
    }
    case 'tilesets': {
      const texture = resolveRelative(asset.path, asset.texture)
      const src = imageUrl(project, texture)
      const size = pngSize(project, texture)
      if (!src || !size) return <ImageDetail url={undefined} alt="" />
      return (
        <TileGallery
          label={`Kacheln von ${asset.name}`}
          tiles={asset.tiles.map((tile) => ({
            id: tile.id,
            name: tile.id,
            atlas: { src, ...size },
            top: tile.region,
            side: tile.side,
            walkable: tile.walkable,
          }))}
        />
      )
    }
    case 'sprites': {
      const page = atlasPageImage(project, resolveRelative(asset.path, asset.atlas))
      return <ImageDetail url={page && imageUrl(project, page)} alt={`Atlas von ${asset.name}`} />
    }
    case 'models':
      return <EmptyState icon={Box} title="Modellansicht folgt" description="Modelle erscheinen bis dahin auf den Karten, die sie platzieren." />
    case 'dialogues':
      return <EmptyState icon={MessagesSquare} title="Dialog-Editor folgt" description="Knoten und Antworten stehen in den Eigenschaften." />
  }
}

function ImageDetail({ url, alt }: { url: string | undefined; alt: string }) {
  if (!url) return <EmptyState icon={ImageOff} title="Bild fehlt" description="Die referenzierte Bilddatei liegt nicht im Projekt." />
  return (
    <div className={styles.imageStage}>
      <img src={url} alt={alt} className={styles.image} />
    </div>
  )
}
