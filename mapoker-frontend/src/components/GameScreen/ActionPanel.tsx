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
  displayName: (idx: number) => string
  onSendAction: (type: string, amount: number) => void
}

export function ActionPanel({
  game, mySeatIndex, canAct, loading,
  toCall, minRaise, maxBet, betPresets, actionAmount, setActionAmount,
  myHandName, currentPlayer, displayName, onSendAction,
}: Props) {
  return (
    <div className="action-panel">
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
            <span className="bet-slider-value">{actionAmount}</span>
          </div>
        </div>
      )}

      <div className="action-buttons">
        <button
          className="ghost danger"
          onClick={() => onSendAction('fold', 0)}
          disabled={!canAct || loading}
        >
          {t('fold')}
        </button>
        <button
          className="primary"
          onClick={() => onSendAction(toCall === 0 ? 'check' : 'call', 0)}
          disabled={!canAct || loading}
        >
          {toCall === 0 ? t('check') : `${t('call')} ${toCall}`}
        </button>
        <button
          className="ghost"
          onClick={() => onSendAction(toCall === 0 ? 'bet' : 'raise', actionAmount)}
          disabled={!canAct || loading || actionAmount <= 0}
        >
          {toCall === 0
            ? `${t('betLabel')} ${actionAmount}`
            : `${t('raiseLabel')} ${actionAmount}`}
        </button>
        <button
          className="ghost"
          onClick={() => onSendAction('all_in', 0)}
          disabled={!canAct || loading}
        >
          {t('allInBtn')}
        </button>
      </div>
    </div>
  )
}
