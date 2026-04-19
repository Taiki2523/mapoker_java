import { useState } from 'react'
import type { AuthUser, BetPreset, GameState, PayoutLine, Player, Showdown } from '../../types'
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
  isOwner: boolean
  isSpectator: boolean
  autoRefresh: boolean
  setAutoRefresh: (v: boolean) => void
  actionAmount: number
  setActionAmount: (n: number) => void
  loading: boolean
  error: string
  inviteCopied: boolean
  loginError: string
  smallBlindIndex: number | null
  bigBlindIndex: number | null
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
  onLoginAsPlayer: () => void
  onLeaveRoom: () => void
  onLogout: () => void
  onStartHand: () => void
  onRunShowdown: () => void
  onSendAction: (type: string, amount: number) => void
}

export function GameScreen({
  game, showdown, isShowdown,
  currentUser, myName, mySeat, mySeatIndex,
  loginSeatIndex, setLoginSeatIndex,
  isOwner, isSpectator, autoRefresh, setAutoRefresh,
  actionAmount, setActionAmount, loading, error,
  inviteCopied, loginError,
  smallBlindIndex, bigBlindIndex,
  toCall, minRaise, maxBet, betPresets,
  myHandName, currentPlayer, canAct,
  winnerNames, payoutLines, displayName,
  onCopyInvite, onLoginAsPlayer, onLeaveRoom, onLogout,
  onStartHand, onRunShowdown, onSendAction,
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
        isOwner={isOwner}
        loading={loading}
        error={error}
        autoRefresh={autoRefresh}
        setAutoRefresh={setAutoRefresh}
        inviteCopied={inviteCopied}
        onCopyInvite={onCopyInvite}
        onLoginAsPlayer={onLoginAsPlayer}
        onLeaveRoom={onLeaveRoom}
        onLogout={onLogout}
        onStartHand={onStartHand}
        onRunShowdown={onRunShowdown}
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
        smallBlindIndex={smallBlindIndex}
        bigBlindIndex={bigBlindIndex}
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
        isOwner={isOwner}
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
