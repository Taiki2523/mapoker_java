import { useEffect, useMemo, useState } from 'react'
import { clearSession, readSession, writeSession } from '../api'
import type { RoomMember, StoredSession } from '../types'

/**
 * テーブルセッション管理 hook。
 * gameId は React Router の useParams から受け取る（URLが正規表現）。
 */
export function useTableSession(gameId: string, roster: RoomMember[]) {
  const [myName, setMyName] = useState('')
  const [mySeatIndex, setMySeatIndex] = useState<number | null>(null)

  // gameId が変わったら localStorage からセッションを復元
  useEffect(() => {
    if (!gameId) {
      setMyName('')
      setMySeatIndex(null)
      return
    }
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

  const clearGameSession = () => {
    if (gameId) clearSession(gameId)
    setMySeatIndex(null)
  }

  return {
    myName, setMyName,
    mySeatIndex, setMySeatIndex,
    mySeat, inferredSeatIndex,
    persistSession, clearGameSession,
  }
}
