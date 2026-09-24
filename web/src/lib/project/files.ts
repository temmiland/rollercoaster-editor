import type { Project } from './model.ts'

/** Resolves a manifest's file reference, which is relative to the manifest's own directory. */
export function resolveRelative(manifestPath: string, reference: string): string {
  const segments = manifestPath.split('/').slice(0, -1)
  for (const segment of reference.split('/')) {
    if (segment === '..') segments.pop()
    else if (segment && segment !== '.') segments.push(segment)
  }
  return segments.join('/')
}

const imageTypes: Record<string, string> = { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp' }
const objectUrls = new WeakMap<Uint8Array, string>()

/**
 * Object URL for an image file of a loaded project. URLs are cached per file content and live as
 * long as the project, which is loaded once per session.
 */
export function imageUrl(project: Project, path: string): string | undefined {
  const bytes = project.files.get(path)
  const type = imageTypes[path.slice(path.lastIndexOf('.') + 1).toLowerCase()]
  if (!bytes || !type) return undefined
  let url = objectUrls.get(bytes)
  if (!url) {
    url = URL.createObjectURL(new Blob([bytes as Uint8Array<ArrayBuffer>], { type }))
    objectUrls.set(bytes, url)
  }
  return url
}

/** Pixel size of a PNG file, read from its IHDR header. */
export function pngSize(project: Project, path: string): { width: number; height: number } | undefined {
  const bytes = project.files.get(path)
  if (!bytes || bytes.length < 24 || bytes[1] !== 0x50 || bytes[2] !== 0x4e || bytes[3] !== 0x47) return undefined
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  return { width: view.getUint32(16), height: view.getUint32(20) }
}

/** First page image named by a libGDX texture atlas: the first non-empty line of the file. */
export function atlasPageImage(project: Project, atlasPath: string): string | undefined {
  const bytes = project.files.get(atlasPath)
  if (!bytes) return undefined
  const firstLine = new TextDecoder().decode(bytes).split(/\r?\n/).find((line) => line.trim())
  return firstLine && resolveRelative(atlasPath, firstLine.trim())
}
