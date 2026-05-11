import { useEffect, useRef, useState } from 'react'
import type { GameState, PayoutLine, Player, RoomMember, Showdown } from '../../types'
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
  stackMode: 'chips' | 'bb'
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
  winnerNames, payoutLines, stackMode,
  displayName, onCloseSession,
}: Props) {
  const formatStack = (stack: number) => {
    if (stackMode === 'bb') {
      const bb = game.big_blind
      return bb > 0 ? `${Math.round((stack / bb) * 10) / 10}BB` : `${stack}`
    }
    return `${stack}`
  }
  const prevHoleRef = useRef<Record<number, number>>({})
  const prevCommLenRef = useRef(0)
  const prevContribRef = useRef<Record<number, number>>({})
  const [dealingSeats, setDealingSeats] = useState<Set<number>>(new Set())
  const [flippingIndices, setFlippingIndices] = useState<Set<number>>(new Set())
  const [sdStep, setSdStep] = useState(0)
  const [newChipSeats, setNewChipSeats] = useState<Set<number>>(new Set())
  const [actionBubbles, setActionBubbles] = useState<Map<number, string>>(new Map())
  const prevCurrentPlayerRef = useRef<number>(-1)
  const prevPlayersRef = useRef<Player[]>([])
  const prevCurrentBetRef = useRef<number>(0)
  const prevStreetRef = useRef<string>('')
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
      const activateId = window.setTimeout(() => setDealingSeats(newDealing), 0)
      const clearId = window.setTimeout(() => setDealingSeats(new Set()), 600)
      return () => {
        window.clearTimeout(activateId)
        window.clearTimeout(clearId)
      }
    }
  }, [game.players])

  useEffect(() => {
    const current = (game.community ?? []).filter((c) => c && c !== '--').length
    if (current > prevCommLenRef.current) {
      const newIdx = new Set<number>()
      for (let i = prevCommLenRef.current; i < current; i += 1) {
        newIdx.add(i)
      }
      prevCommLenRef.current = current
      const activateId = window.setTimeout(() => setFlippingIndices(newIdx), 0)
      const clearId = window.setTimeout(() => setFlippingIndices(new Set()), 500)
      return () => {
        window.clearTimeout(activateId)
        window.clearTimeout(clearId)
      }
    }
    prevCommLenRef.current = current
  }, [game.community])

  useEffect(() => {
    if (!isShowdown) {
      const resetId = window.setTimeout(() => setSdStep(0), 0)
      return () => window.clearTimeout(resetId)
    }
    const resetId = window.setTimeout(() => setSdStep(0), 0)
    const t1 = window.setTimeout(() => setSdStep(1), 800)
    const t2 = window.setTimeout(() => setSdStep(2), 1600)
    const t3 = window.setTimeout(() => setSdStep(3), 2800)
    return () => {
      window.clearTimeout(resetId)
      window.clearTimeout(t1)
      window.clearTimeout(t2)
      window.clearTimeout(t3)
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
      const activateId = window.setTimeout(() => setNewChipSeats(newChips), 0)
      const clearId = window.setTimeout(() => setNewChipSeats(new Set()), 400)
      return () => {
        window.clearTimeout(activateId)
        window.clearTimeout(clearId)
      }
    }
  }, [game.players])

  useEffect(() => {
    const prevPlayer = prevCurrentPlayerRef.current
    const prevPlayers = prevPlayersRef.current
    const prevBet = prevCurrentBetRef.current
    const prevStreet = prevStreetRef.current

    prevCurrentPlayerRef.current = game.current_player
    prevPlayersRef.current = game.players
    prevCurrentBetRef.current = game.current_bet
    prevStreetRef.current = game.street

    if (prevPlayer < 0 || prevStreet !== game.street || prevPlayer === game.current_player) return

    const actingNow = game.players[prevPlayer]
    const actingBefore = prevPlayers[prevPlayer]
    if (!actingNow || !actingBefore) return

    let label = ''
    if (actingNow.folded && !actingBefore.folded) {
      label = t('fold')
    } else if (actingNow.all_in && actingNow.contributed > actingBefore.contributed) {
      label = `${t('allIn')} ¥${actingNow.contributed}`
    } else if (actingNow.contributed > actingBefore.contributed) {
      const delta = actingNow.contributed - actingBefore.contributed
      if (game.current_bet > prevBet) {
        label = prevBet === 0 ? `${t('betLabel')} ¥${actingNow.contributed}` : `${t('raiseLabel')} ¥${actingNow.contributed}`
      } else {
        label = `${t('call')} ¥${delta}`
      }
    } else {
      label = t('check')
    }

    if (!label) return

    const seatIdx = prevPlayer
    setActionBubbles((prev) => new Map(prev).set(seatIdx, label))
    const timerId = window.setTimeout(() => {
      setActionBubbles((prev) => {
        const next = new Map(prev)
        next.delete(seatIdx)
        return next
      })
    }, 3000)
    return () => window.clearTimeout(timerId)
  }, [game.current_player, game.street, game.players, game.current_bet])

  return (
    <div className="table-area" onClick={onCloseSession}>
      <div className="blind-info">
        SB: {Math.floor(game.big_blind / 2)} / BB: {game.big_blind}
      </div>
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
            {!isShowdown && <div className="pot-display">POT {game.pot_total - game.players.reduce((s, p) => s + p.contributed, 0)}</div>}
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

        {/* ---- プレイヤーシート (フェルト基準で配置) ---- */}
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

          const bubble = actionBubbles.get(idx)

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
                {bubble && <div className="action-bubble">{bubble}</div>}
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
                        Stack <strong>{formatStack(player.stack)}</strong>
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
    </div>
  )
}
