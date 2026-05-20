import { useEffect, useRef, useState } from 'react'
import type { BuyInContext, GameState, Table, WalletSummary } from '../types'

type Callbacks = {
  game: GameState | null
  mySeat: number | null
  myName: string
  gameId: string
  table: Table | null
  wallet: WalletSummary | null
  cardRevealEndsAtRef: React.RefObject<number>
  onRebuyConfirm: (amount: number) => void
  onRebuyCancel: () => void
}

export function useBuyInFlow(cb: Callbacks) {
  const [buyInContext, setBuyInContext] = useState<BuyInContext | null>(null)
  const rebuyShownForHandRef = useRef(false)

  const myCurrentStack =
    cb.mySeat !== null ? (cb.game?.players?.[cb.mySeat]?.stack ?? null) : null

  // 新ハンド開始でフラグリセット
  useEffect(() => {
    if (cb.game?.status === 'in_progress') {
      rebuyShownForHandRef.current = false
    }
  }, [cb.game?.status])

  // スタック 0 で手が終わったら自動リバイポップアップ
  useEffect(() => {
    if (cb.game?.status !== 'finished') return
    if (myCurrentStack !== 0) return
    if (cb.mySeat === null || !cb.myName.trim()) return
    if (rebuyShownForHandRef.current) return

    rebuyShownForHandRef.current = true

    const minBuyIn = cb.table?.min_buy_in ?? (cb.game.big_blind ?? 10) * 10
    const maxBuyIn = cb.table?.max_buy_in ?? (cb.game.big_blind ?? 10) * 100
    const walletCap = cb.wallet?.chip_balance ?? Infinity

    const waitMs = Math.max(500, (cb.cardRevealEndsAtRef.current ?? 0) - Date.now())
    const timer = window.setTimeout(() => {
      setBuyInContext({
        tableId: cb.gameId,
        tableName: cb.table?.name ?? 'Table',
        minBuyIn,
        maxBuyIn: Math.min(maxBuyIn, walletCap),
        bigBlind: cb.game?.big_blind ?? 10,
        onConfirm: (amount) => {
          setBuyInContext(null)
          cb.onRebuyConfirm(amount)
        },
        onCancel: () => {
          setBuyInContext(null)
          cb.onRebuyCancel()
        },
      })
    }, waitMs)
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cb.game?.status, myCurrentStack, cb.mySeat, cb.myName])

  return { buyInContext, setBuyInContext, myCurrentStack }
}
