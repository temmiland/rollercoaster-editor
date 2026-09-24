import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { TriangleAlert } from 'lucide-react'
import { isPreviewReply, type ShowMapRequest } from '@/lib/preview/protocol.ts'
import { Spinner, Text } from '../../atoms/index.ts'
import { EmptyState } from '../../molecules/index.ts'
import styles from './PreviewViewport.module.css'

export interface PreviewViewportProps {
  /** The map to show; the previous map stays visible while the next one loads. */
  request: ShowMapRequest
  /** URL of the TeaVM preview build. */
  src?: string
}

interface Result {
  request: ShowMapRequest
  error: string | null
}

/** Embeds the engine preview in an iframe and feeds it maps over postMessage. */
export function PreviewViewport({ request, src = '/preview/index.html' }: PreviewViewportProps) {
  const frameRef = useRef<HTMLIFrameElement>(null)
  // The preview answers in order, so each reply belongs to the oldest request still in flight.
  const inFlight = useRef<ShowMapRequest[]>([])
  const [ready, setReady] = useState(false)
  const [result, setResult] = useState<Result | null>(null)

  const onMessage = useEffectEvent((event: MessageEvent) => {
    if (event.source !== frameRef.current?.contentWindow || !isPreviewReply(event.data)) return
    if (event.data.type === 'ready') {
      setReady(true)
      return
    }
    const answered = inFlight.current.shift()
    if (answered) setResult({ request: answered, error: event.data.error })
  })

  useEffect(() => {
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [])

  useEffect(() => {
    if (!ready) return
    inFlight.current.push(request)
    frameRef.current?.contentWindow?.postMessage(request, '*')
  }, [ready, request])

  const status = !ready ? 'starting' : result?.request !== request ? 'loading' : result.error ? 'failed' : 'shown'

  return (
    <div className={styles.viewport}>
      <iframe ref={frameRef} src={src} title="3D-Vorschau" className={styles.frame} />
      {(status === 'starting' || status === 'loading') && (
        <div className={styles.status} role="status">
          <Spinner size="sm" />
          <Text size="sm">{status === 'starting' ? 'Vorschau startet…' : 'Karte wird geladen…'}</Text>
        </div>
      )}
      {status === 'failed' && result?.error && (
        <div className={styles.overlay}>
          <EmptyState icon={TriangleAlert} tone="danger" title="Karte lässt sich nicht anzeigen" description={result.error} />
        </div>
      )}
    </div>
  )
}
