export type Player = {
  id: string
  stack: number
  contributed: number
  folded: boolean
  all_in: boolean
  hole?: string[]
}

export type GameState = {
  id: string
  status: string
  street: string
  button_index: number
  current_player: number
  current_bet: number
  last_raise_size: number
  big_blind: number
  pot_total: number
  players: Player[]
  community: string[]
  odd_chip_rule: string
  can_start_hand?: boolean
  last_showdown?: Showdown
}

export type Showdown = {
  winners?: number[]
  best_hand?: { rank: string; kickers: string[] } | null
  payouts?: number[]
}

export type AuthUser = {
  id: number
  username: string
}

export type ApiError = {
  error?: {
    code?: string
    message?: string
  }
}

export type RoomMember = {
  name: string
  seatIndex: number
  joinedAt?: string
}

export type RoomMemberApi = {
  name: string
  seat_index: number
  joined_at: string
}

export type BetPreset = {
  label: string
  amount: number
}

export type PayoutLine = {
  name: string
  amount: number
}

export type CreateGameConfig = {
  playerCount: number
  stackSize: number
  bigBlind: number
  buttonIndex: number
  seed: string
  autoStart: boolean
}
