import { useEffect, useState } from 'react'
import { leaveTable } from '../api'
import { mapMembers } from '../utils'
import type { RoomMember, RoomMemberApi } from '../types'

type Callbacks = {
  gameId: string
  myName: string
  mySeatIndex: number | null
  roster: RoomMember[]
  setRoster: (r: RoomMember[]) => void
  navigateToLobby: () => void
}

export function useLeaveRoom(cb: Callbacks) {
  const [leavePending, setLeavePending] = useState(false)

  // 退席予約が解消されたらロビーへ
  useEffect(() => {
    if (!leavePending || !cb.myName.trim()) return
    const stillInRoster = cb.roster.some((m) => m.name === cb.myName.trim())
    if (!stillInRoster) {
      cb.navigateToLobby()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cb.roster, leavePending, cb.myName])

  const leaveRoom = async () => {
    if (!cb.gameId) return
    try {
      const result = await leaveTable(cb.gameId, cb.myName, cb.mySeatIndex)
      const updated = mapMembers(result.members as RoomMemberApi[])
      cb.setRoster(updated)
      const stillIn = updated.some((m) => m.name === cb.myName.trim())
      if (stillIn) {
        setLeavePending(true)
      } else {
        cb.navigateToLobby()
      }
    } catch {
      cb.navigateToLobby()
    }
  }

  return { leavePending, setLeavePending, leaveRoom }
}
