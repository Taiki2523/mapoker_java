import { useEffect, useRef, useState } from 'react'
import { fetchJSON } from '../../api'
import type { ActionLogEntry, GameState } from '../../types'

const MAX_LOG = 4

const ACTION_LABELS: Record<string, string> = {
  FOLD: 'フォールド',
  CHECK: 'チェック',
  CALL: 'コール',
  BET: 'ベット',
  RAISE: 'レイズ',
  ALL_IN: 'オールイン',
}

type Props = {
  game: GameState
  displayName: (idx: number) => string
}

export function ActionLog({ game, displayName }: Props) {
  const [entries, setEntries] = useState<ActionLogEntry[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!game.id || game.status === 'finished' && game.pot_total === 0) return
    fetchJSON<{ actions: ActionLogEntry[] }>(`/v1/games/${game.id}/actions`)
      .then(({ actions }) => setEntries(actions.slice(-MAX_LOG)))
      .catch(() => {})
  }, [game.id, game.current_player, game.street, game.status])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [entries])

  if (entries.length === 0) return null

  const fmt = (entry: ActionLogEntry) => {
    const name = displayName(entry.player_index)
    const label = ACTION_LABELS[entry.type] ?? entry.type.toLowerCase()
    const bb = game.big_blind
    const amtStr = entry.amount > 0
      ? ` ¥${entry.amount}${bb > 0 ? ` (${Math.round((entry.amount / bb) * 10) / 10}BB)` : ''}`
      : ''
    return `${name} が${label}${amtStr}`
  }

  return (
    <div className="action-log">
      {entries.map((e) => (
        <div key={e.seq} className={`action-log-entry action-log-${e.type.toLowerCase()}`}>
          {fmt(e)}
        </div>
      ))}
      <div ref={bottomRef} />
    </div>
  )
}
