import { FolderGit2, Gamepad2 } from 'lucide-react'
import { demoProjectId } from '@/lib/project/projects.ts'
import { useRouter } from '@/lib/router/Router.tsx'
import { workspaceHref } from '@/lib/router/routes.ts'
import { Badge, Button, Heading, Icon, Text } from '@/components/atoms/index.ts'
import styles from './WelcomePage.module.css'

export function WelcomePage() {
  const { navigate, isPending } = useRouter()
  return (
    <main className={styles.page}>
      <title>Rollercoaster Editor</title>
      <div className={styles.card}>
        <Icon icon={Gamepad2} size="lg" className={styles.logo} />
        <Heading level={1}>Rollercoaster Editor</Heading>
        <Text as="p" tone="muted">
          Karten, Modelle, Sprites und Dialoge zu einem Spiel zusammenbauen – am Rechner, Tablet oder Telefon.
        </Text>
        <div className={styles.actions}>
          <Button variant="primary" fullWidth loading={isPending} onClick={() => navigate(workspaceHref(demoProjectId, 'maps'))}>
            Demo-Projekt öffnen
          </Button>
          <Button icon={FolderGit2} fullWidth disabled>
            Gitea-Repo verbinden <Badge>bald</Badge>
          </Button>
        </div>
      </div>
    </main>
  )
}
