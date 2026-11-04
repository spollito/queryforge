import { config } from '../../config'
import { createApiClient } from './client'

/** Shared client for the QueryForge API, configured from VITE_API_BASE_URL. */
export const apiClient = createApiClient({ baseUrl: config.apiBaseUrl })

export { ApiError, type ApiClient } from './client'
export { fetchHealth, type HealthReport, type HealthStatus } from './health'
export {
  analyzeQuery,
  flattenPlan,
  type Analysis,
  type ExplainMode,
  type Finding,
  type PlanNode,
  type PlanRow,
  type Severity,
} from './analyses'
export { locateProblem, problemFrom, type Problem, type SqlLocation } from './problem'
