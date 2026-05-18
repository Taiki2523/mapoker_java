import { useRef } from 'react'
import { apiSendAction, apiShowdown, apiStartHand, apiStraddleIntent } from '../api'
import { t } from '../i18n'
import type { GameState, Showdown } from '../types'

type Callbacks = {
  gameId: string
  mySeat: number | null
  game: GameState | null
  showdown: Showdown | null
  doStraddle: boolean
  setDoStraddle: (v: boolean) => void
  setGame: (g: GameState) => void
  setShowdown: (s: Showdown | null) => void
  setLoading: (v: boolean) => void
  setError: (e: string) => void
  setActionAmount: (v: number) => void
  prevIsMyTurnRef: React.MutableRefObject<boolean>
  refreshGame: (id?: string) => Promise<void>
  runShowdown: (opts?: { suppressError?: boolean }) => Promise<void>
}

export function useGameActions(cb: Callbacks) {
  const startHandInFlight = useRef(false)
  const showdownInFlight = useRef(false)

  const startHand = async (
    id = cb.gameId,
    bigBlindOverride?: number,
    options?: { suppressError?: boolean }
  ) => {
    if (!id || startHandInFlight.current) return
    startHandInFlight.current = true
    cb.setLoading(true)
    cb.setError('')
    cb.setShowdown(null)
    cb.setActionAmount(0)
    cb.prevIsMyTurnRef.current = false
    try {
      const bb = bigBlindOverride ?? cb.game?.big_blind ?? 10
      const straddle = cb.doStraddle
      cb.setDoStraddle(false)
      await apiStartHand(id, bb, straddle)
      await cb.refreshGame(id)
    } catch (err) {
      if (!options?.suppressError) cb.setError((err as Error).message)
    } finally {
      startHandInFlight.current = false
      cb.setLoading(false)
    }
  }

  const sendAction = async (type: string, amount: number) => {
    const seat = cb.mySeat
    if (!cb.gameId || seat === null) { cb.setError(t('errSelectSeatFirst')); return }
    cb.setLoading(true)
    cb.setError('')
    try {
      const data = await apiSendAction(cb.gameId, seat, type, amount)
      cb.setGame(data)
      cb.setShowdown(data.last_showdown ?? null)
      if (data.status === 'showdown' && !cb.showdown) {
        void cb.runShowdown({ suppressError: true })
      } else {
        await cb.refreshGame(cb.gameId)
      }
    } catch (err) {
      cb.setError((err as Error).message)
    } finally {
      cb.setLoading(false)
    }
  }

  const runShowdown = async (options?: { suppressError?: boolean }) => {
    if (!cb.gameId || showdownInFlight.current) return
    showdownInFlight.current = true
    cb.setLoading(true)
    cb.setError('')
    try {
      const data = await apiShowdown(cb.gameId)
      cb.setShowdown(data)
      await cb.refreshGame(cb.gameId)
    } catch (err) {
      if (!options?.suppressError) cb.setError((err as Error).message)
    } finally {
      showdownInFlight.current = false
      cb.setLoading(false)
    }
  }

  const toggleStraddle = (v: boolean) => {
    cb.setDoStraddle(v)
    if (cb.gameId) void apiStraddleIntent(cb.gameId, v).catch(() => {})
  }

  return { startHand, sendAction, runShowdown, toggleStraddle }
}
