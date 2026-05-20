import { useEffect, useState } from 'react'
import type { BetPreset, GameState, Player, RoomMember, Showdown, PayoutLine, Table } from '../../types'
import { TopBar } from './TopBar'
import { TableArea } from './TableArea'
import { ActionPanel } from './ActionPanel'
import { ActionLogDialog } from './ActionLog'
import { OfflineGameScreen } from './OfflineGameScreen'

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
  doStraddle: boolean
  onToggleStraddle: (v: boolean) => void
  table?: Table | null
  onOfflineWin?: (win: boolean) => void
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
  onSendAction, doStraddle, onToggleStraddle,
  table, onOfflineWin,
}: Props) {
  const [stackMode, setStackMode] = useState<'chips' | 'bb'>(() => {
    return (localStorage.getItem(STACK_MODE_KEY) as 'chips' | 'bb') ?? 'chips'
  })
  const [logOpen, setLogOpen] = useState(false)

  useEffect(() => {
    localStorage.setItem(STACK_MODE_KEY, stackMode)
  }, [stackMode])

  const toggleStackMode = () => setStackMode((m) => m === 'chips' ? 'bb' : 'chips')

  // オフラインモードは専用画面を使用
  if (table?.game_type === 'offline') {
    return (
      <OfflineGameScreen
        game={game}
        mySeat={mySeat}
        mySeatIndex={mySeatIndex}
        table={table}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        loading={loading}
        error={error}
        inviteCopied={inviteCopied}
        leavePending={leavePending}
        toCall={toCall}
        minRaise={minRaise}
        maxBet={maxBet}
        betPresets={betPresets}
        actionAmount={actionAmount}
        setActionAmount={setActionAmount}
        canAct={canAct}
        winnerNames={winnerNames}
        displayName={displayName}
        onCopyInvite={onCopyInvite}
        onOpenMyPage={onOpenMyPage}
        onLeaveRoom={onLeaveRoom}
        onSendAction={onSendAction}
        onOfflineWin={onOfflineWin ?? (() => {})}
        doStraddle={doStraddle}
        onToggleStraddle={onToggleStraddle}
      />
    )
  }

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
        onOpenActionLog={() => setLogOpen(true)}
        inviteCode={table?.game_type === 'offline' ? (table.invite_code ?? null) : null}
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
        mySeatIndex={mySeat}
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
        stackMode={stackMode}
        displayName={displayName}
        onSendAction={onSendAction}
        doStraddle={doStraddle}
        onToggleStraddle={onToggleStraddle}
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

      <ActionLogDialog
        game={game}
        displayName={displayName}
        open={logOpen}
        onClose={() => setLogOpen(false)}
      />
    </div>
  )
}
