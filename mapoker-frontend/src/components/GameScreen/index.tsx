import { useEffect, useState } from 'react'
import type { BetPreset, GameState, PayoutLine, Player, RoomMember, Showdown } from '../../types'
import { TopBar } from './TopBar'
import { TableArea } from './TableArea'
import { ActionPanel } from './ActionPanel'
import { t } from '../../i18n'

const STACK_MODE_KEY = 'mapoker_stack_mode'

type Props = {
  game: GameState
  showdown: Showdown | null
  isShowdown: boolean
  mySeat: number | null
  mySeatIndex: number | null
  isSpectator: boolean
  roster: RoomMember[]
  autoRefresh: boolean
  setAutoRefresh: (v: boolean) => void
  actionAmount: number
  setActionAmount: (n: number) => void
  loading: boolean
  error: string
  inviteCopied: boolean
  leavePending: boolean
  toCall: number
  minRaise: number
  maxBet: number
  betPresets: BetPreset[]
  myHandName: string | null
  currentPlayer: Player | null
  canAct: boolean
  winnerNames: string
  payoutLines: PayoutLine[]
  displayName: (idx: number) => string
  onCopyInvite: () => void
  onOpenMyPage: () => void
  onLeaveRoom: () => void
  onSendAction: (type: string, amount: number) => void
}

export function GameScreen({
  game, showdown, isShowdown,
  mySeat, mySeatIndex,
  isSpectator, roster, autoRefresh, setAutoRefresh,
  actionAmount, setActionAmount, loading, error,
  inviteCopied, leavePending,
  toCall, minRaise, maxBet, betPresets,
  myHandName, currentPlayer, canAct,
  winnerNames, payoutLines, displayName,
  onCopyInvite, onOpenMyPage, onLeaveRoom,
  onSendAction,
}: Props) {
  const [stackMode, setStackMode] = useState<'chips' | 'bb'>(() => {
    return (localStorage.getItem(STACK_MODE_KEY) as 'chips' | 'bb') ?? 'chips'
  })

  useEffect(() => {
    localStorage.setItem(STACK_MODE_KEY, stackMode)
  }, [stackMode])

  const toggleStackMode = () => setStackMode((m) => m === 'chips' ? 'bb' : 'chips')

  return (
    <div className="game-layout">
      <TopBar
        game={game}
        mySeatIndex={mySeatIndex}
        canAct={canAct}
        displayName={displayName}
        error={error}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        inviteCopied={inviteCopied}
        stackMode={stackMode}
        onToggleStackMode={toggleStackMode}
        onCopyInvite={onCopyInvite}
        onOpenMyPage={onOpenMyPage}
      />
      <TableArea
        game={game}
        showdown={showdown}
        isShowdown={isShowdown}
        mySeat={mySeat}
        isSpectator={isSpectator}
        roster={roster}
        winnerNames={winnerNames}
        payoutLines={payoutLines}
        stackMode={stackMode}
        displayName={displayName}
        onCloseSession={() => {}}
      />
      <ActionPanel
        game={game}
        mySeatIndex={mySeatIndex}
        canAct={canAct}
        loading={loading}
        toCall={toCall}
        minRaise={minRaise}
        maxBet={maxBet}
        betPresets={betPresets}
        actionAmount={actionAmount}
        setActionAmount={setActionAmount}
        myHandName={myHandName}
        currentPlayer={currentPlayer}
        displayName={displayName}
        onSendAction={onSendAction}
      />

      {/* 退席ボタン・退席予約メッセージ — 右下固定 */}
      <div className="leave-area">
        {leavePending && (
          <span className="leave-pending-msg">{t('leavePending')}</span>
        )}
        <button className="leave-btn" onClick={onLeaveRoom} disabled={loading}>
          {t('leave')}
        </button>
      </div>
    </div>
  )
}
