import { useEffect, useMemo, useState } from 'react'
import { clearSession, readSession, writeSession } from '../api'
import type { RoomMember, StoredSession } from '../types'

export function useTableSession(roster: RoomMember[]) {
  const [gameId, setGameId] = useState('')
  const [myName, setMyName] = useState('')
  const [mySeatIndex, setMySeatIndex] = useState<number | null>(null)

  // URL パラメータから gameId を初期化（mount 時のみ）
  const [initialGameId] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    return params.get('tableId') ?? params.get('gameId') ?? ''
  })

  useEffect(() => {
    if (initialGameId) setGameId(initialGameId)
  }, [initialGameId])

  // gameId が変わったら localStorage からセッションを復元
  useEffect(() => {
    if (!gameId) return
    const data = readSession(gameId)
    if (data) {
      setMyName(data.name)
      setMySeatIndex(data.seatIndex)
    }
  }, [gameId])

  // roster に自分がいたら seatIndex を推論
  useEffect(() => {
    if (!gameId || !myName.trim() || mySeatIndex !== null) return
    const member = roster.find((m) => m.name === myName.trim())
    if (member) setMySeatIndex(member.seatIndex)
  }, [gameId, myName, mySeatIndex, roster])

  // 座席衝突（別人が同じシートを取った）でリセット
  useEffect(() => {
    if (mySeatIndex === null || !myName.trim() || !roster.length) return
    const memberAtSeat = roster.find((m) => m.seatIndex === mySeatIndex)
    if (memberAtSeat && memberAtSeat.name !== myName.trim()) {
      setMySeatIndex(null)
      if (gameId) clearSession(gameId)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roster])

  const inferredSeatIndex = useMemo(() => {
    if (!myName.trim()) return null
    const member = roster.find((m) => m.name === myName.trim())
    return member ? member.seatIndex : null
  }, [myName, roster])

  const mySeat = useMemo(
    () => mySeatIndex ?? inferredSeatIndex,
    [mySeatIndex, inferredSeatIndex]
  )

  const persistSession = (tableId: string, session: Omit<StoredSession, 'updatedAt'>) => {
    writeSession(tableId, session)
  }

  const clearGameSession = (tableId: string) => {
    clearSession(tableId)
    window.history.replaceState(null, '', window.location.pathname)
    setGameId('')
    setMySeatIndex(null)
  }

  return {
    gameId, setGameId,
    myName, setMyName,
    mySeatIndex, setMySeatIndex,
    mySeat, inferredSeatIndex,
    initialGameId,
    persistSession, clearGameSession,
  }
}
