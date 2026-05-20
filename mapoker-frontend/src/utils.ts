import type { RoomMember, RoomMemberApi } from './types'

export const SUIT_META: Record<string, { symbol: string; label: string; color: 'red' | 'black' }> = {
  S: { symbol: '♠', label: 'Spades', color: 'black' },
  H: { symbol: '♥', label: 'Hearts', color: 'red' },
  D: { symbol: '♦', label: 'Diamonds', color: 'red' },
  C: { symbol: '♣', label: 'Clubs', color: 'black' },
}

export function formatRank(rank: string): string {
  if (rank === 'T') return '10'
  return rank
}

export function mapMembers(members: RoomMemberApi[] = []): RoomMember[] {
  return members.map((member) => ({
    name: member.name,
    seatIndex: member.seat_index,
    joinedAt: member.joined_at,
    pendingLeave: member.pending_leave ?? false,
    displayName: member.display_name ?? member.name,
    avatarUrl: member.avatar_url ?? null,
  }))
}

export function seatPosition(seatIdx: number, mySeat: number, n: number) {
  const scale = Math.min(1 + Math.max(0, n - 2) * 0.015, 1.12)
  const crowdOffset = Math.max(0, n - 6)
  const crowd = Math.min(crowdOffset, 3)
  const ORBIT_RX = (52 * scale) + (crowdOffset * 1.2)
  const ORBIT_RY = (38 * scale) + (crowdOffset * 0.8)
  const betScale = Math.max(0.9, 1 - (crowdOffset * 0.04))
  const BET_RX = 30 * betScale
  const BET_RY = 30 * betScale
  const relIdx = ((seatIdx - mySeat) + n) % n
  const angle = Math.PI / 2 + (relIdx * 2 * Math.PI / n)
  const bottomWeight = Math.max(0, Math.sin(angle))
  const topWeight = Math.max(0, -Math.sin(angle))
  const sideWeight = Math.pow(Math.abs(Math.cos(angle)), 1.1)
  const headsUpBoost = n === 2 ? 2.4 : 0
  const seatRx = Math.min(57, ORBIT_RX + sideWeight * (1.2 + crowd * 0.8))
  const seatRy = Math.min(48, ORBIT_RY + bottomWeight * (3.2 + crowd * 0.6) + topWeight * (0.8 + crowd * 0.2))
  const betRx = Math.max(20, BET_RX - sideWeight * (1.6 + crowd * 0.7))
  const betRy = Math.max(
    16,
    BET_RY
      - bottomWeight * (7.4 + crowd * 0.9 + headsUpBoost * 0.5)
      - topWeight * (5.8 + crowd * 0.7 + headsUpBoost),
  )
  return {
    x: 50 + seatRx * Math.cos(angle),
    y: 50 + seatRy * Math.sin(angle),
    betX: 50 + betRx * Math.cos(angle),
    betY: 50 + betRy * Math.sin(angle),
  }
}
