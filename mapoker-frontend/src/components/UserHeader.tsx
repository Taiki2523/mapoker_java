import { t } from '../i18n'

type Props = {
  username: string
  onLogout: () => void
}

export function UserHeader({ username, onLogout }: Props) {
  return (
    <header className="user-header">
      <span className="brand">mapoker</span>
      <div className="user-header-right">
        <span>{username} さん</span>
        {/* TODO: マイページ機能（プレイ履歴など）は将来実装 */}
        <button className="ghost danger" onClick={onLogout}>{t('logout')}</button>
      </div>
    </header>
  )
}
