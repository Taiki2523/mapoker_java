import { useEffect, useRef, useState } from 'react'
import { fetchGame, fetchMembers } from '../api'
import { createStompClient, subscribeGame, subscribeHoleCards, subscribeMembers } from '../ws'
import { mapMembers } from '../utils'
import type { GameState, RoomMember, RoomMemberApi, Showdown } from '../types'

type Callbacks = {
  mySeat: number | null
  mySeatIndex: number | null
  setMySeatIndex: (v: number | null) => void
  isSpectator: boolean
  setGame: React.Dispatch<React.SetStateAction<GameState | null>>
  setShowdown: (s: Showdown | null) => void
  setRoster: (r: RoomMember[]) => void
  cardRevealEndsAtRef: React.RefObject<number>
  streetRevealedAtRef: React.MutableRefObject<number>
}

export function useGameConnection(gameId: string, cb: Callbacks) {
  const [autoRefresh, setAutoRefresh] = useState(true)
  const stompClientRef = useRef<ReturnType<typeof createStompClient> | null>(null)

  const refreshGame = async (id = gameId) => {
    if (!id) return
    try {
      let seat = cb.mySeat
      if (seat === null) {
        try {
          const raw = window.localStorage.getItem(`mapoker.session.${id}`)
          if (raw) {
            const data = JSON.parse(raw) as { seatIndex?: number }
            if (typeof data.seatIndex === 'number') {
              seat = data.seatIndex
              cb.setMySeatIndex(data.seatIndex)
            }
          }
        } catch { /* ignore */ }
      }
      const data = await fetchGame(id, seat, cb.isSpectator)
      cb.setGame(data)
      cb.setShowdown(data.last_showdown ?? null)
    } catch { /* ignore */ }
  }

  const refreshMembers = async (id = gameId) => {
    if (!id) return
    if (Date.now() < (cb.cardRevealEndsAtRef.current ?? 0)) return
    try {
      const data = await fetchMembers(id)
      cb.setRoster(mapMembers(data.members as RoomMemberApi[]))
    } catch { /* ignore */ }
  }

  useEffect(() => {
    if (!autoRefresh || !gameId) return
    const client = createStompClient()
    stompClientRef.current = client

    client.onConnect = () => {
      subscribeGame(client, gameId, (payload) => {
        if (payload.streetRevealedAt) {
          cb.streetRevealedAtRef.current = new Date(payload.streetRevealedAt).getTime()
        }
        const nextGame = payload.game as GameState
        cb.setShowdown(nextGame.last_showdown ?? null)
        cb.setGame((prev) => {
          if (!prev) return nextGame
          const players = nextGame.players.map((p, i) => {
            const prevHole = prev.players[i]?.hole
            const hasKnownCards = Array.isArray(prevHole) &&
              prevHole.some((c) => c && c !== '??' && c !== '--')
            return hasKnownCards ? { ...p, hole: prevHole } : p
          })
          return { ...nextGame, players }
        })
      })
      subscribeMembers(client, gameId, (payload) => {
        cb.setRoster(mapMembers(payload.members as RoomMemberApi[]))
      })
      subscribeHoleCards(client, (payload) => {
        if (payload.tableId !== gameId) return
        cb.setGame((prev) => {
          if (!prev) return prev
          const players = prev.players.map((p, i) =>
            i === payload.seatIndex ? { ...p, hole: payload.hole } : p
          )
          return { ...prev, players }
        })
      })
      void refreshGame(gameId)
      void refreshMembers(gameId)
    }

    client.activate()
    return () => {
      void client.deactivate()
      stompClientRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRefresh, gameId])

  return { autoRefresh, setAutoRefresh, refreshGame, refreshMembers }
}
