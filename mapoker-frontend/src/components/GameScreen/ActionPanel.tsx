import type { BetPreset, GameState, Player } from '../../types'
import { CardFace } from '../Card'
import { t } from '../../i18n'

type Props = {
  game: GameState | null
  mySeatIndex: number | null
  canAct: boolean
  loading: boolean
  toCall: number
  minRaise: number
  maxBet: number
  betPresets: BetPreset[]
  actionAmount: number
  setActionAmount: (n: number) => void
  myHandName: string | null
  currentPlayer: Player | null
  stackMode: 'chips' | 'bb'
  displayName: (idx: number) => string
  onSendAction: (type: string, amount: number) => void
}

export function ActionPanel({
  game, mySeatIndex, canAct, loading,
  toCall, minRaise, maxBet, betPresets, actionAmount, setActionAmount,
  myHandName, currentPlayer, stackMode, displayName, onSendAction,
}: Props) {
  const bb = game?.big_blind ?? 0
  const fmt = (chips: number) => {
    if (stackMode === 'bb' && bb > 0) {
      return `${Math.round((chips / bb) * 10) / 10}BB`
    }
    return `¥${chips}`
  }
  return (
    <div className={`action-panel ${canAct ? 'active' : 'inactive'}`}>
      <div className="action-panel-controls">
      <div className="action-info-row">
        <div className="action-my-cards">
          {(mySeatIndex !== null && game?.players?.[mySeatIndex]?.hole?.length
            ? game.players[mySeatIndex].hole!
            : ['--', '--']
          ).map((card, idx) => (
            <span key={`my-${idx}`} className="card-pill">
              <CardFace card={card} />
            </span>
          ))}
        </div>
        {myHandName && (
          <span className="hand-rank-badge">{myHandName}</span>
        )}
        <div className="action-status">
          {canAct && <span className="your-turn-text">{t('yourTurn')}</span>}
          {!canAct && game?.status === 'in_progress' && (
            <span className="muted-light">
              {currentPlayer ? displayName(game.current_player) : '?'}...
            </span>
          )}
        </div>
      </div>

      {canAct && (
        <div className="bet-control-row">
          <div className="bet-presets">
            {betPresets.map((p) => (
              <button key={p.label} className="preset-btn" onClick={() => setActionAmount(p.amount)}>
                {p.label}
              </button>
            ))}
          </div>
          <div className="bet-slider-wrap">
            <input
              type="range"
              className="bet-slider"
              min={minRaise}
              max={Math.max(maxBet, minRaise)}
              value={actionAmount}
              onChange={(e) => setActionAmount(Number(e.target.value))}
            />
            <span className="bet-slider-value">{fmt(actionAmount)}</span>
          </div>
        </div>
      )}

      {canAct && (
        <div className="action-buttons">
          <button
            className="fold-btn"
            onClick={() => onSendAction('fold', 0)}
            disabled={!canAct || loading}
          >
            {t('fold')}
          </button>
          <button
            className="call-btn"
            onClick={() => onSendAction(toCall === 0 ? 'check' : 'call', 0)}
            disabled={!canAct || loading}
          >
            {toCall === 0 ? t('check') : `${t('call')} ${fmt(toCall)}`}
          </button>
          <button
            className="raise-btn"
            onClick={() => onSendAction(toCall === 0 ? 'bet' : 'raise', actionAmount)}
            disabled={!canAct || loading || actionAmount < minRaise}
          >
            {toCall === 0
              ? `${t('betLabel')} ${fmt(actionAmount)}`
              : `${t('raiseLabel')} ${fmt(actionAmount)}`}
          </button>
          <button
            className="allin-btn"
            onClick={() => onSendAction('all_in', 0)}
            disabled={!canAct || loading}
          >
            {t('allInBtn')}
          </button>
        </div>
      )}
      </div>{/* end action-panel-controls */}
    </div>
  )
}
