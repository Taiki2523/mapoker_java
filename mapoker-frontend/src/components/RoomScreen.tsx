import { useState } from 'react'
import type { AuthUser, CreateGameConfig, TableFlag, TableVisibility } from '../types'
import { t } from '../i18n'
import { UserHeader } from './UserHeader'

type Props = {
  loading: boolean
  error: string
  onCreateGame: (config: CreateGameConfig) => Promise<void>
  onJoinRoom: (idOrUrl: string) => Promise<void>
  currentUser: AuthUser | null
  onOpenMyPage: () => void
  onLogout: () => void
  onOpenLobby: () => void
}

export function RoomScreen({
  loading,
  error,
  onCreateGame,
  onJoinRoom,
  currentUser,
  onOpenMyPage,
  onLogout,
  onOpenLobby,
}: Props) {
  const [roomInput, setRoomInput] = useState('')
  const [tableName, setTableName] = useState('Cash Orbit')
  const [playerCount, setPlayerCount] = useState(2)
  const [stackSize, setStackSize] = useState(100)
  const [bigBlind, setBigBlind] = useState(10)
  const [buttonIndex, setButtonIndex] = useState(0)
  const [seed, setSeed] = useState('')
  const [autoStart, setAutoStart] = useState(true)
  const [visibility, setVisibility] = useState<TableVisibility>('public')
  const [flags, setFlags] = useState<TableFlag[]>(['casual'])

  const toggleFlag = (flag: TableFlag) => {
    setFlags((prev) => (
      prev.includes(flag)
        ? prev.filter((value) => value !== flag)
        : [...prev, flag]
    ))
  }

  return (
    <>
      <UserHeader username={currentUser?.username ?? ''} onOpenMyPage={onOpenMyPage} onLogout={onLogout} />
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
      <div className="button-row">
        <button className="secondary" onClick={onOpenLobby}>
          {t('openLobbyBrowser')}
        </button>
      </div>

      <div className="lobby-divider" />
      <div>
        <h2>{t('createRoomTitle')}</h2>
        <p>{t('createRoomDesc')}</p>
      </div>
      <div className="field-grid">
        <label>
          {t('tableName')}
          <input
            value={tableName}
            onChange={(e) => setTableName(e.target.value)}
            placeholder={t('tableNamePlaceholder')}
          />
        </label>
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
        <label>
          {t('visibility')}
          <select
            value={visibility}
            onChange={(e) => setVisibility(e.target.value as TableVisibility)}
          >
            <option value="public">{t('publicTable')}</option>
            <option value="private">{t('privateTable')}</option>
          </select>
        </label>
      </div>
      <div>
        <div className="label" style={{ marginBottom: '0.45rem' }}>{t('tableFlags')}</div>
        <div className="button-row" style={{ justifyContent: 'flex-start' }}>
          <button type="button" className={flags.includes('casual') ? 'primary' : 'secondary'} onClick={() => toggleFlag('casual')}>
            {t('flagCasual')}
          </button>
          <button type="button" className={flags.includes('serious') ? 'primary' : 'secondary'} onClick={() => toggleFlag('serious')}>
            {t('flagSerious')}
          </button>
          <button type="button" className={flags.includes('newbie') ? 'primary' : 'secondary'} onClick={() => toggleFlag('newbie')}>
            {t('flagNewbie')}
          </button>
          <button type="button" className={flags.includes('short_handed') ? 'primary' : 'secondary'} onClick={() => toggleFlag('short_handed')}>
            {t('flagShortHanded')}
          </button>
        </div>
      </div>
      <div className="button-row">
        <button
          className="primary"
          onClick={() => void onCreateGame({
            tableName,
            playerCount,
            stackSize,
            bigBlind,
            buttonIndex,
            seed,
            autoStart,
            visibility,
            flags,
          })}
          disabled={loading}
        >
          {t('createGame')}
        </button>
      </div>
      {error ? <div className="error">{error}</div> : null}
    </>
  )
}
