import { useState } from 'react'
import { BLIND_FORMAT_CATEGORIES, BLIND_FORMATS } from '../blindFormats'
import type { AuthUser, CreateGameConfig, TableFlag, TableVisibility } from '../types'
import { t } from '../i18n'
import { UserHeader } from './UserHeader'

type Props = {
  loading: boolean
  error: string
  onCreateGame: (config: CreateGameConfig) => Promise<void>
  currentUser: AuthUser | null
  onOpenMyPage: () => void
  onBack: () => void
  appVersion?: string
}

export function RoomScreen({
  loading,
  error,
  onCreateGame,
  currentUser,
  onOpenMyPage,
  onBack,
  appVersion,
}: Props) {
  const [tableName, setTableName] = useState('Cash Orbit')
  const [playerCount, setPlayerCount] = useState(2)
  const [selectedFormat, setSelectedFormat] = useState(BLIND_FORMATS[0])
  const [ante, setAnte] = useState(() => Math.floor(BLIND_FORMATS[0].bigBlind / 10))
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
      <UserHeader username={currentUser?.username ?? ''} onOpenMyPage={onOpenMyPage} appVersion={appVersion} />
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
          {t('stakeLabel')}
          <select
            value={`${selectedFormat.smallBlind}/${selectedFormat.bigBlind}`}
            onChange={(e) => {
              const nextFormat = BLIND_FORMATS.find(
                (format) => `${format.smallBlind}/${format.bigBlind}` === e.target.value
              )
              if (nextFormat) {
                setSelectedFormat(nextFormat)
                setAnte(Math.floor(nextFormat.bigBlind / 10))
              }
            }}
          >
            {BLIND_FORMAT_CATEGORIES.map((category) => (
              <optgroup key={category} label={category}>
                {BLIND_FORMATS
                  .filter((format) => format.category === category)
                  .map((format) => (
                    <option
                      key={format.label}
                      value={`${format.smallBlind}/${format.bigBlind}`}
                    >
                      {format.label}
                    </option>
                  ))}
              </optgroup>
            ))}
          </select>
        </label>
        <label>
          {t('ante')}
          <input
            type="number" min={0} value={ante}
            onChange={(e) => setAnte(Math.max(0, Number(e.target.value)))}
            placeholder={t('anteHelp')}
          />
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
        <button className="ghost" onClick={onBack}>
          {t('backToLobby')}
        </button>
        <button
          className="primary"
          onClick={() => void onCreateGame({
            tableName,
            playerCount,
            smallBlind: selectedFormat.smallBlind,
            bigBlind: selectedFormat.bigBlind,
            ante,
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
