import type { ApiError } from './types'

export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

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
