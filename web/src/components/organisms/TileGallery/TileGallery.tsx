import { TileCard, type TileCardProps } from '../../molecules/index.ts'
import styles from './TileGallery.module.css'

export interface TileGalleryProps {
  label: string
  tiles: Array<TileCardProps & { id: string }>
}

export function TileGallery({ label, tiles }: TileGalleryProps) {
  return (
    <ul className={styles.gallery} aria-label={label}>
      {tiles.map(({ id, ...tile }) => (
        <li key={id}>
          <TileCard {...tile} />
        </li>
      ))}
    </ul>
  )
}
