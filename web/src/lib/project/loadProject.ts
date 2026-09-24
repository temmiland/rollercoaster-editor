import { classify } from './classify.ts'
import type { AssetKind, Project, ProjectProblem } from './model.ts'
import type { ProjectSource } from './ProjectSource.ts'

const decoder = new TextDecoder()

/** Reads every file of a project into memory and indexes the engine documents among them. */
export async function loadProject(source: ProjectSource, signal?: AbortSignal): Promise<Project> {
  const paths = await source.listFiles(signal)
  const contents = await Promise.all(paths.map((path) => source.readFile(path, signal)))
  const files = new Map(paths.map((path, index) => [path, contents[index]!]))

  const assets: Project['assets'] = { maps: [], tilesets: [], models: [], sprites: [], dialogues: [] }
  const problems: ProjectProblem[] = []
  for (const [path, bytes] of files) {
    if (!path.endsWith('.json')) continue
    let json: unknown
    try {
      json = JSON.parse(decoder.decode(bytes))
    } catch (error) {
      problems.push({ path, message: `Kein gültiges JSON: ${(error as Error).message}` })
      continue
    }
    for (const asset of classify(path, json)) (assets[asset.kind] as typeof asset[]).push(asset)
  }
  for (const kind of Object.keys(assets) as AssetKind[]) {
    const seen = new Set<string>()
    for (const asset of assets[kind]) {
      if (seen.has(asset.id)) problems.push({ path: asset.path, message: `Doppelte ID '${asset.id}'` })
      seen.add(asset.id)
    }
    assets[kind].sort((a, b) => a.name.localeCompare(b.name))
  }
  return { id: source.id, name: source.name, files, assets, problems }
}
