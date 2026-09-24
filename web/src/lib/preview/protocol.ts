/** Messages exchanged with the TeaVM preview iframe; see docs/web-editor.md. */
export interface ShowMapRequest {
  type: 'showMap'
  /** Project-relative path → file content. */
  files: Record<string, Uint8Array>
  map: string
  tileset: string
  models?: string
  sprites?: string
}

export interface SetTimeOfDayRequest {
  type: 'setTimeOfDay'
  hours: number
}

export type PreviewRequest = ShowMapRequest | SetTimeOfDayRequest

export type PreviewReply =
  | { source: 'rollercoaster-preview'; type: 'ready'; error: null }
  | { source: 'rollercoaster-preview'; type: 'showMapResult'; error: string | null }

export function isPreviewReply(data: unknown): data is PreviewReply {
  return typeof data === 'object' && data !== null && (data as { source?: unknown }).source === 'rollercoaster-preview'
}
