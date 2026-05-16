/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly APPLICATION_URL?: string
  readonly VITE_GOOGLE_CLIENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
