import type { AuthUser, GameState } from '../../types'
import { t } from '../../i18n'

type Props = {
  game: GameState | null
  currentUser: AuthUser | null
  myName: string
  mySeatIndex: number | null
  canAct?: boolean
  displayName?: (idx: number) => string
  loginSeatIndex: number
  setLoginSeatIndex: (n: number) => void
  loading: boolean
  error: string
  leavePending: boolean
  autoRefresh: boolean
  setAutoRefresh: (v: boolean) => void
  inviteCopied: boolean
  onCopyInvite: () => void
  onOpenMyPage: () => void
  onLoginAsPlayer: () => void
  onLeaveRoom: () => void
  onLogout: () => void
  loginError: string
  showSession: boolean
  onToggleSession: () => void
}

export function TopBar({
  game, currentUser, myName, mySeatIndex, canAct, displayName, loginSeatIndex, setLoginSeatIndex,
  loading, error, leavePending, autoRefresh, setAutoRefresh,
  inviteCopied, onCopyInvite, onOpenMyPage, onLoginAsPlayer, onLeaveRoom, onLogout,
  loginError, showSession, onToggleSession,
}: Props) {
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
        <span className="topbar-stat">POT <strong>{game?.pot_total ?? 0}</strong></span>
        {(game?.current_bet ?? 0) > 0 && (
          <span className="topbar-stat">BET <strong>{game!.current_bet}</strong></span>
        )}
      </div>

      <div className="topbar-right">
        {leavePending && (
          <span style={{ color: 'var(--warning)', fontSize: '0.78rem', fontWeight: 600 }}>
            {t('leavePending')}
          </span>
        )}
        {error && <span className="topbar-error">{error}</span>}
        <label className="toggle-label" style={{ color: 'var(--text-muted)', fontSize: '0.75rem', gap: '0.3rem' }}>
          <input
            type="checkbox"
            checked={autoRefresh}
            onChange={(e) => setAutoRefresh(e.target.checked)}
            style={{ width: '14px', height: '14px' }}
          />
          auto
        </label>
        <button className="icon-btn" onClick={onCopyInvite} data-label={inviteCopied ? 'コピー済み' : '招待URLをコピー'}>
          {inviteCopied ? (
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>
          ) : (
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
          )}
        </button>
        <button className="icon-btn" onClick={onOpenMyPage} data-label="マイページ">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
        </button>
        <div className="session-details">
          <button className="icon-btn" onClick={onToggleSession} data-label="セッション設定">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>
          </button>
          {showSession && (
            <div className="session-popup">
              <div style={{ color: '#e5e7eb', fontWeight: 600, fontSize: '0.85rem' }}>
                {(currentUser?.username ?? myName) || '匿名'} — Seat {mySeatIndex !== null ? mySeatIndex + 1 : '?'}
              </div>
              <label>
                {t('seat')}
                <select
                  value={loginSeatIndex}
                  onChange={(e) => setLoginSeatIndex(Number(e.target.value))}
                >
                  {(game?.players ?? []).map((player, idx) => (
                    <option key={player.id} value={idx}>
                      {player.id}
                    </option>
                  ))}
                </select>
              </label>
              <div className="button-row">
                <button className="primary" onClick={onLoginAsPlayer} disabled={loading}
                  style={{ fontSize: '0.78rem', padding: '0.4rem 0.8rem' }}>
                  {t('takeSeat')}
                </button>
                <button className="ghost" onClick={onLeaveRoom}
                  style={{ fontSize: '0.78rem', padding: '0.4rem 0.8rem' }}>
                  {t('leave')}
                </button>
                <button className="ghost danger" onClick={onLogout}
                  style={{ fontSize: '0.78rem', padding: '0.4rem 0.8rem' }}>
                  {t('logout')}
                </button>
              </div>
              {loginError ? <div className="error" style={{ fontSize: '0.78rem' }}>{loginError}</div> : null}
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
