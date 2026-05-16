import { useEffect, useMemo, useRef, useState } from 'react'
import type { GameState, PayoutLine, Player, RoomMember, Showdown } from '../../types'
import { Card } from '../Card'
import { seatPosition } from '../../utils'
import { t } from '../../i18n'

const STREET_REVEAL_INTERVAL_MS = 1500 // ストリート間の公開間隔（ms）
const FLIP_ANIM_MS = 500              // CSS mp-card-flip の duration と同値

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
    return `¥${stack}`
  }
  // マウント時点のコミュニティ枚数（ページ読み込み時に途中ゲームなら即表示、新規なら0）
  // useState / useRef は初回レンダーの値のみ使用するため、毎レンダー計算しても問題なし
  const _initCommCount = (game?.community ?? []).filter((c) => c && c !== '--').length

  const prevHoleRef = useRef<Record<number, number>>({})
  // StrictMode 対応: cleanup で元の値に戻すため prevCommLenRef をマウント時の枚数で初期化
  const prevCommLenRef = useRef(_initCommCount)
  // ショーダウン開始時点（ペイアウト前）のスタックを凍結し、result 表示まで旧値を見せる
  const frozenStacksRef = useRef<number[] | null>(null)
  const prevContribRef = useRef<Record<number, number>>({})
  const cardAnimEndsAtRef = useRef(0)
  const [dealingSeats, setDealingSeats] = useState<Set<number>>(new Set())
  // 公開済みコミュニティカード枚数（これ未満のインデックスだけ表示）
  // マウント時は現在の枚数で初期化（ページ再読み込み時にカードが消えない）
  const [revealedCount, setRevealedCount] = useState(_initCommCount)
  const [flippingIndices, setFlippingIndices] = useState<Set<number>>(new Set())
  const [sdStep, setSdStep] = useState(0)
  const [newChipSeats, setNewChipSeats] = useState<Set<number>>(new Set())
  const [actionBubbles, setActionBubbles] = useState<Map<number, string>>(new Map())
  const prevCurrentPlayerRef = useRef<number>(-1)
  const prevPlayersRef = useRef<Player[]>([])
  const prevCurrentBetRef = useRef<number>(0)
  const prevStreetRef = useRef<string>('')

  // 枚数のみで変化検知（同じ枚数で参照が変わっても effect を再実行しない）
  const communityCount = useMemo(
    () => (game.community ?? []).filter((c) => c && c !== '--').length,
    [game.community]
  )
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

  // コミュニティカードの公開順序制御
  //
  // prevCommLenRef は effect 本体ではなく各タイマーコールバック内で更新する。
  // こうすることで cleanup がタイマーをキャンセルしても ref が巻き戻らず、
  // 次の effect が正しい prev を参照できる（StrictMode でも production でも動作する）。
  useEffect(() => {
    const current = communityCount
    const prev = prevCommLenRef.current

    if (current <= prev) {
      prevCommLenRef.current = current
      if (current === 0) window.setTimeout(() => setRevealedCount(0), 0)
      return
    }

    const timers: number[] = []
    let delay = 0

    // フロップ（インデックス 0-2）
    if (prev < 3 && current >= 3) {
      const revealTo = Math.min(current, 3)
      const flopSet = new Set(Array.from({ length: revealTo - prev }, (_, i) => prev + i))
      timers.push(window.setTimeout(() => {
        prevCommLenRef.current = Math.max(prevCommLenRef.current, revealTo)
        setRevealedCount(revealTo)
        setFlippingIndices(flopSet)
      }, delay))
      timers.push(window.setTimeout(() => setFlippingIndices(new Set()), delay + FLIP_ANIM_MS))
      delay += STREET_REVEAL_INTERVAL_MS
    }

    // ターン（インデックス 3）
    if (prev < 4 && current >= 4) {
      timers.push(window.setTimeout(() => {
        prevCommLenRef.current = Math.max(prevCommLenRef.current, 4)
        setRevealedCount(4)
        setFlippingIndices(new Set([3]))
      }, delay))
      timers.push(window.setTimeout(() => setFlippingIndices(new Set()), delay + FLIP_ANIM_MS))
      delay += STREET_REVEAL_INTERVAL_MS
    }

    // リバー（インデックス 4）
    if (prev < 5 && current >= 5) {
      timers.push(window.setTimeout(() => {
        prevCommLenRef.current = Math.max(prevCommLenRef.current, 5)
        setRevealedCount(5)
        setFlippingIndices(new Set([4]))
      }, delay))
      timers.push(window.setTimeout(() => setFlippingIndices(new Set()), delay + FLIP_ANIM_MS))
      delay += STREET_REVEAL_INTERVAL_MS
    }

    cardAnimEndsAtRef.current = Date.now() + delay

    return () => {
      cardAnimEndsAtRef.current = 0
      setFlippingIndices(new Set())
      timers.forEach(window.clearTimeout)
    }
  }, [communityCount])

  // ショーダウン開始時点のスタックを凍結（ペイアウト前の値を保持）
  useEffect(() => {
    if (isShowdown) {
      if (frozenStacksRef.current === null) {
        frozenStacksRef.current = game.players.map((p) => p.stack)
      }
    } else {
      frozenStacksRef.current = null
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isShowdown])

  // ショーダウン結果のオーバーレイ表示（カードアニメーション完了後に開始）
  // setSdStep は setTimeout 経由で呼ぶ（react-hooks/set-state-in-effect 対策）
  useEffect(() => {
    if (!isShowdown) return
    const cardWait = Math.max(0, cardAnimEndsAtRef.current - Date.now())
    const t0 = window.setTimeout(() => setSdStep(0), 0)
    const t1 = window.setTimeout(() => setSdStep(1), cardWait)
    const t2 = window.setTimeout(() => setSdStep(2), cardWait + 800)
    const t3 = window.setTimeout(() => setSdStep(3), cardWait + 1600)
    return () => {
      window.clearTimeout(t0)
      window.clearTimeout(t1)
      window.clearTimeout(t2)
      window.clearTimeout(t3)
      setSdStep(0)
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

    if (prevPlayer < 0) return
    if (prevPlayer === game.current_player && prevStreet === game.street) return

    const actingNow = game.players[prevPlayer]
    const actingBefore = prevPlayers[prevPlayer]
    if (!actingNow || !actingBefore) return

    const streetChanged = prevStreet !== game.street
    let label = ''

    if (actingNow.folded && !actingBefore.folded) {
      label = t('fold')
    } else if (actingNow.all_in && !actingBefore.all_in) {
      // 同一ストリート: contributed 増加あり → 金額付き、ストリートまたぎ: リセット済みなので金額なし
      label = actingNow.contributed > actingBefore.contributed
        ? `${t('allIn')} ${formatStack(actingNow.contributed)}`
        : t('allIn')
    } else if (actingNow.contributed > actingBefore.contributed) {
      // ストリートまたぎ時は contributed=0 にリセットされるため、このブランチは同一ストリートのみ到達
      const delta = actingNow.contributed - actingBefore.contributed
      label = game.current_bet > prevBet
        ? (prevBet === 0 ? `${t('betLabel')} ${formatStack(actingNow.contributed)}` : `${t('raiseLabel')} ${formatStack(actingNow.contributed)}`)
        : `${t('call')} ${formatStack(delta)}`
    } else if (streetChanged && prevBet > 0) {
      // ストリートまたぎのコール（contributed リセット済み → prevBet で推定）
      // ※ streetChanged なしだと BB オプションチェック（contributed 変化なし、prevBet>0）と区別できない
      label = `${t('call')} ${formatStack(prevBet)}`
    } else {
      label = t('check')
    }

    if (!label) return

    const seatIdx = prevPlayer
    const showId = window.setTimeout(() => {
      setActionBubbles((prev) => new Map(prev).set(seatIdx, label))
    }, 1000)
    const clearId = window.setTimeout(() => {
      setActionBubbles((prev) => {
        const next = new Map(prev)
        next.delete(seatIdx)
        return next
      })
    }, 4000)
    return () => {
      window.clearTimeout(showId)
      window.clearTimeout(clearId)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
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
            {!isShowdown && <div className="pot-display">POT {formatStack(game.pot_total - game.players.reduce((s, p) => s + p.contributed, 0))}</div>}
            <div className="community-cards-row">
              {communitySlots.map((card, idx) => (
                <span
                  key={`cc-wrap-${idx}`}
                  className={flippingIndices.has(idx) ? 'card-flip-shell flipping' : 'card-flip-shell'}
                >
                  {card && card !== '--' && idx < revealedCount
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
          // カード公開が完了するまでネタバレしない（sdStep >= 3 = result表示タイミング）
          const showResult = sdStep >= 3
          const isWinnerSeat = showResult && (showdown?.winners?.includes(idx) ?? false)
          const isLoserSeat = showResult && !(showdown?.winners?.includes(idx) ?? false) && !player.folded
          // 全員オールイン（アクション不要）の場合のみランアウト中にカードを公開する
          const allRemainingAreAllIn = game.players
            .filter(p => !p.folded)
            .every(p => p.all_in)
          const showCards = mySeat === idx || isSpectator || (showResult && !player.folded)
            || (game.status === 'in_progress' && !player.folded && player.all_in && allRemainingAreAllIn)
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
                  isWinnerSeat ? 'winner' : '',
                  isMe ? 'me' : '',
                  dealingSeats.has(idx) ? 'dealing' : '',
                  showResult ? 'sd-active' : '',
                ].filter(Boolean).join(' ')}
                style={{ left: `${pos.x}%`, top: `${pos.y}%` }}
              >
                {bubble && <div className="action-bubble">{bubble}</div>}
                <div className="player-box">
                  <div className="seat-header">
                    {(() => {
                      const member = roster.find((m) => m.seatIndex === idx)
                      const avatarUrl = member?.avatarUrl
                      const fullName = member?.displayName || displayName(idx)
                      const shortName = member?.name || displayName(idx)
                      return (
                        <div className="seat-avatar-wrap" title={fullName}>
                          <div className="seat-avatar">
                            {avatarUrl
                              ? <img src={avatarUrl} alt={shortName} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: '50%' }} />
                              : shortName.slice(0, 1).toUpperCase()
                            }
                          </div>
                          <div className="player-tooltip">
                            {avatarUrl && <img src={avatarUrl} alt={shortName} style={{ width: 40, height: 40, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />}
                            <div>
                              <div style={{ fontWeight: 700, fontSize: '0.85rem' }}>{fullName}</div>
                              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Stack {formatStack(player.stack)}</div>
                            </div>
                          </div>
                        </div>
                      )
                    })()}
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
                        Stack <strong>{formatStack(
                          showResult || frozenStacksRef.current === null
                            ? player.stack
                            : (frozenStacksRef.current[idx] ?? player.stack)
                        )}</strong>
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
                  {formatStack(player.contributed)}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
