import { ApiError, type ApiClient } from './client'

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN'

/** Spring Boot Actuator health report. The API exposes component statuses but never their details. */
export interface HealthReport {
  status: HealthStatus
  components?: Record<string, { status: HealthStatus }>
}

/**
 * Reads the API's aggregate health. Actuator answers 503 when any component is down,
 * but the body is still a valid report, so it is returned rather than thrown.
 */
export async function fetchHealth(client: ApiClient, signal?: AbortSignal): Promise<HealthReport> {
  try {
    return await client.get<HealthReport>('/actuator/health', { signal })
  } catch (error) {
    if (error instanceof ApiError && error.status === 503 && isHealthReport(error.body)) {
      return error.body
    }
    throw error
  }
}

function isHealthReport(value: unknown): value is HealthReport {
  return typeof value === 'object' && value !== null && typeof (value as { status?: unknown }).status === 'string'
}
