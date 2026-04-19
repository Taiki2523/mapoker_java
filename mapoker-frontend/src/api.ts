import type { ApiError } from './types'

const DEFAULT_APPLICATION_URL = 'http://localhost:3000'

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

export const APPLICATION_URL = trimTrailingSlash(
  import.meta.env.APPLICATION_URL ?? DEFAULT_APPLICATION_URL,
)

export const API_BASE = `${APPLICATION_URL}/api`

export async function fetchJSON<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    credentials: 'include',
    ...options,
  })
  const text = await res.text()
  const data = text ? (JSON.parse(text) as T | ApiError) : ({} as T)
  if (!res.ok) {
    const err = data as ApiError
    throw new Error(err.error?.message ?? `Request failed: ${res.status}`)
  }
  return data as T
}
