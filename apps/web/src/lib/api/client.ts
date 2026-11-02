export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, body: unknown, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

export interface ApiClientOptions {
  baseUrl: string
  timeoutMs?: number
  /** Injectable for tests; defaults to the global fetch. */
  fetch?: typeof globalThis.fetch
}

export interface RequestOptions {
  signal?: AbortSignal
}

export interface ApiClient {
  readonly baseUrl: string
  get<T>(path: string, options?: RequestOptions): Promise<T>
}

const DEFAULT_TIMEOUT_MS = 10_000

export function joinUrl(baseUrl: string, path: string): string {
  return `${baseUrl.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

export function createApiClient({
  baseUrl,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  fetch: fetchImpl = globalThis.fetch.bind(globalThis),
}: ApiClientOptions): ApiClient {
  async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
    const signals = [AbortSignal.timeout(timeoutMs)]
    if (options.signal) signals.push(options.signal)

    const response = await fetchImpl(joinUrl(baseUrl, path), {
      method,
      headers: { Accept: 'application/json' },
      signal: AbortSignal.any(signals),
    })
    const body = await readBody(response)

    if (!response.ok) {
      throw new ApiError(response.status, body, `${method} ${path} failed with HTTP ${response.status}`)
    }
    return body as T
  }

  return {
    baseUrl,
    get: (path, options) => request('GET', path, options),
  }
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return null
  // Matches application/json and vendor types such as Actuator's application/vnd.spring-boot.actuator.v3+json.
  const isJson = /[/+]json\b/.test(response.headers.get('Content-Type') ?? '')
  return isJson ? JSON.parse(text) : text
}
