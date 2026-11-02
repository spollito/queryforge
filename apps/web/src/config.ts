const DEFAULT_API_BASE_URL = 'http://localhost:8080'

/** Build-time configuration from Vite environment variables (see .env.example). */
export const config = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL,
} as const
