import { fileURLToPath } from 'node:url'
import babel from '@rolldown/plugin-babel'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { staticDirectories } from './vite/staticDirectories.ts'

const here = (path: string) => fileURLToPath(new URL(path, import.meta.url))

export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    staticDirectories([
      // Built by `./gradlew :web-preview:gdx_teavm_web_js_build`.
      { urlPath: '/preview', directory: here('../web-preview/build/dist/js/webapp'), bundle: true },
      // Stand-in project until a Gitea repository can be opened.
      {
        urlPath: '/demo-project',
        directory: process.env.ROLLERCOASTER_DEMO_PROJECT ?? here('../../rollercoaster-example/src/main/resources'),
        listFiles: true,
      },
    ]),
  ],
  resolve: { alias: { '@': here('./src') } },
})
