import { useEffect, useRef, useState } from 'react'
import { fetchJSON } from '../../api'
import type { ActionLogEntry, GameState } from '../../types'

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
  open: boolean
  onClose: () => void
}

export function ActionLogDialog({ game, displayName, open, onClose }: Props) {
  const [entries, setEntries] = useState<ActionLogEntry[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!game.id) return
    fetchJSON<{ actions: ActionLogEntry[] }>(`/v1/games/${game.id}/actions`)
      .then(({ actions }) => setEntries(actions))
      .catch(() => {})
  }, [game.id, game.current_player, game.street, game.status])

  useEffect(() => {
    if (open) bottomRef.current?.scrollIntoView({ behavior: 'instant' })
  }, [open, entries])

  if (!open) return null

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
    <div className="action-log-overlay" onClick={onClose} role="presentation">
      <div className="action-log-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="action-log-dialog-header">
          <span>アクションログ</span>
          <button className="ghost" onClick={onClose}>✕</button>
        </div>
        <div className="action-log-dialog-body">
          {entries.length === 0
            ? <div className="muted" style={{ padding: '1rem', textAlign: 'center' }}>まだアクションはありません</div>
            : entries.map((e) => (
              <div key={e.seq} className={`action-log-entry action-log-${e.type.toLowerCase()}`}>
                <span className="action-log-seq">#{e.seq}</span>
                {fmt(e)}
              </div>
            ))
          }
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  )
}
