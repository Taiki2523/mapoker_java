import { Client } from '@stomp/stompjs'
import type { IMessage } from '@stomp/stompjs'

export interface GameBroadcastPayload {
  game: unknown
  streetRevealedAt: string | null
}

export interface MembersBroadcastPayload {
  tableId: string
  members: unknown[]
}

export interface HoleCardsPayload {
  tableId: string
  seatIndex: number
  hole: string[]
}

export function createStompClient(): Client {
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const brokerURL = `${wsProtocol}//${window.location.host}/ws/websocket`
  return new Client({
    brokerURL,
    reconnectDelay: 3000,
  })
}

export function subscribeGame(
  client: Client,
  tableId: string,
  onMessage: (payload: GameBroadcastPayload) => void
): void {
  client.subscribe(`/topic/tables/${tableId}/game`, (msg: IMessage) => {
    onMessage(JSON.parse(msg.body) as GameBroadcastPayload)
  })
}

export function subscribeMembers(
  client: Client,
  tableId: string,
  onMessage: (payload: MembersBroadcastPayload) => void
): void {
  client.subscribe(`/topic/tables/${tableId}/members`, (msg: IMessage) => {
    onMessage(JSON.parse(msg.body) as MembersBroadcastPayload)
  })
}

export function subscribeHoleCards(
  client: Client,
  onMessage: (payload: HoleCardsPayload) => void
): void {
  client.subscribe('/user/queue/hole-cards', (msg: IMessage) => {
    onMessage(JSON.parse(msg.body) as HoleCardsPayload)
  })
}
