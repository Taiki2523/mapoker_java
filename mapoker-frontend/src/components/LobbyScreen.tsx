import { useEffect, useMemo, useState } from 'react'
import { fetchJSON } from '../api'
import { t } from '../i18n'
import type { AuthUser, Table, TableFlag, TableVisibility } from '../types'
import { UserHeader } from './UserHeader'

type Props = {
  currentUser: AuthUser | null
  onOpenMyPage: () => void
  onJoinRoom: (tableId: string) => Promise<void>
  onCreateTable: () => void
  onBack: () => void
  appVersion?: string
}

const filterFlags: TableFlag[] = ['casual', 'serious', 'newbie', 'short_handed']

const flagLabels: Record<TableFlag, string> = {
  casual: t('flagCasual'),
  serious: t('flagSerious'),
  newbie: t('flagNewbie'),
  short_handed: t('flagShortHanded'),
}

const statusLabels: Record<string, string> = {
  waiting: t('tableStatusWaiting'),
  in_progress: t('tableStatusInProgress'),
  showdown: t('tableStatusShowdown'),
  finished: t('tableStatusFinished'),
}

export function LobbyScreen({ currentUser, onOpenMyPage, onJoinRoom, onCreateTable, onBack, appVersion }: Props) {
  const [tables, setTables] = useState<Table[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [visibility, setVisibility] = useState<TableVisibility>('public')
  const [selectedFlags, setSelectedFlags] = useState<TableFlag[]>([])

  const refreshTables = async () => {
    setLoading(true)
    setError('')
    try {
      const data = await fetchJSON<Table[]>('/v1/tables')
      setTables(data)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refreshTables()
  }, [])

  const filteredTables = useMemo(() => {
    return tables.filter((table) => (
      table.visibility === visibility
      && selectedFlags.every((flag) => table.flags.includes(flag))
    ))
  }, [selectedFlags, tables, visibility])

  const toggleFlag = (flag: TableFlag) => {
    setSelectedFlags((prev) => (
      prev.includes(flag)
        ? prev.filter((value) => value !== flag)
        : [...prev, flag]
    ))
  }

  const statusLabel = (status: string) => statusLabels[status] ?? status

  return (
    <>
      <UserHeader username={currentUser?.username ?? ''} onOpenMyPage={onOpenMyPage} appVersion={appVersion} />
      <div className="lobby-browser-head">
        <div>
          <h2>{t('lobbyBrowserTitle')}</h2>
          <p>{t('lobbyBrowserDesc')}</p>
        </div>
        <div className="button-row">
          <button className="secondary" onClick={() => void refreshTables()} disabled={loading}>
            {t('refresh')}
          </button>
          <button className="primary" onClick={onCreateTable}>
            {t('createGame')}
          </button>
          <button className="ghost" onClick={onBack}>
            {t('back')}
          </button>
        </div>
      </div>

      <section className="lobby-filter-card">
        <div className="lobby-filter-section">
          <span className="label">{t('visibility')}</span>
          <div className="lobby-toggle-group" role="tablist" aria-label={t('visibility')}>
            <button
              type="button"
              className={visibility === 'public' ? 'primary' : 'secondary'}
              onClick={() => setVisibility('public')}
            >
              {t('publicTable')}
            </button>
            <button
              type="button"
              className={visibility === 'private' ? 'primary' : 'secondary'}
              onClick={() => setVisibility('private')}
            >
              {t('privateTable')}
            </button>
          </div>
        </div>

        <div className="lobby-filter-section">
          <span className="label">{t('tableFlags')}</span>
          <div className="lobby-flag-grid">
            {filterFlags.map((flag) => (
              <label key={flag} className="lobby-flag-option">
                <input
                  type="checkbox"
                  checked={selectedFlags.includes(flag)}
                  onChange={() => toggleFlag(flag)}
                />
                <span>{flagLabels[flag]}</span>
              </label>
            ))}
          </div>
        </div>
      </section>

      {error ? <div className="error">{error}</div> : null}
      {loading ? <div className="muted">{t('refresh')}...</div> : null}

      <div className="lobby-browser-list">
        {filteredTables.map((table) => (
          <article key={table.id} className="lobby-browser-row">
            <div className="lobby-browser-main">
              <div className="lobby-browser-title-row">
                <strong>{table.name}</strong>
                <span className={table.visibility === 'public' ? 'table-visibility public' : 'table-visibility private'}>
                  {table.visibility === 'public' ? t('publicTable') : t('privateTable')}
                </span>
              </div>
              <div className="lobby-browser-meta">
                <span>{t('stakeLabel')} {table.stake.small_blind}/{table.stake.big_blind}{table.stake.ante > 0 ? ` / ${t('ante')} ${table.stake.ante}` : ''}{table.stake.straddle_enabled ? ` / ${t('straddle')}` : ''}</span>
                <span>{t('membersLabel')} {table.member_count}/{table.max_players}</span>
                <span>{t('status')} {statusLabel(table.status)}</span>
              </div>
              <div className="muted-light">{table.id}</div>
              {table.flags.length > 0 ? (
                <div className="profile-flags">
                  {table.flags.map((flag) => (
                    <span key={flag} className="chip">
                      {flagLabels[flag] ?? flag}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
            <div className="lobby-browser-actions">
              <button className="primary" onClick={() => void onJoinRoom(table.id)} disabled={loading}>
                {t('joinTable')}
              </button>
            </div>
          </article>
        ))}

        {filteredTables.length === 0 && !loading && (
          <div className="lobby-browser-empty">
            {t('noLobbyTables')}
          </div>
        )}
      </div>
    </>
  )
}
