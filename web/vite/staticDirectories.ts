import { createReadStream, readdirSync, statSync } from 'node:fs'
import { cp } from 'node:fs/promises'
import { extname, join, relative, resolve, sep } from 'node:path'
import type { Plugin } from 'vite'

const contentTypes: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.wasm': 'application/wasm',
  '.png': 'image/png',
}

export interface StaticDirectory {
  /** URL prefix without trailing slash, e.g. `/preview`. */
  urlPath: string
  directory: string
  /** Serves `<urlPath>/index.json` listing every file, for sources that cannot list themselves. */
  listFiles?: boolean
  /** Copies the directory into the build output. */
  bundle?: boolean
}

function listFiles(directory: string): string[] {
  return readdirSync(directory, { recursive: true, encoding: 'utf8' })
    .filter((path) => statSync(join(directory, path)).isFile())
    .map((path) => path.split(sep).join('/'))
    .sort()
}

/** Serves directories outside the Vite root, such as the TeaVM preview build and a demo project. */
export function staticDirectories(entries: StaticDirectory[]): Plugin {
  let outDir = 'dist'
  return {
    name: 'rollercoaster-static-directories',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    configureServer(server) {
      for (const entry of entries) {
        const root = resolve(entry.directory)
        server.middlewares.use(entry.urlPath, (request, response, next) => {
          const urlPath = decodeURIComponent((request.url ?? '/').split('?')[0] ?? '/')
          if (entry.listFiles && urlPath === '/index.json') {
            response.setHeader('Content-Type', contentTypes['.json']!)
            response.end(JSON.stringify(listFiles(root)))
            return
          }
          const file = resolve(root, '.' + (urlPath.endsWith('/') ? urlPath + 'index.html' : urlPath))
          if (relative(root, file).startsWith('..')) return next()
          try {
            if (!statSync(file).isFile()) return next()
          } catch {
            return next()
          }
          response.setHeader('Content-Type', contentTypes[extname(file)] ?? 'application/octet-stream')
          createReadStream(file).pipe(response)
        })
      }
    },
    async closeBundle() {
      for (const entry of entries.filter((candidate) => candidate.bundle)) {
        await cp(resolve(entry.directory), join(outDir, entry.urlPath), { recursive: true })
      }
    },
  }
}
