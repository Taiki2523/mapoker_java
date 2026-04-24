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
  }))
}

export function seatPosition(seatIdx: number, mySeat: number, n: number) {
  const ORBIT_RX = 46
  const ORBIT_RY = 46
  const BET_RX = 30
  const BET_RY = 30
  const relIdx = ((seatIdx - mySeat) + n) % n
  const angle = Math.PI / 2 + (relIdx * 2 * Math.PI / n)
  return {
    x: 50 + ORBIT_RX * Math.cos(angle),
    y: 50 + ORBIT_RY * Math.sin(angle),
    betX: 50 + BET_RX * Math.cos(angle),
    betY: 50 + BET_RY * Math.sin(angle),
  }
}
