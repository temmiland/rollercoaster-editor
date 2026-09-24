import { Compass } from 'lucide-react'
import { useRouter } from '@/lib/router/Router.tsx'
import { Button } from '@/components/atoms/index.ts'
import { EmptyState } from '@/components/molecules/index.ts'

export function NotFoundPage() {
  const { navigate } = useRouter()
  return (
    <main style={{ height: '100dvh' }}>
      <title>Nicht gefunden · Rollercoaster Editor</title>
      <EmptyState
        icon={Compass}
        title="Diese Seite gibt es nicht"
        description="Der Link ist veraltet oder falsch geschrieben."
        action={<Button onClick={() => navigate('/')}>Zur Startseite</Button>}
      />
    </main>
  )
}
