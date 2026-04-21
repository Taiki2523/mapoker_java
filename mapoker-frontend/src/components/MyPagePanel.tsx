import { useMemo } from 'react'
import { hasTranslation, t } from '../i18n'
import type {
  AuthUser,
  HandHistoryEntry,
  Table,
  UserTableHistoryEntry,
  WalletLedgerEntry,
  WalletSummary,
} from '../types'

type Props = {
  currentUser: AuthUser
  tables: Table[]
  history: UserTableHistoryEntry[]
  handHistory: HandHistoryEntry[]
  wallet: WalletSummary | null
  walletLedger: WalletLedgerEntry[]
  currentTableId: string
  loading: boolean
  error: string
  onClose: () => void
  onRefresh: () => void
  onOpenTable: (tableId: string) => void
  onClaimDailyBonus: () => void
  onClaimRecovery: () => void
}

const flagLabels: Record<string, string> = {
  casual: t('flagCasual'),
  serious: t('flagSerious'),
  newbie: t('flagNewbie'),
  short_handed: t('flagShortHanded'),
}

const streetLabels: Record<string, string> = {
  preflop: t('streetPreflop'),
  flop: t('streetFlop'),
  turn: t('streetTurn'),
  river: t('streetRiver'),
}

export function MyPagePanel({
  currentUser,
  tables,
  history,
  handHistory,
  wallet,
  walletLedger,
  currentTableId,
  loading,
  error,
  onClose,
  onRefresh,
  onOpenTable,
  onClaimDailyBonus,
  onClaimRecovery,
}: Props) {
  const now = useMemo(() => Date.now(), [])
  const recentTableIds = new Set(history.map((entry) => entry.table_id))
  const historyByTableId = new Map<string, UserTableHistoryEntry>()
  history.forEach((entry) => {
    if (!historyByTableId.has(entry.table_id)) {
      historyByTableId.set(entry.table_id, entry)
    }
  })
  const tablesById = new Map<string, Table>()
  tables.forEach((table) => {
    tablesById.set(table.id, table)
  })
  const myTables = tables.filter((table) => (
    table.members.some((member) => member.name === currentUser.username) || recentTableIds.has(table.id)
  ))
  const publicTables = tables.filter((table) => table.visibility === 'public')
  const currentTable = tables.find((table) => table.id === currentTableId)
  const currentHistory = history.find((entry) => entry.table_id === currentTableId && entry.active)
    ?? historyByTableId.get(currentTableId)

  const formatDate = (value?: string | null) => {
    if (!value) return '—'
    return new Date(value).toLocaleString('ja-JP', {
      month: 'numeric',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const formatStreet = (street: string) => streetLabels[street] ?? street

  const formatCooldown = (value: string | null) => {
    if (!value) return null
    const targetAt = new Date(value).getTime()
    if (Number.isNaN(targetAt) || targetAt <= now) return null
    return t('cooldownUntil', { time: formatDate(value) })
  }

  const formatWalletReason = (reason: string) => (
    hasTranslation(reason) ? t(reason) : reason
  )

  const formatDelta = (delta: number) => (delta >= 0 ? `+${delta}` : `${delta}`)

  const tableNameFor = (tableId: string) => {
    return tablesById.get(tableId)?.name
      ?? historyByTableId.get(tableId)?.table_name
      ?? tableId
  }

  const winnerNamesFor = (entry: HandHistoryEntry) => {
    const names = entry.winners.map((winner) => (
      entry.players.find((player) => player.seat_index === winner)?.name
      ?? t('seatN', { n: winner + 1 })
    ))
    return names.length > 0 ? names.join(', ') : '—'
  }

  const dailyBonusCooldown = wallet ? formatCooldown(wallet.next_daily_bonus_at) : null
  const recoveryCooldown = wallet ? formatCooldown(wallet.next_recovery_at) : null

  return (
    <div className="profile-overlay" onClick={onClose} role="presentation">
      <aside
        className="profile-panel"
        onClick={(event) => event.stopPropagation()}
        aria-label={t('myPage')}
      >
        <div className="profile-header">
          <div>
            <div className="label">{t('myPage')}</div>
            <h2>{currentUser.username}</h2>
            <p>{t('loggedInAs')}</p>
          </div>
          <div className="button-row">
            <button className="secondary" onClick={onRefresh} disabled={loading}>
              {t('refresh')}
            </button>
            <button className="ghost" onClick={onClose}>
              {t('close')}
            </button>
          </div>
        </div>

        {wallet ? (
          <>
            <section className="profile-section">
              <div className="profile-card-grid">
                <div className="profile-card">
                  <span className="label">{t('chipBalance')}</span>
                  <strong>{wallet.chip_balance}</strong>
                </div>
                <div className="profile-card">
                  <span className="label">{t('claimDailyBonus')}</span>
                  <button
                    className="primary"
                    onClick={onClaimDailyBonus}
                    disabled={loading || dailyBonusCooldown !== null}
                  >
                    {t('claimDailyBonus')}
                  </button>
                  {dailyBonusCooldown ? <span className="muted">{dailyBonusCooldown}</span> : null}
                </div>
                <div className="profile-card">
                  <span className="label">{t('claimRecovery')}</span>
                  <button
                    className="secondary"
                    onClick={onClaimRecovery}
                    disabled={loading || recoveryCooldown !== null}
                  >
                    {t('claimRecovery')}
                  </button>
                  {recoveryCooldown ? <span className="muted">{recoveryCooldown}</span> : null}
                </div>
              </div>
            </section>

            <section className="profile-section">
              <div className="profile-section-head">
                <h3>{t('walletLedger')}</h3>
              </div>
              <div className="profile-table-list">
                {walletLedger.map((entry) => (
                  <article key={entry.id} className="profile-table-card compact">
                    <div className="profile-table-main">
                      <div>
                        <strong>{formatWalletReason(entry.reason)}</strong>
                        <div className="muted-light">
                          {t('chipBalance')} {entry.balance_after} · {formatDate(entry.created_at)}
                        </div>
                      </div>
                      <strong>{formatDelta(entry.delta)}</strong>
                    </div>
                  </article>
                ))}
                {walletLedger.length === 0 && (
                  <div className="muted" style={{ padding: '0.75rem 0' }}>{t('noWalletLedger')}</div>
                )}
              </div>
            </section>
          </>
        ) : null}

        <section className="profile-section">
          <div className="profile-card-grid">
            <div className="profile-card">
              <span className="label">{t('currentTable')}</span>
              <strong>{(currentTable?.name ?? currentTableId) || t('none')}</strong>
              <span className="muted">
                {currentHistory
                  ? `${t('seatN', { n: currentHistory.seat_index + 1 })} · ${currentHistory.active ? t('currentlySeated') : t('lastPlayed')}`
                  : t('notJoined')}
              </span>
            </div>
            <div className="profile-card">
              <span className="label">{t('playHistory')}</span>
              <strong>{history.length}</strong>
              <span className="muted">{t('playHistoryDesc')}</span>
            </div>
            <div className="profile-card">
              <span className="label">{t('openTables')}</span>
              <strong>{publicTables.length}</strong>
              <span className="muted">{t('openTablesDesc')}</span>
            </div>
          </div>
        </section>

        {error ? <div className="error">{error}</div> : null}
        {loading ? <div className="muted">{t('refresh')}...</div> : null}

        <section className="profile-section">
          <div className="profile-section-head">
            <h3>{t('myTables')}</h3>
            <span className="muted">{t('myTablesDesc')}</span>
          </div>
          <div className="profile-table-list">
            {myTables.map((table) => {
              const entry = historyByTableId.get(table.id)
              return (
                <article key={table.id} className="profile-table-card">
                  <div className="profile-table-main">
                    <div>
                      <strong>{table.name}</strong>
                      <div className="muted-light">
                        {table.id} · {table.member_count}/{table.max_players} · {table.status}
                      </div>
                    </div>
                    <button className="primary" onClick={() => onOpenTable(table.id)} disabled={loading}>
                      {table.id === currentTableId ? t('reopen') : t('open')}
                    </button>
                  </div>
                  <div className="profile-meta-row">
                    <span className={`table-visibility ${table.visibility}`}>{table.visibility}</span>
                    {entry ? (
                      <span className="muted">
                        {t('seatN', { n: entry.seat_index + 1 })} · {entry.active ? t('currentlySeated') : `${t('lastPlayed')} ${formatDate(entry.left_at ?? entry.joined_at)}`}
                      </span>
                    ) : null}
                  </div>
                  {table.flags.length > 0 ? (
                    <div className="profile-flags">
                      {table.flags.map((flag) => (
                        <span key={flag} className="chip">
                          {flagLabels[flag] ?? flag}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </article>
              )
            })}
            {myTables.length === 0 && (
              <div className="muted" style={{ padding: '0.75rem 0' }}>{t('noMyTables')}</div>
            )}
          </div>
        </section>

        <section className="profile-section">
          <div className="profile-section-head">
            <h3>{t('playHistory')}</h3>
            <span className="muted">{t('playHistoryDesc')}</span>
          </div>
          <div className="profile-table-list">
            {history.map((entry) => (
              <article key={`${entry.table_id}-${entry.joined_at}`} className="profile-table-card compact">
                <div className="profile-table-main">
                  <div>
                    <strong>{entry.table_name}</strong>
                    <div className="muted-light">
                      {entry.table_id} · {t('seatN', { n: entry.seat_index + 1 })} · {entry.active ? t('currentlySeated') : t('lastPlayed')}
                    </div>
                  </div>
                  <button className="ghost" onClick={() => onOpenTable(entry.table_id)} disabled={loading}>
                    {t('open')}
                  </button>
                </div>
                <div className="profile-meta-row">
                  <span className={`table-visibility ${entry.visibility}`}>{entry.visibility}</span>
                  <span className="muted">
                    {t('joinedAt')} {formatDate(entry.joined_at)} · {t('leftAt')} {entry.active ? t('currentlySeated') : formatDate(entry.left_at)}
                  </span>
                </div>
                {entry.flags.length > 0 ? (
                  <div className="profile-flags">
                    {entry.flags.map((flag) => (
                      <span key={flag} className="chip">
                        {flagLabels[flag] ?? flag}
                      </span>
                    ))}
                  </div>
                ) : null}
              </article>
            ))}
            {history.length === 0 && (
              <div className="muted" style={{ padding: '0.75rem 0' }}>{t('noPlayHistory')}</div>
            )}
          </div>
        </section>

        <section className="profile-section">
          <div className="profile-section-head">
            <h3>{t('handHistoryTitle')}</h3>
            <span className="muted">{t('handHistoryDesc')}</span>
          </div>
          <div className="profile-table-list">
            {handHistory.map((entry) => (
              <article key={entry.hand_id} className="profile-table-card compact">
                <div className="profile-table-main">
                  <div>
                    <strong>{tableNameFor(entry.table_id)}</strong>
                    <div className="muted-light">
                      {entry.table_id} · {t('pot')} {entry.pot} · {t('winners')} {winnerNamesFor(entry)}
                    </div>
                  </div>
                  <button className="ghost" onClick={() => onOpenTable(entry.table_id)} disabled={loading}>
                    {t('open')}
                  </button>
                </div>
                <div className="profile-meta-row">
                  <span className="muted">
                    {t('street')} {formatStreet(entry.street)} · {t('finishedAt')} {formatDate(entry.finished_at)}
                  </span>
                </div>
              </article>
            ))}
            {handHistory.length === 0 && (
              <div className="muted" style={{ padding: '0.75rem 0' }}>{t('noHandHistory')}</div>
            )}
          </div>
        </section>

        <section className="profile-section">
          <div className="profile-section-head">
            <h3>{t('publicTablesTitle')}</h3>
            <span className="muted">{t('publicTablesDesc')}</span>
          </div>
          <div className="profile-table-list">
            {publicTables.map((table) => (
              <article key={table.id} className="profile-table-card compact">
                <div className="profile-table-main">
                  <div>
                    <strong>{table.name}</strong>
                    <div className="muted-light">
                      {table.member_count}/{table.max_players} · blinds {table.stake.small_blind}/{table.stake.big_blind}
                    </div>
                  </div>
                  <button className="ghost" onClick={() => onOpenTable(table.id)} disabled={loading}>
                    {t('open')}
                  </button>
                </div>
              </article>
            ))}
            {publicTables.length === 0 && (
              <div className="muted" style={{ padding: '0.75rem 0' }}>{t('noOpenTables')}</div>
            )}
          </div>
        </section>
      </aside>
    </div>
  )
}
