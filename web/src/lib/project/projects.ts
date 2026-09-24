import { loadProject } from './loadProject.ts'
import type { Project } from './model.ts'
import { HttpProjectSource, type ProjectSource } from './ProjectSource.ts'

export const demoProjectId = 'demo'

function sourceFor(projectId: string): ProjectSource {
  if (projectId === demoProjectId) return new HttpProjectSource(demoProjectId, 'Demo-Projekt', '/demo-project')
  throw new Error(`Unbekanntes Projekt '${projectId}'`)
}

const cache = new Map<string, Promise<Project>>()

/** One stable promise per project, as `use()` requires; read it inside a Suspense boundary. */
export function projectPromise(projectId: string): Promise<Project> {
  let promise = cache.get(projectId)
  if (!promise) {
    promise = loadProject(sourceFor(projectId))
    cache.set(projectId, promise)
  }
  return promise
}

/** Drops a cached (usually failed) load so the next read starts over. */
export function forgetProject(projectId: string): void {
  cache.delete(projectId)
}
