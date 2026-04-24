import { useEffect, useRef, useState } from 'react'
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
  const prevHoleRef = useRef<Record<number, number>>({})
  const prevCommLenRef = useRef(0)
  const prevContribRef = useRef<Record<number, number>>({})
  const [dealingSeats, setDealingSeats] = useState<Set<number>>(new Set())
  const [flippingIndices, setFlippingIndices] = useState<Set<number>>(new Set())
  const [sdStep, setSdStep] = useState(0)
  const [newChipSeats, setNewChipSeats] = useState<Set<number>>(new Set())
  const seatedIndices = new Set(roster.map((m) => m.seatIndex))
  const isWaiting = game.status === 'finished' && game.pot_total === 0 && (game.community ?? []).length === 0
  const communitySlots = Array.from({ length: 5 }, (_, i) => game.community?.[i] ?? null)

  useEffect(() => {
    const newDealing = new Set<number>()
    game.players.forEach((p, i) => {
      const prev = prevHoleRef.current[i] ?? 0
      if (prev === 0 && (p.hole?.length ?? 0) > 0) {
        newDealing.add(i)
      }
      prevHoleRef.current[i] = p.hole?.length ?? 0
    })

    if (newDealing.size > 0) {
      setDealingSeats(newDealing)
      const id = setTimeout(() => setDealingSeats(new Set()), 600)
      return () => clearTimeout(id)
    }
  }, [game.players])

  useEffect(() => {
    const current = (game.community ?? []).filter((c) => c && c !== '--').length
    if (current > prevCommLenRef.current) {
      const newIdx = new Set<number>()
      for (let i = prevCommLenRef.current; i < current; i += 1) {
        newIdx.add(i)
      }
      setFlippingIndices(newIdx)
      const id = setTimeout(() => setFlippingIndices(new Set()), 500)
      prevCommLenRef.current = current
      return () => clearTimeout(id)
    }
    prevCommLenRef.current = current
  }, [game.community])

  useEffect(() => {
    if (!isShowdown) {
      setSdStep(0)
      return
    }
    const t1 = setTimeout(() => setSdStep(1), 800)
    const t2 = setTimeout(() => setSdStep(2), 1600)
    const t3 = setTimeout(() => setSdStep(3), 2800)
    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
      clearTimeout(t3)
    }
  }, [isShowdown])

  useEffect(() => {
    const newChips = new Set<number>()
    game.players.forEach((p, i) => {
      const prev = prevContribRef.current[i] ?? 0
      if (p.contributed > prev) {
        newChips.add(i)
      }
      prevContribRef.current[i] = p.contributed
    })
    if (newChips.size > 0) {
      setNewChipSeats(newChips)
      const id = setTimeout(() => setNewChipSeats(new Set()), 400)
      return () => clearTimeout(id)
    }
  }, [game.players])

  return (
    <div className="table-area" onClick={onCloseSession}>
      <div className="poker-felt">
        {/* ---- 常時表示: ポット + コミュニティカード (waiting 時を除く) ---- */}
        {isWaiting ? (
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
            {!isShowdown && <div className="pot-display">POT {game.pot_total ?? 0}</div>}
            <div className="community-cards-row">
              {communitySlots.map((card, idx) => (
                <span
                  key={`cc-wrap-${idx}`}
                  className={flippingIndices.has(idx) ? 'card-flip-shell flipping' : 'card-flip-shell'}
                >
                  {card && card !== '--'
                    ? <Card card={card} variant="front" size="md" />
                    : <Card variant="slot" size="md" />}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* ---- ショーダウン結果オーバーレイ (コミュニティカードの上に重ねる) ---- */}
        {showdown && isShowdown && (
          <div className={`showdown-overlay ${sdStep >= 3 ? 'sd-visible' : ''}`}>
            <div className="showdown-winner">🏆 {winnerNames}</div>
            {showdown.best_hand?.rank && (
              <div className="showdown-hand">
                {t(showdown.best_hand.rank as Parameters<typeof t>[0]) ?? showdown.best_hand.rank}
              </div>
            )}
            <div className="showdown-payouts">
              {payoutLines.map((l) => `${l.name} +${l.amount}`).join('  ·  ')}
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
                dealingSeats.has(idx) ? 'dealing' : '',
                isShowdown ? 'sd-active' : '',
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
                className={[
                  betChipClass(player.contributed),
                  newChipSeats.has(idx) ? 'chip-new' : '',
                ].filter(Boolean).join(' ')}
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
