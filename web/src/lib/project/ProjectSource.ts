/** Where a project's files come from: the demo over HTTP today, a Gitea repository later. */
export interface ProjectSource {
  readonly id: string
  readonly name: string
  listFiles(signal?: AbortSignal): Promise<string[]>
  readFile(path: string, signal?: AbortSignal): Promise<Uint8Array>
}

/** Reads a directory served over HTTP that lists its files in `index.json`. */
export class HttpProjectSource implements ProjectSource {
  readonly id: string
  readonly name: string
  private readonly baseUrl: string

  constructor(id: string, name: string, baseUrl: string) {
    this.id = id
    this.name = name
    this.baseUrl = baseUrl.replace(/\/$/, '')
  }

  async listFiles(signal?: AbortSignal): Promise<string[]> {
    const response = await fetch(`${this.baseUrl}/index.json`, { signal })
    if (!response.ok) throw new Error(`Dateiliste nicht lesbar (${response.status})`)
    return (await response.json()) as string[]
  }

  async readFile(path: string, signal?: AbortSignal): Promise<Uint8Array> {
    const response = await fetch(`${this.baseUrl}/${path.split('/').map(encodeURIComponent).join('/')}`, { signal })
    if (!response.ok) throw new Error(`'${path}' nicht lesbar (${response.status})`)
    return new Uint8Array(await response.arrayBuffer())
  }
}
