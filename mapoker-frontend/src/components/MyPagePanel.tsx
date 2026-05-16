import { useState } from 'react'
import { fetchJSON, uploadFile } from '../api'
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
  onLogout: () => void
  onUpdateUser: (user: AuthUser) => void
  onClaimDailyBonus: () => void
  onClaimRecovery: () => void
}

function ProfileEditModal({
  currentUser,
  onSave,
  onCancel,
}: {
  currentUser: AuthUser
  onSave: (user: AuthUser) => void
  onCancel: () => void
}) {
  const [newUsername, setNewUsername] = useState(currentUser.username)
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(currentUser.avatar_url)
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState<{ type: 'ok' | 'error'; text: string } | null>(null)

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setAvatarFile(file)
    setPreviewUrl(URL.createObjectURL(file))
  }

  const handleSave = async () => {
    const trimmed = newUsername.trim()
    setSaving(true)
    setMsg(null)
    try {
      let updated: AuthUser = currentUser
      if (avatarFile) {
        updated = await uploadFile<AuthUser>('/v1/auth/me/avatar', avatarFile)
      }
      if (trimmed && trimmed !== updated.username) {
        updated = await fetchJSON<AuthUser>('/v1/auth/me/username', {
          method: 'PATCH',
          body: JSON.stringify({ newUsername: trimmed }),
        })
      }
      if (!avatarFile && trimmed === currentUser.username) { onCancel(); return }
      onSave(updated)
    } catch (err) {
      setMsg({ type: 'error', text: err instanceof Error ? err.message : '更新に失敗しました' })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="profile-overlay" onClick={onCancel} role="presentation">
      <div
        className="profile-panel"
        style={{ maxWidth: '28rem', margin: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="profile-header">
          <div>
            <div className="label">プロフィール変更</div>
          </div>
          <button className="ghost" onClick={onCancel}>{t('close')}</button>
        </div>

        <section className="profile-section">
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
            {previewUrl && (
              <img src={previewUrl} alt="avatar"
                style={{ width: 72, height: 72, borderRadius: '50%', objectFit: 'cover' }} />
            )}
            <label style={{ cursor: 'pointer', fontSize: '0.85rem', color: 'var(--accent)' }}>
              アイコンを変更
              <input type="file" accept="image/jpeg,image/png"
                style={{ display: 'none' }} onChange={handleFileChange} disabled={saving} />
            </label>
          </div>
          <div className="field-grid">
            <label>
              表示名
              <input
                type="text"
                value={newUsername}
                onChange={(e) => setNewUsername(e.target.value)}
                placeholder={currentUser.username}
                disabled={saving}
                autoFocus
                onKeyDown={(e) => e.key === 'Enter' && void handleSave()}
              />
            </label>
          </div>
          {msg && (
            <div className={msg.type === 'ok' ? 'success-msg' : 'error'} style={{ marginTop: '0.5rem' }}>
              {msg.text}
            </div>
          )}
          <div className="button-row" style={{ marginTop: '0.75rem' }}>
            <button
              className="primary"
              onClick={() => void handleSave()}
              disabled={saving || !newUsername.trim()}
            >
              {saving ? '保存中...' : '保存'}
            </button>
            <button className="ghost" onClick={onCancel} disabled={saving}>
              {t('cancel')}
            </button>
          </div>
        </section>
      </div>
    </div>
  )
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
  onLogout,
  onUpdateUser,
  onClaimDailyBonus,
  onClaimRecovery,
}: Props) {
  const [showProfileEdit, setShowProfileEdit] = useState(false)

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

  const dailyBonusCooldown = wallet ? formatCooldown(wallet.next_daily_bonus_at) : null
  const recoveryCooldown = wallet ? formatCooldown(wallet.next_recovery_at) : null

  return (
    <>
      <div className="profile-overlay" onClick={onClose} role="presentation">
        <aside
          className="profile-panel"
          onClick={(event) => event.stopPropagation()}
          aria-label={t('myPage')}
        >
          <div className="profile-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              {currentUser.avatar_url && (
                <img src={currentUser.avatar_url} alt="avatar"
                  style={{ width: 48, height: 48, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
              )}
              <div>
                <div className="label">{t('myPage')}</div>
                <h2>{currentUser.display_name}</h2>
                <p>{t('loggedInAs')}</p>
              </div>
            </div>
            <div className="button-row">
              <button className="ghost danger" onClick={onLogout}>
                {t('logout')}
              </button>
              <button className="ghost" onClick={onClose}>
                {t('close')}
              </button>
              <button className="secondary" onClick={() => setShowProfileEdit(true)}>
                プロフィール変更
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
        </aside>
      </div>

      {showProfileEdit && (
        <ProfileEditModal
          currentUser={currentUser}
          onSave={(updated) => {
            onUpdateUser(updated)
            setShowProfileEdit(false)
          }}
          onCancel={() => setShowProfileEdit(false)}
        />
      )}
    </>
  )
}
