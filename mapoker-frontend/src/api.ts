import type { ApiError } from './types'

const DEFAULT_APPLICATION_URL = 'http://localhost:3000'

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

function resolveApplicationUrl(): string {
  if (typeof window !== 'undefined') {
    return trimTrailingSlash(window.location.origin)
  }

  return trimTrailingSlash(import.meta.env.APPLICATION_URL ?? DEFAULT_APPLICATION_URL)
}

export const APPLICATION_URL = resolveApplicationUrl()

export const API_BASE = `${APPLICATION_URL}/api`

export async function fetchCsrfHeaders(): Promise<Record<string, string>> {
  const res = await fetch(`${API_BASE}/v1/auth/csrf`, { credentials: 'include', cache: 'no-store' })
  if (!res.ok) throw new Error(`http_${res.status}`)
  const data = await res.json() as { headerName: string | null; token: string | null }
  return data.headerName && data.token ? { [data.headerName]: data.token } : {}
}

export async function uploadFile<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: form,
    headers: await fetchCsrfHeaders(),
    credentials: 'include',
  })
  const text = await res.text()
  let data: T | ApiError = {} as T
  try { if (text) data = JSON.parse(text) as T | ApiError } catch { /* non-JSON response */ }
  if (!res.ok) {
    const err = data as ApiError
    throw new Error(err.error?.code ?? `http_${res.status}`)
  }
  return data as T
}

import type {
  AuthUser, GameState, JoinResponse, RoomMemberApi, Showdown, StoredSession,
  Table, UserTableHistoryEntry, WalletLedgerEntry, WalletSummary,
} from './types'

// ---- typed API wrappers ----

export const fetchMe = () => fetchJSON<AuthUser>('/v1/auth/me')
export const fetchVersion = () => fetchJSON<{ version: string }>('/v1/version')
export const apiLogout = () => fetchJSON<void>('/v1/auth/logout', { method: 'POST' })

export const fetchGame = (gameId: string, seat: number | null, spectator: boolean) => {
  const params = new URLSearchParams()
  if (seat !== null) params.set('viewer_index', String(seat))
  if (spectator) params.set('spectator', '1')
  const q = params.toString()
  return fetchJSON<GameState>(q ? `/v1/games/${gameId}?${q}` : `/v1/games/${gameId}`)
}

export const fetchMembers = (gameId: string) =>
  fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${gameId}/members`)

export const fetchTable = (tableId: string) =>
  fetchJSON<Table>(`/v1/tables/${tableId}`)

export const fetchTables = () => fetchJSON<Table[]>('/v1/tables')
export const fetchHistory = () => fetchJSON<UserTableHistoryEntry[]>('/v1/auth/history')
export const fetchWallet = () => fetchJSON<WalletSummary>('/v1/wallet')
export const fetchWalletLedger = () => fetchJSON<WalletLedgerEntry[]>('/v1/wallet/ledger?limit=20')
export const claimDailyBonus = () => fetchJSON<void>('/v1/wallet/daily-bonus', { method: 'POST' })

export const joinTable = (tableId: string, name: string, buyIn: number) =>
  fetchJSON<JoinResponse>(`/v1/tables/${tableId}/join`, {
    method: 'POST',
    body: JSON.stringify({ name, buy_in: buyIn }),
  })

export const leaveTable = (tableId: string, name: string, seatIndex: number | null) =>
  fetchJSON<{ members: RoomMemberApi[] }>(`/v1/tables/${tableId}/leave`, {
    method: 'POST',
    body: JSON.stringify({ name, seat_index: seatIndex }),
  })

export const apiStartHand = (gameId: string, bigBlind: number, straddle: boolean) =>
  fetchJSON<void>(`/v1/games/${gameId}/start`, {
    method: 'POST',
    body: JSON.stringify({ big_blind: bigBlind, straddle }),
  })

export const apiSendAction = (gameId: string, playerIndex: number, type: string, amount: number) =>
  fetchJSON<GameState>(`/v1/games/${gameId}/actions`, {
    method: 'POST',
    body: JSON.stringify({ player_index: playerIndex, action: { type, amount } }),
  })

export const apiShowdown = (gameId: string) =>
  fetchJSON<Showdown>(`/v1/games/${gameId}/showdown`, { method: 'POST' })

export const apiOfflineShowdown = (gameId: string, declaredWinners: number[]) =>
  fetchJSON<Showdown>(`/v1/games/${gameId}/showdown`, {
    method: 'POST',
    body: JSON.stringify({ declared_winners: declaredWinners }),
  })

export const apiDeclareShowdown = (gameId: string, playerIndex: number, win: boolean) =>
  fetchJSON<GameState>(`/v1/games/${gameId}/showdown/declare`, {
    method: 'POST',
    body: JSON.stringify({ player_index: playerIndex, win }),
  })

export const apiStraddleIntent = (gameId: string, straddle: boolean) =>
  fetchJSON<void>(`/v1/games/${gameId}/straddle-intent`, {
    method: 'POST',
    body: JSON.stringify({ straddle }),
  })

export const createTable = (payload: object) =>
  fetchJSON<Table>('/v1/tables', { method: 'POST', body: JSON.stringify(payload) })

export const seatOffline = (
  tableId: string,
  body: { seat_index: number; name: string; buy_in: number; invite_code: string }
) =>
  fetchJSON<{ seat_index: number; members: unknown[] }>(
    `/v1/games/${tableId}/seat`,
    { method: 'POST', body: JSON.stringify(body) }
  )

export const readSession = (tableId: string): StoredSession | null => {
  try {
    const raw = window.localStorage.getItem(`mapoker.session.${tableId}`)
    return raw ? (JSON.parse(raw) as StoredSession) : null
  } catch { return null }
}

export const writeSession = (tableId: string, session: Omit<StoredSession, 'updatedAt'>) => {
  window.localStorage.setItem(
    `mapoker.session.${tableId}`,
    JSON.stringify({ ...session, updatedAt: new Date().toISOString() })
  )
}

export const clearSession = (tableId: string) =>
  window.localStorage.removeItem(`mapoker.session.${tableId}`)

// ---- low-level fetch ----

export async function fetchJSON<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfHeaders = await fetchCsrfHeaders()
    for (const [name, value] of Object.entries(csrfHeaders)) headers.set(name, value)
  }
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...options,
    headers,
  })
  const text = await res.text()
  let data: T | ApiError = {} as T
  try { if (text) data = JSON.parse(text) as T | ApiError } catch { /* non-JSON response */ }
  if (!res.ok) {
    const err = data as ApiError
    throw new Error(err.error?.code ?? `http_${res.status}`)
  }
  return data as T
}
