import { Badge, ImageRegion, Text } from '../../atoms/index.ts'
import styles from './TileCard.module.css'

type Region = readonly [number, number, number, number]

export interface TileCardProps {
  name: string
  atlas: { src: string; width: number; height: number }
  top: Region
  side?: Region
  walkable: boolean
}

/** One tile type: its top face, its side face and whether actors may walk on it. */
export function TileCard({ name, atlas, top, side, walkable }: TileCardProps) {
  const region = { src: atlas.src, imageWidth: atlas.width, imageHeight: atlas.height }
  return (
    <figure className={styles.card}>
      <div className={styles.faces}>
        <ImageRegion {...region} region={top} size={64} label={`${name}, Oberseite`} className={styles.face} />
        <ImageRegion {...region} region={side ?? top} size={40} label={`${name}, Seite`} className={styles.face} />
      </div>
      <figcaption className={styles.caption}>
        <Text size="sm" weight="medium" truncate>
          {name}
        </Text>
        <Badge tone={walkable ? 'success' : 'warning'}>{walkable ? 'begehbar' : 'gesperrt'}</Badge>
      </figcaption>
    </figure>
  )
}
