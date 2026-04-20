import { useState } from 'react'
import type { AuthUser, CreateGameConfig } from '../types'
import { t } from '../i18n'
import { UserHeader } from './UserHeader'

type Props = {
  loading: boolean
  error: string
  onCreateGame: (config: CreateGameConfig) => Promise<void>
  onJoinRoom: (idOrUrl: string) => Promise<void>
  currentUser: AuthUser | null
  onLogout: () => void
}

export function RoomScreen({ loading, error, onCreateGame, onJoinRoom, currentUser, onLogout }: Props) {
  const [roomInput, setRoomInput] = useState('')
  const [playerCount, setPlayerCount] = useState(2)
  const [stackSize, setStackSize] = useState(100)
  const [bigBlind, setBigBlind] = useState(10)
  const [buttonIndex, setButtonIndex] = useState(0)
  const [seed, setSeed] = useState('')
  const [autoStart, setAutoStart] = useState(true)

  return (
    <>
      <UserHeader username={currentUser?.username ?? ''} onLogout={onLogout} />
      <div>
        <h2>{t('roomTitle')}</h2>
        <p>{t('roomDesc')}</p>
      </div>
      <div className="room-row">
        <label>
          {t('roomIdLabel')}
          <input
            value={roomInput}
            onChange={(e) => setRoomInput(e.target.value)}
            placeholder={t('roomIdPlaceholder')}
            onKeyDown={(e) => e.key === 'Enter' && void onJoinRoom(roomInput)}
          />
        </label>
        <button className="ghost" onClick={() => void onJoinRoom(roomInput)} disabled={loading}>
          {t('joinRoom')}
        </button>
      </div>

      <div className="lobby-divider" />
      <div>
        <h2>{t('createRoomTitle')}</h2>
        <p>{t('createRoomDesc')}</p>
      </div>
      <div className="field-grid">
        <label>
          {t('players')}
          <input
            type="number" min={2} max={9} value={playerCount}
            onChange={(e) => setPlayerCount(Number(e.target.value))}
          />
        </label>
        <label>
          {t('stack')}
          <input
            type="number" min={1} value={stackSize}
            onChange={(e) => setStackSize(Number(e.target.value))}
          />
        </label>
        <label>
          {t('bigBlind')}
          <input
            type="number" min={1} value={bigBlind}
            onChange={(e) => setBigBlind(Number(e.target.value))}
          />
        </label>
        <label>
          {t('buttonIndex')}
          <input
            type="number" min={0} max={Math.max(0, playerCount - 1)} value={buttonIndex}
            onChange={(e) => setButtonIndex(Number(e.target.value))}
          />
        </label>
        <label>
          {t('seed')}
          <input
            type="number" placeholder={t('seedPlaceholder')} value={seed}
            onChange={(e) => setSeed(e.target.value)}
          />
        </label>
        <label className="toggle-label">
          <input
            type="checkbox" checked={autoStart}
            onChange={(e) => setAutoStart(e.target.checked)}
          />
          {t('autoStartHand')}
        </label>
      </div>
      <div className="button-row">
        <button
          className="primary"
          onClick={() => void onCreateGame({ playerCount, stackSize, bigBlind, buttonIndex, seed, autoStart })}
          disabled={loading}
        >
          {t('createGame')}
        </button>
      </div>
      {error ? <div className="error">{error}</div> : null}
    </>
  )
}
