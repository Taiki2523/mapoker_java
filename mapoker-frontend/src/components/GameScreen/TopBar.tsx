import type { GameState } from '../../types'
import { t } from '../../i18n'

type Props = {
  game: GameState | null
  mySeatIndex: number | null
  canAct?: boolean
  displayName?: (idx: number) => string
  error: string
  autoRefresh: boolean
  setAutoRefresh: (v: boolean) => void
  inviteCopied: boolean
  stackMode: 'chips' | 'bb'
  onToggleStackMode: () => void
  onCopyInvite: () => void
  onOpenMyPage: () => void
  onOpenActionLog: () => void
}

export function TopBar({
  game, mySeatIndex, canAct, displayName,
  error, autoRefresh, setAutoRefresh,
  inviteCopied, stackMode, onToggleStackMode,
  onCopyInvite, onOpenMyPage, onOpenActionLog,
}: Props) {
  const bb = game?.big_blind ?? 0
  const fmt = (chips: number) => {
    if (stackMode === 'bb' && bb > 0) {
      return `${Math.round((chips / bb) * 10) / 10}BB`
    }
    return `¥${chips}`
  }
  const resolvedCanAct = canAct ?? (
    game?.status === 'in_progress'
    && mySeatIndex !== null
    && game.current_player === mySeatIndex
    && (game.players[mySeatIndex]?.stack ?? 0) > 0
  )
  const currentPlayerName = game?.current_player !== undefined
    ? (displayName?.(game.current_player) ?? game.players[game.current_player]?.id ?? '')
    : ''
  const statusText = resolvedCanAct
    ? t('yourTurn')
    : game?.status === 'in_progress' && currentPlayerName
      ? `${currentPlayerName}...`
      : game?.status === 'showdown'
        ? t('showdown')
        : game?.status === 'finished'
          ? t('finished')
          : ''

  return (
    <header className="game-topbar">
      <div className="topbar-left">
        <span className="topbar-brand">mapoker</span>
        {game?.street && (
          <span className={`street-badge street-badge--${game.street.toLowerCase()}`}>
            {game.street.toUpperCase()}
          </span>
        )}
        {statusText && <span className="topbar-status">{statusText}</span>}
      </div>

      <div className="topbar-center">
        <span className="topbar-stat">POT <strong>{fmt(game ? game.pot_total - game.players.reduce((s, p) => s + p.contributed, 0) : 0)}</strong></span>
        {(game?.current_bet ?? 0) > 0 && (
          <span className="topbar-stat">BET <strong>{fmt(game!.current_bet)}</strong></span>
        )}
      </div>

      <div className="topbar-right">
        {error && <span className="topbar-error">{error}</span>}
        <button
          className="icon-btn topbar-stack-toggle"
          onClick={onToggleStackMode}
          data-label={stackMode === 'chips' ? 'BB表示に切替' : 'チップ表示に切替'}
        >
          {stackMode === 'chips' ? 'BB' : '¥'}
        </button>
        <label className="topbar-auto-label">
          <input
            type="checkbox"
            checked={autoRefresh}
            onChange={(e) => setAutoRefresh(e.target.checked)}
          />
          auto
        </label>
        <button className="icon-btn" onClick={onCopyInvite} data-label={inviteCopied ? 'コピー済み' : '招待URLをコピー'}>
          {inviteCopied
            ? <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>
            : <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
          }
        </button>
        <button className="icon-btn" onClick={onOpenActionLog} data-label="アクションログ">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/><line x1="9" y1="12" x2="15" y2="12"/><line x1="9" y1="16" x2="13" y2="16"/></svg>
        </button>
        <button className="icon-btn" onClick={onOpenMyPage} data-label="マイページ">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        </button>
      </div>
    </header>
  )
}
