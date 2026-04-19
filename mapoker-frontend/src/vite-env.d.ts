/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly APPLICATION_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
