import { Suspense } from 'react'
import { CloudOff } from 'lucide-react'
import { ErrorBoundary } from './lib/ErrorBoundary.tsx'
import { forgetProject } from './lib/project/projects.ts'
import { RouterProvider, useRouter } from './lib/router/Router.tsx'
import { parseRoute } from './lib/router/routes.ts'
import { Button, Spinner, Text } from './components/atoms/index.ts'
import { EmptyState } from './components/molecules/index.ts'
import { NotFoundPage } from './pages/NotFoundPage/NotFoundPage.tsx'
import { WelcomePage } from './pages/WelcomePage/WelcomePage.tsx'
import { WorkspacePage } from './pages/WorkspacePage/WorkspacePage.tsx'
import styles from './App.module.css'

export function App() {
  return (
    <RouterProvider>
      <Routes />
    </RouterProvider>
  )
}

function Routes() {
  const { path } = useRouter()
  const route = parseRoute(path)
  switch (route.page) {
    case 'welcome':
      return <WelcomePage />
    case 'notFound':
      return <NotFoundPage />
    case 'workspace':
      return (
        // Keyed by project: switching projects starts a fresh load and clears a previous error.
        <ErrorBoundary
          key={route.projectId}
          fallback={(error, reset) => (
            <main className={styles.fullscreen}>
              <EmptyState
                icon={CloudOff}
                tone="danger"
                title="Projekt lässt sich nicht laden"
                description={error.message}
                action={
                  <Button
                    onClick={() => {
                      forgetProject(route.projectId)
                      reset()
                    }}
                  >
                    Erneut versuchen
                  </Button>
                }
              />
            </main>
          )}
        >
          <Suspense
            fallback={
              <main className={styles.fullscreen} role="status">
                <Spinner size="lg" />
                <Text tone="muted">Projekt wird geladen…</Text>
              </main>
            }
          >
            <WorkspacePage projectId={route.projectId} section={route.section} itemId={route.itemId} />
          </Suspense>
        </ErrorBoundary>
      )
  }
}
