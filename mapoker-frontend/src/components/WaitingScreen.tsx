import type { AuthUser, GameState, RoomMember } from '../types'
import { t } from '../i18n'
import { UserHeader } from './UserHeader'

type Props = {
  gameId: string
  inviteUrl: string
  inviteCopied: boolean
  onCopyInvite: () => void
  game: GameState | null
  roster: RoomMember[]
  mySeatIndex: number | null
  loginSeatIndex: number
  setLoginSeatIndex: (n: number) => void
  onJoinLobby: (seat?: number) => void
  onStartHand: () => void
  isOwner: boolean
  canStartHand: boolean
  lobbyReady: boolean
  targetPlayerCount: number
  loading: boolean
  loginError: string
  currentUser: AuthUser | null
  onLogout: () => void
}

export function WaitingScreen({
  gameId, inviteUrl, inviteCopied, onCopyInvite,
  game, roster, mySeatIndex, loginSeatIndex, setLoginSeatIndex, onJoinLobby, onStartHand,
  isOwner, canStartHand, lobbyReady, targetPlayerCount, loading, loginError,
  currentUser, onLogout,
}: Props) {
  return (
    <>
      <UserHeader username={currentUser?.username ?? ''} onLogout={onLogout} />
      <div>
        <h2>{t('waitingRoomTitle')}</h2>
        <p>{t('waitingRoomDesc')}</p>
      </div>
      <div className="invite-box">
        <span className="label">{t('inviteCode')}</span>
        <strong>{gameId}</strong>
      </div>
      {inviteUrl ? (
        <div className="invite-box">
          <span className="label">{t('inviteUrl')}</span>
          <div className="invite-url-row">
            <input value={inviteUrl} readOnly style={{ fontSize: '0.78rem', padding: '0.4rem 0.55rem' }} />
            <button className="ghost" onClick={onCopyInvite} style={{ flexShrink: 0 }}>
              {inviteCopied ? t('copied') : t('copy')}
            </button>
          </div>
        </div>
      ) : null}
      {mySeatIndex === null ? (
        <div className="field-grid">
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
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button className="primary" onClick={() => onJoinLobby()} disabled={loading}>
              {t('joinLobby')}
            </button>
          </div>
        </div>
      ) : null}
      <div>
        <div className="players-progress">
          <span>{t('playersJoined')}</span>
          <strong>{roster.length} / {targetPlayerCount}</strong>
        </div>
        <div className="lobby-list" style={{ marginTop: '0.5rem' }}>
          {roster.map((member) => (
            <div key={`${member.seatIndex}-${member.name}`} className="lobby-item">
              <span>{t('seatN', { n: member.seatIndex + 1 })}</span>
              <strong>{member.name || t('playerN', { n: member.seatIndex + 1 })}</strong>
            </div>
          ))}
          {roster.length === 0 && (
            <div className="muted" style={{ textAlign: 'center', padding: '0.5rem' }}>
              {t('waitingForPlayers')}
            </div>
          )}
        </div>
      </div>
      {isOwner && lobbyReady ? (
        <button
          className="primary"
          onClick={onStartHand}
          disabled={!gameId || loading || !canStartHand}
        >
          {t('startHand')}
        </button>
      ) : null}
      {loginError ? <div className="error">{loginError}</div> : null}
    </>
  )
}
