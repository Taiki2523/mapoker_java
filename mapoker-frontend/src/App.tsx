import { useEffect, useMemo, useRef, useState } from 'react'
import { useMatch, useNavigate } from 'react-router-dom'
import './App.css'
import {
  apiLogout, createTable, fetchMe, fetchVersion, joinTable, leaveTable,
} from './api'
import { t } from './i18n'
import { bestHandName } from './handEval'
import { mapMembers } from './utils'
import type {
  AuthUser, BetPreset, CreateGameConfig, GameState, RoomMember, RoomMemberApi,
  Showdown, Table,
} from './types'
import { AuthScreen } from './components/AuthScreen'
import { BuyInPopup } from './components/BuyInPopup'
import { GameTypeScreen } from './components/GameTypeScreen'
import { LobbyScreen } from './components/LobbyScreen'
import { MyPagePanel } from './components/MyPagePanel'
import { RoomScreen } from './components/RoomScreen'
import { GameScreen } from './components/GameScreen'

import { useBuyInFlow } from './hooks/useBuyInFlow'
import { useGameActions } from './hooks/useGameActions'
import { useGameConnection } from './hooks/useGameConnection'
import { useLeaveRoom } from './hooks/useLeaveRoom'
import { useProfileData } from './hooks/useProfileData'
import { useTableSession } from './hooks/useTableSession'

const NEXT_HAND_DELAY_MS = 7000
const CARD_REVEAL_MS_PER_STREET = 1500

