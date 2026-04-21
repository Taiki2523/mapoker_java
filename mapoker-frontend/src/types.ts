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

export type TableVisibility = 'public' | 'private'

export type TableFlag = 'casual' | 'serious' | 'newbie' | 'short_handed'

export type StoredSession = {
  name: string
  seatIndex: number
  owner: boolean
  updatedAt?: string
}

export type UserTableHistoryEntry = {
  table_id: string
  table_name: string
  seat_index: number
  visibility: string
  status: string
  flags: string[]
  joined_at: string
  left_at?: string | null
  active: boolean
}

export type HandHistoryPlayer = {
  name: string
  seat_index: number
  stack_before: number
  stack_after: number
  folded: boolean
  hole_cards: string[]
}

export type HandHistoryEntry = {
  table_id: string
  hand_id: string
  players: HandHistoryPlayer[]
  winners: number[]
  pot: number
  street: string
  finished_at: string
}

export type Table = {
  id: string
  room_id: string
  name: string
  game_type: string
  stake: {
    small_blind: number
    big_blind: number
  }
  min_buy_in: number
  max_buy_in: number
  max_players: number
  flags: TableFlag[]
  visibility: TableVisibility
  status: string
  game_id: string
  created_at: string
  member_count: number
  members: RoomMemberApi[]
  game?: GameState | null
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
  tableName: string
  playerCount: number
  stackSize: number
  bigBlind: number
  buttonIndex: number
  seed: string
  autoStart: boolean
  visibility: TableVisibility
  flags: TableFlag[]
}
