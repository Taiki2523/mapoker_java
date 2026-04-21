import { t } from '../i18n'

type Props = {
  username: string
  onOpenMyPage: () => void
  onLogout: () => void
}

export function UserHeader({ username, onOpenMyPage, onLogout }: Props) {
  return (
    <header className="user-header">
      <span className="brand">mapoker</span>
      <div className="user-header-right">
        <span>{username} さん</span>
        <button className="ghost" onClick={onOpenMyPage}>{t('myPage')}</button>
        <button className="ghost danger" onClick={onLogout}>{t('logout')}</button>
      </div>
    </header>
  )
}
