import { use } from 'react'
import { ArrowLeft, FileQuestion, House, MousePointerClick } from 'lucide-react'
import { useLayoutClass } from '@/lib/layout/useMediaQuery.ts'
import type { AssetKind } from '@/lib/project/model.ts'
import { projectPromise } from '@/lib/project/projects.ts'
import { useRouter } from '@/lib/router/Router.tsx'
import { assetKinds, workspaceHref } from '@/lib/router/routes.ts'
import { Badge, IconButton } from '@/components/atoms/index.ts'
import { EmptyState } from '@/components/molecules/index.ts'
import { AppBar, AssetList, Inspector, Navigation } from '@/components/organisms/index.ts'
import { WorkspaceTemplate } from '@/components/templates/index.ts'
import { sections } from '../sections.ts'
import { AssetDetail } from './AssetDetail.tsx'
import { describe, properties } from './assetText.ts'

export interface WorkspacePageProps {
  projectId: string
  section: AssetKind
  itemId?: string
}

/** Suspends until the project is loaded; wrap it in Suspense and an error boundary. */
export function WorkspacePage({ projectId, section, itemId }: WorkspacePageProps) {
  const project = use(projectPromise(projectId))
  const layout = useLayoutClass()
  const { navigate, isPending } = useRouter()
  const compact = layout === 'compact'
  const meta = sections[section]
  const assets = project.assets[section]
  const selected = itemId === undefined ? undefined : assets.find((asset) => asset.id === itemId)
  const listHref = workspaceHref(projectId, section)

  const leading =
    compact && itemId !== undefined ? (
      <IconButton icon={ArrowLeft} label={`Zurück zu ${meta.label}`} onClick={() => navigate(listHref)} />
    ) : (
      <IconButton icon={House} label="Startseite" onClick={() => navigate('/')} />
    )

  const detail = selected ? (
    <AssetDetail project={project} asset={selected} />
  ) : itemId !== undefined ? (
    <EmptyState icon={FileQuestion} title="Nicht gefunden" description={`„${itemId}“ gibt es in ${meta.label} nicht.`} />
  ) : (
    <EmptyState icon={MousePointerClick} title="Nichts ausgewählt" description={`Wähle einen Eintrag aus ${meta.label}.`} />
  )

  return (
    <>
      <title>{`${selected?.name ?? meta.label} · ${project.name}`}</title>
      <WorkspaceTemplate
        layout={layout}
        showDetail={itemId !== undefined}
        appBar={
          <AppBar
            title={compact && selected ? selected.name : project.name}
            subtitle={meta.label}
            leading={leading}
            busy={isPending}
            actions={
              project.problems.length > 0 && (
                <Badge tone="warning">
                  {project.problems.length} {project.problems.length === 1 ? 'Problem' : 'Probleme'}
                </Badge>
              )
            }
          />
        }
        navigation={
          <Navigation
            label="Bereiche"
            placement={compact ? 'bar' : 'rail'}
            currentKey={section}
            items={assetKinds.map((kind) => ({
              key: kind,
              href: workspaceHref(projectId, kind),
              icon: sections[kind].icon,
              label: sections[kind].label,
            }))}
          />
        }
        list={
          <AssetList
            title={meta.label}
            selectedId={itemId}
            items={assets.map((asset) => ({
              id: asset.id,
              href: workspaceHref(projectId, section, asset.id),
              title: asset.name,
              subtitle: describe(asset),
              icon: meta.icon,
              trailing: asset.kind === 'maps' && asset.folded ? <Badge>gefaltet</Badge> : undefined,
            }))}
            empty={<EmptyState icon={meta.icon} title={`Keine ${meta.label}`} description={meta.emptyHint} />}
          />
        }
        detail={detail}
        inspector={selected && <Inspector title={selected.name} properties={properties(selected)} />}
      />
    </>
  )
}
