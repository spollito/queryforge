import { config } from '../../config'
import { createApiClient } from './client'

/** Shared client for the QueryForge API, configured from VITE_API_BASE_URL. */
export const apiClient = createApiClient({ baseUrl: config.apiBaseUrl })

export { ApiError, type ApiClient } from './client'
export { fetchHealth, type HealthReport, type HealthStatus } from './health'
