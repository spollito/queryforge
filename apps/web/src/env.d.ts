interface ImportMetaEnv {
  /** Base URL of the QueryForge API, for example http://localhost:8080. */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
