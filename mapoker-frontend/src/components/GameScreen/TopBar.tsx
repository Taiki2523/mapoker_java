import type { AuthUser, GameState } from '../../types'
import { t } from '../../i18n'

type Props = {
  game: GameState | null
  currentUser: AuthUser | null
  myName: string
  mySeatIndex: number | null
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
  game, currentUser, myName, mySeatIndex, loginSeatIndex, setLoginSeatIndex,
  loading, error, leavePending, autoRefresh, setAutoRefresh,
  inviteCopied, onCopyInvite, onOpenMyPage, onLoginAsPlayer, onLeaveRoom, onLogout,
  loginError, showSession, onToggleSession,
}: Props) {
  return (
    <header className="game-topbar">
      <span className="topbar-brand">mapoker</span>

      <div className="topbar-info">
        {game?.street && (
          <span className="street-badge">{game.street.toUpperCase()}</span>
        )}
        <span>Pot <strong>{game?.pot_total ?? 0}</strong></span>
        <span>Bet <strong>{game?.current_bet ?? 0}</strong></span>
        {game?.status && game.status !== 'in_progress' && (
          <span style={{ color: '#fbbf24', fontWeight: 600 }}>{game.status}</span>
        )}
      </div>

      <div className="topbar-right">
        {leavePending && (
          <span style={{ color: '#fbbf24', fontSize: '0.78rem', fontWeight: 600 }}>
            {t('leavePending')}
          </span>
        )}
        {error && <span className="topbar-error">{error}</span>}
        <label className="toggle-label" style={{ color: '#6b7280', fontSize: '0.75rem', gap: '0.3rem' }}>
          <input
            type="checkbox"
            checked={autoRefresh}
            onChange={(e) => setAutoRefresh(e.target.checked)}
            style={{ width: '14px', height: '14px' }}
          />
          auto
        </label>
        <button className="icon-btn" onClick={onCopyInvite} title="Copy invite link">
          {inviteCopied ? '✓' : '🔗'}
        </button>
        <button className="icon-btn" onClick={onOpenMyPage} title={t('myPage')}>
          ☺
        </button>
        <div className="session-details">
          <button className="icon-btn" onClick={onToggleSession} title="Session settings">
            ⚙
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
