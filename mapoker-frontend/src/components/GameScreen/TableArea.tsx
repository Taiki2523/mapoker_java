import type { GameState, PayoutLine, Showdown } from '../../types'
import { CardFace } from '../Card'
import { seatPosition } from '../../utils'
import { t } from '../../i18n'

type Props = {
  game: GameState
  showdown: Showdown | null
  isShowdown: boolean
  mySeat: number | null
  isSpectator: boolean
  smallBlindIndex: number | null
  bigBlindIndex: number | null
  winnerNames: string
  payoutLines: PayoutLine[]
  displayName: (idx: number) => string
  onCloseSession: () => void
}

export function TableArea({
  game, showdown, isShowdown, mySeat, isSpectator,
  smallBlindIndex, bigBlindIndex, winnerNames, payoutLines,
  displayName, onCloseSession,
}: Props) {
  return (
    <div className="table-area" onClick={onCloseSession}>
      <div className="poker-felt">
        {showdown && isShowdown ? (
          <div className="showdown-result">
            <div className="showdown-winner">🏆 {winnerNames}</div>
            {showdown.best_hand?.rank && (
              <div className="showdown-hand">
                {t(showdown.best_hand.rank as Parameters<typeof t>[0]) ?? showdown.best_hand.rank}
              </div>
            )}
            <div className="showdown-payouts">
              {payoutLines.map((l) => `${l.name} +${l.amount}`).join('  ·  ')}
            </div>
            {game.community?.some((c) => c !== '--') && (
              <div className="community-cards-row" style={{ marginTop: '0.4rem' }}>
                {game.community.map((card, idx) => (
                  <span key={`sd-${idx}`} className="card-pill">
                    <CardFace card={card} />
                  </span>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="felt-center">
            <div className="pot-display">POT {game.pot_total ?? 0}</div>
            <div className="community-cards-row">
              {(game.community?.length ? game.community : ['--', '--', '--', '--', '--']).map(
                (card, idx) => (
                  <span key={`cc-${idx}`} className="card-pill">
                    <CardFace card={card} />
                  </span>
                )
              )}
            </div>
          </div>
        )}
      </div>

      {game.players.map((player, idx) => {
        const n = game.players.length
        const anchorSeat = mySeat ?? 0
        const pos = seatPosition(idx, anchorSeat, n)
        const isActive = game.current_player === idx
        const isWinnerSeat = showdown?.winners?.includes(idx) ?? false
        const isLoserSeat = isShowdown && !isWinnerSeat
        const showCards = mySeat === idx || isSpectator || (isShowdown && !player.folded)
        const cards = showCards && player.hole?.length ? player.hole : ['--', '--']
        const isMe = mySeat === idx

        return (
          <div key={player.id}>
            <div
              className={[
                'player-seat',
                isActive ? 'active' : '',
                player.folded || isLoserSeat ? 'folded' : '',
                isWinnerSeat && isShowdown ? 'winner' : '',
                isMe ? 'me' : '',
              ].filter(Boolean).join(' ')}
              style={{ left: `${pos.x}%`, top: `${pos.y}%` }}
            >
              <div className="player-box">
                <div className="player-name-row">
                  <span className="player-name">{displayName(idx)}</span>
                  <div className="badges">
                    {game.button_index === idx && <span className="badge btn">D</span>}
                    {smallBlindIndex === idx && <span className="badge sb">SB</span>}
                    {bigBlindIndex === idx && <span className="badge bb">BB</span>}
                    {player.folded && <span className="badge warn">F</span>}
                    {player.all_in && <span className="badge accent">AI</span>}
                  </div>
                </div>
                <div className="player-stack">
                  Stack <strong>{player.stack}</strong>
                </div>
                <div className="player-hole-cards">
                  {cards.map((card, ci) => (
                    <span key={`${player.id}-${ci}`} className="card-mini">
                      <CardFace card={card} />
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {player.contributed > 0 && !player.folded && (
              <div
                className="bet-chip"
                style={{ left: `${pos.betX}%`, top: `${pos.betY}%` }}
              >
                {player.contributed}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
