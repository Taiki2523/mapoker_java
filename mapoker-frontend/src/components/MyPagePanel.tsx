import { useState } from 'react'
import { fetchJSON } from '../api'
import { hasTranslation, t } from '../i18n'
import type {
  AuthUser,
  Table,
  UserTableHistoryEntry,
  WalletLedgerEntry,
  WalletSummary,
} from '../types'

type Props = {
  currentUser: AuthUser
  tables: Table[]
  history: UserTableHistoryEntry[]
  wallet: WalletSummary | null
  walletLedger: WalletLedgerEntry[]
  currentTableId: string
  loading: boolean
  error: string
  onClose: () => void
  onRefresh: () => void
  onLogout: () => void
  onUpdateUser: (user: AuthUser) => void
  onClaimDailyBonus: () => void
  onClaimRecovery: () => void
}

export function MyPagePanel({
  currentUser,
  tables,
  history,
  wallet,
  walletLedger,
  currentTableId,
  loading,
  error,
  onClose,
  onRefresh,
  onLogout,
  onUpdateUser,
  onClaimDailyBonus,
  onClaimRecovery,
}: Props) {
  const [newUsername, setNewUsername] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileMsg, setProfileMsg] = useState<{ type: 'ok' | 'error'; text: string } | null>(null)
  const historyByTableId = new Map<string, UserTableHistoryEntry>()
  history.forEach((entry) => {
    if (!historyByTableId.has(entry.table_id)) {
      historyByTableId.set(entry.table_id, entry)
    }
  })
  const currentTable = tables.find((table) => table.id === currentTableId)
  const currentHistory = history.find((entry) => entry.table_id === currentTableId && entry.active)
    ?? historyByTableId.get(currentTableId)

  const formatDate = (value?: string | null): string => {
    if (!value) return '—'
    return new Date(value).toLocaleString('ja-JP', {
      month: 'numeric',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const formatCooldown = (value: string | null) => {
    if (!value) return null
    const targetAt = new Date(value).getTime()
    if (Number.isNaN(targetAt)) return null
    return t('cooldownUntil', { time: formatDate(value) })
  }

  const formatWalletReason = (reason: string) => (
    hasTranslation(reason) ? t(reason) : reason
  )

  const formatDelta = (delta: number) => (delta >= 0 ? `+${delta}` : `${delta}`)

  const handleSaveProfile = async () => {
    if (newPassword && newPassword !== confirmPassword) {
      setProfileMsg({ type: 'error', text: 'パスワードが一致しません' })
      return
    }
    if (!newUsername.trim() && !newPassword) return
    setProfileSaving(true)
    setProfileMsg(null)
    try {
      const updated = await fetchJSON<AuthUser>('/v1/auth/me', {
        method: 'PUT',
        body: JSON.stringify({
          newUsername: newUsername.trim() || null,
          currentPassword: currentPassword || null,
          newPassword: newPassword || null,
        }),
      })
      onUpdateUser(updated)
      setNewUsername('')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setProfileMsg({ type: 'ok', text: '更新しました' })
    } catch (err) {
      setProfileMsg({ type: 'error', text: err instanceof Error ? err.message : '更新に失敗しました' })
    } finally {
      setProfileSaving(false)
    }
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
            <button className="ghost danger" onClick={onLogout}>
              {t('logout')}
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
          </div>
        </section>

        {error ? <div className="error">{error}</div> : null}
        {loading ? <div className="muted">{t('refresh')}...</div> : null}

        <section className="profile-section">
          <div className="profile-section-head">
            <h3>アカウント設定</h3>
          </div>
          <div className="field-grid">
            <label>
              新しいユーザー名
              <input
                type="text"
                value={newUsername}
                onChange={(e) => setNewUsername(e.target.value)}
                placeholder={currentUser.username}
                disabled={profileSaving}
              />
            </label>
            <label>
              現在のパスワード
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder="パスワード変更時に入力"
                disabled={profileSaving}
              />
            </label>
            <label>
              新しいパスワード
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="8文字以上"
                disabled={profileSaving}
              />
            </label>
            <label>
              新しいパスワード（確認）
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="もう一度入力"
                disabled={profileSaving}
              />
            </label>
          </div>
          {profileMsg && (
            <div className={profileMsg.type === 'ok' ? 'success-msg' : 'error'} style={{ marginTop: '0.5rem' }}>
              {profileMsg.text}
            </div>
          )}
          <div className="button-row" style={{ marginTop: '0.75rem' }}>
            <button
              className="primary"
              onClick={() => void handleSaveProfile()}
              disabled={profileSaving || (!newUsername.trim() && !newPassword)}
            >
              {profileSaving ? '保存中...' : '保存'}
            </button>
          </div>
        </section>
      </aside>
    </div>
  )
}
