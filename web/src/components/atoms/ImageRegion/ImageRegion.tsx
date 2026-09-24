import { cx } from '@/lib/cx.ts'
import styles from './ImageRegion.module.css'

export interface ImageRegionProps {
  /** Image containing the region, e.g. a texture atlas. */
  src: string
  imageWidth: number
  imageHeight: number
  /** Pixel rectangle inside the image: x, y, width, height. */
  region: readonly [number, number, number, number]
  /** Displayed width in CSS pixels; height follows the region's aspect ratio. */
  size: number
  label: string
  className?: string
}

/** Shows one rectangle of a larger image, scaled with crisp pixels. */
export function ImageRegion({ src, imageWidth, imageHeight, region, size, label, className }: ImageRegionProps) {
  const [x, y, width, height] = region
  const scale = size / Math.max(width, 1)
  return (
    <span
      role="img"
      aria-label={label}
      className={cx(styles.region, className)}
      style={{
        width: size,
        height: height * scale,
        backgroundImage: `url(${src})`,
        backgroundSize: `${imageWidth * scale}px ${imageHeight * scale}px`,
        backgroundPosition: `${-x * scale}px ${-y * scale}px`,
      }}
    />
  )
}
