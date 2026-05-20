import { useState } from 'react'
import { GoogleLogin } from '@react-oauth/google'
import { fetchJSON, uploadFile } from '../api'
import type { AuthUser } from '../types'
import { t } from '../i18n'

type Props = {
  onAuthSuccess: (user: AuthUser) => void
}

function AvatarPreview({ src, size = 72 }: { src: string | null; size?: number }) {
  if (!src) return null
  return (
    <img
      src={src}
      alt="avatar"
      style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover' }}
    />
  )
}

function ProfileSetupScreen({
  user,
  onComplete,
}: {
  user: AuthUser
  onComplete: (user: AuthUser) => void
}) {
  const [name, setName] = useState(user.username)
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(user.avatar_url)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setAvatarFile(file)
    setPreviewUrl(URL.createObjectURL(file))
  }

  const handleSave = async () => {
    const trimmed = name.trim()
    if (!trimmed) return
    setSaving(true)
    setError('')
    try {
      let updated: AuthUser = user
      if (avatarFile) {
        updated = await uploadFile<AuthUser>('/v1/auth/me/avatar', avatarFile)
      }
      if (trimmed !== updated.username) {
        updated = await fetchJSON<AuthUser>('/v1/auth/me/username', {
          method: 'PATCH',
          body: JSON.stringify({ newUsername: trimmed }),
        })
      }
      onComplete({ ...updated, new_user: false })
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新に失敗しました')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div>
        <h2>プロフィール設定</h2>
        <p>ゲームで使う表示名とアイコンを設定してください</p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.75rem' }}>
        <AvatarPreview src={previewUrl} />
        <label style={{ cursor: 'pointer', fontSize: '0.85rem', color: 'var(--accent)' }}>
          アイコンを変更
          <input
            type="file"
            accept="image/jpeg,image/png"
            style={{ display: 'none' }}
            onChange={handleFileChange}
            disabled={saving}
          />
        </label>
      </div>

      <label>
        表示名
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="例：yamada_taro"
          autoFocus
          disabled={saving}
          onKeyDown={(e) => e.key === 'Enter' && void handleSave()}
        />
      </label>

      {error ? <div className="error">{error}</div> : null}

      <div className="button-row">
        <button
          className="primary"
          onClick={() => void handleSave()}
          disabled={saving || !name.trim()}
        >
          {saving ? '保存中...' : 'はじめる'}
        </button>
      </div>
    </>
  )
}

export function AuthScreen({ onAuthSuccess }: Props) {
  const [error, setError] = useState('')
  const [newUser, setNewUser] = useState<AuthUser | null>(null)

  const handleGoogleSuccess = async (credential: string) => {
    setError('')
    try {
      const user = await fetchJSON<AuthUser>('/v1/auth/google', {
        method: 'POST',
        body: JSON.stringify({ idToken: credential }),
      })
      if (user.new_user) {
        setNewUser(user)
      } else {
        onAuthSuccess(user)
      }
    } catch (err) {
      setError((err as Error).message)
    }
  }

  if (newUser) {
    return (
      <ProfileSetupScreen
        user={newUser}
        onComplete={onAuthSuccess}
      />
    )
  }

  return (
    <>
      <div>
        <h2>{t('signIn')}</h2>
      </div>
      <div style={{ display: 'flex', justifyContent: 'center', marginTop: '2rem' }}>
        <GoogleLogin
          onSuccess={({ credential }) => {
            if (credential) void handleGoogleSuccess(credential)
          }}
          onError={() => setError(t('signInFailed'))}
          useOneTap
        />
      </div>
      {error ? <div className="error">{error}</div> : null}
    </>
  )
}
