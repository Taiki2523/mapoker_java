import { useEffect, useRef, useState } from 'react'
import { fetchJSON } from '../../api'
import type { ActionLogEntry, GameState, PayoutLine, Showdown } from '../../types'
import { hasTranslation, t } from '../../i18n'

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
  showdown: Showdown | null
  payoutLines: PayoutLine[]
  displayName: (idx: number) => string
  open: boolean
  onClose: () => void
}

type LogItem =
  | { kind: 'action'; entry: ActionLogEntry }
  | { kind: 'showdown'; label: string }
  | { kind: 'payout'; name: string; amount: number }

export function ActionLogDialog({ game, showdown, payoutLines, displayName, open, onClose }: Props) {
  const [entries, setEntries] = useState<ActionLogEntry[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!game.id) return
    fetchJSON<{ actions: ActionLogEntry[] }>(`/v1/games/${game.id}/actions`)
      .then(({ actions }) => setEntries(actions))
      .catch(() => {})
  }, [game.id, game.current_player, game.street, game.status, showdown])

  useEffect(() => {
    if (open) bottomRef.current?.scrollIntoView({ behavior: 'instant' })
  }, [open, entries, showdown])

  if (!open) return null

  const bb = game.big_blind
  const fmt = (chips: number) =>
    `¥${chips}${bb > 0 ? ` (${Math.round((chips / bb) * 10) / 10}BB)` : ''}`

  const items: LogItem[] = entries.map((e) => ({ kind: 'action', entry: e }))

  // showdown 結果はハンド終了後かつ解決済みのときのみ追加（ネタバレ防止）
  const resolved = game.status === 'finished' && showdown !== null
  if (resolved && payoutLines.length > 0) {
    const isFoldWin = showdown.best_hand == null
    if (isFoldWin) {
      items.push({ kind: 'showdown', label: 'フォールド勝ち' })
    } else {
      const winnerNames = (showdown.winners ?? []).map((i) => displayName(i)).join(', ')
      const rank = showdown.best_hand?.rank
      const rankLabel = rank && hasTranslation(rank) ? t(rank as Parameters<typeof t>[0]) : (rank ?? '')
      items.push({ kind: 'showdown', label: `${winnerNames} — ${rankLabel}` })
    }
    for (const p of payoutLines) {
      items.push({ kind: 'payout', name: p.name, amount: p.amount })
    }
  }

  const fmtAction = (entry: ActionLogEntry) => {
    const name = displayName(entry.player_index)
    const label = ACTION_LABELS[entry.type] ?? entry.type.toLowerCase()
    const amtStr = entry.amount > 0 ? ` ${fmt(entry.amount)}` : ''
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
          {items.length === 0
            ? <div className="muted" style={{ padding: '1rem', textAlign: 'center' }}>まだアクションはありません</div>
            : items.map((item, i) => {
                if (item.kind === 'showdown') {
                  return (
                    <div key={`sd-${i}`} className="action-log-entry action-log-showdown">
                      <span className="action-log-seq">🃏</span>
                      {item.label}
                    </div>
                  )
                }
                if (item.kind === 'payout') {
                  return (
                    <div key={`pay-${i}`} className="action-log-entry action-log-payout">
                      <span className="action-log-seq">💰</span>
                      {`${item.name} が ${fmt(item.amount)} を獲得`}
                    </div>
                  )
                }
                return (
                  <div key={item.entry.seq} className={`action-log-entry action-log-${item.entry.type.toLowerCase()}`}>
                    <span className="action-log-seq">#{item.entry.seq}</span>
                    {fmtAction(item.entry)}
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
