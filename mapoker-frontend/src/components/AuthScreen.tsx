import { useState } from 'react'
import { fetchJSON } from '../api'
import type { AuthUser } from '../types'
import { t } from '../i18n'

type Props = {
  onAuthSuccess: (user: AuthUser) => void
}

export function AuthScreen({ onAuthSuccess }: Props) {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleAuth = async () => {
    if (!username.trim() || !password.trim()) return
    setLoading(true)
    setError('')
    try {
      const path = mode === 'signin' ? '/v1/auth/login' : '/v1/auth/register'
      const user = await fetchJSON<AuthUser>(path, {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      })
      onAuthSuccess(user)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <div>
        <h2>{mode === 'signin' ? t('signIn') : t('signUp')}</h2>
      </div>
      <label>
        {t('usernameLabel')}
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder={t('usernamePlaceholder')}
          autoComplete="username"
          onKeyDown={(e) => e.key === 'Enter' && void handleAuth()}
        />
      </label>
      <label>
        {t('passwordLabel')}
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder={t('passwordPlaceholder')}
          autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
          onKeyDown={(e) => e.key === 'Enter' && void handleAuth()}
        />
      </label>
      <div className="button-row">
        <button className="primary" onClick={() => void handleAuth()} disabled={loading}>
          {mode === 'signin' ? t('signIn') : t('signUp')}
        </button>
      </div>
      <div style={{ textAlign: 'center' }}>
        <button
          className="ghost"
          onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError('') }}
          style={{ fontSize: '0.85rem' }}
        >
          {mode === 'signin' ? t('goToSignUp') : t('goToSignIn')}
        </button>
      </div>
      {error ? <div className="error">{error}</div> : null}
    </>
  )
}
