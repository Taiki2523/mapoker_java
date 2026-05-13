import { t } from '../i18n'

type Props = {
  username: string
  onOpenMyPage: () => void
  appVersion?: string
}

export function UserHeader({ username, onOpenMyPage, appVersion }: Props) {
  return (
    <header className="user-header">
      <span className="brand">
        mapoker
        {appVersion && <span className="app-version">v{appVersion}</span>}
      </span>
      <div className="user-header-right">
        <span>{username} さん</span>
        <button className="ghost" onClick={onOpenMyPage}>{t('myPage')}</button>
      </div>
    </header>
  )
}
