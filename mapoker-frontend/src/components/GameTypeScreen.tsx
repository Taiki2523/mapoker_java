import { t } from '../i18n'
import type { AuthUser } from '../types'
import { UserHeader } from './UserHeader'

type Props = {
  currentUser: AuthUser | null
  onOpenMyPage: () => void
  onLogout: () => void
  onSelectRing: () => void
}

export function GameTypeScreen({ currentUser, onOpenMyPage, onLogout, onSelectRing }: Props) {
  return (
    <>
      <UserHeader username={currentUser?.username ?? ''} onOpenMyPage={onOpenMyPage} onLogout={onLogout} />
      <div>
        <h2>{t('gameTypeTitle')}</h2>
        <p>{t('gameTypeDesc')}</p>
      </div>
      <div className="game-type-grid">
        <button className="game-type-card" onClick={onSelectRing}>
          <span className="game-type-label">{t('ring')}</span>
        </button>
        <button className="game-type-card" disabled>
          <span className="game-type-label">{t('tournament')}</span>
          <span className="game-type-badge">{t('comingSoon')}</span>
        </button>
      </div>
    </>
  )
}
