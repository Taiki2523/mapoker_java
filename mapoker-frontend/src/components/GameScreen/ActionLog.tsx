import { useEffect, useRef, useState } from 'react'
import { fetchJSON } from '../../api'
import type { ActionLogEntry, GameState } from '../../types'
import { hasTranslation, t } from '../../i18n'

const ACTION_LABELS: Record<string, string> = {
  fold: 'フォールド',
  check: 'チェック',
  call: 'コール',
  bet: 'ベット',
  raise: 'レイズ',
  all_in: 'オールイン',
  showdown: 'ショーダウン',
  payout: 'ポット獲得',
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

  const bb = game.big_blind
  const fmt = (chips: number) =>
    `¥${chips}${bb > 0 ? ` (${Math.round((chips / bb) * 10) / 10}BB)` : ''}`

  const renderEntry = (e: ActionLogEntry) => {
    const name = displayName(e.player_index)
    const type = e.type.toLowerCase()

    if (type === 'showdown') {
      const rank = e.label
      const rankLabel = rank && hasTranslation(rank) ? t(rank as Parameters<typeof t>[0]) : (rank ?? '')
      return { text: `${name} — ${rankLabel}`, cls: 'action-log-showdown', icon: '🃏' }
    }
    if (type === 'payout') {
      return { text: `${name} が ${fmt(e.amount)} を獲得`, cls: 'action-log-payout', icon: '💰' }
    }

    const label = ACTION_LABELS[type] ?? type
    const amtStr = e.amount > 0 ? ` ${fmt(e.amount)}` : ''
    return { text: `${name} が${label}${amtStr}`, cls: `action-log-${type}`, icon: `#${e.seq}` }
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
            : entries.map((e, i) => {
                const { text, cls, icon } = renderEntry(e)
                return (
                  <div key={i} className={`action-log-entry ${cls}`}>
                    <span className="action-log-seq">{icon}</span>
                    {text}
                  </div>
                )
              })
          }
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  )
}