function App() {
  // ---- ルーティング ----
  const navigate = useNavigate()
  const match = useMatch('/table/:tableId')
  const gameId = match?.params.tableId ?? ''

  // gameId への遷移をラップ（URL が正規表現）
  const goToTable = (id: string) => navigate(`/table/${id}`, { replace: true })
  const goToLobby = () => navigate('/', { replace: true })

  // ---- グローバル state ----
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [appVersion, setAppVersion] = useState('')
  const [roomScreenMode, setRoomScreenMode] = useState<'gameType' | 'room' | 'lobby'>('gameType')
  const [showMyPage, setShowMyPage] = useState(false)

  // ---- ゲーム state ----
  const [game, setGame] = useState<GameState | null>(null)
  const [showdown, setShowdown] = useState<Showdown | null>(null)
  const [roster, setRoster] = useState<RoomMember[]>([])
  const [table, setTable] = useState<Table | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [inviteCopied, setInviteCopied] = useState(false)
  const [actionAmount, setActionAmount] = useState(0)
  const [doStraddle, setDoStraddle] = useState(false)

  // ---- refs ----
  const cardRevealEndsAtRef = useRef(0)
  const streetRevealedAtRef = useRef(0)
  const prevCommLenRef = useRef(0)
  const prevIsMyTurnRef = useRef(false)

  // ---- hooks ----
  const session = useTableSession(gameId, roster)
  const { myName, setMyName, mySeatIndex, setMySeatIndex, mySeat, persistSession, clearGameSession } = session

  const profile = useProfileData(showMyPage, !!currentUser, formatErrorMessage)
  const { wallet, walletLedger, profileTables, profileHistory, profileLoading, profileError, refreshProfileTables, handleClaimDailyBonus } = profile

  // ---- 派生値 ----
  const currentPlayer = useMemo(() => game ? game.players[game.current_player] : null, [game])
  const isSpectator = useMemo(() => {
    if (!game || mySeat === null) return false
    return game.players[mySeat]?.stack === 0
  }, [game, mySeat])

  const displayName = useMemo(() => (seatIndex: number) => {
    const member = roster.find((m) => m.seatIndex === seatIndex)
    return member?.displayName || member?.name || game?.players?.[seatIndex]?.id || t('seatN', { n: seatIndex + 1 })
  }, [game?.players, roster])

  const isShowdown = game?.status === 'showdown' || (game?.status === 'finished' && showdown !== null)
  const inviteUrl = gameId ? `${window.location.origin}/table/${gameId}` : ''

  const toCall = useMemo(() => {
    if (!game || !currentPlayer) return 0
    return Math.max(0, game.current_bet - currentPlayer.contributed)
  }, [game, currentPlayer])

  const minRaise = useMemo(() => {
    if (!game) return 0
    return game.current_bet === 0 ? game.big_blind : game.current_bet * 2
  }, [game])

  const maxBet = useMemo(() => {
    if (!game || mySeat === null) return 0
    return game.players[mySeat]?.stack ?? 0
  }, [game, mySeat])

  const betPresets = useMemo((): BetPreset[] => {
    if (!game) return []
    const cap = Math.max(maxBet, minRaise)
    const clamp = (v: number) => Math.min(cap, Math.max(minRaise, v))
    if (game.street === 'preflop') {
      return [
        { label: 'x2', amount: clamp(game.current_bet * 2) },
        { label: 'x3', amount: clamp(game.current_bet * 3) },
        { label: 'x4', amount: clamp(game.current_bet * 4) },
        { label: 'ALL', amount: maxBet },
      ].filter((p) => p.amount > 0 && p.amount <= maxBet)
    }
    const pot = game.pot_total
    return [
      { label: t('presetMin'), amount: minRaise },
      { label: '30%', amount: clamp(Math.round(pot * 0.3)) },
      { label: '50%', amount: clamp(Math.round(pot * 0.5)) },
      { label: '100%', amount: clamp(pot) },
      { label: '200%', amount: clamp(pot * 2) },
      { label: t('presetAll'), amount: maxBet },
    ].filter((p) => p.amount > 0 && p.amount <= maxBet)
  }, [game, minRaise, maxBet])

  const myHandName = useMemo(() => {
    if (mySeat === null || !game) return null
    const hole = (game.players[mySeat]?.hole ?? []).filter((c) => c && c !== '--')
    if (hole.length < 2) return null
    return bestHandName(hole, (game.community ?? []).filter((c) => c && c !== '--'))
  }, [game, mySeat])

  const isMyTurn = useMemo(() => {
    if (!game || mySeat === null) return false
    return game.current_player === mySeat
  }, [game, mySeat])

  const canAct = useMemo(
    () => isMyTurn && game?.status === 'in_progress' && !isSpectator,
    [game?.status, isMyTurn, isSpectator]
  )

  const winnerNames = useMemo(() => {
    if (!showdown) return ''
    return (showdown.winners ?? []).map((idx) => displayName(idx)).join(', ')
  }, [displayName, showdown])

  const payoutLines = useMemo(() => {
    if (!showdown || !game) return []
    return (showdown.payouts ?? [])
      .map((amount, idx) => ({ name: displayName(idx), amount }))
      .filter((l) => l.amount > 0)
  }, [displayName, game, showdown])

  const viewMode = useMemo(() => {
    if (!currentUser) return 'auth'
    if (!gameId || !game) return 'room'
    return 'game'
  }, [currentUser, game, gameId])

  // ---- game connection ----
  const connection = useGameConnection(gameId, {
    mySeat, mySeatIndex, setMySeatIndex, isSpectator,
    setGame, setShowdown, setRoster,
    cardRevealEndsAtRef, streetRevealedAtRef,
  })
  const { autoRefresh, setAutoRefresh, refreshGame, refreshMembers } = connection

  // ---- navigate to lobby ----
  const navigateToLobby = () => {
    clearGameSession()
    setGame(null)
    setMySeatIndex(null)
    setRoster([])
    setLeavePending_(false)
    setShowdown(null)
    setRoomScreenMode('lobby')
    goToLobby()
  }

  // ---- leave room ----
  const leaveHook = useLeaveRoom({
    gameId, myName, mySeatIndex, roster, setRoster, navigateToLobby,
  })
  const { leavePending, setLeavePending: setLeavePending_, leaveRoom } = leaveHook

  // ---- game actions ----
  const actions = useGameActions({
    gameId, mySeat, game, showdown,
    doStraddle, setDoStraddle,
    setGame: (g) => setGame(g),
    setShowdown,
    setLoading, setError, setActionAmount,
    prevIsMyTurnRef,
    refreshGame,
    runShowdown: async (opts) => actions.runShowdown(opts),
  })

  // ---- buy-in flow ----
  const buyIn = useBuyInFlow({
    game, mySeat, myName, gameId, table, wallet,
    cardRevealEndsAtRef,
    onRebuyConfirm: (amount) => {
      void doTableJoin(gameId, myName, amount)
        .then(() => refreshGame(gameId))
        .catch((err) => setError(formatErrorMessage(err)))
    },
    onRebuyCancel: () => void leaveRoom(),
  })
  const { buyInContext, setBuyInContext } = buyIn

  // ---- 初期化 ----
  useEffect(() => {
    void fetchMe()
      .then((user) => { setCurrentUser(user); setMyName(user.username) })
      .catch(() => {})
    void fetchVersion()
      .then((res) => setAppVersion(res.version))
      .catch(() => {})
  }, [])

  // URL に tableId があれば初回ロード
  useEffect(() => {
    if (!gameId) return
    void refreshGame(gameId)
    void refreshMembers(gameId)
    void refreshTable(gameId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])  // mount 時のみ

  // isMyTurn 変化で actionAmount をリセット
  useEffect(() => {
    if (isMyTurn && !prevIsMyTurnRef.current) setActionAmount(minRaise)
    prevIsMyTurnRef.current = isMyTurn
  }, [isMyTurn, minRaise])

  // コミュニティカード公開アニメーション終了時刻の追跡
  useEffect(() => {
    const current = (game?.community ?? []).filter((c) => c && c !== '--').length
    const prev = prevCommLenRef.current
    prevCommLenRef.current = current
    if (current <= prev) {
      if (current === 0) cardRevealEndsAtRef.current = 0
      return
    }
    let ms = 0
    if (prev < 3 && current >= 3) ms += CARD_REVEAL_MS_PER_STREET
    if (prev < 4 && current >= 4) ms += CARD_REVEAL_MS_PER_STREET
    if (prev < 5 && current >= 5) ms += CARD_REVEAL_MS_PER_STREET
    if (ms > 0) {
      const base = streetRevealedAtRef.current > 0 ? streetRevealedAtRef.current : Date.now()
      streetRevealedAtRef.current = 0
      cardRevealEndsAtRef.current = base + ms + 1600
    }
  }, [game?.community])

  // 自動ショーダウン
  useEffect(() => {
    if (!game) return
    if (game.status === 'showdown' && !showdown) {
      const delay = Math.max(0, cardRevealEndsAtRef.current - Date.now())
      if (delay > 0) {
        const timer = window.setTimeout(() => void actions.runShowdown({ suppressError: true }), delay)
        return () => window.clearTimeout(timer)
      }
      void actions.runShowdown({ suppressError: true })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game, showdown])

  // 自動ハンド開始
  useEffect(() => {
    const isAutoStartable = game?.status === 'finished' || !game?.status
    const activeRosterCount = roster.filter((m) => !m.pendingLeave).length
    if (!game || !isAutoStartable || !game.can_start_hand || activeRosterCount < 2) return
    const timer = window.setTimeout(
      () => void actions.startHand(undefined, undefined, { suppressError: true }),
      NEXT_HAND_DELAY_MS
    )
    return () => window.clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game?.status, game?.can_start_hand, gameId, roster])

  // ---- helpers ----
  function formatErrorMessage(err: unknown) {
    const message = err instanceof Error ? err.message : String(err)
    if (message === 'insufficient funds') return t('insufficientFunds')
    return message
  }

  const refreshTable = async (id = gameId) => {
    if (!id) return null
    try {
      const { fetchTable } = await import('./api')
      const data = await fetchTable(id)
      setTable(data)
      return data
    } catch {
      setTable(null)
      return null
    }
  }

  const doTableJoin = async (tableId: string, name: string, buyInAmount: number) => {
    const result = await joinTable(tableId, name, buyInAmount)
    const assignedSeatIndex = result.assigned_seat_index
    setMySeatIndex(assignedSeatIndex)
    setRoster(mapMembers(result.members as RoomMemberApi[]))
    persistSession(tableId, { name, seatIndex: assignedSeatIndex })
    return assignedSeatIndex
  }

  const handleAuthSuccess = (user: AuthUser) => {
    setCurrentUser(user)
    setMyName(user.username)
  }

  const handleLogout = async () => {
    if (gameId && myName.trim()) {
      try {
        await leaveTable(gameId, myName.trim(), mySeatIndex)
      } catch { /* ignore */ }
    }
    try { await apiLogout() } catch { /* ignore */ }
    setCurrentUser(null)
    setMyName('')
    clearGameSession()
    setGame(null)
    setTable(null)
    setMySeatIndex(null)
    setShowdown(null)
    setBuyInContext(null)
    setShowMyPage(false)
    setRoomScreenMode('gameType')
    profile.clear()
  }

  const openMyPage = () => {
    setShowMyPage(true)
    // showMyPage=true になると useProfileData が自動フェッチする
  }

  const copyInvite = async () => {
    if (!inviteUrl) return
    try {
      await navigator.clipboard.writeText(inviteUrl)
      setInviteCopied(true)
      window.setTimeout(() => setInviteCopied(false), 1500)
    } catch { setInviteCopied(false) }
  }

  const showBuyInPopup = (
    t_: Table, id: string,
    onConfirm: (amount: number) => void,
    onCancel: () => void
  ) => {
    const effectiveMax = wallet ? Math.min(t_.max_buy_in, wallet.chip_balance) : t_.max_buy_in
    setBuyInContext({
      tableId: id,
      tableName: t_.name,
      minBuyIn: t_.min_buy_in,
      maxBuyIn: effectiveMax,
      bigBlind: t_.stake.big_blind,
      onConfirm: (amount) => { setBuyInContext(null); onConfirm(amount) },
      onCancel: () => { setBuyInContext(null); onCancel() },
    })
  }

  const createGame = async (config: CreateGameConfig) => {
    setLoading(true)
    setError('')
    setShowdown(null)
    setRoomScreenMode('room')
    try {
      const payload = {
        table_name: config.tableName.trim() || 'Cash Orbit',
        player_count: config.playerCount,
        small_blind: config.smallBlind,
        big_blind: config.bigBlind,
        ante: config.ante,
        straddle_enabled: config.straddleEnabled,
        visibility: config.visibility,
        flags: config.flags,
      }
      const data = await createTable(payload)
      if (!data.game) throw new Error('table game payload is missing')
      const name = myName.trim() || 'Host'

      const doJoinAndNavigate = async (buyInAmount: number) => {
        setLoading(true)
        try {
          await doTableJoin(data.id, name, buyInAmount)
          goToTable(data.id)
          setGame(data.game ?? null)
          setTable(data)
          await refreshGame(data.id)
        } catch (err) {
          setError(formatErrorMessage(err))
        } finally {
          setLoading(false)
        }
      }

      if (data.min_buy_in > 0) {
        if (wallet && wallet.chip_balance < data.min_buy_in) {
          setError(`チップが不足しています（最低バイイン: ${data.min_buy_in.toLocaleString()}）`)
          setRoomScreenMode('lobby')
          return
        }
        showBuyInPopup(data, data.id,
          (amount) => void doJoinAndNavigate(amount),
          () => setRoomScreenMode('lobby')
        )
      } else {
        await doJoinAndNavigate(0)
      }
    } catch (err) {
      setError(formatErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const lobbyJoinWithBuyIn = async (tableId: string) => {
    setRoomScreenMode('room')
    setTable(null)
    setShowdown(null)
    goToTable(tableId)
    await refreshGame(tableId)
    await refreshMembers(tableId)
    const fetchedTable = await refreshTable(tableId)
    if (!myName.trim()) return

    const backToLobby = () => {
      setTable(null)
      setRoomScreenMode('lobby')
      goToLobby()
    }

    const doJoin = async (buyInAmount: number) => {
      try {
        await doTableJoin(tableId, myName.trim(), buyInAmount)
      } catch (err) {
        setError(formatErrorMessage(err))
        backToLobby()
      }
    }

    if (fetchedTable && fetchedTable.min_buy_in > 0) {
      if (wallet && wallet.chip_balance < fetchedTable.min_buy_in) {
        setError(`チップが不足しています（最低バイイン: ${fetchedTable.min_buy_in.toLocaleString()}）`)
        backToLobby()
        return
      }
      showBuyInPopup(fetchedTable, tableId,
        (amount) => void doJoin(amount),
        backToLobby
      )
    } else {
      await doJoin(0)
    }
  }

  // ---- render ----
  return (
    <div className="app">
      {viewMode !== 'game' && (
        <div className="lobby-screen">
          <div className="lobby-panel">
            {viewMode === 'auth' && (
              <AuthScreen onAuthSuccess={handleAuthSuccess} />
            )}
            {viewMode === 'room' && roomScreenMode === 'gameType' && (
              <GameTypeScreen
                currentUser={currentUser}
                onOpenMyPage={() => openMyPage()}
                onSelectRing={() => setRoomScreenMode('lobby')}
                appVersion={appVersion}
              />
            )}
            {viewMode === 'room' && roomScreenMode === 'room' && (
              <RoomScreen
                loading={loading}
                error={error}
                onCreateGame={createGame}
                currentUser={currentUser}
                onOpenMyPage={() => openMyPage()}
                onBack={() => setRoomScreenMode('lobby')}
                appVersion={appVersion}
              />
            )}
            {viewMode === 'room' && roomScreenMode === 'lobby' && (
              <LobbyScreen
                currentUser={currentUser}
                onOpenMyPage={() => openMyPage()}
                onJoinRoom={lobbyJoinWithBuyIn}
                onCreateTable={() => setRoomScreenMode('room')}
                onBack={() => setRoomScreenMode('gameType')}
                appVersion={appVersion}
              />
            )}
          </div>
        </div>
      )}
      {viewMode === 'game' && game && (
        <GameScreen
          game={game}
          showdown={showdown}
          isShowdown={isShowdown ?? false}
          mySeat={mySeat}
          mySeatIndex={mySeatIndex}
          isSpectator={isSpectator}
          roster={roster}
          autoRefresh={autoRefresh}
          setAutoRefresh={setAutoRefresh}
          actionAmount={actionAmount}
          setActionAmount={setActionAmount}
          loading={loading}
          error={error}
          inviteCopied={inviteCopied}
          leavePending={leavePending}
          toCall={toCall}
          minRaise={minRaise}
          maxBet={maxBet}
          betPresets={betPresets}
          myHandName={myHandName}
          currentPlayer={currentPlayer}
          canAct={canAct}
          winnerNames={winnerNames}
          payoutLines={payoutLines}
          displayName={displayName}
          onCopyInvite={() => void copyInvite()}
          onOpenMyPage={() => openMyPage()}
          onLeaveRoom={leaveRoom}
          onSendAction={(type, amount) => void actions.sendAction(type, amount)}
          doStraddle={doStraddle}
          onToggleStraddle={actions.toggleStraddle}
        />
      )}
      {buyInContext && (
        <BuyInPopup
          tableName={buyInContext.tableName}
          minBuyIn={buyInContext.minBuyIn}
          maxBuyIn={buyInContext.maxBuyIn}
          bigBlind={buyInContext.bigBlind}
          onConfirm={buyInContext.onConfirm}
          onCancel={buyInContext.onCancel}
        />
      )}
      {showMyPage && currentUser && (
        <MyPagePanel
          currentUser={currentUser}
          tables={profileTables}
          history={profileHistory}
          wallet={wallet}
          walletLedger={walletLedger}
          currentTableId={gameId}
          loading={profileLoading}
          error={profileError}
          onClose={() => setShowMyPage(false)}
          onLogout={() => void handleLogout()}
          onUpdateUser={(user) => { setCurrentUser(user); setMyName(user.username) }}
          onClaimDailyBonus={() => void handleClaimDailyBonus()}
        />
      )}
    </div>
  )
}

export default App
