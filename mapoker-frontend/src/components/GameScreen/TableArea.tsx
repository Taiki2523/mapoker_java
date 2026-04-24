import type { GameState, PayoutLine, RoomMember, Showdown } from '../../types'
import { Card } from '../Card'
import { seatPosition } from '../../utils'
import { t } from '../../i18n'

type Props = {
  game: GameState
  showdown: Showdown | null
  isShowdown: boolean
  mySeat: number | null
  isSpectator: boolean
  roster: RoomMember[]
  winnerNames: string
  payoutLines: PayoutLine[]
  displayName: (idx: number) => string
  onCloseSession: () => void
}

function betChipClass(amount: number): string {
  if (amount <= 50) return 'bet-chip bet-chip--white'
  if (amount <= 200) return 'bet-chip bet-chip--green'
  if (amount <= 1000) return 'bet-chip bet-chip--black'
  return 'bet-chip bet-chip--purple'
}

export function TableArea({
  game, showdown, isShowdown, mySeat, isSpectator, roster,
  winnerNames, payoutLines,
  displayName, onCloseSession,
}: Props) {
  const seatedIndices = new Set(roster.map((m) => m.seatIndex))
  const isWaiting = game.status === 'finished' && game.pot_total === 0 && (game.community ?? []).length === 0
  const communitySlots = Array.from({ length: 5 }, (_, i) => game.community?.[i] ?? null)

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
            {communitySlots.some((card) => card && card !== '--') && (
              <div className="community-cards-row" style={{ marginTop: '0.4rem' }}>
                {communitySlots.map((card, idx) => (
                  card && card !== '--'
                    ? <Card key={`sd-${idx}`} card={card} variant="front" size="md" />
                    : <Card key={`sd-${idx}`} variant="slot" size="md" />
                ))}
              </div>
            )}
          </div>
        ) : isWaiting ? (
          <div className="felt-center">
            <div className="waiting-status">
              <div className="waiting-members">
                {roster.length} / {game.players.length} {t('players')}
              </div>
              {!game.can_start_hand && (
                <div className="waiting-label">{t('waitingForPlayers')}</div>
              )}
            </div>
          </div>
        ) : (
          <div className="felt-center">
            <div className="pot-display">POT {game.pot_total ?? 0}</div>
            <div className="community-cards-row">
              {communitySlots.map((card, idx) => (
                card && card !== '--'
                  ? <Card key={`cc-${idx}`} card={card} variant="front" size="md" />
                  : <Card key={`cc-${idx}`} variant="slot" size="md" />
              ))}
            </div>
          </div>
        )}
      </div>

      {game.players.map((player, idx) => {
        if (!seatedIndices.has(idx)) return null

        const n = game.players.length
        const anchorSeat = mySeat ?? 0
        const pos = seatPosition(idx, anchorSeat, n)
        const isActive = game.current_player === idx
        const isWinnerSeat = showdown?.winners?.includes(idx) ?? false
        const isLoserSeat = isShowdown && !isWinnerSeat
        const showCards = mySeat === idx || isSpectator || (isShowdown && !player.folded)
        const cards = player.hole?.length ? player.hole : ['--', '--']
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
                <div className="seat-header">
                  <div className="seat-avatar">
                    {displayName(idx).slice(0, 1).toUpperCase()}
                  </div>
                  <div className="seat-info">
                    <div className="player-name-row">
                      <span className="player-name">{displayName(idx)}</span>
                      <div className="badges">
                        {game.button_index === idx && <span className="badge btn">D</span>}
                        {game.small_blind_idx === idx && <span className="badge sb">SB</span>}
                        {game.big_blind_idx === idx && <span className="badge bb">BB</span>}
                        {player.folded && <span className="badge warn">F</span>}
                        {player.all_in && <span className="badge accent">AI</span>}
                      </div>
                    </div>
                    <div className="player-stack">
                      Stack <strong>{player.stack}</strong>
                    </div>
                  </div>
                </div>
                <div className="player-hole-cards">
                  {cards.map((card, ci) => (
                    showCards
                      ? <Card key={`${player.id}-${ci}`} card={card} variant="front" size="sm" />
                      : <Card key={`${player.id}-${ci}`} variant="back" size="sm" />
                  ))}
                </div>
              </div>
            </div>

            {player.contributed > 0 && !player.folded && (
              <div
                className={betChipClass(player.contributed)}
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
