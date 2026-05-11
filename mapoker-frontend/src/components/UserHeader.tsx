import { t } from '../i18n'

type Props = {
  username: string
  onOpenMyPage: () => void
}

export function UserHeader({ username, onOpenMyPage }: Props) {
  return (
    <header className="user-header">
      <span className="brand">mapoker</span>
      <div className="user-header-right">
        <span>{username} さん</span>
        <button className="ghost" onClick={onOpenMyPage}>{t('myPage')}</button>
      </div>
    </header>
  )
}
