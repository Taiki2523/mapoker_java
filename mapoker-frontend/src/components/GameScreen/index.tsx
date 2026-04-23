import { useState } from 'react'
import type { AuthUser, BetPreset, GameState, PayoutLine, Player, RoomMember, Showdown } from '../../types'
import { TopBar } from './TopBar'
import { TableArea } from './TableArea'
import { ActionPanel } from './ActionPanel'

type Props = {
  game: GameState
  showdown: Showdown | null
  isShowdown: boolean
  currentUser: AuthUser | null
  myName: string
  mySeat: number | null
  mySeatIndex: number | null
  loginSeatIndex: number
  setLoginSeatIndex: (n: number) => void
  isSpectator: boolean
  roster: RoomMember[]
  autoRefresh: boolean
  setAutoRefresh: (v: boolean) => void
  actionAmount: number
  setActionAmount: (n: number) => void
  loading: boolean
  error: string
  inviteCopied: boolean
  loginError: string
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
  onLoginAsPlayer: () => void
  onLeaveRoom: () => void
  onLogout: () => void
  onSendAction: (type: string, amount: number) => void
}

export function GameScreen({
  game, showdown, isShowdown,
  currentUser, myName, mySeat, mySeatIndex,
  loginSeatIndex, setLoginSeatIndex,
  isSpectator, roster, autoRefresh, setAutoRefresh,
  actionAmount, setActionAmount, loading, error,
  inviteCopied, loginError, leavePending,
  toCall, minRaise, maxBet, betPresets,
  myHandName, currentPlayer, canAct,
  winnerNames, payoutLines, displayName,
  onCopyInvite, onOpenMyPage, onLoginAsPlayer, onLeaveRoom, onLogout,
  onSendAction,
}: Props) {
  const [showSession, setShowSession] = useState(false)

  return (
    <div className="game-layout">
      <TopBar
        game={game}
        currentUser={currentUser}
        myName={myName}
        mySeatIndex={mySeatIndex}
        loginSeatIndex={loginSeatIndex}
        setLoginSeatIndex={setLoginSeatIndex}
        loading={loading}
        error={error}
        leavePending={leavePending}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        inviteCopied={inviteCopied}
        onCopyInvite={onCopyInvite}
        onOpenMyPage={onOpenMyPage}
        onLoginAsPlayer={onLoginAsPlayer}
        onLeaveRoom={onLeaveRoom}
        onLogout={onLogout}
        loginError={loginError}
        showSession={showSession}
        onToggleSession={() => setShowSession((v) => !v)}
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
        displayName={displayName}
        onCloseSession={() => showSession && setShowSession(false)}
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
    </div>
  )
}
